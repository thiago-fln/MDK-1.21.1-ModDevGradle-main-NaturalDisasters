package com.naturaldisasters.weather;

import com.naturaldisasters.disaster.Earthquake;
import com.naturaldisasters.disaster.FloodManager;
import com.naturaldisasters.disaster.Sandstorm;
import com.naturaldisasters.disaster.Tsunami;
import com.naturaldisasters.entity.TornadoVariant;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Per-dimension persistent state of every active weather system and disaster. */
public class DisasterData extends SavedData {
    public static final String NAME = "naturaldisasters";

    public final List<StormSystem> storms = new ArrayList<>();
    public final List<Earthquake> earthquakes = new ArrayList<>();
    public final List<Sandstorm> sandstorms = new ArrayList<>();
    public final List<Tsunami> tsunamis = new ArrayList<>();
    public final FloodManager flood = new FloodManager();
    public float windAngle = (float) (Math.random() * Math.PI * 2);
    public float windSpeed = 0.12f;
    public int nextId = 1;
    /** true = southern hemisphere (clockwise rotation, opposite ENSO effects). Fixed when the world is created. */
    public boolean southern = HemisphereSetting.forNewWorld();
    public final Enso enso = new Enso();
    /** Weather forced by /disaster weather (see {@link ForcedWeather}) and the game time it expires at. */
    public ForcedWeather forcedWeather = ForcedWeather.AUTO;
    public long forcedUntil;

    /** Live tornado positions reported by the entities themselves (transient). */
    public final Map<UUID, TornadoInfo> tornadoes = new HashMap<>();

    public record TornadoInfo(UUID id, double x, double z, int category, TornadoVariant variant, float radius, long lastSeen) {}

    public static DisasterData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(DisasterData::new, DisasterData::load, null), NAME);
    }

    public static long day(ServerLevel level) {
        return level.getDayTime() / 24000L;
    }

    /** Current ENSO strength (0 when neutral). */
    public float ensoStrength(ServerLevel level) {
        return enso.strength(day(level));
    }

    /** The weather currently forced by command, or AUTO once it has expired. */
    public ForcedWeather forced(ServerLevel level) {
        if (forcedWeather != ForcedWeather.AUTO && level.getGameTime() >= forcedUntil) {
            forcedWeather = ForcedWeather.AUTO;
            setDirty();
        }
        return forcedWeather;
    }

    public int newId() {
        setDirty();
        return nextId++;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag s = new ListTag();
        for (StormSystem storm : storms) s.add(storm.save());
        tag.put("storms", s);
        ListTag q = new ListTag();
        for (Earthquake e : earthquakes) q.add(e.save());
        tag.put("earthquakes", q);
        ListTag ss = new ListTag();
        for (Sandstorm e : sandstorms) ss.add(e.save());
        tag.put("sandstorms", ss);
        ListTag ts = new ListTag();
        for (Tsunami t : tsunamis) ts.add(t.save());
        tag.put("tsunamis", ts);
        tag.put("flood", flood.save());
        tag.putFloat("windAngle", windAngle);
        tag.putFloat("windSpeed", windSpeed);
        tag.putInt("nextId", nextId);
        tag.putBoolean("southern", southern);
        tag.put("enso", enso.save());
        tag.putInt("forcedWeather", forcedWeather.ordinal());
        tag.putLong("forcedUntil", forcedUntil);
        return tag;
    }

    public static DisasterData load(CompoundTag tag, HolderLookup.Provider registries) {
        DisasterData d = new DisasterData();
        ListTag s = tag.getList("storms", Tag.TAG_COMPOUND);
        for (int i = 0; i < s.size(); i++) d.storms.add(StormSystem.load(s.getCompound(i)));
        ListTag q = tag.getList("earthquakes", Tag.TAG_COMPOUND);
        for (int i = 0; i < q.size(); i++) d.earthquakes.add(Earthquake.load(q.getCompound(i)));
        ListTag ss = tag.getList("sandstorms", Tag.TAG_COMPOUND);
        for (int i = 0; i < ss.size(); i++) d.sandstorms.add(Sandstorm.load(ss.getCompound(i)));
        ListTag ts = tag.getList("tsunamis", Tag.TAG_COMPOUND);
        for (int i = 0; i < ts.size(); i++) d.tsunamis.add(Tsunami.load(ts.getCompound(i)));
        d.flood.load(tag.getCompound("flood"));
        d.windAngle = tag.getFloat("windAngle");
        d.windSpeed = tag.contains("windSpeed") ? tag.getFloat("windSpeed") : 0.12f;
        d.nextId = Math.max(1, tag.getInt("nextId"));
        d.southern = tag.getBoolean("southern");
        d.enso.load(tag.getCompound("enso"));
        d.forcedWeather = ForcedWeather.byId(tag.getInt("forcedWeather"));
        d.forcedUntil = tag.getLong("forcedUntil");
        return d;
    }
}
