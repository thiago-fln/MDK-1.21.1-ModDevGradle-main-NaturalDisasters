package com.naturaldisasters.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Rope tornado: a very thin, long column that snakes across the sky. Also the dissipating stage of classic tornadoes. */
public class RopeTornadoEntity extends TornadoEntity {
    public RopeTornadoEntity(EntityType<? extends TornadoEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public TornadoVariant getVariant() {
        return TornadoVariant.ROPE;
    }

    @Override
    public float coreMultiplier() {
        return 0.4f;
    }

    @Override
    public float radiusProfile(double t) {
        return (float) (0.45 + 1.4 * Math.pow(t, 3.5));
    }

    @Override
    public float swayFactor() {
        return 2.2f;
    }
}
