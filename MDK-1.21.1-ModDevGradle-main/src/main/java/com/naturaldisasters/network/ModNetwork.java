package com.naturaldisasters.network;

import com.naturaldisasters.client.ClientHooks;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // Lambdas keep the client class from being resolved on dedicated servers.
        registrar.playToClient(ShakePayload.TYPE, ShakePayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientHooks.handleShake(payload)));
        registrar.playToClient(WeatherSyncPayload.TYPE, WeatherSyncPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientHooks.handleWeatherSync(payload)));
        registrar.playToClient(RadarGridPayload.TYPE, RadarGridPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientHooks.handleRadarGrid(payload)));
    }
}
