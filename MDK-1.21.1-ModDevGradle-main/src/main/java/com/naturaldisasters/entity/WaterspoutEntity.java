package com.naturaldisasters.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;

/** Tromba d'água: forms only over oceans / rivers, sucks up water and dissolves as soon as it reaches dry land. */
public class WaterspoutEntity extends TornadoEntity {
    private int landTicks;

    public WaterspoutEntity(EntityType<? extends TornadoEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public TornadoVariant getVariant() {
        return TornadoVariant.WATERSPOUT;
    }

    @Override
    protected boolean canBecomeFirenado() {
        return false;
    }

    @Override
    public float coreMultiplier() {
        return 0.7f;
    }

    @Override
    public float radiusProfile(double t) {
        return (float) (0.45 + 2.2 * Math.pow(t, 2.6));
    }

    @Override
    protected void groundEffects(net.minecraft.server.level.ServerLevel level) {
        // over water there is nothing to scour
    }

    public boolean isOverWater() {
        BlockPos below = BlockPos.containing(getX(), getY() - 0.5, getZ());
        return level().getFluidState(below).is(FluidTags.WATER) || level().getFluidState(below.below()).is(FluidTags.WATER);
    }

    @Override
    protected double massFactor(Entity e) {
        if (e instanceof WaterAnimal || e instanceof Boat) return 1.6;
        return super.massFactor(e);
    }

    @Override
    protected void variantTick(ServerLevel level) {
        if (isOverWater()) {
            landTicks = 0;
            if (life % 3 == 0) {
                // water sucked up from the surface and thrown into the air
                level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.5, getZ(), 30,
                        getCoreRadius() * 0.6, 0.3, getCoreRadius() * 0.6, 0.4);
                level.sendParticles(ParticleTypes.FALLING_WATER, getX(), getY() + 20, getZ(), 12,
                        getCoreRadius() * 1.5, 8, getCoreRadius() * 1.5, 0.0);
            }
        } else if (++landTicks > 20 && maxLife - life > 60) {
            life = maxLife - 60; // touched land: falls apart quickly
        }
    }
}
