package com.naturaldisasters.disaster;

import com.naturaldisasters.Config;
import com.naturaldisasters.weather.Atmosphere;
import com.naturaldisasters.weather.StormStage;
import com.naturaldisasters.weather.StormSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * Owns every flood of a dimension. Severe storms near rivers/oceans start floods that rise up to the configured
 * height and spread up to the edge of the storm; the /disaster flood command starts one directly. Floods drain
 * gradually when their storm moves away (or after a while, for command floods).
 */
public class FloodManager {
    private static final int MAX_FLOODS = 6;
    private static final int MAX_NATURAL_HALF_TILES = 20; // 123 x 123 blocks

    public final List<Flood> floods = new ArrayList<>();

    public void tick(ServerLevel level, List<StormSystem> storms, IntSupplier newId) {
        if (!floods.isEmpty()) {
            int budget = Math.max(12, Config.FLOOD_RATE.get() / floods.size());
            floods.removeIf(f -> !f.tick(level, budget));
        }
        if (level.getGameTime() % 100 == 0) {
            followStorms(storms);
            if (Config.FLOODING.get()) startNaturalFloods(level, storms, newId);
        }
    }

    /** Storm floods hold while their storm is over them and drain once it has passed. */
    private void followStorms(List<StormSystem> storms) {
        for (Flood f : floods) {
            if (f.stormId < 0 || f.isDraining()) continue;
            StormSystem storm = null;
            for (StormSystem s : storms) if (s.id == f.stormId) storm = s;
            double reach = storm == null ? 0 : storm.radius + 100;
            if (storm == null || storm.energy < 0.4f || storm.distanceSqr(f.centerX, f.centerZ) > reach * reach) f.drain();
        }
    }

    private void startNaturalFloods(ServerLevel level, List<StormSystem> storms, IntSupplier newId) {
        if (floods.size() >= MAX_FLOODS) return;
        RandomSource r = level.random;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || floodNear(p.getX(), p.getZ(), 96)) continue;
            StormSystem storm = null;
            for (StormSystem s : storms) {
                if (s.stage.ordinal() < StormStage.THUNDERSTORM.ordinal() || s.energy < 0.45f) continue;
                double reach = s.radius + 60;
                if (s.distanceSqr(p.getX(), p.getZ()) < reach * reach) storm = s;
            }
            if (storm == null) continue;
            BlockPos water = findFloodableWater(level, p.blockPosition(), r);
            if (water == null) continue;
            int half = Math.min(MAX_NATURAL_HALF_TILES, Math.max(1, (int) (storm.radius / 3)));
            Flood f = Flood.create(newId.getAsInt(), water.getX(), water.getZ(), water.getY(), roundToTiles(Config.MAX_FLOOD_HEIGHT.get()), half);
            f.stormId = storm.id;
            floods.add(f);
            if (floods.size() >= MAX_FLOODS) return;
        }
    }

    private static BlockPos findFloodableWater(ServerLevel level, BlockPos around, RandomSource r) {
        for (int i = 0; i < 40; i++) {
            int x = around.getX() + r.nextInt(97) - 48, z = around.getZ() + r.nextInt(97) - 48;
            if (!level.isLoaded(new BlockPos(x, 0, z))) continue;
            BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
            if (!level.getFluidState(top).is(FluidTags.WATER)) continue;
            Holder<Biome> b = level.getBiome(top);
            if (Atmosphere.isWater(b) || b.is(Tags.Biomes.IS_SWAMP)) return top;
        }
        return null;
    }

    /** Command flood centred on a position: the water rises from that height (the player's feet). */
    public Flood startFlood(ServerLevel level, int id, BlockPos center, int height, int limit, int holdTicks) {
        Flood f = Flood.create(id, center.getX(), center.getZ(), center.getY() - 1, height, limit / 3);
        f.holdTicks = holdTicks;
        floods.add(f);
        return f;
    }

    private static final int MAX_TSUNAMI_FLOODS = 40;
    private static final int TSUNAMI_HALF_TILES = 7; // 45 x 45 blocks, centres snapped to a 45 block grid

    /**
     * Makes sure a tsunami flood covers (x, z). Areas are snapped to a 45 block grid so neighbouring ones tile
     * exactly; they rise fast, hold for a while and then drain like the sea pulling back.
     */
    public void coverWithTsunami(ServerLevel level, IntSupplier newId, double x, double z, int seaY, int rise) {
        for (Flood f : floods) if (f.contains(x, z)) return;
        if (floods.size() >= MAX_FLOODS + MAX_TSUNAMI_FLOODS) return;
        int size = (TSUNAMI_HALF_TILES * 2 + 1) * 3;
        int cx = Math.floorDiv((int) Math.floor(x), size) * size + size / 2;
        int cz = Math.floorDiv((int) Math.floor(z), size) * size + size / 2;
        Flood f = Flood.create(newId.getAsInt(), cx, cz, seaY, roundToTiles(rise), TSUNAMI_HALF_TILES);
        f.taper = false;
        f.riseStep = 2;
        f.holdTicks = 900;
        floods.add(f);
    }

    /** Rounds down to a multiple of 3 (the flood works in 3 block units), minimum 3. */
    public static int roundToTiles(int blocks) {
        return Math.max(3, blocks / 3 * 3);
    }

    public boolean floodNear(double x, double z, double range) {
        for (Flood f : floods) {
            double dx = f.centerX - x, dz = f.centerZ - z;
            if (dx * dx + dz * dz < range * range) return true;
        }
        return false;
    }

    /** Current water rise (blocks) of the flood covering a point, 0 if none. */
    public float levelAt(double x, double z) {
        float best = 0;
        for (Flood f : floods) if (f.contains(x, z)) best = Math.max(best, f.surfaceHeight());
        return best;
    }

    public void drainAll() {
        floods.forEach(Flood::drain);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        ListTag list = new ListTag();
        for (Flood f : floods) list.add(f.save());
        t.put("floods", list);
        return t;
    }

    public void load(CompoundTag t) {
        ListTag list = t.getList("floods", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) floods.add(Flood.load(list.getCompound(i)));
    }
}
