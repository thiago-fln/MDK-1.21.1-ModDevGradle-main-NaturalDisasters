package com.naturaldisasters.client.screen;

import com.naturaldisasters.client.ClientWeatherState;
import com.naturaldisasters.entity.TornadoVariant;
import com.naturaldisasters.menu.RadarMenu;
import com.naturaldisasters.network.RadarGridPayload;
import com.naturaldisasters.network.WeatherSyncPayload;
import com.naturaldisasters.weather.StormStage;
import com.naturaldisasters.weather.WeatherEngine;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * Weather radar display. Reflectivity mode paints storm cells as a heat map (green → yellow → red → magenta)
 * with hook echoes on supercells and debris signatures on tornadoes; thermal mode shows the temperature field
 * streamed by the server with storm outlines on top. Covers a 500 block radius around the radar block.
 */
public class RadarScreen extends AbstractContainerScreen<RadarMenu> {
    private static final int MAP = 180, CELLS = 60, CELL = MAP / CELLS;
    private static final int[] DBZ_COLORS = {0xFF0B5E1E, 0xFF13A52C, 0xFF27E33E, 0xFFF0E321, 0xFFF59A16, 0xFFE5231B, 0xFFB5121B, 0xFFD02BD4};
    private static final float[] DBZ_STEPS = {0.07f, 0.18f, 0.3f, 0.42f, 0.55f, 0.68f, 0.8f, 0.92f};
    private static final float[] TEMP_STOPS = {-10f, 0f, 12f, 22f, 32f, 45f};
    private static final int[] TEMP_COLORS = {0x3048FF, 0x2FB5FF, 0x2EE59A, 0xC8EB2E, 0xFFA51F, 0xFF2B1F};

    private boolean thermal;

    public RadarScreen(RadarMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 316;
        this.imageHeight = 214;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("gui.naturaldisasters.radar.mode"), b -> thermal = !thermal)
                .bounds(leftPos + 196, topPos + 190, 112, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 7, 0xFF9CFFB0, false);
        Component mode = Component.translatable(thermal ? "gui.naturaldisasters.radar.thermal" : "gui.naturaldisasters.radar.reflectivity");
        g.drawString(font, mode, 8 + MAP - font.width(mode), 7, 0xFF7FA88A, false);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x0 = leftPos, y0 = topPos;
        g.fill(x0, y0, x0 + imageWidth, y0 + imageHeight, 0xFF2A2F36);
        g.fill(x0 + 1, y0 + 1, x0 + imageWidth - 1, y0 + imageHeight - 1, 0xFF161A1F);
        int mx = x0 + 8, my = y0 + 18;
        g.fill(mx - 1, my - 1, mx + MAP + 1, my + MAP + 1, 0xFF3C6E47);
        g.fill(mx, my, mx + MAP, my + MAP, 0xFF04110A);

        float time = minecraft.level.getGameTime() + partialTick;
        float since = Mth.clamp(time - ClientWeatherState.lastSyncTime, 0f, 60f);
        WeatherSyncPayload w = ClientWeatherState.latest;
        BlockPos c = menu.getPos();
        float scale = (MAP / 2f) / RadarMenu.RADIUS;

        for (int i = 0; i < CELLS; i++) {
            for (int j = 0; j < CELLS; j++) {
                double wx = c.getX() + ((i + 0.5) / CELLS * 2 - 1) * RadarMenu.RADIUS;
                double wz = c.getZ() + ((j + 0.5) / CELLS * 2 - 1) * RadarMenu.RADIUS;
                float refl = w == null ? 0 : reflectivity(w, wx, wz, since, time);
                int color;
                if (thermal) {
                    color = thermalColor(wx, wz);
                    if (refl > 0.3f) color = darken(color, 0.55f);
                } else {
                    color = dbzColor(refl);
                    float sand = w == null ? 0 : sandstorm(w, wx, wz);
                    if (sand > 0.1f) color = blend(color == 0 ? 0xFF04110A : color, 0xFFD9B26A, Math.min(0.85f, sand));
                }
                if (color != 0) {
                    int px = mx + i * CELL, py = my + j * CELL;
                    g.fill(px, py, px + CELL, py + CELL, color);
                }
            }
        }

