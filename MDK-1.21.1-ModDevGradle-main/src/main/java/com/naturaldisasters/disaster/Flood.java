package com.naturaldisasters.disaster;

import com.naturaldisasters.block.AbstractFloodBlock;
import com.naturaldisasters.block.FloodBlock;
import com.naturaldisasters.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Arrays;

/**
 * One flood area: a square grid of 3x3 tiles around a centre. Water is made of 3 x 0.5 x 3 units: the surface
 * rises half a block at a time from {@link #baseY} up to {@code maxRise} blocks, holds, then drains the same way.
 * The outer rings of tiles stop lower so the water steps down towards the limit. Each "pass" walks every tile
 * (a few dozen per tick) and makes it match the current surface. Only air and flood blocks are ever touched,
 * and ground heights are recorded before any water is placed, so draining restores the map exactly.
 */
public class Flood {
    private static final int UNKNOWN = Integer.MIN_VALUE;

    public int id;
    public int centerX, centerZ;
    /** Y of the block the water rests on at level 0 (water surface starts at baseY + 1). */
    public int baseY;
    /** Maximum rise in blocks (multiple of 3). */
    public int maxRise;
    /** Half size of the area in tiles: the area spans (2 * halfTiles + 1) tiles of 3x3 blocks per side. */
    public int halfTiles;
    /** Storm this flood belongs to, or -1 for a command flood. */
    public int stormId = -1;
    /** Ticks to stay at full height before draining (command floods), -1 = follow the storm. */
    public int holdTicks = -1;
    /**
     * Contained flood: water can't rise more than one block above the lowest terrain just outside the area (it
     * would spill over), so it fills valleys like a lake and spreads shallow over flat land instead of standing
     * in the air as a tower. Off for tsunami floods, which are a moving surge and tile next to each other.
     */
    public boolean taper = true;
    /** Half blocks the surface moves per pass (tsunamis rise much faster than river floods). */
    public int riseStep = 1;

    /** Current surface in half blocks above the base. */
    public int level;
    /** Surface the flood is moving towards, in half blocks. */
    public int target;
    private boolean draining;
    private int cursor;
    private int idleTicks;
    private int appliedLevel = -1;
    /** Full passes check every height of each tile; normal passes only the few layers around the moving surface. */
    private boolean fullPass = true;
    /** Lowest ground of each tile (top solid block of its 9 columns). */
    private int[] ground;
    /** Lowest ground just outside the area (the basin rim), or UNKNOWN. */
    private int rim = UNKNOWN;

    public static Flood create(int id, int centerX, int centerZ, int baseY, int maxRise, int halfTiles) {
        Flood f = new Flood();
        f.id = id;
        f.centerX = centerX;
        f.centerZ = centerZ;
        f.baseY = baseY;
        f.maxRise = maxRise;
        f.halfTiles = halfTiles;
        f.target = maxRise * 2;
        f.ground = new int[f.tiles() * f.tiles()];
        Arrays.fill(f.ground, UNKNOWN);
        return f;
    }

    /** Tiles per side. */
    public int tiles() {
        return halfTiles * 2 + 1;
    }

    public int minX() {
        return centerX - 1 - halfTiles * 3;
    }

    public int minZ() {
        return centerZ - 1 - halfTiles * 3;
    }

    public boolean contains(double x, double z) {
        int side = tiles() * 3;
        return x >= minX() && x < minX() + side && z >= minZ() && z < minZ() + side;
    }

    public float surfaceHeight() {
        return Math.max(0, level) / 2f;
    }

    public boolean isDraining() {
        return draining;
    }

    /**
     * Starts draining. The level may go below zero: water also fills ground lower than the base (e.g. a basin
     * below the player who started the flood), and all of it has to drain away.
     */
    public void drain() {
        draining = true;
        int lowest = baseY;
        for (int g : ground) if (g != UNKNOWN) lowest = Math.min(lowest, g);
        target = -2 * (baseY - lowest);
    }

