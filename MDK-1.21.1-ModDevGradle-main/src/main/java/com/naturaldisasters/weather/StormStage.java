package com.naturaldisasters.weather;

/** Rain → thunderstorm → wall cloud → tornado warning. */
public enum StormStage {
    RAIN(0f),
    THUNDERSTORM(0.3f),
    WALL_CLOUD(0.55f),
    TORNADO_WARNING(0.75f);

    public final float threshold;

    StormStage(float threshold) {
        this.threshold = threshold;
    }

    public static StormStage fromEnergy(float energy, boolean supercell) {
        StormStage result = RAIN;
        for (StormStage s : values()) {
            if (energy >= s.threshold) result = s;
        }
        if (!supercell && result.ordinal() > THUNDERSTORM.ordinal()) result = THUNDERSTORM;
        return result;
    }

    public String translationKey() {
        return "stage.naturaldisasters." + name().toLowerCase();
    }

    public static StormStage byId(int id) {
        StormStage[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, id))];
    }
}
