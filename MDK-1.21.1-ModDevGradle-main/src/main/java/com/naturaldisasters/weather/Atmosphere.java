package com.naturaldisasters.weather;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.Tags;

/**
 * Invisible atmospheric model. Temperature, humidity and pressure are derived from the biome under a point
 * (also works for unloaded chunks, the biome source is sampled directly), the day/night cycle and slow
 * drifting noise so that the same place does not always have identical conditions.
 */
public final class Atmosphere {
    public record Reading(float temperature, float humidity, float pressure) {}

    /** Result of scanning the area around a player for frontal boundaries. */
    public record FrontAnalysis(float instability, float avgHumidity, float avgTemperature, double frontX, double frontZ) {}

    private Atmosphere() {}

    public static Reading sample(ServerLevel level, double x, double z) {
        Holder<Biome> biome = level.getBiome(BlockPos.containing(x, level.getSeaLevel() + 4, z));
        long time = level.getGameTime();
        float temp = biome.value().getBaseTemperature() * 20f + 2f;
        double dayFraction = (level.getDayTime() % 24000L) / 24000.0;
        temp += (float) Math.sin(dayFraction * Math.PI * 2) * 5f;
        temp += noise(x, z, time) * 3f;

        float hum = humidityOf(biome) + noise(z + 9134.0, x - 3412.0, time) * 0.08f;
        hum = Math.max(0f, Math.min(1f, hum));

        DisasterData data = DisasterData.get(level);
        float enso = data.ensoStrength(level);
        if (enso > 0) {
            temp += data.enso.temperatureOffset(enso);
            hum = Math.max(0f, Math.min(1f, hum + data.enso.humidityOffset(enso, data.southern)));
        }

        float pressure = 1013f - (temp - 15f) * 0.45f - hum * 6f + noise(x - 7777.0, z + 1234.0, time / 2) * 4f;
        return new Reading(temp, hum, pressure);
    }

    public static float humidityOf(Holder<Biome> b) {
        if (b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_DEEP_OCEAN)) return 0.95f;
        if (b.is(BiomeTags.IS_RIVER)) return 0.9f;
        if (b.is(Tags.Biomes.IS_SWAMP) || b.is(BiomeTags.IS_JUNGLE)) return 0.88f;
        if (b.is(BiomeTags.IS_BEACH)) return 0.75f;
        if (!b.value().hasPrecipitation()) return 0.1f;
        if (b.is(BiomeTags.IS_FOREST) || b.is(BiomeTags.IS_TAIGA)) return 0.65f;
        if (b.is(Tags.Biomes.IS_PLAINS)) return 0.55f;
        return 0.5f;
    }

    public static boolean isWater(Holder<Biome> b) {
        return b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_DEEP_OCEAN) || b.is(BiomeTags.IS_RIVER);
    }

    public static boolean isDesertLike(Holder<Biome> b) {
        return b.is(Tags.Biomes.IS_DESERT) || b.is(BiomeTags.IS_BADLANDS);
    }

    /**
     * Samples a 3x3 grid around a point. Neighbouring samples where a hot, dry air mass (desert) meets a cool,
     * humid one (plains, ocean, river) produce a frontal boundary with high instability, which favours supercells.
     */
    public static FrontAnalysis analyzeFronts(ServerLevel level, double cx, double cz) {
        final int n = 3;
        final double spacing = 160;
        Reading[][] grid = new Reading[n][n];
        float sumH = 0, sumT = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                Reading r = sample(level, cx + (i - 1) * spacing, cz + (j - 1) * spacing);
                grid[i][j] = r;
                sumH += r.humidity();
                sumT += r.temperature();
            }
        }
        float best = 0;
        double fx = cx, fz = cz;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                for (int[] d : new int[][]{{1, 0}, {0, 1}}) {
                    int i2 = i + d[0], j2 = j + d[1];
                    if (i2 >= n || j2 >= n) continue;
                    Reading a = grid[i][j], b = grid[i2][j2];
                    Reading hot = a.temperature() >= b.temperature() ? a : b;
                    Reading cold = hot == a ? b : a;
                    if (hot.humidity() > 0.35f || cold.humidity() < 0.45f) continue;
                    float dT = Math.min(1f, (hot.temperature() - cold.temperature()) / 25f);
                    float inst = dT * cold.humidity() * (1f - hot.humidity());
                    if (inst > best) {
                        best = inst;
                        fx = cx + ((i + i2) / 2.0 - 1) * spacing;
                        fz = cz + ((j + j2) / 2.0 - 1) * spacing;
                    }
                }
            }
        }
        float avgH = sumH / (n * n), avgT = sumT / (n * n);
        if (avgT > 18f && avgH > 0.6f) best = Math.max(best, 0.35f * avgH);
        return new FrontAnalysis(Math.min(1f, best), avgH, avgT, fx, fz);
    }

    /** Cheap smooth deterministic noise in [-1, 1] that drifts with time. */
    public static float noise(double x, double z, long time) {
        double sx = x / 600.0 + time / 48000.0;
        double sz = z / 600.0 - time / 72000.0;
        return (float) (Math.sin(sx * 1.7 + Math.sin(sz * 1.3)) * Math.cos(sz * 1.1 - Math.sin(sx * 0.7)));
    }
}
