package com.naturaldisasters.network;

import com.naturaldisasters.NaturalDisasters;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Camera shake request: intensity 0..1, lasting {@code duration} ticks. */
public record ShakePayload(float intensity, int duration) implements CustomPacketPayload {
    public static final Type<ShakePayload> TYPE = new Type<>(NaturalDisasters.id("shake"));
    public static final StreamCodec<FriendlyByteBuf, ShakePayload> STREAM_CODEC = StreamCodec.ofMember(
            (p, buf) -> {
                buf.writeFloat(p.intensity);
                buf.writeVarInt(p.duration);
            },
            buf -> new ShakePayload(buf.readFloat(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
