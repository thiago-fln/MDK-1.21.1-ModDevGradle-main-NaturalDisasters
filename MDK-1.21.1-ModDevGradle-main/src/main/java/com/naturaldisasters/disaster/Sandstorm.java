package com.naturaldisasters.disaster;

import com.naturaldisasters.Config;
import com.naturaldisasters.block.SandLayerBlock;
import com.naturaldisasters.registry.ModRegistry;
import com.naturaldisasters.weather.Atmosphere;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Desert-only sandstorm. Players caught in it (in a desert/badlands biome, under open sky) get the custom
 * sand blindness effect, and sand layers accumulate on the ground, slowly burying structures.
 */
public class Sandstorm {
    public int id;
    public double x, z;
    public float radius;
    public int duration, age;

    public static Sandstorm create(int id, double x, double z, RandomSource r) {
        Sandstorm s = new Sandstorm();
        s.id = id;
        s.x = x;
        s.z = z;
        s.radius = 120 + r.nextInt(100);
        s.duration = 3600 + r.nextInt(3600);
        return s;
    }

    public float intensity() {
        float in = Math.min(1f, age / 200f);
        float out = Math.min(1f, (duration - age) / 200f);
        return Math.max(0f, Math.min(in, out));
    }

    /** @return false once the storm is over. */
    public boolean tick(ServerLevel level, float windAngle, float windSpeed) {
        age++;
        x += Math.cos(windAngle) * windSpeed * 0.6;
        z += Math.sin(windAngle) * windSpeed * 0.6;

        if (age % 100 == 0 && !Atmosphere.isDesertLike(level.getBiome(BlockPos.containing(x, level.getSeaLevel() + 4, z)))) {
            duration = Math.min(duration, age + 200); // dissipates outside of deserts
        }

        float intensity = intensity();
        if (intensity <= 0.05f) return age < duration;
        RandomSource r = level.random;
        int layerRate = Config.SAND_LAYER_RATE.get();

        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            double dx = p.getX() - x, dz = p.getZ() - z;
            if (dx * dx + dz * dz > radius * radius) continue;
            BlockPos feet = p.blockPosition();
            if (!Atmosphere.isDesertLike(level.getBiome(feet))) continue;

            if (age % 20 == 0 && level.canSeeSky(feet.above())) {
                p.addEffect(new MobEffectInstance(ModRegistry.SAND_BLINDNESS, 60, intensity > 0.6f ? 1 : 0, true, false, true));
            }
            if (layerRate > 0) {
                for (int i = 0; i < layerRate; i++) depositSand(level, feet, r);
            }
        }
        return age < duration;
    }

    private void depositSand(ServerLevel level, BlockPos around, RandomSource r) {
        int x = around.getX() + r.nextInt(65) - 32;
        int z = around.getZ() + r.nextInt(65) - 32;
        BlockPos col = new BlockPos(x, 0, z);
        if (!level.isLoaded(col)) return;
        BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
        if (!Atmosphere.isDesertLike(level.getBiome(pos))) return;
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        if (belowState.is(ModRegistry.SAND_LAYER.get())) {
            int layers = belowState.getValue(SandLayerBlock.LAYERS);
            if (layers >= 7) level.setBlock(below, Blocks.SAND.defaultBlockState(), 3);
            else level.setBlock(below, belowState.setValue(SandLayerBlock.LAYERS, layers + 1), 3);
            return;
        }
        if (!level.getBlockState(pos).isAir()) return;
        if (!belowState.isFaceSturdy(level, below, Direction.UP)) return;
        level.setBlock(pos, ModRegistry.SAND_LAYER.get().defaultBlockState(), 3);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putDouble("x", x);
        t.putDouble("z", z);
        t.putFloat("radius", radius);
        t.putInt("duration", duration);
        t.putInt("age", age);
        return t;
    }

    public static Sandstorm load(CompoundTag t) {
        Sandstorm s = new Sandstorm();
        s.id = t.getInt("id");
        s.x = t.getDouble("x");
        s.z = t.getDouble("z");
        s.radius = t.getFloat("radius");
        s.duration = t.getInt("duration");
        s.age = t.getInt("age");
        return s;
    }
}
