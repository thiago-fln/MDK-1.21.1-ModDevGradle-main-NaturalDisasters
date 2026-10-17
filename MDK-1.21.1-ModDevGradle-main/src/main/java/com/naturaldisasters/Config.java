package com.naturaldisasters;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    public enum Hemisphere { NORTH, SOUTH }

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    // ---------------------------------------------------------------- weather
    static { B.comment("Global weather engine (pressure / temperature / humidity per biome)").push("weather"); }

    public static final ModConfigSpec.BooleanValue WEATHER_ENGINE = B
            .comment("Enable the weather engine (storm systems, supercells, tornado touchdowns).")
            .define("enableWeatherEngine", true);
    public static final ModConfigSpec.BooleanValue CONTROL_VANILLA_WEATHER = B
            .comment("Let the engine drive vanilla rain/thunder: it rains only while a storm system is near a player.")
            .define("controlVanillaWeather", true);
    public static final ModConfigSpec.DoubleValue STORM_FREQUENCY = B
            .comment("Multiplier for how often new storm systems form.")
            .defineInRange("stormFrequency", 1.0, 0.0, 20.0);
    public static final ModConfigSpec.DoubleValue STORM_GROWTH = B
            .comment("Multiplier for how fast storms evolve through their stages.")
            .defineInRange("stormGrowthSpeed", 1.0, 0.1, 10.0);
    public static final ModConfigSpec.IntValue MAX_STORMS = B
            .comment("Maximum storm systems within 900 blocks of a single player.")
            .defineInRange("maxStormsPerPlayer", 4, 0, 16);
    public static final ModConfigSpec.BooleanValue STORM_LIGHTNING = B
            .comment("Thunderstorms strike real lightning bolts inside the storm cell.")
            .define("stormLightning", true);
    public static final ModConfigSpec.BooleanValue STORM_ALERTS = B
            .comment("Send wall cloud / tornado warning messages to nearby players.")
            .define("stormAlerts", true);

    public static final ModConfigSpec.EnumValue<Hemisphere> DEFAULT_HEMISPHERE = B
            .comment("Hemisphere for NEW worlds when it isn't picked on the Create World screen (dedicated servers).",
                    "Southern hemisphere: tornadoes and mesocyclones rotate clockwise, and El Niño / La Niña have the opposite effects.")
            .defineEnum("defaultHemisphere", Hemisphere.NORTH);

    static { B.pop(); B.comment("El Niño / La Niña (ENSO) cycle").push("enso"); }

    public static final ModConfigSpec.BooleanValue ENSO = B
            .comment("Enable the El Niño / La Niña cycle.")
            .define("enableEnso", true);
    public static final ModConfigSpec.IntValue MONTH_DAYS = B
            .comment("Length of a Minecraft 'month' in Minecraft days (1 day = 20 real minutes).")
            .defineInRange("monthLengthDays", 30, 1, 365);
    public static final ModConfigSpec.IntValue ENSO_MIN_MONTHS = B
            .comment("Shortest El Niño / La Niña episode, in months.")
            .defineInRange("episodeMinMonths", 4, 1, 60);
    public static final ModConfigSpec.IntValue ENSO_MAX_MONTHS = B
            .comment("Longest El Niño / La Niña episode, in months.")
            .defineInRange("episodeMaxMonths", 9, 1, 60);
    public static final ModConfigSpec.IntValue NEUTRAL_MIN_MONTHS = B
            .comment("Shortest neutral period between episodes, in months.")
            .defineInRange("neutralMinMonths", 3, 1, 60);
    public static final ModConfigSpec.IntValue NEUTRAL_MAX_MONTHS = B
            .comment("Longest neutral period between episodes, in months.")
            .defineInRange("neutralMaxMonths", 8, 1, 60);

    static { B.pop(); B.comment("Tornadoes").push("tornado"); }

    public static final ModConfigSpec.IntValue MAX_INTENSITY = B
            .comment("Highest Fujita category a natural tornado can reach.")
            .defineInRange("maxIntensity", 5, 1, 5);
    public static final ModConfigSpec.IntValue MAX_TORNADOES = B
            .comment("Maximum simultaneous tornadoes per dimension spawned by storms.")
            .defineInRange("maxActiveTornadoes", 3, 0, 32);
    public static final ModConfigSpec.BooleanValue BLOCK_DAMAGE = B
            .comment("Tornadoes destroy and lift blocks.")
            .define("blockDamage", true);
    public static final ModConfigSpec.BooleanValue RESPECT_MOB_GRIEFING = B
            .comment("Disable tornado block damage when the mobGriefing gamerule is false.")
            .define("respectMobGriefing", false);
    public static final ModConfigSpec.DoubleValue DESTRUCTION_RATE = B
            .comment("Multiplier for block destruction attempts per tick.")
            .defineInRange("destructionRate", 1.0, 0.0, 10.0);
    public static final ModConfigSpec.IntValue MAX_SUSPENDED_BLOCKS = B
            .comment("Maximum blocks a single tornado keeps suspended in the air. When exceeded the oldest debris is removed.")
            .defineInRange("maxSuspendedBlocks", 150, 0, 2000);
    public static final ModConfigSpec.BooleanValue EXCESS_DEBRIS_DROPS = B
            .comment("true: evicted debris drops on the ground as an item. false: evicted debris is deleted.")
            .define("excessDebrisDropsItems", true);
    public static final ModConfigSpec.BooleanValue DEBRIS_DROPS = B
            .comment("Lifted blocks that cannot be placed when landing drop as items.")
            .define("debrisDropsItems", true);
    public static final ModConfigSpec.BooleanValue AFFECT_PLAYERS = B
            .comment("Tornadoes pull survival-mode players.")
            .define("affectPlayers", true);
    public static final ModConfigSpec.DoubleValue TORNADO_SPEED = B
            .comment("Multiplier for tornado ground speed.")
            .defineInRange("tornadoSpeed", 1.0, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue TORNADO_LIFETIME = B
            .comment("Multiplier for tornado lifetime.")
            .defineInRange("tornadoLifetime", 1.0, 0.1, 10.0);
    public static final ModConfigSpec.BooleanValue FIRENADO_FIRE = B
            .comment("Firenadoes set blocks on fire.")
            .define("firenadoPlacesFire", true);

    static { B.pop(); B.comment("Dynamic flooding").push("flood"); }

    public static final ModConfigSpec.BooleanValue FLOODING = B
            .comment("Severe storms make rivers and oceans overflow. Flood blocks are temporary and removed when the flood drains.")
            .define("enableFlooding", true);
    public static final ModConfigSpec.IntValue MAX_FLOOD_HEIGHT = B
            .comment("How many blocks storm floods rise above the water level (rounded down to a multiple of 3).")
            .defineInRange("maxFloodHeight", 6, 3, 30);
    public static final ModConfigSpec.IntValue FLOOD_RATE = B
            .comment("Flood tiles (3x3 units) updated per tick, shared by all floods. Higher = faster rising, more server load.")
            .defineInRange("floodTilesPerTick", 240, 16, 5000);

    static { B.pop(); B.comment("Earthquakes").push("earthquake"); }

    public static final ModConfigSpec.BooleanValue EARTHQUAKES = B
            .define("enableEarthquakes", true);
    public static final ModConfigSpec.DoubleValue EARTHQUAKE_CHANCE = B
            .comment("Chance, checked every 10 seconds per player, of a natural earthquake.")
            .defineInRange("earthquakeChance", 0.0012, 0.0, 1.0);
    public static final ModConfigSpec.IntValue SHAKE_RADIUS = B
            .comment("Radius in blocks where players feel the camera shake.")
            .defineInRange("shakeRadius", 150, 16, 1024);
    public static final ModConfigSpec.BooleanValue FISSURES = B
            .comment("Earthquakes tear fissures in the ground.")
            .define("fissures", true);
    public static final ModConfigSpec.BooleanValue CAVE_COLLAPSES = B
            .comment("Earthquakes collapse cave ceilings.")
            .define("caveCollapses", true);

    public static final ModConfigSpec.BooleanValue TSUNAMIS = B
            .comment("Magnitude 7+ earthquakes near the ocean send a tsunami towards the coast.")
            .define("tsunamis", true);

    static { B.pop(); B.comment("Sandstorms").push("sandstorm"); }

    public static final ModConfigSpec.BooleanValue SANDSTORMS = B
            .define("enableSandstorms", true);
    public static final ModConfigSpec.DoubleValue SANDSTORM_CHANCE = B
            .comment("Chance, checked every 10 seconds per player standing in a desert, of a sandstorm.")
            .defineInRange("sandstormChance", 0.005, 0.0, 1.0);
    public static final ModConfigSpec.IntValue SAND_LAYER_RATE = B
            .comment("Sand layer placement attempts per tick around each player caught in a sandstorm. 0 disables burying.")
            .defineInRange("sandLayerRate", 3, 0, 64);

    static { B.pop(); B.comment("Warning systems").push("warning"); }

    public static final ModConfigSpec.IntValue SIREN_RADIUS = B
            .comment("Tornado siren detection radius in blocks.")
            .defineInRange("sirenRadius", 200, 16, 1024);
    public static final ModConfigSpec.IntValue SIREN_MIN_CATEGORY = B
            .comment("Minimum Fujita category that triggers the siren.")
            .defineInRange("sirenMinCategory", 2, 1, 5);

    static { B.pop(); }

    public static final ModConfigSpec SPEC = B.build();

    private Config() {}
}
