package com.naturaldisasters.client.particle;

import com.naturaldisasters.entity.MultivortexEntity;
import com.naturaldisasters.entity.TornadoEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A particle bound to a tornado: it orbits the (swaying) funnel axis in a rising spiral. Dense swarms of these,
 * together with the translucent funnel mesh, make the tornado visible without spinning real blocks.
 */
public class VortexParticle extends TextureSheetParticle {
    public enum Kind { DUST, DEBRIS, FLAME, SMOKE, SPRAY, MIST }

    private final TornadoEntity owner;
    private final Kind kind;
    private final SpriteSet sprites;
    private final int subVortex;
    private final float spinMul;
    private final double rise;
    private final float baseAlpha;
    private double t;
    private float radiusFrac;
    private float angle;

    public VortexParticle(ClientLevel level, TornadoEntity owner, Kind kind, SpriteSet sprites,
                          double t, float radiusFrac, float angle, int subVortex) {
        super(level, owner.getX(), owner.getY(), owner.getZ());
        this.owner = owner;
        this.kind = kind;
        this.sprites = sprites;
        this.subVortex = subVortex;
        this.t = t;
        this.radiusFrac = radiusFrac;
        this.angle = angle;
        this.hasPhysics = false;
        this.gravity = 0;
        this.spinMul = 0.8f + random.nextFloat() * 0.5f;

        float shade = 0.85f + random.nextFloat() * 0.3f;
        switch (kind) {
            case DUST -> {
                lifetime = 50 + random.nextInt(40);
                quadSize = 1.4f + random.nextFloat() * 2.2f;
                setColor(0.47f * shade, 0.44f * shade, 0.40f * shade);
                rise = 0.006 + random.nextDouble() * 0.01;
                baseAlpha = 0.5f;
            }
            case DEBRIS -> {
                lifetime = 30 + random.nextInt(30);
                quadSize = 0.15f + random.nextFloat() * 0.3f;
                setColor(0.33f * shade, 0.27f * shade, 0.2f * shade);
                rise = 0.002 + random.nextDouble() * 0.006;
                baseAlpha = 0.95f;
            }
            case FLAME -> {
                lifetime = 25 + random.nextInt(25);
                quadSize = 0.6f + random.nextFloat() * 1.4f;
                setColor(1f, 0.85f + random.nextFloat() * 0.15f, 0.7f);
                rise = 0.01 + random.nextDouble() * 0.012;
                baseAlpha = 0.95f;
            }
            case SMOKE -> {
                lifetime = 60 + random.nextInt(40);
                quadSize = 1.8f + random.nextFloat() * 2.5f;
                setColor(0.16f * shade, 0.14f * shade, 0.13f * shade);
                rise = 0.006 + random.nextDouble() * 0.01;
                baseAlpha = 0.7f;
            }
            case SPRAY -> {
                lifetime = 20 + random.nextInt(20);
                quadSize = 0.4f + random.nextFloat() * 0.6f;
                setColor(0.85f, 0.92f, 1f);
                rise = 0.004 + random.nextDouble() * 0.006;
                baseAlpha = 0.9f;
            }
            default -> { // MIST
                lifetime = 50 + random.nextInt(30);
                quadSize = 1.2f + random.nextFloat() * 1.8f;
                setColor(0.82f * shade, 0.88f * shade, 0.95f);
                rise = 0.008 + random.nextDouble() * 0.01;
                baseAlpha = 0.35f;
            }
        }
        this.alpha = 0;
        pickSprite(sprites);
        updatePosition();
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime || owner.isRemoved()) {
            remove();
            return;
        }
        angle += owner.getSpinSpeed() * owner.spinDirection() * spinMul * (kind == Kind.DEBRIS ? 1.3f : 1f);
        t += rise;
        if (kind == Kind.DEBRIS || kind == Kind.SPRAY) radiusFrac += 0.02f;
        if (t > 1.02) {
            remove();
            return;
        }
        updatePosition();
        if (kind == Kind.DUST || kind == Kind.SMOKE || kind == Kind.MIST) setSpriteFromAge(sprites);
        float progress = age / (float) lifetime;
        alpha = baseAlpha * Math.min(1f, progress * 5f) * Math.min(1f, (1f - progress) * 3f) * Math.min(1f, owner.getScale() * 2f);
    }

    private void updatePosition() {
        float time = level.getGameTime();
        double height = owner.getFunnelTopY() - owner.getY();
        double cx = owner.getX(), cz = owner.getZ();
        float r;
        if (subVortex >= 0 && owner instanceof MultivortexEntity m) {
            float a = m.getSubVortexAngle(subVortex, time);
            cx += Mth.cos(a) * m.getSubOrbitRadius();
            cz += Mth.sin(a) * m.getSubOrbitRadius();
            r = TornadoEntity.funnelRadius(m.getSubVortexCore(), t) * radiusFrac;
        } else {
            r = owner.funnelRadius(t) * radiusFrac;
        }
        Vec3 off = owner.funnelOffset(t, time);
        setPos(cx + off.x + Mth.cos(angle) * r, owner.getY() + t * height, cz + off.z + Mth.sin(angle) * r);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return kind == Kind.FLAME ? LightTexture.FULL_BRIGHT : super.getLightColor(partialTick);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
