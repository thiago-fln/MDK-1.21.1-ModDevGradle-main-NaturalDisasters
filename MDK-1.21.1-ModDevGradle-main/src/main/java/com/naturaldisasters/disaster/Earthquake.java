package com.naturaldisasters.disaster;

import com.naturaldisasters.Config;
import com.naturaldisasters.network.ShakePayload;
import com.naturaldisasters.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * An earthquake: camera shake for everyone within the configured radius, fissures that tear open the ground
 * (lines of blocks replaced with air at varying depth) and cave ceilings that collapse.
 */
public class Earthquake {
    public int id;
    public BlockPos center = BlockPos.ZERO;
    public float magnitude;
    public int duration;
    public int age;
    public final List<Fissure> fissures = new ArrayList<>();

    public static class Fissure {
        double x, z;
        float angle, length, progress, maxDepth;
        int width;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putDouble("x", x);
            t.putDouble("z", z);
            t.putFloat("angle", angle);
            t.putFloat("length", length);
            t.putFloat("progress", progress);
            t.putFloat("depth", maxDepth);
            t.putInt("width", width);
            return t;
        }

        static Fissure load(CompoundTag t) {
            Fissure f = new Fissure();
            f.x = t.getDouble("x");
            f.z = t.getDouble("z");
            f.angle = t.getFloat("angle");
            f.length = t.getFloat("length");
            f.progress = t.getFloat("progress");
            f.maxDepth = t.getFloat("depth");
            f.width = t.getInt("width");
            return f;
        }
    }

    public static Earthquake create(int id, BlockPos center, float magnitude, RandomSource r) {
        Earthquake q = new Earthquake();
        q.id = id;
        q.center = center;
        q.magnitude = magnitude;
        q.duration = (int) (200 + magnitude * 40 + r.nextInt(100));
        if (magnitude >= 3.5f) {
            int count = 1 + (int) (magnitude / 3f);
            for (int i = 0; i < count; i++) {
                Fissure f = new Fissure();
                f.x = center.getX() + r.nextInt(33) - 16;
                f.z = center.getZ() + r.nextInt(33) - 16;
                f.angle = r.nextFloat() * Mth.TWO_PI;
                f.length = magnitude * 6f + r.nextFloat() * magnitude * 4f;
                f.width = 1 + (magnitude >= 6 ? 1 : 0) + (magnitude >= 8 ? 1 : 0);
                f.maxDepth = 4f + magnitude * 2.5f;
                q.fissures.add(f);
            }
        }
        return q;
    }

    /** @return false once the quake has finished. */
    public boolean tick(ServerLevel level) {
        age++;
        RandomSource r = level.random;
        float envelope = Math.min(1f, age / 40f) * Math.min(1f, (duration - age) / 60f);
        envelope = Math.max(0f, envelope);
        int radius = Config.SHAKE_RADIUS.get();

        if (age % 5 == 0) {
            for (ServerPlayer p : level.players()) {
                double d = Math.sqrt(p.distanceToSqr(center.getX() + 0.5, p.getY(), center.getZ() + 0.5));
                if (d > radius) continue;
                float intensity = (magnitude / 10f) * envelope * (float) Math.pow(1.0 - d / radius, 0.8);
                if (intensity > 0.01f) PacketDistributor.sendToPlayer(p, new ShakePayload(intensity, 12));
                if (age % 40 == 5 && intensity > 0.05f) {
                    p.playNotifySound(ModRegistry.EARTHQUAKE_RUMBLE.get(), SoundSource.AMBIENT,
                            Math.min(1.5f, intensity * 2f), 0.5f + r.nextFloat() * 0.2f);
                }
            }
        }

        if (Config.FISSURES.get() && envelope > 0.3f) {
            float speed = 0.2f + magnitude * 0.04f;
            for (Fissure f : fissures) {
                if (f.progress >= f.length) continue;
                int before = (int) f.progress;
                f.progress += speed;
                for (int step = before; step < (int) f.progress && step < f.length; step++) {
                    carveStep(level, f, step, r);
                }
            }
        }

        if (Config.CAVE_COLLAPSES.get() && magnitude >= 4f && age % 2 == 0 && envelope > 0.2f) {
            int attempts = (int) magnitude;
            for (int i = 0; i < attempts; i++) collapseAttempt(level, r);
        }
        return age < duration;
    }

    private void carveStep(ServerLevel level, Fissure f, int step, RandomSource r) {
        f.angle += (r.nextFloat() - 0.5f) * 0.25f;
        f.x += Mth.cos(f.angle);
        f.z += Mth.sin(f.angle);
        double t = step / (double) f.length;
        float depth = 1f + f.maxDepth * (float) Math.pow(Math.sin(Math.PI * t), 0.6) * (0.65f + r.nextFloat() * 0.35f);
        int width = (t < 0.1 || t > 0.9) ? 1 : f.width;
        float px = -Mth.sin(f.angle), pz = Mth.cos(f.angle);
        for (int k = -(width - 1) / 2; k <= width / 2; k++) {
            int x = Mth.floor(f.x + px * k), z = Mth.floor(f.z + pz * k);
            BlockPos col = new BlockPos(x, center.getY(), z);
            if (!level.isLoaded(col)) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            int d = (int) depth - Math.abs(k) * 2;
            BlockState surface = level.getBlockState(new BlockPos(x, top, z));
            for (int y = top; y > top - d && y > level.getMinBuildHeight() + 2; y--) {
                BlockPos p = new BlockPos(x, y, z);
                BlockState s = level.getBlockState(p);
                if (s.isAir() || !s.getFluidState().isEmpty()) continue;
                if (s.getDestroySpeed(level, p) < 0 || s.hasBlockEntity()) break;
                level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            if (!surface.isAir()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, surface),
                        x + 0.5, top + 1, z + 0.5, 10, 0.6, 0.3, 0.6, 0.15);
            }
        }
    }

    private void collapseAttempt(ServerLevel level, RandomSource r) {
        int x = center.getX() + r.nextInt(97) - 48;
        int z = center.getZ() + r.nextInt(97) - 48;
        BlockPos col = new BlockPos(x, 0, z);
        if (!level.isLoaded(col)) return;
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int minY = level.getMinBuildHeight() + 6;
        int span = surface - 8 - minY;
        if (span <= 0) return;
        BlockPos p = new BlockPos(x, minY + r.nextInt(span), z);
        if (!level.getBlockState(p).isAir()) return;
        BlockPos ceiling = null;
        for (int dy = 1; dy <= 12; dy++) {
            BlockPos q = p.above(dy);
            if (!level.getBlockState(q).isAir()) {
                ceiling = q;
                break;
            }
        }
        if (ceiling == null || ceiling.getY() >= surface - 3) return;
        int blocks = 1 + r.nextInt(3);
        for (int k = 0; k < blocks; k++) {
            BlockPos q = ceiling.above(k);
            BlockState s = level.getBlockState(q);
            if (s.isAir() || !s.getFluidState().isEmpty() || s.hasBlockEntity() || s.getDestroySpeed(level, q) < 0) break;
            FallingBlockEntity fbe = FallingBlockEntity.fall(level, q, s);
            fbe.setHurtsEntities(2f, 40);
        }
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 3, 0.5, 0.5, 0.5, 0.01);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putLong("center", center.asLong());
        t.putFloat("magnitude", magnitude);
        t.putInt("duration", duration);
        t.putInt("age", age);
        ListTag list = new ListTag();
        for (Fissure f : fissures) list.add(f.save());
        t.put("fissures", list);
        return t;
    }

    public static Earthquake load(CompoundTag t) {
        Earthquake q = new Earthquake();
        q.id = t.getInt("id");
        q.center = BlockPos.of(t.getLong("center"));
        q.magnitude = t.getFloat("magnitude");
        q.duration = t.getInt("duration");
        q.age = t.getInt("age");
        ListTag list = t.getList("fissures", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) q.fissures.add(Fissure.load(list.getCompound(i)));
        return q;
    }
}