    private int ground(ServerLevel level, int tile) {
        if (tile < 0 || tile >= ground.length) return UNKNOWN;
        if (ground[tile] != UNKNOWN) return ground[tile];
        int cx = tileCenterX(tile), cz = tileCenterZ(tile);
        int min = Integer.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.isLoaded(new BlockPos(cx + dx, 0, cz + dz))) return UNKNOWN;
                min = Math.min(min, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx + dx, cz + dz) - 1);
            }
        }
        ground[tile] = min;
        return min;
    }

    private int tileCenterX(int tile) {
        return minX() + 1 + (tile % tiles()) * 3;
    }

    private int tileCenterZ(int tile) {
        return minZ() + 1 + (tile / tiles()) * 3;
    }

    /** @return false once fully drained. */
    public boolean tick(ServerLevel level, int budget) {
        if (cursor == 0 && appliedLevel == this.level && this.level == target) {
            // the surface matches the target everywhere
            if (draining && this.level <= 0) return false;
            if (!draining && holdTicks >= 0 && --holdTicks <= 0) {
                drain();
                return true;
            }
            if (++idleTicks < 100) return true; // re-check now and then for chunks loaded later
            idleTicks = 0;
            fullPass = true;
        }
        if (cursor == 0 && (fullPass || rim == UNKNOWN)) rim = computeRim(level);
        int processed = 0;
        while (cursor < ground.length && processed < budget) {
            applyTile(level, cursor++);
            processed++;
        }
        if (cursor >= ground.length) {
            cursor = 0;
            appliedLevel = this.level;
            fullPass = false;
            if (this.level < target) this.level = Math.min(target, this.level + riseStep);
            else if (this.level > target) this.level = Math.max(target, this.level - riseStep);
        }
        return true;
    }

    /** Lowest ground among the columns just outside the area (loaded ones only). */
    private int computeRim(ServerLevel world) {
        int side = tiles() * 3, min = Integer.MAX_VALUE;
        for (int i = -1; i <= side; i++) {
            min = Math.min(min, columnGround(world, minX() + i, minZ() - 1));
            min = Math.min(min, columnGround(world, minX() + i, minZ() + side));
            min = Math.min(min, columnGround(world, minX() - 1, minZ() + i));
            min = Math.min(min, columnGround(world, minX() + side, minZ() + i));
        }
        return min == Integer.MAX_VALUE ? baseY : min;
    }

    private static int columnGround(ServerLevel world, int x, int z) {
        if (!world.isLoaded(new BlockPos(x, 0, z))) return Integer.MAX_VALUE;
        return world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }

    /**
     * Absolute surface (half blocks) of a tile for a given flood level. Contained floods may only stand above the
     * basin rim (or the flood's own base, whichever is higher) as a gentle ramp: half a block in the outermost ring
     * of tiles, plus half a block per tile further in. Inside a basin the water is flat; on open ground it forms a
     * low, wide sheet instead of a tower.
     */
    private int surfaceAt(int tile, int lvl) {
        int abs = (baseY + 1) * 2 + lvl;
        if (taper && rim != UNKNOWN) {
            int tx = tile % tiles() - halfTiles, tz = tile / tiles() - halfTiles;
            int ring = halfTiles - Math.max(Math.abs(tx), Math.abs(tz)); // 0 = outermost ring of tiles
            int floor = Math.max(rim, baseY);
            abs = Math.min(abs, (floor + 1) * 2 + 1 + ring);
        }
        return abs;
    }

    private int surface(int tile) {
        return surfaceAt(tile, level);
    }

    /** Water level (0-2 half blocks) a tile wants at height y, or 0 if it is dry there or unknown. */
    private int wanted(ServerLevel world, int tile, int y) {
        int g = ground(world, tile);
        if (g == UNKNOWN || y <= g) return 0;
        return Math.max(0, Math.min(2, surface(tile) - y * 2));
    }

    private int neighbour(int tile, int dx, int dz) {
        int tx = tile % tiles() + dx, tz = tile / tiles() + dz;
        if (tx < 0 || tz < 0 || tx >= tiles() || tz >= tiles()) return -1;
        return tz * tiles() + tx;
    }

    private void applyTile(ServerLevel world, int tile) {
        int g = ground(world, tile);
        if (g == UNKNOWN || g > baseY + maxRise) return;
        int cx = tileCenterX(tile), cz = tileCenterZ(tile);
        int top = baseY + 1 + maxRise;
        int north = neighbour(tile, 0, -1), south = neighbour(tile, 0, 1), west = neighbour(tile, -1, 0), east = neighbour(tile, 1, 0);
        BlockState centre = ModRegistry.FLOOD_BLOCK.get().defaultBlockState();
        BlockState filler = ModRegistry.FLOOD_FILLER.get().defaultBlockState();

        int yFrom = g + 1, yTo = top;
        if (!fullPass) {
            // only the layers the surface moved through (plus one either side for the TOP/side flags) can change
            int lowSurface = surfaceAt(tile, Math.min(level, appliedLevel)) / 2;
            int highSurface = surfaceAt(tile, Math.max(level, appliedLevel)) / 2;
            yFrom = Math.max(yFrom, lowSurface - 2);
            yTo = Math.min(yTo, highSurface + 2);
        }
        for (int y = yFrom; y <= yTo; y++) {
            int h = wanted(world, tile, y);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = new BlockPos(cx + dx, y, cz + dz);
                    BlockState current = world.getBlockState(pos);
                    if (!(current.getBlock() instanceof AbstractFloodBlock) && !current.isAir()) continue;
                    BlockState want;
                    if (h == 0) {
                        want = Blocks.AIR.defaultBlockState();
                    } else if (dx == 0 && dz == 0) {
                        want = centre.setValue(AbstractFloodBlock.LEVEL, h)
                                .setValue(FloodBlock.TOP, h < 2 || wanted(world, tile, y + 1) == 0)
                                .setValue(FloodBlock.NORTH, open(world, north, cx, y, cz - 3, h))
                                .setValue(FloodBlock.SOUTH, open(world, south, cx, y, cz + 3, h))
                                .setValue(FloodBlock.WEST, open(world, west, cx - 3, y, cz, h))
                                .setValue(FloodBlock.EAST, open(world, east, cx + 3, y, cz, h));
                    } else {
                        want = filler.setValue(AbstractFloodBlock.LEVEL, h);
                    }
                    if (current != want) world.setBlock(pos, want, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /**
     * Whether the side facing a neighbouring tile must be drawn: it is hidden when the neighbour holds at least as
     * much water, either in this flood or in an adjacent flood area (so neighbouring floods join seamlessly).
     */
    private boolean open(ServerLevel world, int neighbour, int nx, int y, int nz, int h) {
        if (neighbour >= 0) return wanted(world, neighbour, y) < h;
        BlockPos pos = new BlockPos(nx, y, nz);
        if (!world.isLoaded(pos)) return true;
        BlockState other = world.getBlockState(pos);
        return !(other.getBlock() instanceof AbstractFloodBlock) || other.getValue(AbstractFloodBlock.LEVEL) < h;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putInt("cx", centerX);
        t.putInt("cz", centerZ);
        t.putInt("base", baseY);
        t.putInt("rise", maxRise);
        t.putInt("half", halfTiles);
        t.putInt("storm", stormId);
        t.putInt("hold", holdTicks);
        t.putInt("level", level);
        t.putInt("target", target);
        t.putBoolean("draining", draining);
        t.putIntArray("ground", ground);
        t.putInt("format", 2);
        t.putBoolean("taper", taper);
        t.putInt("riseStep", riseStep);
        return t;
    }

    public static Flood load(CompoundTag t) {
        Flood f = create(t.getInt("id"), t.getInt("cx"), t.getInt("cz"), t.getInt("base"), t.getInt("rise"), t.getInt("half"));
        f.stormId = t.getInt("storm");
        f.holdTicks = t.getInt("hold");
        f.target = t.getInt("target");
        f.draining = t.getBoolean("draining");
        f.taper = !t.contains("taper") || t.getBoolean("taper");
        f.riseStep = Math.max(1, t.getInt("riseStep"));
        if (t.getInt("format") == 2) {
            f.level = t.getInt("level");
            int[] g = t.getIntArray("ground");
            if (g.length == f.ground.length) f.ground = g;
        } else {
            f.drain(); // floods saved by the old version: just let them go
        }
        return f;
    }
}
