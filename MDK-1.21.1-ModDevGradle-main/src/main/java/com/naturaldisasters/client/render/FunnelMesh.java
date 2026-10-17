package com.naturaldisasters.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/** Procedural geometry helpers (QUADS for the entity translucent render type). */
public final class FunnelMesh {
    private FunnelMesh() {}

    public static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z,
                              float r, float g, float b, float a, float u, float v, int light, float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    /**
     * Flat ring between innerR and outerR at height y. {@code sag} lowers the inner edge, giving the cloud a
     * bulging, lowered centre (wall cloud). Alpha fades from {@code alphaInner} to 0 at the outer edge.
     */
    public static void annulus(VertexConsumer vc, PoseStack.Pose pose, float cx, float y, float cz,
                               float innerR, float outerR, float sag, float r, float g, float b, float alphaInner,
                               float uScroll, int light) {
        final int rings = 5, segments = 36;
        for (int i = 0; i < rings; i++) {
            float f0 = i / (float) rings, f1 = (i + 1) / (float) rings;
            float r0 = Mth.lerp(f0, innerR, outerR), r1 = Mth.lerp(f1, innerR, outerR);
            float y0 = y - (1 - f0) * sag, y1 = y - (1 - f1) * sag;
            float a0 = alphaInner * (1 - f0 * f0), a1 = alphaInner * (1 - f1 * f1);
            for (int j = 0; j < segments; j++) {
                float t0 = j / (float) segments * Mth.TWO_PI, t1 = (j + 1) / (float) segments * Mth.TWO_PI;
                float c0 = Mth.cos(t0), s0 = Mth.sin(t0), c1 = Mth.cos(t1), s1 = Mth.sin(t1);
                float u0 = j / (float) segments * 4 + uScroll, u1 = (j + 1) / (float) segments * 4 + uScroll;
                vertex(vc, pose, cx + c0 * r0, y0, cz + s0 * r0, r, g, b, a0, u0, f0, light, 0, -1, 0);
                vertex(vc, pose, cx + c1 * r0, y0, cz + s1 * r0, r, g, b, a0, u1, f0, light, 0, -1, 0);
                vertex(vc, pose, cx + c1 * r1, y1, cz + s1 * r1, r, g, b, a1, u1, f1, light, 0, -1, 0);
                vertex(vc, pose, cx + c0 * r1, y1, cz + s0 * r1, r, g, b, a1, u0, f1, light, 0, -1, 0);
            }
        }
    }

    /** Open cylinder (or cone when the radii differ) with alpha fading towards the top. */
    public static void cylinder(VertexConsumer vc, PoseStack.Pose pose, float cx, float y0, float cz, float height,
                                float rBottom, float rTop, float r, float g, float b, float alphaBottom, float alphaTop,
                                float uScroll, float vScroll, int light) {
        final int segments = 28;
        float y1 = y0 + height;
        for (int j = 0; j < segments; j++) {
            float t0 = j / (float) segments * Mth.TWO_PI, t1 = (j + 1) / (float) segments * Mth.TWO_PI;
            float c0 = Mth.cos(t0), s0 = Mth.sin(t0), c1 = Mth.cos(t1), s1 = Mth.sin(t1);
            float u0 = j / (float) segments * 3 + uScroll, u1 = (j + 1) / (float) segments * 3 + uScroll;
            vertex(vc, pose, cx + c0 * rBottom, y0, cz + s0 * rBottom, r, g, b, alphaBottom, u0, vScroll, light, c0, 0, s0);
            vertex(vc, pose, cx + c1 * rBottom, y0, cz + s1 * rBottom, r, g, b, alphaBottom, u1, vScroll, light, c1, 0, s1);
            vertex(vc, pose, cx + c1 * rTop, y1, cz + s1 * rTop, r, g, b, alphaTop, u1, vScroll + 1, light, c1, 0, s1);
            vertex(vc, pose, cx + c0 * rTop, y1, cz + s0 * rTop, r, g, b, alphaTop, u0, vScroll + 1, light, c0, 0, s0);
        }
    }
}
