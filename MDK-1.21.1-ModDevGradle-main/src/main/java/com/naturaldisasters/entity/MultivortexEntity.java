package com.naturaldisasters.entity;

import com.naturaldisasters.Config;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** A large parent vortex with 2-4 smaller sub-vortices orbiting its centre, each with its own force field. */
public class MultivortexEntity extends TornadoEntity {
    private static final EntityDataAccessor<Integer> DATA_SUBS = SynchedEntityData.defineId(MultivortexEntity.class, EntityDataSerializers.INT);

    public MultivortexEntity(EntityType<? extends TornadoEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SUBS, 3);
    }

    @Override
    protected void onCreated(RandomSource random) {
        entityData.set(DATA_SUBS, minSubVortices() + random.nextInt(maxSubVortices() - minSubVortices() + 1));
    }

    @Override
    public TornadoVariant getVariant() {
        return TornadoVariant.MULTIVORTEX;
    }

    public int getSubVortexCount() {
        return Mth.clamp(entityData.get(DATA_SUBS), minSubVortices(), maxSubVortices());
    }

    protected int minSubVortices() {
        return 2;
    }

    protected int maxSubVortices() {
        return 4;
    }

    public float getSubVortexCore() {
        return getCoreRadius() * 0.3f;
    }

    public float getSubOrbitRadius() {
        return getCoreRadius() * 0.8f;
    }

    /** Angle (radians) of sub-vortex i. Uses game time so server physics and client rendering agree. */
    public float getSubVortexAngle(int i, float time) {
        return time * 0.07f * spinDirection() + Mth.TWO_PI * i / getSubVortexCount() + getId() * 0.37f;
    }

    @Override
    protected void applyForces(ServerLevel level) {
        float time = level.getGameTime();
        applyVortex(level, getX(), getZ(), getCoreRadius() * 0.9f, getPullRadius(), strength() * 0.75f);
        float sub = getSubVortexCore(), orbit = getSubOrbitRadius();
        for (int i = 0; i < getSubVortexCount(); i++) {
            float a = getSubVortexAngle(i, time);
            applyVortex(level, getX() + Mth.cos(a) * orbit, getZ() + Mth.sin(a) * orbit, sub, sub * 2.4f, strength() * 0.95f);
        }
    }

    @Override
    protected void destroyBlocks(ServerLevel level) {
        double rate = Config.DESTRUCTION_RATE.get() * getScale();
        int mainBudget = (int) ((2 + getIntensity() * 3) * rate);
        destroyArea(level, getX(), getZ(), getCoreRadius(), mainBudget, destroyPower(0));
        float time = level.getGameTime();
        float sub = getSubVortexCore(), orbit = getSubOrbitRadius();
        int subBudget = (int) ((1 + getIntensity() * 1.5) * rate);
        for (int i = 0; i < getSubVortexCount(); i++) {
            float a = getSubVortexAngle(i, time);
            // the small suction vortices are the most violent part of the tornado
            destroyArea(level, getX() + Mth.cos(a) * orbit, getZ() + Mth.sin(a) * orbit, sub * 1.2f, subBudget, destroyPower(1));
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_SUBS, tag.contains("SubVortices") ? tag.getInt("SubVortices") : 3);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("SubVortices", getSubVortexCount());
    }
}
