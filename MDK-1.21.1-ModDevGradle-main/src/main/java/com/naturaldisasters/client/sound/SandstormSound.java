package com.naturaldisasters.client.sound;

import com.naturaldisasters.client.ClientWeatherState;
import com.naturaldisasters.registry.ModRegistry;
import net.minecraft.sounds.SoundSource;

/** Howling wind heard by the player while inside a sandstorm. */
public class SandstormSound extends LoopingSound {
    public SandstormSound() {
        super(ModRegistry.SANDSTORM_WIND.get(), SoundSource.WEATHER);
        this.relative = true;
        this.x = this.y = this.z = 0;
        update();
    }

    @Override
    protected void update() {
        float fog = ClientWeatherState.sandFog;
        if (fog <= 0.01f && age() > 20) {
            finish();
            return;
        }
        volume = Math.max(0.001f, fog * 0.9f);
        pitch = 0.75f + fog * 0.2f;
    }
}
