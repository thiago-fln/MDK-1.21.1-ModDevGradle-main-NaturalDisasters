package com.naturaldisasters.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.naturaldisasters.NaturalDisasters;
import com.naturaldisasters.client.IrisCompat;
import com.naturaldisasters.entity.MultivortexEntity;
import com.naturaldisasters.entity.TornadoEntity;
import com.naturaldisasters.entity.TornadoVariant;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the funnel as several translucent, procedurally built shells that widen from the ground into the cloud
 * base. Rotation is shown by scrolling the texture at different speeds per shell, so no real blocks ever spin.
 *
 * The entity pass only queues visible tornadoes; the geometry is drawn later in {@link #drawQueued} (after
 * translucent terrain and particles) without writing depth, so water and fire behind the funnel stay visible.
 */
public class TornadoRenderer extends EntityRenderer<TornadoEntity> {
    public static final ResourceLocation TEXTURE = NaturalDisasters.id("textures/entity/tornado_funnel.png");
    private static final int SEG_V = 32, SEG_A = 24;
    private static final float[] SHELL_RADIUS = {0.72f, 1.0f, 1.28f};
    private static final float[] SHELL_ALPHA = {0.55f, 0.38f, 0.16f};
    private static final float[] SHELL_SPIN = {1.5f, 1.0f, 0.6f};
    private static final int SKY_LIGHT = LightTexture.pack(0, 15);

    private static final List<TornadoEntity> QUEUE = new ArrayList<>();

    private record Palette(float br, float bg, float bb, float tr, float tg, float tb,
                           float dr, float dg, float db, float alpha, boolean glowingCore) {}

    private static final Palette CLASSIC = new Palette(0.42f, 0.37f, 0.31f, 0.55f, 0.56f, 0.58f, 0.40f, 0.33f, 0.25f, 1f, false);
    private static final Palette FIRE = new Palette(1f, 0.55f, 0.15f, 0.25f, 0.2f, 0.18f, 0.2f, 0.16f, 0.14f, 1f, true);
    private static final Palette WATER = new Palette(0.72f, 0.84f, 0.95f, 0.66f, 0.72f, 0.8f, 0.9f, 0.95f, 1f, 0.65f, false);
    private static final Palette ROPE = new Palette(0.62f, 0.62f, 0.64f, 0.66f, 0.67f, 0.7f, 0.5f, 0.45f, 0.38f, 0.9f, false);
    private static final Palette WEDGE = new Palette(0.24f, 0.22f, 0.2f, 0.32f, 0.33f, 0.36f, 0.3f, 0.25f, 0.2f, 1.25f, false);
    private static final Palette LANDSPOUT = new Palette(0.66f, 0.55f, 0.4f, 0.72f, 0.68f, 0.62f, 0.62f, 0.5f, 0.36f, 0.7f, false);
    private static final Palette EL_RENO = new Palette(0.4f, 0.42f, 0.45f, 0.5f, 0.52f, 0.56f, 0.38f, 0.36f, 0.33f, 0.85f, false);

