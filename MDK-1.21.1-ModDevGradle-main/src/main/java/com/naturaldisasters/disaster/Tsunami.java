package com.naturaldisasters.disaster;

import com.naturaldisasters.network.ShakePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.function.IntSupplier;

/**
 * A tsunami: a wave front (a line {@link #width} blocks long) that forms over the ocean and travels towards the
 * coast. Over open water it moves fast; where it reaches land it slows down, sweeps entities along, smashes weak
 * blocks and floods the land behind it (fast-rising flood areas) as far inland as its height allows. The flood
 * holds for a while and then drains, like the sea pulling back.
 */
public class Tsunami {
    private static final int SAMPLE_SPACING = 12;

    public int id;
    public double originX, originZ;
    public double dirX, dirZ;
    public double traveled;
    public float width, height;
    public double speed = 0.8;
    /** Y of the sea surface block. */
    public int seaY;
    public int age;
    /** Distance travelled over land per sample of the front; negative once that part of the wave has died out. */
    private float[] inland;

    public static Tsunami create(int id, double originX, double originZ, double dirX, double dirZ,
                                 float width, float height, int seaY) {
        Tsunami t = new Tsunami();
        t.id = id;
        t.originX = originX;
        t.originZ = originZ;
        double len = Math.max(1e-6, Math.sqrt(dirX * dirX + dirZ * dirZ));
        t.dirX = dirX / len;
        t.dirZ = dirZ / len;
        t.width = width;
        t.height = height;
        t.seaY = seaY;
        t.inland = new float[Math.max(2, (int) (width / SAMPLE_SPACING) + 1)];
        return t;
    }

    public double frontX() {
        return originX + dirX * traveled;
    }

    public double frontZ() {
        return originZ + dirZ * traveled;
    }

    /** Point along the front; {@code t} in [-0.5, 0.5]. */
    private double pointX(double t) {
        return frontX() - dirZ * t * width;
    }

    private double pointZ(double t) {
        return frontZ() + dirX * t * width;
    }

    /** Local wave height after travelling {@code inlandDistance} blocks over land. */
    public float heightAfter(float inlandDistance) {
        return height * (1f - inlandDistance / (height * 10f));
    }

    /** @return false once the whole front has died out. */
    public boolean tick(ServerLevel level, FloodManager floods, IntSupplier newId) {
        age++;
        traveled += speed;
        RandomSource r = level.random;
        boolean anyOnLand = false, anyAlive = false;

        if (age % 5 == 0) {
            for (int k = 0; k < inland.length; k++) {
                if (inland[k] < 0) continue;
                double t = k / (double) (inland.length - 1) - 0.5;
                int x = Mth.floor(pointX(t)), z = Mth.floor(pointZ(t));
                BlockPos col = new BlockPos(x, seaY, z);
                if (!level.isLoaded(col)) {
                    anyAlive = true;
                    continue;
                }
                BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
                if (level.getFluidState(top).is(FluidTags.WATER) && top.getY() <= seaY + 1) {
                    if (inland[k] == 0) anyAlive = true; // still at sea
                    else inland[k] += (float) speed * 5;  // crossing a lake or river behind the coast
                    if (inland[k] > 0) anyOnLand = true;
                    continue;
                }
                inland[k] += (float) speed * 5;
                float local = heightAfter(inland[k]);
                if (local < 1.5f || top.getY() > seaY + local) {
                    inland[k] = -1; // ran out of energy or hit high ground
                    continue;
                }
                anyAlive = true;
                anyOnLand = true;
                floods.coverWithTsunami(level, newId, x, z, seaY, (int) Math.ceil(local));
            }
            speed = anyOnLand ? 0.35 : 0.8;
            if (!anyAlive) return false;
        }

        sweepEntities(level);
        if (age % 2 == 0) smashBlocks(level, r);
        if (age % 10 == 0) effects(level);
        return age < 20 * 60 * 6; // safety net: 6 minutes
    }

