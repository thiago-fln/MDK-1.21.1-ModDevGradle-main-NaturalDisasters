package com.naturaldisasters.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.naturaldisasters.ClientConfig;
import com.naturaldisasters.NaturalDisasters;
import com.naturaldisasters.client.ClientWeatherState;
import com.naturaldisasters.client.IrisCompat;
import com.naturaldisasters.entity.TornadoEntity;
import com.naturaldisasters.entity.TornadoVariant;
import com.naturaldisasters.network.WeatherSyncPayload;
import com.naturaldisasters.weather.StormStage;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Volumetric-looking storm clouds built from hundreds of camera-facing cloud puffs, sorted back to front:
 * a dark flat base, a cumulonimbus tower lit from above, an anvil spreading downwind, rain shafts under the
 * forward flank and, on supercells, a lowered rotating wall cloud. Tornado funnels hang from a rotating
 * cloud collar. Puff layouts are seeded by storm id so they stay stable from frame to frame.
 */
public final class StormCloudRenderer {
    public static final ResourceLocation PUFF = NaturalDisasters.id("textures/environment/cloud_puff.png");
    private static final float BASE_Y = TornadoEntity.CLOUD_BASE_Y;
    private static final float GROUND_Y = 63f;
    private static final double MAX_DISTANCE = 1100;

    /** One billboard. {@code upright} puffs only face the camera horizontally (rain shafts). */
    private record Puff(double x, double y, double z, float w, float h, float r, float g, float b, float a,
                        int variant, float rot, boolean upright, double dist) {}

    private static final List<Puff> PUFFS = new ArrayList<>();

    private StormCloudRenderer() {}

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack ps = event.getPoseStack();

        // 1) tornado funnels (no depth write, so water/fire behind them stay visible)
        if (!TornadoRenderer.queued().isEmpty()) {
            RenderType vapor = ModRenderTypes.vapor(TornadoRenderer.TEXTURE);
            TornadoRenderer.drawQueued(ps, buffers.getBuffer(vapor), cam, partial);
            buffers.endBatch(vapor);
        }

        float time = mc.level.getGameTime() + partial;

        // 2) tsunami fronts
        TsunamiRenderer.render(ps, buffers, cam, ClientWeatherState.latest,
                Math.max(0f, Math.min(60f, time - ClientWeatherState.lastSyncTime)), time);

        // 3) clouds
        PUFFS.clear();
        Vec3 sky = daylight(mc.level.getTimeOfDay(partial)); // day/night only: darkness comes from the clouds themselves
        for (TornadoEntity t : TornadoRenderer.queued()) {
            if (t.isRemoved()) continue;
            addTornadoCollar(t, cam, time, sky, partial);
            addDebrisCloud(t, cam, time, sky, partial);
        }
        TornadoRenderer.clearQueue();

        WeatherSyncPayload weather = ClientWeatherState.latest;
        float sinceSync = Math.max(0f, Math.min(60f, time - ClientWeatherState.lastSyncTime));
        if (ClientConfig.STORM_CLOUDS.get() && weather != null) {
            float dir = weather.southern() ? -1f : 1f;
            for (WeatherSyncPayload.Storm s : weather.storms()) addStorm(s, cam, time, sinceSync, sky, dir);
        }
        if (ClientConfig.REPLACE_VANILLA_CLOUDS.get()) {
            addFairWeather(weather, cam, time, sinceSync, sky, mc.level.getRainLevel(partial));
        }
        if (PUFFS.isEmpty()) return;

