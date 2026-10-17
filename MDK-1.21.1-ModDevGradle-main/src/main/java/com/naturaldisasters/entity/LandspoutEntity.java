package com.naturaldisasters.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Landspout: a weak, dusty, non-supercell tornado (land equivalent of a waterspout). Never stronger than EF1. */
public class LandspoutEntity extends TornadoEntity {
    public LandspoutEntity(EntityType<? extends TornadoEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public TornadoVariant getVariant() {
        return TornadoVariant.LANDSPOUT;
    }

    @Override
    public void setIntensity(float intensity) {
        super.setIntensity(Math.min(intensity, 1.9f));
    }

    @Override
    public float coreMultiplier() {
        return 0.55f;
    }

    @Override
    public float radiusProfile(double t) {
        return (float) (0.5 + 1.6 * Math.pow(t, 2.0));
    }

    @Override
    public float swayFactor() {
        return 1.3f;
    }
}