        drawRangeRings(g, mx + MAP / 2, my + MAP / 2, scale);
        drawSweep(g, mx + MAP / 2, my + MAP / 2, time);
        if (w != null) drawMarkers(g, w, mx, my, c, scale, since, time);
        int cx = mx + MAP / 2, cy = my + MAP / 2;
        g.fill(cx - 3, cy, cx + 4, cy + 1, 0xFFFFFFFF);
        g.fill(cx, cy - 3, cx + 1, cy + 4, 0xFFFFFFFF);
        g.drawString(font, "N", cx - 2, my + 2, 0xFFB8FFC6, false);
        g.drawString(font, "S", cx - 2, my + MAP - 10, 0xFFB8FFC6, false);
        g.drawString(font, "W", mx + 2, cy - 4, 0xFFB8FFC6, false);
        g.drawString(font, "E", mx + MAP - 7, cy - 4, 0xFFB8FFC6, false);

        drawLegend(g, mx, my + MAP + 3);
        drawInfo(g, x0 + 196, y0 + 18, w, c, since);
    }

    // ------------------------------------------------------------------ fields

    private static float reflectivity(WeatherSyncPayload w, double wx, double wz, float since, float time) {
        float v = 0;
        for (WeatherSyncPayload.Storm s : w.storms()) {
            double sx = s.x() + s.vx() * since, sz = s.z() + s.vz() * since;
            double dx = wx - sx, dz = wz - sz, r = Math.max(10, s.radius());
            double d2 = (dx * dx + dz * dz) / (r * r);
            if (d2 > 4) continue;
            double band = 0.85 + 0.15 * Math.sin(Math.atan2(dz, dx) * 3 + Math.sqrt(d2) * 6 - time * 0.02);
            double val = (0.15 + s.energy()) * Math.exp(-d2 * 2.2) * band;
            if (s.supercell() && s.stage() >= StormStage.WALL_CLOUD.ordinal()) {
                // hook echo wrapping around the rear-flank mesocyclone
                double speed = Math.max(1e-4, Math.sqrt(s.vx() * s.vx() + s.vz() * s.vz()));
                double fx = s.vx() / speed, fz = s.vz() / speed;
                double hx = sx - fx * r * 0.45 - fz * r * 0.35, hz = sz - fz * r * 0.45 + fx * r * 0.35;
                double hd = ((wx - hx) * (wx - hx) + (wz - hz) * (wz - hz)) / (r * r * 0.04);
                val = Math.max(val, s.energy() * 0.9 * Math.exp(-hd));
            }
            v = (float) (1 - (1 - v) * (1 - Math.min(1, val)));
        }
        for (WeatherSyncPayload.Tornado t : w.tornadoes()) {
            double dx = wx - t.x(), dz = wz - t.z();
            double d2 = (dx * dx + dz * dz) / 196.0;
            if (d2 < 4) v = Math.max(v, (float) (1.05 * Math.exp(-d2)));
        }
        return v;
    }

    private static float sandstorm(WeatherSyncPayload w, double wx, double wz) {
        float v = 0;
        for (WeatherSyncPayload.Zone z : w.sandstorms()) {
            double dx = wx - z.x(), dz = wz - z.z();
            double d2 = (dx * dx + dz * dz) / (z.radius() * z.radius());
            if (d2 < 2) v = Math.max(v, (float) (z.strength() * Math.exp(-d2 * 1.5)));
        }
        return v;
    }

    private static int dbzColor(float v) {
        int color = 0;
        for (int i = 0; i < DBZ_STEPS.length; i++) if (v >= DBZ_STEPS[i]) color = DBZ_COLORS[i];
        return color;
    }

    private int thermalColor(double wx, double wz) {
        RadarGridPayload grid = ClientWeatherState.grid;
        if (grid == null || grid.size() < 2) return 0xFF10202A;
        double fx = (wx - (grid.centerX() - grid.radius())) / (grid.radius() * 2.0) * (grid.size() - 1);
        double fz = (wz - (grid.centerZ() - grid.radius())) / (grid.radius() * 2.0) * (grid.size() - 1);
        fx = Mth.clamp(fx, 0, grid.size() - 1.001);
        fz = Mth.clamp(fz, 0, grid.size() - 1.001);
        int ix = (int) fx, iz = (int) fz;
        double ax = fx - ix, az = fz - iz;
        int n = grid.size();
        float[] t = grid.temperature();
        double top = Mth.lerp(ax, t[iz * n + ix], t[iz * n + ix + 1]);
        double bottom = Mth.lerp(ax, t[(iz + 1) * n + ix], t[(iz + 1) * n + ix + 1]);
        return 0xFF000000 | tempRgb((float) Mth.lerp(az, top, bottom));
    }

    private static int tempRgb(float temp) {
        if (temp <= TEMP_STOPS[0]) return TEMP_COLORS[0];
        for (int i = 1; i < TEMP_STOPS.length; i++) {
            if (temp <= TEMP_STOPS[i]) {
                float f = (temp - TEMP_STOPS[i - 1]) / (TEMP_STOPS[i] - TEMP_STOPS[i - 1]);
                return lerpRgb(TEMP_COLORS[i - 1], TEMP_COLORS[i], f);
            }
        }
        return TEMP_COLORS[TEMP_COLORS.length - 1];
    }

    // ------------------------------------------------------------------ overlays

    private void drawRangeRings(GuiGraphics g, int cx, int cy, float scale) {
        for (int r = 100; r <= RadarMenu.RADIUS; r += 100) {
            float pr = r * scale;
            int points = (int) (pr * 2.5f);
            int color = r == RadarMenu.RADIUS ? 0x9040FF60 : 0x5040FF60;
            for (int k = 0; k < points; k++) {
                float a = k / (float) points * Mth.TWO_PI;
                int px = cx + Math.round(Mth.cos(a) * pr), py = cy + Math.round(Mth.sin(a) * pr);
                g.fill(px, py, px + 1, py + 1, color);
            }
        }
        g.fill(cx - MAP / 2, cy, cx + MAP / 2, cy + 1, 0x2040FF60);
        g.fill(cx, cy - MAP / 2, cx + 1, cy + MAP / 2, 0x2040FF60);
    }

    private void drawSweep(GuiGraphics g, int cx, int cy, float time) {
        float base = time * 0.06f;
        for (int trail = 0; trail < 8; trail++) {
            float a = base - trail * 0.04f;
            int alpha = 0x90 - trail * 0x10;
            float c = Mth.cos(a), s = Mth.sin(a);
            for (int k = 0; k < MAP / 2; k++) {
                int px = cx + Math.round(c * k), py = cy + Math.round(s * k);
                g.fill(px, py, px + 1, py + 1, (alpha << 24) | 0x60FF80);
            }
        }
    }

    private void drawMarkers(GuiGraphics g, WeatherSyncPayload w, int mx, int my, BlockPos c, float scale, float since, float time) {
        g.enableScissor(mx, my, mx + MAP, my + MAP);
        for (WeatherSyncPayload.Zone q : w.earthquakes()) {
            int px = mx + MAP / 2 + Math.round((q.x() - c.getX()) * scale);
            int py = my + MAP / 2 + Math.round((q.z() - c.getZ()) * scale);
            float pulse = (time % 30) / 30f;
            float pr = (4 + pulse * 14);
            for (int k = 0; k < 40; k++) {
                float a = k / 40f * Mth.TWO_PI;
                int x = px + Math.round(Mth.cos(a) * pr), y = py + Math.round(Mth.sin(a) * pr);
                g.fill(x, y, x + 1, y + 1, 0xFFFF9020);
            }
            g.drawString(font, "M" + String.format("%.1f", q.strength()), px + 4, py - 10, 0xFFFFB050, true);
        }
        for (WeatherSyncPayload.Tornado t : w.tornadoes()) {
            int px = mx + MAP / 2 + Math.round((t.x() - c.getX()) * scale);
            int py = my + MAP / 2 + Math.round((t.z() - c.getZ()) * scale);
            boolean blink = ((int) (time / 6)) % 2 == 0;
            int color = blink ? 0xFFFF2020 : 0xFFFFFFFF;
            for (int k = 0; k < 6; k++) g.fill(px - (5 - k), py - 4 + k, px + (5 - k) + 1, py - 3 + k, color);
            TornadoVariant variant = TornadoVariant.byId(t.variant());
            String label = "F" + t.category() + (variant == TornadoVariant.CLASSIC ? "" : " " + variant.shortCode);
            g.pose().pushPose();
            g.pose().translate(px + 6, py - 9, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, label, 0, 0, 0xFFFFFFFF, true);
            g.pose().popPose();
        }
        for (WeatherSyncPayload.Storm s : w.storms()) {
            if (s.stage() != StormStage.TORNADO_WARNING.ordinal()) continue;
            int px = mx + MAP / 2 + Math.round((s.x() + s.vx() * since - c.getX()) * scale);
            int py = my + MAP / 2 + Math.round((s.z() + s.vz() * since - c.getZ()) * scale);
            // tornado warning polygon: box ahead of the storm along its track
            int half = Math.max(6, Math.round(s.radius() * scale * 0.6f));
            int color = ((int) (time / 8)) % 2 == 0 ? 0xFFFF3030 : 0xFFAA0000;
            g.renderOutline(px - half, py - half, half * 2, half * 2, color);
        }
        for (WeatherSyncPayload.Wave wave : w.tsunamis()) {
            double wx = wave.x() + wave.dirX() * wave.speed() * since, wz = wave.z() + wave.dirZ() * wave.speed() * since;
            int steps = Math.max(8, (int) (wave.width() * scale));
            int color = ((int) (time / 6)) % 2 == 0 ? 0xFF35C8FF : 0xFFFFFFFF;
            for (int k = 0; k <= steps; k++) {
                double t = k / (double) steps - 0.5;
                int px = mx + MAP / 2 + (int) Math.round((wx - wave.dirZ() * t * wave.width() - c.getX()) * scale);
                int py = my + MAP / 2 + (int) Math.round((wz + wave.dirX() * t * wave.width() - c.getZ()) * scale);
                g.fill(px - 1, py - 1, px + 1, py + 1, color);
            }
            int ax = mx + MAP / 2 + (int) Math.round((wx - c.getX()) * scale), az = my + MAP / 2 + (int) Math.round((wz - c.getZ()) * scale);
            for (int k = 1; k <= 6; k++) {
                g.fill(ax + Math.round(wave.dirX() * k), az + Math.round(wave.dirZ() * k), ax + Math.round(wave.dirX() * k) + 1,
                        az + Math.round(wave.dirZ() * k) + 1, color);
            }
        }
        g.disableScissor();
    }

    private void drawLegend(GuiGraphics g, int x, int y) {
        if (thermal) {
            for (int i = 0; i < MAP; i++) {
                float temp = Mth.lerp(i / (float) MAP, -10f, 45f);
                g.fill(x + i, y, x + i + 1, y + 6, 0xFF000000 | tempRgb(temp));
            }
            return;
        }
        int w = MAP / DBZ_COLORS.length;
        for (int i = 0; i < DBZ_COLORS.length; i++) g.fill(x + i * w, y, x + (i + 1) * w, y + 6, DBZ_COLORS[i]);
    }

    private void drawInfo(GuiGraphics g, int x, int y, WeatherSyncPayload w, BlockPos c, float since) {
        int line = 11;
        if (w == null) {
            g.drawString(font, Component.translatable("gui.naturaldisasters.radar.no_data"), x, y, 0xFFAAAAAA, false);
            return;
        }
        g.drawString(font, Component.translatable("gui.naturaldisasters.radar.temperature", String.format("%.1f", w.temperature())), x, y, 0xFFE0E0E0, false);
        g.drawString(font, Component.translatable("gui.naturaldisasters.radar.humidity", Math.round(w.humidity() * 100)), x, y + line, 0xFFE0E0E0, false);
        g.drawString(font, Component.translatable("gui.naturaldisasters.radar.pressure", Math.round(w.pressure())), x, y + line * 2, 0xFFE0E0E0, false);
        Component windDir = Component.translatable(WeatherEngine.compassKey(0, 0, Math.cos(w.windAngle()), Math.sin(w.windAngle())));
        g.drawString(font, Component.translatable("gui.naturaldisasters.radar.wind", String.format("%.1f", w.windSpeed() * 20), windDir), x, y + line * 3, 0xFFE0E0E0, false);

        Component enso = w.ensoPhase() == 0
                ? Component.translatable("enso.naturaldisasters.neutral")
                : Component.translatable("gui.naturaldisasters.radar.enso", Component.translatable(
                        com.naturaldisasters.weather.Enso.Phase.byId(w.ensoPhase()).key()), Math.round(w.ensoStrength() * 100));
        g.drawString(font, enso, x, y + line * 4, w.ensoPhase() == 1 ? 0xFFFFB347 : w.ensoPhase() == 2 ? 0xFF7FB2FF : 0xFFAAAAAA, false);

        int yy = y + line * 5 + 6;
        g.fill(x, yy - 4, x + 112, yy - 3, 0xFF3C6E47);

        StormStage worst = null;
        int stormsInRange = 0;
        double r2 = (double) RadarMenu.RADIUS * RadarMenu.RADIUS;
        for (WeatherSyncPayload.Storm s : w.storms()) {
            double dx = s.x() + s.vx() * since - c.getX(), dz = s.z() + s.vz() * since - c.getZ();
            if (dx * dx + dz * dz > r2) continue;
            stormsInRange++;
            StormStage st = StormStage.byId(s.stage());
            if (worst == null || st.ordinal() > worst.ordinal()) worst = st;
        }
        g.drawString(font, Component.translatable("gui.naturaldisasters.radar.storms", stormsInRange), x, yy, 0xFFE0E0E0, false);
        yy += line;
        Component threat;
        int threatColor;
        if (worst == null) {
            threat = Component.translatable("gui.naturaldisasters.radar.clear");
            threatColor = 0xFF55FF55;
        } else {
            threat = Component.translatable(worst.translationKey());
            threatColor = switch (worst) {
                case RAIN -> 0xFF55FF55;
                case THUNDERSTORM -> 0xFFFFFF55;
                case WALL_CLOUD -> 0xFFFFAA00;
                case TORNADO_WARNING -> 0xFFFF5555;
            };
        }
        g.drawString(font, threat, x, yy, threatColor, false);
        yy += line;

        WeatherSyncPayload.Tornado nearest = null;
        double best = Double.MAX_VALUE;
        for (WeatherSyncPayload.Tornado t : w.tornadoes()) {
            double dx = t.x() - c.getX(), dz = t.z() - c.getZ();
            double d = dx * dx + dz * dz;
            if (d < best && d <= r2) {
                best = d;
                nearest = t;
            }
        }
        if (nearest != null) {
            Component dir = Component.translatable(WeatherEngine.compassKey(c.getX(), c.getZ(), nearest.x(), nearest.z()));
            Component variant = Component.translatable("radar.naturaldisasters.variant." + TornadoVariant.byId(nearest.variant()).id());
            g.drawString(font, Component.translatable("gui.naturaldisasters.radar.tornado", nearest.category(), (int) Math.sqrt(best), dir)
                    .withStyle(ChatFormatting.RED), x, yy, 0xFFFF5555, false);
            yy += line;
            g.drawString(font, variant, x, yy, 0xFFFF9999, false);
            yy += line;
        }
        if (w.floodLevel() >= 0.5f) {
            g.drawString(font, Component.translatable("gui.naturaldisasters.radar.flood", String.format("%.1f", w.floodLevel())), x, yy, 0xFF55AAFF, false);
            yy += line;
        }
        for (WeatherSyncPayload.Zone s : w.sandstorms()) {
            double dx = s.x() - c.getX(), dz = s.z() - c.getZ();
            if (dx * dx + dz * dz < r2) {
                g.drawString(font, Component.translatable("gui.naturaldisasters.radar.sandstorm"), x, yy, 0xFFE8C27A, false);
                yy += line;
                break;
            }
        }
        for (WeatherSyncPayload.Wave wave : w.tsunamis()) {
            double dx = wave.x() - c.getX(), dz = wave.z() - c.getZ();
            Component dir = Component.translatable(WeatherEngine.compassKey(c.getX(), c.getZ(), wave.x(), wave.z()));
            g.drawString(font, Component.translatable("gui.naturaldisasters.radar.tsunami", (int) Math.sqrt(dx * dx + dz * dz), dir),
                    x, yy, 0xFF35C8FF, false);
            yy += line;
        }
        if (!w.earthquakes().isEmpty()) {
            g.drawString(font, Component.translatable("gui.naturaldisasters.radar.earthquake"), x, yy, 0xFFFF9020, false);
        }
    }

    // ------------------------------------------------------------------ colour helpers

    private static int lerpRgb(int a, int b, float f) {
        int r = (int) Mth.lerp(f, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int gr = (int) Mth.lerp(f, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(f, a & 0xFF, b & 0xFF);
        return (r << 16) | (gr << 8) | bl;
    }

    private static int blend(int base, int over, float f) {
        return 0xFF000000 | lerpRgb(base & 0xFFFFFF, over & 0xFFFFFF, f);
    }

    private static int darken(int color, float f) {
        int r = (int) (((color >> 16) & 0xFF) * f), gr = (int) (((color >> 8) & 0xFF) * f), b = (int) ((color & 0xFF) * f);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }
}
