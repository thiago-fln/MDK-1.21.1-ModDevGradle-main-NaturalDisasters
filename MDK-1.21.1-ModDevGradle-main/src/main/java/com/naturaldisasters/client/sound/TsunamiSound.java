package com.naturaldisasters.client.sound;

import com.naturaldisasters.client.ClientWeatherState;
import com.naturaldisasters.network.WeatherSyncPayload;
import com.naturaldisasters.registry.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/** Deep roar of the nearest tsunami front, growing louder as it approaches. */
public class TsunamiSound extends LoopingSound {
    public TsunamiSound() {
        super(ModRegistry.TSUNAMI_ROAR.get(), SoundSource.WEATHER);
        update();
    }

    @Override
    protected void update() {
        WeatherSyncPayload w = ClientWeatherState.latest;
        var player = Minecraft.getInstance().player;
        if (w == null || w.tsunamis().isEmpty() || player == null) {
            finish();
            return;
        }
        double best = Double.MAX_VALUE;
        WeatherSyncPayload.Wave nearest = null;
        for (WeatherSyncPayload.Wave wave : w.tsunamis()) {
            // distance to the front line, not just its centre
            double rx = player.getX() - wave.x(), rz = player.getZ() - wave.z();
            double along = Math.abs(rx * wave.dirX() + rz * wave.dirZ());
            double across = Math.max(0, Math.abs(-rx * wave.dirZ() + rz * wave.dirX()) - wave.width() / 2);
            double d = Math.sqrt(along * along + across * across);
            if (d < best) {
                best = d;
                nearest = wave;
            }
        }
        if (nearest == null) return;
        x = player.getX() + (nearest.x() - player.getX()) * 0.2;
        y = player.getY();
        z = player.getZ() + (nearest.z() - player.getZ()) * 0.2;
        float near = (float) Mth.clamp(1 - best / 320, 0, 1);
        volume = Math.max(0.001f, near * near * (0.6f + nearest.height() * 0.03f));
        pitch = 0.5f;
    }
}
