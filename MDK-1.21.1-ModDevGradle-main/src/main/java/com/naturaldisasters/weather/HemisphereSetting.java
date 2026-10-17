package com.naturaldisasters.weather;

import com.naturaldisasters.Config;

/**
 * Hemisphere picked on the Create World screen. The integrated server runs in the same JVM, so the choice is
 * read when the world's weather data is created for the first time; dedicated servers use the config default.
 */
public final class HemisphereSetting {
    private static volatile Boolean pendingSouth;

    private HemisphereSetting() {}

    public static void setPending(boolean south) {
        pendingSouth = south;
    }

    public static void clearPending() {
        pendingSouth = null;
    }

    public static boolean forNewWorld() {
        Boolean p = pendingSouth;
        return p != null ? p : Config.DEFAULT_HEMISPHERE.get() == Config.Hemisphere.SOUTH;
    }
}
