package com.naturaldisasters.weather;

import java.util.Locale;

/** Weather set by command. AUTO = the weather engine decides. */
public enum ForcedWeather {
    AUTO, SUN, CLOUDY, RAIN, THUNDER;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ForcedWeather byId(int id) {
        ForcedWeather[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, id))];
    }
}
