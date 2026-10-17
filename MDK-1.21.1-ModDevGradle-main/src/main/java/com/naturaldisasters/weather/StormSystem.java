package com.naturaldisasters.weather;

import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A storm cell moving across the world. Lives in {@link DisasterData}, not in chunks, so it keeps moving everywhere. */
public class StormSystem {
    public int id;
    public double x, z, vx, vz;
    /** 0..1, drives the stage. */
    public float energy;
    /** Maximum energy this storm can reach (from atmospheric instability). */
    public float potential;
    public boolean supercell;
    public int age, maxAge;
    public float radius = 40;
    public StormStage stage = StormStage.RAIN;
    public int warningTicks;
    public int tornadoesSpawned;
    public UUID tornado;

    /** Transient: highest stage each player has been alerted about. */
    public final transient Map<UUID, StormStage> alerted = new HashMap<>();

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putDouble("x", x);
        t.putDouble("z", z);
        t.putDouble("vx", vx);
        t.putDouble("vz", vz);
        t.putFloat("energy", energy);
        t.putFloat("potential", potential);
        t.putBoolean("supercell", supercell);
        t.putInt("age", age);
        t.putInt("maxAge", maxAge);
        t.putInt("warning", warningTicks);
        t.putInt("spawned", tornadoesSpawned);
        if (tornado != null) t.putUUID("tornado", tornado);
        return t;
    }

    public static StormSystem load(CompoundTag t) {
        StormSystem s = new StormSystem();
        s.id = t.getInt("id");
        s.x = t.getDouble("x");
        s.z = t.getDouble("z");
        s.vx = t.getDouble("vx");
        s.vz = t.getDouble("vz");
        s.energy = t.getFloat("energy");
        s.potential = t.getFloat("potential");
        s.supercell = t.getBoolean("supercell");
        s.age = t.getInt("age");
        s.maxAge = t.getInt("maxAge");
        s.warningTicks = t.getInt("warning");
        s.tornadoesSpawned = t.getInt("spawned");
        if (t.hasUUID("tornado")) s.tornado = t.getUUID("tornado");
        s.updateDerived();
        return s;
    }

    public void updateDerived() {
        stage = StormStage.fromEnergy(energy, supercell);
        radius = 40f + energy * (supercell ? 110f : 70f);
    }

    public double distanceSqr(double px, double pz) {
        double dx = px - x, dz = pz - z;
        return dx * dx + dz * dz;
    }
}
