package com.naturaldisasters.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Custom blindness used by sandstorms; the client draws a dense sand-coloured fog while it is active. */
public class SandBlindnessEffect extends MobEffect {
    public SandBlindnessEffect() {
        super(MobEffectCategory.HARMFUL, 0xD8B878);
    }
}
