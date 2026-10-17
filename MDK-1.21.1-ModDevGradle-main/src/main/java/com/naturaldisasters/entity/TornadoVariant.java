package com.naturaldisasters.entity;

import com.naturaldisasters.registry.ModRegistry;
import net.minecraft.world.entity.EntityType;

import java.util.Locale;

public enum TornadoVariant {
    CLASSIC("TOR"),
    MULTIVORTEX("MV"),
    FIRENADO("FN"),
    WATERSPOUT("WS"),
    ROPE("RP"),
    WEDGE("WDG"),
    LANDSPOUT("LS"),
    EL_RENO("ELR");

    public final String shortCode;

    TornadoVariant(String shortCode) {
        this.shortCode = shortCode;
    }

    public EntityType<? extends TornadoEntity> entityType() {
        return switch (this) {
            case CLASSIC -> ModRegistry.TORNADO.get();
            case MULTIVORTEX -> ModRegistry.MULTIVORTEX.get();
            case FIRENADO -> ModRegistry.FIRENADO.get();
            case WATERSPOUT -> ModRegistry.WATERSPOUT.get();
            case ROPE -> ModRegistry.ROPE.get();
            case WEDGE -> ModRegistry.WEDGE.get();
            case LANDSPOUT -> ModRegistry.LANDSPOUT.get();
            case EL_RENO -> ModRegistry.EL_RENO.get();
        };
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static TornadoVariant byId(int id) {
        TornadoVariant[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, id))];
    }

    public static TornadoVariant byName(String name) {
        for (TornadoVariant v : values()) if (v.id().equalsIgnoreCase(name)) return v;
        return CLASSIC;
    }
}
