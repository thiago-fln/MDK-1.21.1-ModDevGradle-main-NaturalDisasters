package com.naturaldisasters.gametest;

import com.naturaldisasters.NaturalDisasters;
import com.naturaldisasters.entity.FirenadoEntity;
import com.naturaldisasters.entity.TornadoEntity;
import com.naturaldisasters.entity.TornadoVariant;
import com.naturaldisasters.weather.DisasterData;
import com.naturaldisasters.weather.StormSystem;
import com.naturaldisasters.weather.WeatherEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Run with the gameTestServer run configuration (./gradlew runGameTestServer). */
@GameTestHolder(NaturalDisasters.MODID)
@PrefixGameTestTemplate(false)
public class DisasterGameTests {
    private static final BlockPos CENTER = new BlockPos(8, 1, 8);

    private static void floor(GameTestHelper helper, Block block, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                helper.setBlock(CENTER.offset(x, 0, z), block);
            }
        }
    }

    private static TornadoEntity spawn(GameTestHelper helper, TornadoVariant variant, float intensity) {
        BlockPos abs = helper.absolutePos(CENTER);
        TornadoEntity t = TornadoEntity.spawn(helper.getLevel(), variant, abs.getX() + 0.5, abs.getZ() + 0.5, intensity, 0, 2000, -1);
        helper.assertTrue(t != null, "tornado did not spawn");
        return t;
    }

    private static List<? extends TornadoEntity> tornadoesNear(GameTestHelper helper, int range) {
        BlockPos abs = helper.absolutePos(CENTER);
        AABB box = new AABB(abs).inflate(range);
        return helper.getLevel().getEntities(EntityTypeTest.forClass(TornadoEntity.class), e -> e.getBoundingBox().intersects(box));
    }

    private static void cleanup(GameTestHelper helper) {
        for (TornadoEntity t : tornadoesNear(helper, 200)) t.discard();
        DisasterData data = DisasterData.get(helper.getLevel());
        data.storms.clear();
        data.earthquakes.clear();
        data.tornadoes.clear();
    }

    @GameTest(template = "empty", skyAccess = true, batch = "tornado_damage", timeoutTicks = 260)
    public static void tornadoLiftsBlocksAndMobs(GameTestHelper helper) {
        cleanup(helper);
        floor(helper, Blocks.DIRT, 6);
        floor(helper, Blocks.OAK_PLANKS, 0);
        Pig pig = helper.spawn(EntityType.PIG, CENTER.offset(3, 1, 0));
        double startY = pig.getY();
        TornadoEntity t = spawn(helper, TornadoVariant.CLASSIC, 5.5f);
        final boolean[] pigLifted = {false};
        helper.onEachTick(() -> {
            if (pig.isAlive() && pig.getY() > startY + 2) pigLifted[0] = true;
        });
        helper.runAfterDelay(200, () -> {
            int dirtLeft = 0;
            for (int x = -6; x <= 6; x++) {
                for (int z = -6; z <= 6; z++) {
                    if (helper.getBlockState(CENTER.offset(x, 0, z)).is(Blocks.DIRT)) dirtLeft++;
                }
            }
            boolean alive = t.isAlive() || !tornadoesNear(helper, 80).isEmpty(); // it may have turned into a firenado over leftover lava
            cleanup(helper);
            helper.assertTrue(alive, "tornado died early");
            helper.assertTrue(dirtLeft < 169, "F5 tornado destroyed no dirt");
            helper.assertTrue(pigLifted[0] || !pig.isAlive(), "pig was never lifted");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "tornado_stone", timeoutTicks = 200)
    public static void weakTornadoOnlyScratchesStone(GameTestHelper helper) {
        cleanup(helper);
        floor(helper, Blocks.STONE_BRICKS, 6);
        TornadoEntity t = spawn(helper, TornadoVariant.CLASSIC, 2.5f);
        helper.runAfterDelay(160, () -> {
            int gone = 0;
            for (int x = -6; x <= 6; x++) {
                for (int z = -6; z <= 6; z++) {
                    var s = helper.getBlockState(CENTER.offset(x, 0, z));
                    if (!s.is(Blocks.STONE_BRICKS) && !s.is(Blocks.CRACKED_STONE_BRICKS)) gone++;
                }
            }
            cleanup(helper);
            helper.assertTrue(gone == 0, "F2 tornado removed " + gone + " stone brick blocks");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "firenado", timeoutTicks = 200)
    public static void tornadoOverLavaBecomesFirenado(GameTestHelper helper) {
        cleanup(helper);
        floor(helper, Blocks.LAVA, 7);
        spawn(helper, TornadoVariant.CLASSIC, 2f);
        helper.succeedWhen(() -> {
            boolean fire = tornadoesNear(helper, 40).stream().anyMatch(e -> e instanceof FirenadoEntity);
            helper.assertTrue(fire, "no firenado yet");
            cleanup(helper);
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "variants", timeoutTicks = 200)
    public static void multivortexAndWaterspoutTick(GameTestHelper helper) {
        cleanup(helper);
        floor(helper, Blocks.GRASS_BLOCK, 6);
        TornadoEntity mv = spawn(helper, TornadoVariant.MULTIVORTEX, 4f);
        TornadoEntity ws = spawn(helper, TornadoVariant.WATERSPOUT, 2f);
        helper.runAfterDelay(120, () -> {
            boolean mvAlive = mv.isAlive();
            boolean wsGone = !ws.isAlive() || ws.getScale() < 1f; // waterspout over land falls apart
            cleanup(helper);
            helper.assertTrue(mvAlive, "multivortex died early");
            helper.assertTrue(wsGone, "waterspout survived on dry land");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "earthquake", timeoutTicks = 500)
    public static void earthquakeRunsToCompletion(GameTestHelper helper) {
        cleanup(helper);
        ServerLevel level = helper.getLevel();
        DisasterData data = DisasterData.get(level);
        WeatherEngine.startEarthquake(level, data, helper.absolutePos(CENTER), 2f);
        helper.succeedWhen(() -> helper.assertTrue(data.earthquakes.isEmpty(), "earthquake still running"));
    }

    @GameTest(template = "empty", skyAccess = true, batch = "storm", timeoutTicks = 400)
    public static void supercellTouchesDown(GameTestHelper helper) {
        cleanup(helper);
        floor(helper, Blocks.GRASS_BLOCK, 6);
        ServerLevel level = helper.getLevel();
        DisasterData data = DisasterData.get(level);
        BlockPos abs = helper.absolutePos(CENTER);
        StormSystem s = WeatherEngine.spawnStorm(level, data, abs.getX(), abs.getZ(), true, 1f);
        s.vx = s.vz = 0;
        s.energy = 0.95f;
        s.warningTicks = 360;
        s.updateDerived();
        helper.succeedWhen(() -> {
            helper.assertTrue(s.tornado != null, "no touchdown yet");
            cleanup(helper);
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "new_variants", timeoutTicks = 200)
    public static void newVariantsSpawnAndTick(GameTestHelper helper) {
        cleanup(helper);
        floor(helper, Blocks.GRASS_BLOCK, 6);
        TornadoEntity rope = spawn(helper, TornadoVariant.ROPE, 2f);
        TornadoEntity wedge = spawn(helper, TornadoVariant.WEDGE, 4f);
        TornadoEntity land = spawn(helper, TornadoVariant.LANDSPOUT, 4f);
        TornadoEntity elReno = spawn(helper, TornadoVariant.EL_RENO, 1f);
        elReno.skipFormation();
        helper.runAfterDelay(100, () -> {
            // a variant may turn into a firenado over leftover lava from another test: count tornadoes, not entities
            boolean alive = tornadoesNear(helper, 120).size() >= 4;
            int landCat = land.getCategory(), renoCat = elReno.getCategory();
            int subs = ((com.naturaldisasters.entity.MultivortexEntity) elReno).getSubVortexCount();
            boolean wider = wedge.getCoreRadius() > rope.getCoreRadius() * 2;
            cleanup(helper);
            helper.assertTrue(alive, "a new variant died early");
            helper.assertTrue(landCat == 1, "landspout stronger than EF1: " + landCat);
            helper.assertTrue(renoCat >= 3, "El Reno weaker than EF3: " + renoCat);
            helper.assertTrue(subs >= 4 && subs <= 6, "El Reno sub-vortices: " + subs);
            helper.assertTrue(wider, "wedge is not wider than rope");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "flood", timeoutTicks = 400)
    public static void floodFillsBasinAndDrainsCleanly(GameTestHelper helper) {
        cleanup(helper);
        floor(helper, Blocks.STONE, 7);
        // a stone basin around the 9x9 flood area: the water is contained and can rise the full 3 blocks
        for (int i = -5; i <= 5; i++) {
            for (int y = 1; y <= 4; y++) {
                helper.setBlock(CENTER.offset(i, y, -5), Blocks.STONE);
                helper.setBlock(CENTER.offset(i, y, 5), Blocks.STONE);
                helper.setBlock(CENTER.offset(-5, y, i), Blocks.STONE);
                helper.setBlock(CENTER.offset(5, y, i), Blocks.STONE);
            }
        }
        ServerLevel level = helper.getLevel();
        DisasterData data = DisasterData.get(level);
        data.flood.floods.clear();
        data.flood.startFlood(level, data.newId(), helper.absolutePos(CENTER.above()), 3, 3, 60);
        var lvl = com.naturaldisasters.block.AbstractFloodBlock.LEVEL;
        var centre = com.naturaldisasters.registry.ModRegistry.FLOOD_BLOCK.get();
        var filler = com.naturaldisasters.registry.ModRegistry.FLOOD_FILLER.get();
        helper.runAfterDelay(60, () -> {
            for (int y = 1; y <= 3; y++) {
                var state = helper.getBlockState(CENTER.above(y));
                helper.assertTrue(state.is(centre) && state.getValue(lvl) == 2, "unit not full at +" + y + ": " + state);
                var side = helper.getBlockState(CENTER.offset(1, y, 1));
                helper.assertTrue(side.is(filler) && side.getValue(lvl) == 2, "filler missing at +" + y + ": " + side);
                var edge = helper.getBlockState(CENTER.offset(3, y, 0));
                helper.assertTrue(edge.is(centre) && edge.getValue(lvl) == 2, "contained edge not full at +" + y + ": " + edge);
            }
            helper.assertTrue(helper.getBlockState(CENTER.above(3)).getValue(com.naturaldisasters.block.FloodBlock.TOP), "top unit has no surface");
            helper.assertTrue(!helper.getBlockState(CENTER.above(1)).getValue(com.naturaldisasters.block.FloodBlock.TOP), "buried unit draws a surface");
            helper.assertTrue(helper.getBlockState(CENTER.above(4)).isAir(), "flood rose above its height");
            helper.assertTrue(helper.getBlockState(CENTER.above()).getDestroySpeed(level, helper.absolutePos(CENTER.above())) < 0,
                    "flood block is breakable");
        });
        helper.runAfterDelay(300, () -> {
            assertNoFloodLeft(helper);
            helper.assertTrue(data.flood.floods.isEmpty(), "flood never finished draining");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "flood_flat", timeoutTicks = 400)
    public static void floodOnFlatGroundStaysShallow(GameTestHelper helper) {
        cleanup(helper);
        floor(helper, Blocks.STONE, 7);
        ServerLevel level = helper.getLevel();
        DisasterData data = DisasterData.get(level);
        data.flood.floods.clear();
        data.flood.startFlood(level, data.newId(), helper.absolutePos(CENTER.above()), 9, 3, 60);
        var lvl = com.naturaldisasters.block.AbstractFloodBlock.LEVEL;
        var centre = com.naturaldisasters.registry.ModRegistry.FLOOD_BLOCK.get();
        helper.runAfterDelay(80, () -> {
            // nothing holds the water in: it spreads one block deep instead of standing up as a tower
            var state = helper.getBlockState(CENTER.above(1));
            helper.assertTrue(state.is(centre) && state.getValue(lvl) == 2, "flat flood should be one block deep: " + state);
            helper.assertTrue(helper.getBlockState(CENTER.above(2)).isAir(), "flood formed a tower on flat ground");
            var edge = helper.getBlockState(CENTER.offset(3, 1, 0));
            helper.assertTrue(edge.is(centre) && edge.getValue(lvl) == 1, "edge should thin out to half a block: " + edge);
        });
        helper.runAfterDelay(300, () -> {
            assertNoFloodLeft(helper);
            helper.assertTrue(data.flood.floods.isEmpty(), "flood never finished draining");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "flood_height", timeoutTicks = 700)
    public static void floodRisesFromPlayerHeight(GameTestHelper helper) {
        cleanup(helper);
        floor(helper, Blocks.STONE, 7);
        for (int i = -5; i <= 5; i++) {
            for (int y = 1; y <= 8; y++) {
                helper.setBlock(CENTER.offset(i, y, -5), Blocks.STONE);
                helper.setBlock(CENTER.offset(i, y, 5), Blocks.STONE);
                helper.setBlock(CENTER.offset(-5, y, i), Blocks.STONE);
                helper.setBlock(CENTER.offset(5, y, i), Blocks.STONE);
            }
        }
        ServerLevel level = helper.getLevel();
        DisasterData data = DisasterData.get(level);
        data.flood.floods.clear();
        // "player" standing 3 blocks above the basin floor asks for a 3 block flood
        data.flood.startFlood(level, data.newId(), helper.absolutePos(CENTER.above(4)), 3, 3, 200);
        var block = com.naturaldisasters.registry.ModRegistry.FLOOD_BLOCK.get();
        helper.runAfterDelay(90, () -> {
            for (int y = 1; y <= 6; y++) {
                helper.assertTrue(helper.getBlockState(CENTER.above(y)).is(block), "basin not filled at +" + y);
            }
            helper.assertTrue(helper.getBlockState(CENTER.above(7)).isAir(), "water should stop 3 blocks above the player's feet");
        });
        helper.runAfterDelay(550, () -> {
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    for (int y = 1; y <= 8; y++) {
                        helper.assertTrue(!(helper.getBlockState(CENTER.offset(x, y, z)).getBlock()
                                instanceof com.naturaldisasters.block.AbstractFloodBlock), "flood block left behind");
                    }
                }
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "scour", timeoutTicks = 300)
    public static void tornadoScoursGrassIntoDirt(GameTestHelper helper) {
        cleanup(helper);
        floor(helper, Blocks.GRASS_BLOCK, 7);
        TornadoEntity t = spawn(helper, TornadoVariant.CLASSIC, 3.5f);
        t.skipFormation();
        helper.runAfterDelay(120, () -> {
            int bare = 0;
            for (int x = -7; x <= 7; x++) {
                for (int z = -7; z <= 7; z++) {
                    var st = helper.getBlockState(CENTER.offset(x, 0, z));
                    if (st.is(Blocks.DIRT) || st.is(Blocks.COARSE_DIRT)) bare++;
                }
            }
            cleanup(helper);
            helper.assertTrue(bare > 0, "the tornado left no scoured track");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "weather", timeoutTicks = 200)
    public static void forcedWeatherControlsRainAndThunder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DisasterData data = DisasterData.get(level);
        data.forcedWeather = com.naturaldisasters.weather.ForcedWeather.THUNDER;
        data.forcedUntil = level.getGameTime() + 1000;
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(level.getLevelData().isRaining() && level.getLevelData().isThundering(), "thunder weather not applied");
            data.forcedWeather = com.naturaldisasters.weather.ForcedWeather.SUN;
        });
        helper.runAfterDelay(90, () -> {
            boolean clear = !level.getLevelData().isRaining() && !level.getLevelData().isThundering();
            data.forcedWeather = com.naturaldisasters.weather.ForcedWeather.AUTO;
            helper.assertTrue(clear, "sun weather did not clear the rain");
            helper.succeed();
        });
    }

    private static void assertNoFloodLeft(GameTestHelper helper) {
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                for (int y = 1; y <= 4; y++) {
                    helper.assertTrue(!(helper.getBlockState(CENTER.offset(x, y, z)).getBlock()
                            instanceof com.naturaldisasters.block.AbstractFloodBlock), "flood block left behind");
                }
            }
        }
    }

    @GameTest(template = "empty", skyAccess = true, batch = "climate", timeoutTicks = 100)
    public static void hemisphereAndEnso(GameTestHelper helper) {
        cleanup(helper);
        ServerLevel level = helper.getLevel();
        DisasterData data = DisasterData.get(level);
        boolean wasSouth = data.southern;
        data.southern = true;
        TornadoEntity south = spawn(helper, TornadoVariant.CLASSIC, 2f);
        data.southern = false;
        TornadoEntity north = spawn(helper, TornadoVariant.CLASSIC, 2f);
        float southDir = south.spinDirection(), northDir = north.spinDirection();

        long day = DisasterData.day(level);
        data.enso.start(level, com.naturaldisasters.weather.Enso.Phase.LA_NINA, day - 40, 6, level.random);
        float strength = data.enso.strength(day);
        float northTornadoes = data.enso.supercellFactor(strength, false), southTornadoes = data.enso.supercellFactor(strength, true);
        data.enso.start(level, com.naturaldisasters.weather.Enso.Phase.NEUTRAL, day, 1, level.random);
        data.southern = wasSouth;
        cleanup(helper);

        helper.assertTrue(southDir < 0 && northDir > 0, "rotation should be clockwise in the south: " + southDir + " / " + northDir);
        helper.assertTrue(strength > 0, "La Niña has no strength");
        helper.assertTrue(northTornadoes > 1 && southTornadoes < 1, "La Niña should favour tornadoes in the north only");
        helper.succeed();
    }

    @GameTest(template = "empty", skyAccess = true, batch = "tsunami", timeoutTicks = 900)
    public static void tsunamiFloodsLandAndRecedes(GameTestHelper helper) {
        cleanup(helper);
        ServerLevel level = helper.getLevel();
        DisasterData data = DisasterData.get(level);
        data.flood.floods.clear();
        data.tsunamis.clear();
        // its own flat "beach" so leftovers from other tests can't stop the wave
        for (int x = -18; x <= 8; x++) {
            for (int z = -6; z <= 6; z++) helper.setBlock(CENTER.offset(x, 0, z), Blocks.SAND);
        }
        BlockPos abs = helper.absolutePos(CENTER);
        int ground = abs.getY();
        Pig pig = helper.spawn(EntityType.PIG, CENTER.above());
        double pigStart = pig.getX();
        // wave starts 15 blocks west of the pig heading east, "sea level" at the beach
        data.tsunamis.add(com.naturaldisasters.disaster.Tsunami.create(data.newId(), abs.getX() - 15, abs.getZ(), 1, 0, 12, 8, ground));
        final boolean[] flooded = {false};
        helper.onEachTick(() -> {
            if (flooded[0]) return;
            for (int x = -20; x <= 20 && !flooded[0]; x++) {
                for (int z = -20; z <= 20 && !flooded[0]; z++) {
                    var st = level.getBlockState(abs.offset(x, ground + 1 - abs.getY(), z));
                    if (st.getBlock() instanceof com.naturaldisasters.block.AbstractFloodBlock) flooded[0] = true;
                }
            }
        });
        helper.runAfterDelay(120, () -> {
            boolean pushed = !pig.isAlive() || pig.getX() > pigStart + 3;
            helper.assertTrue(flooded[0], "tsunami did not flood the land");
            helper.assertTrue(pushed, "wave did not carry the pig inland");
            data.tsunamis.clear();
            data.flood.drainAll();
            helper.succeedWhen(() -> helper.assertTrue(data.flood.floods.isEmpty(), "tsunami flood still draining"));
        });
    }
}
