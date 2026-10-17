package com.naturaldisasters.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Looping, manually attenuated sound whose volume and pitch are recomputed every tick. */
public abstract class LoopingSound extends AbstractTickableSoundInstance {
    private int age;

    protected LoopingSound(SoundEvent event, SoundSource source) {
        super(event, source, SoundInstance.createUnseededRandom());
        this.looping = true;
        this.delay = 0;
        this.volume = 0.001f;
        this.attenuation = Attenuation.NONE;
    }

    public int age() {
        return age;
    }

    @Override
    public final void tick() {
        age++;
        update();
    }

    protected abstract void update();

    protected void finish() {
        stop();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public boolean canPlaySound() {
        return true;
    }
}
