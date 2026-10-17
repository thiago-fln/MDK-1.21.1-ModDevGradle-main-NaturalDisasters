package com.naturaldisasters.weather;

import com.naturaldisasters.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.Locale;

/**
 * El Niño – Southern Oscillation. Episodes last months of Minecraft time, build up over the first month and fade
 * over the last one. Their effects depend on the world's hemisphere, loosely following the real teleconnections:
 * <ul>
 *   <li>El Niño — warmer everywhere. North: fewer supercells, more ordinary rain. South: much stormier and wetter
 *       (floods, supercells), like southern Brazil.</li>
 *   <li>La Niña — cooler. North: very active tornado seasons, drier deserts. South: drought, fewer storms, more
 *       sandstorms.</li>
 * </ul>
 */
public class Enso {
    public enum Phase {
        NEUTRAL, EL_NINO, LA_NINA;

        public String key() {
            return "enso.naturaldisasters." + name().toLowerCase(Locale.ROOT);
        }

        public static Phase byId(int id) {
            return values()[Mth.clamp(id, 0, 2)];
        }
    }

    public Phase phase = Phase.NEUTRAL;
    public long startDay = -1;
    public int durationDays;
    /** Peak strength of the current episode (0.4 weak … 1 very strong). */
    public float peak = 0.7f;

    /** Called every few seconds with the current Minecraft day. */
    public void tick(ServerLevel level, long day, RandomSource r) {
        if (!Config.ENSO.get()) return;
        if (startDay < 0) startNeutral(day, r); // first time this world is seen
        if (day < startDay) startDay = day;  // time set backwards
        if (day < startDay + durationDays) return;
        if (phase == Phase.NEUTRAL) {
            start(level, r.nextBoolean() ? Phase.EL_NINO : Phase.LA_NINA, day, -1, r);
        } else {
            announce(level, Component.translatable("enso.naturaldisasters.ended", Component.translatable(phase.key())), ChatFormatting.AQUA);
            startNeutral(day, r);
        }
    }

    private void startNeutral(long day, RandomSource r) {
        phase = Phase.NEUTRAL;
        startDay = day;
        durationDays = months(Config.NEUTRAL_MIN_MONTHS.get(), Config.NEUTRAL_MAX_MONTHS.get(), r);
    }

    /** Starts an episode; {@code monthsOverride} < 0 picks a random length. */
    public void start(ServerLevel level, Phase newPhase, long day, int monthsOverride, RandomSource r) {
        phase = newPhase;
        startDay = day;
        durationDays = monthsOverride > 0 ? monthsOverride * Config.MONTH_DAYS.get()
                : months(Config.ENSO_MIN_MONTHS.get(), Config.ENSO_MAX_MONTHS.get(), r);
        peak = 0.4f + r.nextFloat() * 0.6f;
        if (newPhase != Phase.NEUTRAL) {
            announce(level, Component.translatable("enso.naturaldisasters.started", Component.translatable(newPhase.key()),
                    durationDays / Config.MONTH_DAYS.get()), newPhase == Phase.EL_NINO ? ChatFormatting.GOLD : ChatFormatting.BLUE);
        }
    }

    private static int months(int min, int max, RandomSource r) {
        int lo = Math.min(min, max), hi = Math.max(min, max);
        return (lo + r.nextInt(hi - lo + 1)) * Config.MONTH_DAYS.get();
    }

    private static void announce(ServerLevel level, Component msg, ChatFormatting color) {
        for (ServerPlayer p : level.players()) p.sendSystemMessage(msg.copy().withStyle(color));
    }

    /** Current strength 0..1: ramps up during the first month and down during the last one. */
    public float strength(long day) {
        if (phase == Phase.NEUTRAL || !Config.ENSO.get() || durationDays <= 0) return 0f;
        float month = Config.MONTH_DAYS.get();
        float elapsed = day - startDay, remaining = startDay + durationDays - day;
        return peak * Mth.clamp(Math.min(elapsed / month, remaining / month), 0f, 1f);
    }

    // ------------------------------------------------------------------ effects (s = strength, south = hemisphere)

    public float temperatureOffset(float s) {
        return switch (phase) {
            case EL_NINO -> 1.5f * s;
            case LA_NINA -> -1.2f * s;
            default -> 0f;
        };
    }

    public float humidityOffset(float s, boolean south) {
        return switch (phase) {
            case EL_NINO -> (south ? 0.12f : 0.03f) * s;
            case LA_NINA -> (south ? -0.12f : -0.05f) * s;
            default -> 0f;
        };
    }

    public float supercellFactor(float s, boolean south) {
        return switch (phase) {
            case EL_NINO -> 1f + (south ? 0.5f : -0.3f) * s;
            case LA_NINA -> 1f + (south ? -0.3f : 0.5f) * s;
            default -> 1f;
        };
    }

    public float rainFactor(float s, boolean south) {
        return switch (phase) {
            case EL_NINO -> 1f + (south ? 0.5f : 0.2f) * s;
            case LA_NINA -> 1f + (south ? -0.4f : -0.1f) * s;
            default -> 1f;
        };
    }

    public float sandstormFactor(float s, boolean south) {
        return switch (phase) {
            case EL_NINO -> 1f - (south ? 0.3f : 0.2f) * s;
            case LA_NINA -> 1f + (south ? 0.6f : 0.5f) * s;
            default -> 1f;
        };
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("phase", phase.ordinal());
        t.putLong("start", startDay);
        t.putInt("duration", durationDays);
        t.putFloat("peak", peak);
        return t;
    }

    public void load(CompoundTag t) {
        if (!t.contains("phase")) return;
        phase = Phase.byId(t.getInt("phase"));
        startDay = t.getLong("start");
        durationDays = t.getInt("duration");
        peak = t.getFloat("peak");
    }
}
