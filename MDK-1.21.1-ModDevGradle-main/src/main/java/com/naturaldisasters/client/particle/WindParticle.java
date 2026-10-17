package com.naturaldisasters.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;

/** Wind-driven particle (blowing sand, generic dust) that keeps its velocity and fades out. */
public class WindParticle extends TextureSheetParticle {
    private final float baseAlpha;

    public WindParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                        SpriteSet sprites, float r, float g, float b, float size) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.hasPhysics = false;
        this.gravity = 0;
        this.friction = 1f;
        this.lifetime = 30 + random.nextInt(30);
        this.quadSize = size * (0.6f + random.nextFloat() * 0.8f);
        float shade = 0.9f + random.nextFloat() * 0.2f;
        setColor(r * shade, g * shade, b * shade);
        this.baseAlpha = 0.75f;
        this.alpha = 0;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float progress = age / (float) lifetime;
        alpha = baseAlpha * Math.min(1f, progress * 6f) * Math.min(1f, (1f - progress) * 4f);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
