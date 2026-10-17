package com.naturaldisasters.client;

import com.naturaldisasters.network.RadarGridPayload;
import com.naturaldisasters.network.WeatherSyncPayload;
import org.jetbrains.annotations.Nullable;

/** Client-side copy of the server weather snapshot plus local visual state (shake, sand fog). */
public final class ClientWeatherState {
    @Nullable
    public static WeatherSyncPayload latest;
    public static long lastSyncTime;
    @Nullable
    public static RadarGridPayload grid;

    private static float shakeIntensity;
    private static int shakeTicks;
    private static int shakeDuration = 1;

    /** 0..1 smoothed sand fog factor (and the previous tick's value for interpolation). */
    public static float sandFog, sandFogPrev;
    public static int sandAmplifier;

    private ClientWeatherState() {}

    public static void addShake(float intensity, int duration) {
        float current = shake(0f);
        if (intensity >= current) {
            shakeIntensity = intensity;
            shakeTicks = duration;
            shakeDuration = Math.max(1, duration);
        } else {
            shakeTicks = Math.max(shakeTicks, duration / 2);
        }
    }

    public static float shake(float partialTick) {
        if (shakeTicks <= 0) return 0f;
        float remaining = Math.max(0f, shakeTicks - partialTick);
        return shakeIntensity * Math.min(1f, remaining / Math.min(10f, shakeDuration));
    }

    public static void tick(boolean inSandstorm, int amplifier) {
        if (shakeTicks > 0) shakeTicks--;
        sandFogPrev = sandFog;
        float target = inSandstorm ? (amplifier >= 1 ? 1f : 0.75f) : 0f;
        sandFog += (target - sandFog) * 0.06f;
        if (Math.abs(sandFog - target) < 0.002f) sandFog = target;
        sandAmplifier = amplifier;
    }

    public static float sandFog(float partialTick) {
        return sandFogPrev + (sandFog - sandFogPrev) * partialTick;
    }

    public static float windAngle() {
        return latest == null ? 0f : latest.windAngle();
    }

    public static void reset() {
        latest = null;
        grid = null;
        shakeTicks = 0;
        sandFog = sandFogPrev = 0;
    }
}
