package com.naturaldisasters.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Wedge tornado: wider than it is tall-looking, a dark wall of debris touching the ground over a broad path. */
public class WedgeTornadoEntity extends TornadoEntity {
    public WedgeTornadoEntity(EntityType<? extends TornadoEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public TornadoVariant getVariant() {
        return TornadoVariant.WEDGE;
    }

    @Override
    public float coreMultiplier() {
        return 1.7f;
    }

    @Override
    public float radiusProfile(double t) {
        return (float) (1.0 + 1.1 * Math.pow(t, 1.6));
    }

    @Override
    public float swayFactor() {
        return 0.2f;
    }
}
