package com.naturaldisasters;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue PARTICLE_DENSITY = B
            .comment("Multiplier for tornado and sandstorm particle density.")
            .defineInRange("particleDensity", 1.0, 0.0, 3.0);
    public static final ModConfigSpec.BooleanValue CAMERA_SHAKE = B
            .comment("Shake the camera during earthquakes and near tornadoes.")
            .define("cameraShake", true);
    public static final ModConfigSpec.DoubleValue SHAKE_STRENGTH = B
            .comment("Camera shake strength multiplier.")
            .defineInRange("shakeStrength", 1.0, 0.0, 3.0);
    public static final ModConfigSpec.BooleanValue STORM_CLOUDS = B
            .comment("Render wall clouds and storm bases in the sky.")
            .define("stormClouds", true);

    public static final ModConfigSpec.BooleanValue REPLACE_VANILLA_CLOUDS = B
            .comment("Replace the flat vanilla clouds with the mod's volumetric clouds (white in fair weather, grey in rain, dark in storms).")
            .define("replaceVanillaClouds", true);
    public static final ModConfigSpec.DoubleValue CLOUD_COVER = B
            .comment("Fair-weather cloud coverage (0 = clear sky, 1 = overcast).")
            .defineInRange("cloudCover", 0.35, 0.0, 1.0);
    public static final ModConfigSpec.IntValue CLOUD_RANGE = B
            .comment("How far (blocks) fair-weather clouds are drawn around the camera.")
            .defineInRange("cloudRange", 640, 128, 2048);
    public static final ModConfigSpec.EnumValue<ShaderMode> SHADER_COMPAT = B
            .comment("Shader pack (Iris) compatibility. AUTO detects an active Iris shader pack and switches to rendering",
                    "paths that shader packs light and shadow correctly. ON forces it, OFF disables it.")
            .defineEnum("shaderCompatibility", ShaderMode.AUTO);

    public enum ShaderMode { AUTO, ON, OFF }

    public static final ModConfigSpec SPEC = B.build();

    private ClientConfig() {}
}