    public TornadoRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0;
    }

    @Override
    public ResourceLocation getTextureLocation(TornadoEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(TornadoEntity e, float yaw, float partialTicks, PoseStack ps, MultiBufferSource buffers, int packedLight) {
        if (e.getScale() <= 0.01f) return;
        if (!QUEUE.contains(e)) QUEUE.add(e); // the cloud collar is always drawn with the clouds
        if (IrisCompat.shaderPackActive()) {
            // shader packs light and shadow entities properly, so draw the funnel in the normal entity pass
            double y = Mth.lerp(partialTicks, e.yo, e.getY());
            draw(e, ps.last(), buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), partialTicks, y);
        }
    }

    /** Draws every tornado queued during the entity pass. Called from the level stage event. */
    public static void drawQueued(PoseStack ps, VertexConsumer vc, Vec3 cam, float partialTicks) {
        if (IrisCompat.shaderPackActive()) return; // already drawn in the entity pass
        for (TornadoEntity e : QUEUE) {
            if (e.isRemoved()) continue;
            double x = Mth.lerp(partialTicks, e.xo, e.getX());
            double y = Mth.lerp(partialTicks, e.yo, e.getY());
            double z = Mth.lerp(partialTicks, e.zo, e.getZ());
            ps.pushPose();
            ps.translate(x - cam.x, y - cam.y, z - cam.z);
            draw(e, ps.last(), vc, partialTicks, y);
            ps.popPose();
        }
    }

    public static List<TornadoEntity> queued() {
        return QUEUE;
    }

    public static void clearQueue() {
        QUEUE.clear();
    }

    private static Palette palette(TornadoVariant v) {
        return switch (v) {
            case FIRENADO -> FIRE;
            case WATERSPOUT -> WATER;
            case ROPE -> ROPE;
            case WEDGE -> WEDGE;
            case LANDSPOUT -> LANDSPOUT;
            case EL_RENO -> EL_RENO;
            default -> CLASSIC;
        };
    }

    private static void draw(TornadoEntity e, PoseStack.Pose pose, VertexConsumer vc, float partialTicks, double y) {
        float scale = e.getScale();
        float time = e.level().getGameTime() + partialTicks;
        float height = (float) (e.getFunnelTopY() - y);
        float core = e.getCoreRadius();
        TornadoVariant variant = e.getVariant();
        Palette pal = palette(variant);
        float fade = Math.min(1f, scale * 1.5f) * pal.alpha;

        if (e instanceof MultivortexEntity m) {
            drawFunnel(vc, pose, e, core * 0.9f, height, time, pal, fade * 0.6f, 0, 0, 1f, false);
            float orbit = m.getSubOrbitRadius();
            for (int i = 0; i < m.getSubVortexCount(); i++) {
                float a = m.getSubVortexAngle(i, time);
                drawFunnel(vc, pose, e, m.getSubVortexCore(), height * 0.55f, time * 1.4f, pal, fade * 0.95f,
                        Mth.cos(a) * orbit, Mth.sin(a) * orbit, 0.55f, true);
            }
        } else {
            drawFunnel(vc, pose, e, core, height, time, pal, fade, 0, 0, 1f, false);
        }

        // debris / spray cloud wrapping the base (landspouts and ropes kick up much less)
        float debrisMul = variant == TornadoVariant.ROPE || variant == TornadoVariant.LANDSPOUT ? 0.5f : 1f;
        float spinU = -time * e.getSpinSpeed() * e.spinDirection() * 0.4f;
        FunnelMesh.cylinder(vc, pose, 0, -0.5f, 0, core * 0.9f, core * 1.35f, core * 1.75f,
                pal.dr, pal.dg, pal.db, 0.32f * fade * debrisMul, 0f, spinU, time * 0.01f, SKY_LIGHT);
        FunnelMesh.cylinder(vc, pose, 0, -0.5f, 0, core * 0.5f, core * 1.1f, core * 1.3f,
                pal.dr, pal.dg, pal.db, 0.4f * fade * debrisMul, 0.05f, spinU * 1.6f, time * 0.02f, SKY_LIGHT);

        // ground effects: a shadow on the ground and a dust skirt racing around the base
        if (variant != TornadoVariant.WATERSPOUT) {
            FunnelMesh.annulus(vc, pose, 0, 0.08f, 0, 0, core * 2.6f, 0f, 0f, 0f, 0f, 0.3f * fade, 0f, SKY_LIGHT);
            float skirtU = -time * e.getSpinSpeed() * e.spinDirection() * 0.6f;
            FunnelMesh.annulus(vc, pose, 0, 0.45f, 0, core * 0.6f, core * 2.4f, -0.6f,
                    pal.dr, pal.dg, pal.db, 0.5f * fade * debrisMul, skirtU, SKY_LIGHT);
            FunnelMesh.annulus(vc, pose, 0, 1.6f, 0, core * 0.8f, core * 2.0f, -1.2f,
                    pal.dr * 1.1f, pal.dg * 1.1f, pal.db * 1.1f, 0.3f * fade * debrisMul, skirtU * 1.5f, SKY_LIGHT);
        }

        if (variant == TornadoVariant.EL_RENO) {
            // rain curtain wrapping the circulation: you rarely get a clear view of it
            FunnelMesh.cylinder(vc, pose, 0, -0.5f, 0, height, core * 1.9f, core * 2.6f,
                    0.55f, 0.58f, 0.62f, 0.28f * fade, 0.12f * fade, -time * 0.002f, -time * 0.03f, SKY_LIGHT);
        }
    }

    private static void drawFunnel(VertexConsumer vc, PoseStack.Pose pose, TornadoEntity e, float coreR, float height,
                                   float time, Palette pal, float alphaMul, float baseX, float baseZ, float swayMul,
                                   boolean defaultProfile) {
        float coreScale = coreR / Math.max(0.01f, e.getCoreRadius());
        float dir = e.spinDirection();
        // horizontal component of the sun direction (+x = east): the sunlit side of the funnel is brighter
        float sunX = -Mth.sin(e.level().getSunAngle(Mth.frac(time)));
        // weak tornadoes: the condensation funnel often doesn't reach the ground, only the debris whirl does
        float gap = defaultProfile ? 0f : Math.max(0f, (3f - e.getIntensity()) / 3f) * 0.22f;
        for (int s = 0; s < SHELL_RADIUS.length; s++) {
            float spin = time * e.getSpinSpeed() * dir * SHELL_SPIN[s];
            float uScroll = -spin / Mth.TWO_PI * 2f;
            float vScroll = -time * 0.012f * (s + 1);
            boolean glow = pal.glowingCore && s == 0;
            int light = glow ? LightTexture.FULL_BRIGHT : SKY_LIGHT;
            for (int i = 0; i < SEG_V; i++) {
                double t0 = i / (double) SEG_V, t1 = (i + 1) / (double) SEG_V;
                float r0 = (defaultProfile ? TornadoEntity.funnelRadius(coreR, t0) : e.funnelRadius(t0) * coreScale) * SHELL_RADIUS[s];
                float r1 = (defaultProfile ? TornadoEntity.funnelRadius(coreR, t1) : e.funnelRadius(t1) * coreScale) * SHELL_RADIUS[s];
                Vec3 o0 = e.funnelOffset(t0, time).scale(swayMul), o1 = e.funnelOffset(t1, time).scale(swayMul);
                float y0 = (float) (t0 * height), y1 = (float) (t1 * height);
                float a0 = Math.min(1f, SHELL_ALPHA[s] * alphaMul * heightAlpha(t0) * gapAlpha(t0, gap));
                float a1 = Math.min(1f, SHELL_ALPHA[s] * alphaMul * heightAlpha(t1) * gapAlpha(t1, gap));
                if (a0 <= 0.003f && a1 <= 0.003f) continue;
                float cr0 = Mth.lerp((float) t0, pal.br, pal.tr), cg0 = Mth.lerp((float) t0, pal.bg, pal.tg), cb0 = Mth.lerp((float) t0, pal.bb, pal.tb);
                float cr1 = Mth.lerp((float) t1, pal.br, pal.tr), cg1 = Mth.lerp((float) t1, pal.bg, pal.tg), cb1 = Mth.lerp((float) t1, pal.bb, pal.tb);
                float v0 = (float) t0 * 4 + vScroll, v1 = (float) t1 * 4 + vScroll;
                float x0 = baseX + (float) o0.x, z0 = baseZ + (float) o0.z;
                float x1 = baseX + (float) o1.x, z1 = baseZ + (float) o1.z;
                for (int j = 0; j < SEG_A; j++) {
                    float ang0 = j / (float) SEG_A * Mth.TWO_PI, ang1 = (j + 1) / (float) SEG_A * Mth.TWO_PI;
                    float c0 = Mth.cos(ang0), s0 = Mth.sin(ang0), c1 = Mth.cos(ang1), s1 = Mth.sin(ang1);
                    float u0 = j / (float) SEG_A * 2 + uScroll, u1 = (j + 1) / (float) SEG_A * 2 + uScroll;
                    // turbulent, rotating bulges so the column is never a perfect cylinder
                    float r00 = r0 * turbulence(ang0, t0, time, dir), r01 = r0 * turbulence(ang1, t0, time, dir);
                    float r11 = r1 * turbulence(ang1, t1, time, dir), r10 = r1 * turbulence(ang0, t1, time, dir);
                    float l0 = 0.84f + 0.16f * c0 * sunX, l1 = 0.84f + 0.16f * c1 * sunX;
                    FunnelMesh.vertex(vc, pose, x0 + c0 * r00, y0, z0 + s0 * r00, cr0 * l0, cg0 * l0, cb0 * l0, a0, u0, v0, light, c0, 0, s0);
                    FunnelMesh.vertex(vc, pose, x0 + c1 * r01, y0, z0 + s1 * r01, cr0 * l1, cg0 * l1, cb0 * l1, a0, u1, v0, light, c1, 0, s1);
                    FunnelMesh.vertex(vc, pose, x1 + c1 * r11, y1, z1 + s1 * r11, cr1 * l1, cg1 * l1, cb1 * l1, a1, u1, v1, light, c1, 0, s1);
                    FunnelMesh.vertex(vc, pose, x1 + c0 * r10, y1, z1 + s0 * r10, cr1 * l0, cg1 * l0, cb1 * l0, a1, u0, v1, light, c0, 0, s0);
                }
            }
        }
    }

    private static float turbulence(float angle, double t, float time, float dir) {
        double v = 1.0
                + 0.07 * Math.sin(angle * 3 + t * 9 - time * 0.08 * dir)
                + 0.05 * Math.sin(angle * 7 - t * 21 + time * 0.13 * dir)
                + 0.04 * Math.sin(t * 31 + time * 0.05);
        return (float) v;
    }

    /** Fades the funnel out below {@code gap} (fraction of the height) so weak funnels hang above the ground. */
    private static float gapAlpha(double t, float gap) {
        if (gap <= 0) return 1f;
        float f = (float) Mth.clamp((t - gap) / 0.08, 0, 1);
        return f * f * (3 - 2 * f);
    }

    private static float heightAlpha(double t) {
        float bottom = (float) Mth.clamp(t / 0.03, 0, 1);
        float top = (float) Mth.clamp((t - 0.8) / 0.2, 0, 1);
        return bottom * (1f - 0.55f * top * top * (3 - 2 * top));
    }
}
