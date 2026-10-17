package com.naturaldisasters.entity;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Modelled on the 2013 El Reno tornado: the widest ever recorded. A huge, rain-wrapped multivortex circulation
 * with 4-6 violent suction vortices, fast and erratic movement that changes direction without warning.
 */
public class ElRenoEntity extends MultivortexEntity {
    public ElRenoEntity(EntityType<? extends TornadoEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void onCreated(RandomSource random) {
        super.onCreated(random);
        speed = Math.max(speed, 0.2 + random.nextDouble() * 0.1);
    }

    @Override
    public TornadoVariant getVariant() {
        return TornadoVariant.EL_RENO;
    }

    @Override
    public void setIntensity(float intensity) {
        super.setIntensity(Math.max(intensity, 3f));
    }

    @Override
    public float coreMultiplier() {
        return 2.3f;
    }

    @Override
    public float radiusProfile(double t) {
        return (float) (0.9 + 1.4 * Math.pow(t, 1.8));
    }

    @Override
    public float swayFactor() {
        return 0.35f;
    }

    @Override
    protected int minSubVortices() {
        return 4;
    }

    @Override
    protected int maxSubVortices() {
        return 6;
    }

    @Override
    protected double wanderAmount() {
        // occasional sharp turns, like El Reno's sudden turn to the north-east
        return random.nextFloat() < 0.01f ? 1.2 : 0.1;
    }
}
