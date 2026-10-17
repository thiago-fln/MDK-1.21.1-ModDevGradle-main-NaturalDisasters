package com.naturaldisasters.network;

import com.naturaldisasters.NaturalDisasters;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Temperature / humidity grid around a radar (size x size samples spanning ±radius blocks). */
public record RadarGridPayload(int centerX, int centerZ, int radius, int size, float[] temperature, float[] humidity)
        implements CustomPacketPayload {
    public static final Type<RadarGridPayload> TYPE = new Type<>(NaturalDisasters.id("radar_grid"));
    public static final StreamCodec<FriendlyByteBuf, RadarGridPayload> STREAM_CODEC = StreamCodec.ofMember(
            (p, buf) -> {
                buf.writeVarInt(p.centerX);
                buf.writeVarInt(p.centerZ);
                buf.writeVarInt(p.radius);
                buf.writeVarInt(p.size);
                for (float f : p.temperature) buf.writeFloat(f);
                for (float f : p.humidity) buf.writeFloat(f);
            },
            buf -> {
                int cx = buf.readVarInt(), cz = buf.readVarInt(), r = buf.readVarInt(), size = buf.readVarInt();
                if (size < 0 || size > 64) throw new IllegalArgumentException("Bad radar grid size " + size);
                float[] t = new float[size * size], h = new float[size * size];
                for (int i = 0; i < t.length; i++) t[i] = buf.readFloat();
                for (int i = 0; i < h.length; i++) h[i] = buf.readFloat();
                return new RadarGridPayload(cx, cz, r, size, t, h);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
