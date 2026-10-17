package com.naturaldisasters.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.naturaldisasters.network.WeatherSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Draws tsunami fronts as a long curling wall of water (vanilla water texture, tinted) with a white foam crest.
 * The cross-section rises gently from behind, peaks and drops steeply in front. Segments where the terrain is
 * higher than the wave are skipped, so the wave visibly breaks against hills.
 */
public final class TsunamiRenderer {
    private static final ResourceLocation WATER = ResourceLocation.withDefaultNamespace("block/water_still");
    /** Cross-section: distance along the travel direction (blocks, + = ahead) and height fraction. */
    private static final float[][] PROFILE = {
            {-46, 0f}, {-26, 0.25f}, {-14, 0.6f}, {-7, 0.88f}, {-3, 1f}, {0, 0.97f}, {1.6f, 0.8f}, {2.8f, 0.5f}, {3.6f, 0.2f}, {4.2f, 0f}
    };
    private static final int SEGMENTS = 64;

    private TsunamiRenderer() {}

    public static void render(PoseStack ps, MultiBufferSource.BufferSource buffers, Vec3 cam, WeatherSyncPayload weather,
                              float sinceSync, float time) {
        if (weather == null || weather.tsunamis().isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        TextureAtlasSprite sprite = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(WATER);
        RenderType type = ModRenderTypes.vapor(InventoryMenu.BLOCK_ATLAS);
        VertexConsumer vc = buffers.getBuffer(type);
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        PoseStack.Pose pose = ps.last();
        int light = LightTexture.pack(0, 15);
        for (WeatherSyncPayload.Wave w : weather.tsunamis()) drawWave(vc, pose, level, w, sinceSync, time, sprite, light);
        ps.popPose();
        buffers.endBatch(type);
    }

    private static void drawWave(VertexConsumer vc, PoseStack.Pose pose, ClientLevel level, WeatherSyncPayload.Wave w,
                                 float sinceSync, float time, TextureAtlasSprite sprite, int light) {
        double fx = w.x() + w.dirX() * w.speed() * sinceSync, fz = w.z() + w.dirZ() * w.speed() * sinceSync;
        double px = -w.dirZ(), pz = w.dirX(); // along the front
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        float sea = w.seaY() + 0.9f;
        for (int i = 0; i < SEGMENTS; i++) {
            double t0 = i / (double) SEGMENTS - 0.5, t1 = (i + 1) / (double) SEGMENTS - 0.5;
            // the ends of the front taper down; a slow ripple keeps the crest from looking ruler-straight
            float h0 = waveHeight(w, t0, time), h1 = waveHeight(w, t1, time);
            double ax = fx + px * t0 * w.width(), az = fz + pz * t0 * w.width();
            double bx = fx + px * t1 * w.width(), bz = fz + pz * t1 * w.width();
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor((ax + bx) / 2), Mth.floor((az + bz) / 2)) - 1;
            if (ground > sea + Math.max(h0, h1)) continue; // breaking against high ground
            for (int k = 0; k < PROFILE.length - 1; k++) {
                float d0 = PROFILE[k][0], d1 = PROFILE[k + 1][0];
                float y00 = sea + PROFILE[k][1] * h0, y01 = sea + PROFILE[k + 1][1] * h0;
                float y10 = sea + PROFILE[k][1] * h1, y11 = sea + PROFILE[k + 1][1] * h1;
                float shade = 0.75f + 0.25f * PROFILE[k][1];
                float r = 0.42f * shade, g = 0.6f * shade, b = 0.78f * shade, a = 0.82f;
                quad(vc, pose, ax, az, bx, bz, w.dirX(), w.dirZ(), d0, d1, y00, y01, y10, y11, r, g, b, a, u0, u1, v0, v1, light);
            }
            // foam along the crest
            float c0 = sea + h0 + 0.15f, c1 = sea + h1 + 0.15f;
            quad(vc, pose, ax, az, bx, bz, w.dirX(), w.dirZ(), -4f, 1.2f, c0 - 0.6f, c0, c1 - 0.6f, c1,
                    0.95f, 0.97f, 1f, 0.85f, u0, u1, v0, v1, light);
        }
    }

    private static float waveHeight(WeatherSyncPayload.Wave w, double t, float time) {
        float ends = (float) Math.min(1.0, (0.5 - Math.abs(t)) * 6);
        float ripple = 1f + 0.08f * Mth.sin((float) (t * 40 + time * 0.05));
        return Math.max(0f, w.height() * ends * ripple);
    }

    /** One strip between two front points (a, b) and two cross-section stations (d0, d1). */
    private static void quad(VertexConsumer vc, PoseStack.Pose pose, double ax, double az, double bx, double bz,
                             float dx, float dz, float d0, float d1, float ya0, float ya1, float yb0, float yb1,
                             float r, float g, float b, float a, float u0, float u1, float v0, float v1, int light) {
        float x1 = (float) (ax + dx * d0), z1 = (float) (az + dz * d0);
        float x2 = (float) (bx + dx * d0), z2 = (float) (bz + dz * d0);
        float x3 = (float) (bx + dx * d1), z3 = (float) (bz + dz * d1);
        float x4 = (float) (ax + dx * d1), z4 = (float) (az + dz * d1);
        FunnelMesh.vertex(vc, pose, x1, ya0, z1, r, g, b, a, u0, v0, light, 0, 1, 0);
        FunnelMesh.vertex(vc, pose, x2, yb0, z2, r, g, b, a, u1, v0, light, 0, 1, 0);
        FunnelMesh.vertex(vc, pose, x3, yb1, z3, r, g, b, a, u1, v1, light, 0, 1, 0);
        FunnelMesh.vertex(vc, pose, x4, ya1, z4, r, g, b, a, u0, v1, light, 0, 1, 0);
    }
}
