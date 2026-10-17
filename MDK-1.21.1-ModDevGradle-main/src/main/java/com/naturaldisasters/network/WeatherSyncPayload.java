package com.naturaldisasters.network;

import com.naturaldisasters.NaturalDisasters;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** Periodic snapshot of the weather around a player: used for wall clouds, radar, fog and HUD. */
public record WeatherSyncPayload(float temperature, float humidity, float pressure,
                                 float windAngle, float windSpeed, float floodLevel,
                                 List<Storm> storms, List<Tornado> tornadoes,
                                 List<Zone> sandstorms, List<Zone> earthquakes,
                                 boolean southern, int ensoPhase, float ensoStrength,
                                 List<Wave> tsunamis, int forcedWeather) implements CustomPacketPayload {

    /** Tsunami front: centre, travel direction, length, height, speed (blocks/tick) and sea surface Y. */
    public record Wave(float x, float z, float dirX, float dirZ, float width, float height, float speed, int seaY) {}

    public record Storm(int id, float x, float z, float radius, float energy, int stage, boolean supercell, float vx, float vz) {}

    public record Tornado(float x, float z, float radius, int category, int variant) {}

    public record Zone(float x, float z, float radius, float strength) {}

    public static final Type<WeatherSyncPayload> TYPE = new Type<>(NaturalDisasters.id("weather_sync"));
    public static final StreamCodec<FriendlyByteBuf, WeatherSyncPayload> STREAM_CODEC =
            StreamCodec.ofMember(WeatherSyncPayload::write, WeatherSyncPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeFloat(temperature);
        buf.writeFloat(humidity);
        buf.writeFloat(pressure);
        buf.writeFloat(windAngle);
        buf.writeFloat(windSpeed);
        buf.writeFloat(floodLevel);
        buf.writeVarInt(storms.size());
        for (Storm s : storms) {
            buf.writeVarInt(s.id);
            buf.writeFloat(s.x);
            buf.writeFloat(s.z);
            buf.writeFloat(s.radius);
            buf.writeFloat(s.energy);
            buf.writeByte(s.stage);
            buf.writeBoolean(s.supercell);
            buf.writeFloat(s.vx);
            buf.writeFloat(s.vz);
        }
        buf.writeVarInt(tornadoes.size());
        for (Tornado t : tornadoes) {
            buf.writeFloat(t.x);
            buf.writeFloat(t.z);
            buf.writeFloat(t.radius);
            buf.writeByte(t.category);
            buf.writeByte(t.variant);
        }
        writeZones(buf, sandstorms);
        writeZones(buf, earthquakes);
        buf.writeBoolean(southern);
        buf.writeByte(ensoPhase);
        buf.writeFloat(ensoStrength);
        buf.writeVarInt(tsunamis.size());
        for (Wave w : tsunamis) {
            buf.writeFloat(w.x);
            buf.writeFloat(w.z);
            buf.writeFloat(w.dirX);
            buf.writeFloat(w.dirZ);
            buf.writeFloat(w.width);
            buf.writeFloat(w.height);
            buf.writeFloat(w.speed);
            buf.writeVarInt(w.seaY);
        }
        buf.writeByte(forcedWeather);
    }

    private static WeatherSyncPayload read(FriendlyByteBuf buf) {
        float temp = buf.readFloat(), hum = buf.readFloat(), pres = buf.readFloat();
        float wa = buf.readFloat(), ws = buf.readFloat(), flood = buf.readFloat();
        int n = buf.readVarInt();
        List<Storm> storms = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            storms.add(new Storm(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readByte(), buf.readBoolean(), buf.readFloat(), buf.readFloat()));
        }
        n = buf.readVarInt();
        List<Tornado> tornadoes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            tornadoes.add(new Tornado(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readByte(), buf.readByte()));
        }
        List<Zone> sand = readZones(buf), quakes = readZones(buf);
        boolean south = buf.readBoolean();
        int enso = buf.readByte();
        float ensoStrength = buf.readFloat();
        n = buf.readVarInt();
        List<Wave> waves = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            waves.add(new Wave(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readVarInt()));
        }
        return new WeatherSyncPayload(temp, hum, pres, wa, ws, flood, storms, tornadoes, sand, quakes,
                south, enso, ensoStrength, waves, buf.readByte());
    }

    private static void writeZones(FriendlyByteBuf buf, List<Zone> zones) {
        buf.writeVarInt(zones.size());
        for (Zone z : zones) {
            buf.writeFloat(z.x);
            buf.writeFloat(z.z);
            buf.writeFloat(z.radius);
            buf.writeFloat(z.strength);
        }
    }

    private static List<Zone> readZones(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Zone> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(new Zone(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));
        return list;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