        PUFFS.sort((a, b) -> Double.compare(b.dist, a.dist)); // back to front
        boolean shaders = IrisCompat.shaderPackActive();
        RenderType type = shaders ? RenderType.entityTranslucent(PUFF) : ModRenderTypes.cloud(PUFF);
        VertexConsumer vc = buffers.getBuffer(type);
        PoseStack.Pose pose = ps.last();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        for (Puff p : PUFFS) emit(vc, pose, cam, left, up, p, shaders);
        buffers.endBatch(type);
    }

    /** Vanilla-like day/night cloud tint, without the rain darkening (storm darkness is per cloud). */
    private static Vec3 daylight(float timeOfDay) {
        float day = Mth.clamp(Mth.cos(timeOfDay * Mth.TWO_PI) * 2f + 0.5f, 0f, 1f);
        return new Vec3(day * 0.9f + 0.1f, day * 0.9f + 0.1f, day * 0.85f + 0.15f);
    }

    /** Shade of the cloud base for a storm: light grey for plain rain, darker and darker as storms intensify. */
    private static float stormShade(StormStage stage, float energy) {
        if (stage == StormStage.RAIN) return 0.92f - energy * 0.15f;
        return Math.max(0.45f, 0.74f - (energy - 0.3f) * 0.4f);
    }

    // ------------------------------------------------------------------ fair-weather cloud field (replaces vanilla clouds)

    private static final int CELL = 40;
    private static final float FAIR_Y = 192f;

    /**
     * A drifting field of cumulus clouds anchored to cells that move with the wind. Pure white in fair weather,
     * light grey where it rains and dark under thunderstorms; denser close to storms.
     */
    private static void addFairWeather(WeatherSyncPayload weather, Vec3 cam, float time, float sinceSync,
                                       Vec3 sky, float vanillaRain) {
        float windAngle = ClientWeatherState.windAngle();
        double driftX = Math.cos(windAngle) * time * 0.04, driftZ = Math.sin(windAngle) * time * 0.04;
        int range = ClientConfig.CLOUD_RANGE.get();
        float cover = ClientConfig.CLOUD_COVER.get().floatValue();
        float forcedShade = 1f;
        if (weather != null) {
            switch (com.naturaldisasters.weather.ForcedWeather.byId(weather.forcedWeather())) {
                case SUN -> cover = 0.12f;
                case CLOUDY -> { cover = 0.9f; forcedShade = 0.9f; }
                case RAIN -> { cover = 0.97f; forcedShade = 0.8f; }
                case THUNDER -> { cover = 1.1f; forcedShade = 0.52f; }
                default -> {}
            }
        }
        int c0x = Mth.floor((cam.x - driftX - range) / CELL), c1x = Mth.floor((cam.x - driftX + range) / CELL);
        int c0z = Mth.floor((cam.z - driftZ - range) / CELL), c1z = Mth.floor((cam.z - driftZ + range) / CELL);
        double range2 = (double) range * range;
        for (int i = c0x; i <= c1x; i++) {
            for (int j = c0z; j <= c1z; j++) {
                double cx = i * CELL + CELL / 2.0 + driftX, cz = j * CELL + CELL / 2.0 + driftZ;
                double dx = cx - cam.x, dz = cz - cam.z;
                if (dx * dx + dz * dz > range2) continue;

                Random rnd = new Random(i * 341873128712L + j * 132897987541L + 0x5DEECE66DL);
                // clustered coverage: big clear patches and cloudy bands
                float local = cover + 0.3f * (float) (Math.sin(i * 0.23 + Math.cos(j * 0.17)) * Math.cos(j * 0.21 - i * 0.07));
                float shade = Math.min(forcedShade, 1f - vanillaRain * 0.15f);
                if (weather != null) {
                    for (WeatherSyncPayload.Storm s : weather.storms()) {
                        double sx = s.x() + s.vx() * sinceSync, sz = s.z() + s.vz() * sinceSync;
                        double reach = Math.max(40, s.radius()) * 1.7;
                        double d2 = (cx - sx) * (cx - sx) + (cz - sz) * (cz - sz);
                        if (d2 > reach * reach) continue;
                        float f = 1f - (float) (Math.sqrt(d2) / reach);
                        local = Math.max(local, 0.5f + f * 0.5f);
                        shade = Math.min(shade, Mth.lerp(f, 1f, stormShade(StormStage.byId(s.stage()), s.energy()) + 0.08f));
                    }
                }
                if (rnd.nextFloat() >= local) continue;

                double ox = cx + (rnd.nextDouble() - 0.5) * CELL * 0.6, oz = cz + (rnd.nextDouble() - 0.5) * CELL * 0.6;
                int puffs = 3 + rnd.nextInt(5);
                float size = 10 + rnd.nextFloat() * 8;
                for (int k = 0; k < puffs; k++) {
                    double px = ox + (rnd.nextDouble() - 0.5) * size * 2.2, pz = oz + (rnd.nextDouble() - 0.5) * size * 2.2;
                    double py = FAIR_Y + rnd.nextDouble() * 6 + (k == 0 ? 4 : 0);
                    float puffSize = size * (0.7f + rnd.nextFloat() * 0.6f);
                    add(cam, px, py, pz, puffSize * 1.25f, puffSize, sky, shade * (0.94f + rnd.nextFloat() * 0.08f), 0.9f, rnd);
                }
            }
        }
    }

    // ------------------------------------------------------------------ cloud structures

    private static void addStorm(WeatherSyncPayload.Storm s, Vec3 cam, float time, float sinceSync, Vec3 sky, float dir) {
        double cx = s.x() + s.vx() * sinceSync, cz = s.z() + s.vz() * sinceSync;
        double dx = cx - cam.x, dz = cz - cam.z;
        if (dx * dx + dz * dz > MAX_DISTANCE * MAX_DISTANCE) return;

        Random rnd = new Random(s.id() * 341873128712L + 132897987541L);
        float radius = Math.max(30f, s.radius());
        float e = s.energy();
        StormStage stage = StormStage.byId(s.stage());
        double speed = Math.sqrt(s.vx() * s.vx() + s.vz() * s.vz());
        double fx = speed < 1e-4 ? 1 : s.vx() / speed, fz = speed < 1e-4 ? 0 : s.vz() / speed; // forward
        double px = -fz, pz = fx; // right of motion
        float baseShade = stormShade(stage, e); // light grey rain, darker as storms strengthen
        float spin = s.supercell() ? time * 0.0006f * dir : 0f; // the whole supercell base slowly rotates

        // flat, dark base
        int baseCount = Math.min(140, 35 + (int) (radius * 0.6f));
        for (int i = 0; i < baseCount; i++) {
            double a = rnd.nextDouble() * Mth.TWO_PI + spin, d = Math.sqrt(rnd.nextDouble()) * radius;
            float size = 16 + rnd.nextFloat() * 16 + radius * 0.06f;
            float shade = baseShade * (0.88f + rnd.nextFloat() * 0.18f);
            add(cam, cx + Math.cos(a) * d, BASE_Y + rnd.nextFloat() * 9, cz + Math.sin(a) * d, size * 1.4f, size,
                    sky, shade, 0.82f, rnd);
        }

        // rain shafts below the forward flank
        int shafts = 2 + (int) (e * 4);
        for (int i = 0; i < shafts; i++) {
            double off = (rnd.nextDouble() * 0.6 + 0.05) * radius, side = (rnd.nextDouble() - 0.5) * radius * 0.9;
            double x = cx + fx * off + px * side, z = cz + fz * off + pz * side;
            float h = (BASE_Y - GROUND_Y) / 2f;
            float shade = baseShade * 0.9f;
            addUpright(cam, x, GROUND_Y + h, z, radius * (0.18f + rnd.nextFloat() * 0.15f), h, sky, shade, 0.18f + e * 0.17f, rnd);
        }

        if (stage.ordinal() >= StormStage.THUNDERSTORM.ordinal()) {
            // cumulonimbus tower, lit from the top, slightly tilted downwind
            float towerH = 45 + e * 110;
            float towerR = radius * 0.42f;
            int towerCount = 25 + (int) (e * 55);
            for (int i = 0; i < towerCount; i++) {
                float hf = (float) Math.pow(rnd.nextDouble(), 0.8);
                double a = rnd.nextDouble() * Mth.TWO_PI, d = Math.sqrt(rnd.nextDouble()) * towerR * (0.65f + 0.35f * hf);
                double tilt = hf * towerH * 0.25;
                float size = 18 + rnd.nextFloat() * 22;
                float shade = Mth.lerp(hf, baseShade * 1.15f, 1.0f) * (0.9f + rnd.nextFloat() * 0.12f);
                add(cam, cx + Math.cos(a) * d + fx * tilt, BASE_Y + 10 + hf * towerH, cz + Math.sin(a) * d + fz * tilt,
                        size, size, sky, shade, 0.9f, rnd);
            }
            // anvil spreading downwind at the top
            if (e > 0.4f) {
                int anvilCount = 25 + (int) (e * 35);
                double ax = cx + fx * radius * 0.6, az = cz + fz * radius * 0.6;
                for (int i = 0; i < anvilCount; i++) {
                    double along = (rnd.nextDouble() - 0.35) * radius * 1.6, across = (rnd.nextDouble() - 0.5) * radius * 1.2;
                    float size = 24 + rnd.nextFloat() * 22;
                    add(cam, ax + fx * along + px * across, BASE_Y + 10 + towerH + rnd.nextFloat() * 8,
                            az + fz * along + pz * across, size * 1.6f, size * 0.7f, sky, 0.9f + rnd.nextFloat() * 0.1f, 0.72f, rnd);
                }
            }
        }

        if (stage.ordinal() >= StormStage.WALL_CLOUD.ordinal()) {
            // lowered, visibly rotating wall cloud in the rear flank
            double wx = cx - fx * radius * 0.35 + px * radius * 0.15, wz = cz - fz * radius * 0.35 + pz * radius * 0.15;
            float wr = Math.max(20f, radius * 0.28f);
            float rotation = time * 0.012f * dir;
            for (int i = 0; i < 40; i++) {
                double a = i / 40.0 * Mth.TWO_PI + rotation + rnd.nextDouble() * 0.3;
                double d = wr * (0.25 + 0.75 * rnd.nextDouble());
                double lower = 4 + rnd.nextDouble() * 10 + (1 - d / wr) * 9;
                float size = 10 + rnd.nextFloat() * 10;
                add(cam, wx + Math.cos(a) * d, BASE_Y - lower, wz + Math.sin(a) * d, size, size * 0.8f,
                        sky, baseShade * 0.85f, 0.92f, rnd);
            }
        }
    }

    /**
     * The billowing dust / debris cloud at the base of the tornado: a wide dome of puffs that is constantly
     * reborn near the ground, swirls with the vortex, rises and spreads outwards as it fades. Its size grows
     * with the tornado's category; colour depends on what is being lifted (dust, smoke, sea spray).
     */
    private static void addDebrisCloud(TornadoEntity t, Vec3 cam, float time, Vec3 sky, float partial) {
        TornadoVariant v = t.getVariant();
        float r, g, b;
        switch (v) {
            case FIRENADO -> { r = 0.22f; g = 0.19f; b = 0.17f; }
            case WATERSPOUT -> { r = 0.92f; g = 0.95f; b = 1f; }
            case LANDSPOUT -> { r = 0.78f; g = 0.66f; b = 0.5f; }
            default -> { r = 0.62f; g = 0.5f; b = 0.37f; }
        }
        float scale = t.getScale();
        if (scale < 0.05f) return;
        double x = Mth.lerp(partial, t.xo, t.getX()), y = Mth.lerp(partial, t.yo, t.getY()), z = Mth.lerp(partial, t.zo, t.getZ());
        float core = t.getCoreRadius();
        float intensity = t.getIntensity();
        boolean small = v == TornadoVariant.ROPE || v == TornadoVariant.LANDSPOUT;
        float domeR = core * (1.6f + intensity * 0.35f) * (small ? 0.6f : 1f);   // how far out it spreads
        float domeH = core * (0.8f + intensity * 0.3f) * (small ? 0.5f : 1f);    // how high it climbs
        int count = (int) ((40 + intensity * 22) * (small ? 0.5f : 1f) * Math.min(1f, scale * 1.5f));
        float dir = t.spinDirection();
        double axisDist = Math.sqrt(cam.distanceToSqr(x, cam.y, z));
        for (int i = 0; i < count; i++) {
            Random rnd = new Random(t.getId() * 7919L + i * 104729L + 31L); // per puff, so skipped puffs don't shift the rest
            float seed = rnd.nextFloat();
            float period = 60f + rnd.nextFloat() * 50f;            // ticks for one rise-and-spread cycle
            float p = Mth.frac(time / period + seed);               // 0 = born at the ground, 1 = dissipated
            float r0 = core * (0.5f + rnd.nextFloat() * 0.8f);
            float radius = r0 + p * (domeR - r0);
            // rotates fast near the funnel, slower as it spreads out
            float angle = rnd.nextFloat() * Mth.TWO_PI + dir * (time * t.getSpinSpeed() * 0.35f * (core / Math.max(core, radius)) + p * 2f);
            float rise = domeH * (float) Math.sqrt(p) * (0.6f + rnd.nextFloat() * 0.6f);
            double px = x + Mth.cos(angle) * radius, pz = z + Mth.sin(angle) * radius;
            double py = y + 0.5 + rise * (1f - 0.35f * (radius / domeR));
            float size = (core * 0.35f + 3f) * (0.5f + p * 1.3f) * (0.8f + rnd.nextFloat() * 0.5f);
            float alpha = 0.85f * Math.min(1f, p * 6f) * (1f - p) * (1f - p * 0.3f) * Math.min(1f, scale * 1.5f);
            // puffs behind the funnel are drawn after it, so keep them faint to not cover it
            double puffDist = Math.sqrt(cam.distanceToSqr(px, cam.y, pz));
            if (puffDist > axisDist + core * 0.5) alpha *= 0.45f;
            if (alpha < 0.02f) continue;
            float shade = (0.75f + 0.25f * (float) (py - y) / Math.max(1f, domeH)) * (0.9f + rnd.nextFloat() * 0.15f);
            Vec3 tint = new Vec3(sky.x * r, sky.y * g, sky.z * b);
            add(cam, px, py, pz, size * 1.15f, size, tint, shade, alpha, rnd);
        }
    }

    /** Rotating cloud collar the funnel hangs from. */
    private static void addTornadoCollar(TornadoEntity t, Vec3 cam, float time, Vec3 sky, float partial) {
        Random rnd = new Random(t.getId() * 9182371L + 77L);
        double x = Mth.lerp(partial, t.xo, t.getX()), z = Mth.lerp(partial, t.zo, t.getZ());
        Vec3 off = t.funnelOffset(1, time);
        double top = t.getFunnelTopY();
        float r = Math.max(14f, t.funnelRadius(1) * 1.25f);
        float rotation = time * t.getSpinSpeed() * t.spinDirection() * 0.08f;
        boolean small = t.getVariant() == TornadoVariant.LANDSPOUT || t.getVariant() == TornadoVariant.ROPE;
        int count = small ? 24 : 48;
        float alpha = 0.92f * Math.min(1f, t.getScale() * 1.5f);
        for (int i = 0; i < count; i++) {
            double a = rnd.nextDouble() * Mth.TWO_PI + rotation;
            double d = r * (0.3 + rnd.nextDouble() * 1.3);
            double lower = (1 - Math.min(1, d / (r * 1.6))) * 10 + rnd.nextDouble() * 4;
            float size = 10 + rnd.nextFloat() * 10 + r * 0.12f;
            add(cam, x + off.x + Math.cos(a) * d, top - lower, z + off.z + Math.sin(a) * d, size, size * 0.8f,
                    sky, 0.48f + rnd.nextFloat() * 0.08f, alpha, rnd);
        }
    }

    // ------------------------------------------------------------------ puff helpers

    private static void add(Vec3 cam, double x, double y, double z, float w, float h, Vec3 sky, float shade, float alpha, Random rnd) {
        PUFFS.add(new Puff(x, y, z, w, h, c(sky.x * shade), c(sky.y * shade), c(sky.z * shade), alpha,
                rnd.nextInt(4), (rnd.nextFloat() - 0.5f) * 0.6f, false, cam.distanceToSqr(x, y, z)));
    }

    /**
     * Clamps a colour channel to [0, 1]. Vertex colours are packed into bytes, so a value even slightly above 1
     * wraps around to nearly 0 - that is what made some puffs render pitch black.
     */
    private static float c(double v) {
        return (float) Math.max(0.0, Math.min(1.0, v));
    }

    private static void addUpright(Vec3 cam, double x, double y, double z, float w, float h, Vec3 sky, float shade, float alpha, Random rnd) {
        PUFFS.add(new Puff(x, y, z, w, h, c(sky.x * shade), c(sky.y * shade), c(sky.z * shade * 1.05),
                alpha, rnd.nextInt(4), 0f, true, cam.distanceToSqr(x, y, z)));
    }

    private static void emit(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, Vector3f left, Vector3f up, Puff p, boolean entity) {
        float x = (float) (p.x - cam.x), y = (float) (p.y - cam.y), z = (float) (p.z - cam.z);
        float ax, ay, az, bx, by, bz;
        if (p.upright) {
            // cylindrical billboard: horizontal axis faces the camera, vertical axis stays vertical
            float lx = left.x(), lz = left.z(), len = Mth.sqrt(lx * lx + lz * lz);
            if (len < 1e-4f) { lx = 1; lz = 0; len = 1; }
            ax = lx / len * p.w; ay = 0; az = lz / len * p.w;
            bx = 0; by = p.h; bz = 0;
        } else {
            float c = Mth.cos(p.rot), s = Mth.sin(p.rot);
            ax = (left.x() * c + up.x() * s) * p.w; ay = (left.y() * c + up.y() * s) * p.w; az = (left.z() * c + up.z() * s) * p.w;
            bx = (up.x() * c - left.x() * s) * p.h; by = (up.y() * c - left.y() * s) * p.h; bz = (up.z() * c - left.z() * s) * p.h;
        }
        float u0 = (p.variant % 2) * 0.5f, v0 = (p.variant / 2) * 0.5f, u1 = u0 + 0.5f, v1 = v0 + 0.5f;
        // left is +u on screen-left, so start from the right edge
        vertex(vc, pose, x - ax - bx, y - ay - by, z - az - bz, u1, v1, p, entity);
        vertex(vc, pose, x + ax - bx, y + ay - by, z + az - bz, u0, v1, p, entity);
        vertex(vc, pose, x + ax + bx, y + ay + by, z + az + bz, u0, v0, p, entity);
        vertex(vc, pose, x - ax + bx, y - ay + by, z - az + bz, u1, v0, p, entity);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, Puff p, boolean entity) {
        if (entity) {
            // shader-pack path: regular entity vertex format, fully lit so the pack decides the shading
            FunnelMesh.vertex(vc, pose, x, y, z, p.r, p.g, p.b, p.a, u, v, LightTexture.FULL_BRIGHT, 0, 1, 0);
        } else {
            vc.addVertex(pose, x, y, z).setUv(u, v).setColor(p.r, p.g, p.b, p.a);
        }
    }
}