    /** Entities in the breaking front are carried inland and battered. */
    private void sweepEntities(ServerLevel level) {
        double fx = frontX(), fz = frontZ();
        double reach = width / 2 + 4;
        AABB box = new AABB(fx - reach, seaY - 4, fz - reach, fx + reach, seaY + height + 4, fz + reach);
        for (Entity e : level.getEntities((Entity) null, box, e -> !(e instanceof HangingEntity) && !e.isSpectator())) {
            if (e instanceof Player p && (p.isCreative() || p.getAbilities().flying)) continue;
            double rx = e.getX() - fx, rz = e.getZ() - fz;
            double along = rx * dirX + rz * dirZ;           // distance in front of (+) / behind (-) the wave face
            double across = -rx * dirZ + rz * dirX;
            if (Math.abs(across) > width / 2 || along < -10 || along > 3) continue;
            if (e.getY() > seaY + height + 2) continue;
            Vec3 v = e.getDeltaMovement();
            double push = 0.45 + height * 0.02;
            e.setDeltaMovement(v.x * 0.6 + dirX * push, Math.max(v.y, 0.12), v.z * 0.6 + dirZ * push);
            e.hasImpulse = true;
            if (e instanceof ServerPlayer) e.hurtMarked = true;
            e.clearFire();
            if (e instanceof LivingEntity && age % 20 == 0) e.hurt(level.damageSources().drown(), 1.5f + height * 0.15f);
        }
    }

    /** The wave face rips out weak blocks (plants, leaves, glass, wooden structures) on the land it reaches. */
    private void smashBlocks(ServerLevel level, RandomSource r) {
        for (int i = 0; i < 12; i++) {
            int k = r.nextInt(inland.length);
            if (inland[k] <= 0) continue;
            double t = k / (double) (inland.length - 1) - 0.5 + (r.nextDouble() - 0.5) / inland.length;
            double back = r.nextDouble() * 4;
            int x = Mth.floor(pointX(t) - dirX * back), z = Mth.floor(pointZ(t) - dirZ * back);
            BlockPos colPos = new BlockPos(x, seaY, z);
            if (!level.isLoaded(colPos)) continue;
            int topY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            float local = heightAfter(inland[k]);
            if (topY > seaY + local) continue;
            BlockPos pos = new BlockPos(x, topY, z);
            BlockState s = level.getBlockState(pos);
            if (s.isAir() || s.liquid() || s.hasBlockEntity() || s.getDestroySpeed(level, pos) < 0) continue;
            float res = s.getBlock().getExplosionResistance();
            boolean flimsy = s.canBeReplaced() || s.getCollisionShape(level, pos).isEmpty() || s.is(BlockTags.LEAVES) || res <= 0.5f;
            if (flimsy) level.destroyBlock(pos, false);
            else if (res <= 3.0f && r.nextFloat() < 0.35f) level.destroyBlock(pos, true);
        }
    }

    private void effects(ServerLevel level) {
        double fx = frontX(), fz = frontZ();
        level.sendParticles(ParticleTypes.SPLASH, fx, seaY + height * 0.8, fz, 40, width * 0.3, height * 0.3, width * 0.3, 0.3);
        for (ServerPlayer p : level.players()) {
            double dx = p.getX() - fx, dz = p.getZ() - fz;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > width) continue;
            float intensity = (float) ((0.05 + height * 0.01) * (1 - d / width));
            if (intensity > 0.02f) PacketDistributor.sendToPlayer(p, new ShakePayload(intensity, 14));
        }
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putDouble("ox", originX);
        t.putDouble("oz", originZ);
        t.putDouble("dx", dirX);
        t.putDouble("dz", dirZ);
        t.putDouble("traveled", traveled);
        t.putFloat("width", width);
        t.putFloat("height", height);
        t.putDouble("speed", speed);
        t.putInt("seaY", seaY);
        t.putInt("age", age);
        int[] in = new int[inland.length];
        for (int i = 0; i < in.length; i++) in[i] = Math.round(inland[i] * 16);
        t.putIntArray("inland", in);
        return t;
    }

    public static Tsunami load(CompoundTag t) {
        Tsunami w = create(t.getInt("id"), t.getDouble("ox"), t.getDouble("oz"), t.getDouble("dx"), t.getDouble("dz"),
                t.getFloat("width"), t.getFloat("height"), t.getInt("seaY"));
        w.traveled = t.getDouble("traveled");
        w.speed = t.getDouble("speed");
        w.age = t.getInt("age");
        int[] in = t.getIntArray("inland");
        for (int i = 0; i < in.length && i < w.inland.length; i++) w.inland[i] = in[i] / 16f;
        return w;
    }
}
