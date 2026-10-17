package com.naturaldisasters.client.particle;

import com.naturaldisasters.entity.TornadoEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A small piece of the actual ground (grass, dirt, sand, planks, leaves...) torn up by a tornado. It spirals up
 * the funnel, gets flung out at some height and then tumbles back down under gravity, like real debris fallout.
 */
public class BlockDebrisParticle extends TextureSheetParticle {
    private final TornadoEntity owner;
    private final float uo, vo;
    private final float ejectAt;
    private final float spinMul;
    private double t;
    private float radiusFrac;
    private float angle;
    private boolean ejected;
    private float tumble;

    public BlockDebrisParticle(ClientLevel level, TornadoEntity owner, TextureAtlasSprite sprite, float r, float g, float b,
                               double t, float radiusFrac, float angle) {
        super(level, owner.getX(), owner.getY(), owner.getZ());
        this.owner = owner;
        this.t = t;
        this.radiusFrac = radiusFrac;
        this.angle = angle;
        setSprite(sprite);
        setColor(r, g, b);
        this.uo = random.nextFloat() * 3f;
        this.vo = random.nextFloat() * 3f;
        this.quadSize = 0.06f + random.nextFloat() * 0.16f;
        this.lifetime = 120 + random.nextInt(80);
        this.ejectAt = 0.25f + random.nextFloat() * 0.55f;
        this.spinMul = 1.0f + random.nextFloat() * 0.5f;
        this.tumble = (random.nextFloat() - 0.5f) * 0.6f;
        this.hasPhysics = false;
        this.gravity = 0;
        orbitPosition();
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        roll += tumble;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        if (!ejected) {
            if (owner.isRemoved()) {
                eject(0, 0, 0);
                return;
            }
            double px = x, py = y, pz = z;
            angle += owner.getSpinSpeed() * owner.spinDirection() * spinMul;
            t += 0.006 + owner.getIntensity() * 0.0015;
            orbitPosition();
            if (t >= ejectAt) eject(x - px, y - py, z - pz); // keeps its orbital speed when thrown out
        } else {
            yd -= 0.04;
            xd *= 0.985;
            yd *= 0.985;
            zd *= 0.985;
            move(xd, yd, zd);
            if (onGround) {
                xd *= 0.6;
                zd *= 0.6;
                tumble = 0;
            }
        }
    }

    private void eject(double vx, double vy, double vz) {
        ejected = true;
        hasPhysics = true;
        Vec3 out = new Vec3(x - owner.getX(), 0, z - owner.getZ());
        out = out.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : out.normalize();
        xd = vx * 0.8 + out.x * 0.35;
        yd = Math.max(vy, 0) + 0.1;
        zd = vz * 0.8 + out.z * 0.35;
    }

    private void orbitPosition() {
        float time = level.getGameTime();
        double height = owner.getFunnelTopY() - owner.getY();
        float r = owner.funnelRadius(t) * radiusFrac;
        Vec3 off = owner.funnelOffset(t, time);
        setPos(owner.getX() + off.x + Mth.cos(angle) * r, owner.getY() + t * height, owner.getZ() + off.z + Mth.sin(angle) * r);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.TERRAIN_SHEET;
    }

    // pick a random 4x4 pixel piece of the block texture, like vanilla block-breaking particles
    @Override
    protected float getU0() {
        return sprite.getU((uo + 1f) / 4f);
    }

    @Override
    protected float getU1() {
        return sprite.getU(uo / 4f);
    }

    @Override
    protected float getV0() {
        return sprite.getV(vo / 4f);
    }

    @Override
    protected float getV1() {
        return sprite.getV((vo + 1f) / 4f);
    }
}
