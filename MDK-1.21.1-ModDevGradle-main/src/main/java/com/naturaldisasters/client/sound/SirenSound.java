package com.naturaldisasters.client.sound;

import com.naturaldisasters.block.SirenBlock;
import com.naturaldisasters.block.SirenBlockEntity;
import com.naturaldisasters.registry.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/** High intensity wailing siren: pitch sweeps up and down while the siren block is active. */
public class SirenSound extends LoopingSound {
    private static final double AUDIBLE_RANGE = 240;
    private final SirenBlockEntity siren;

    public SirenSound(SirenBlockEntity siren) {
        super(ModRegistry.TORNADO_SIREN_SOUND.get(), SoundSource.BLOCKS);
        this.siren = siren;
        this.x = siren.getBlockPos().getX() + 0.5;
        this.y = siren.getBlockPos().getY() + 0.5;
        this.z = siren.getBlockPos().getZ() + 0.5;
        update();
    }

    @Override
    protected void update() {
        BlockState state = siren.getBlockState();
        if (siren.isRemoved() || !state.hasProperty(SirenBlock.ACTIVE) || !state.getValue(SirenBlock.ACTIVE)) {
            finish();
            return;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        double dist = Math.sqrt(player.distanceToSqr(x, y, z));
        float near = (float) Mth.clamp(1 - dist / AUDIBLE_RANGE, 0, 1);
        volume = Math.max(0.001f, near * 1.6f);
        // classic wail: ~7 second rise and fall
        float phase = (age() % 140) / 140f;
        pitch = 0.55f + 1.1f * (0.5f - 0.5f * Mth.cos(phase * Mth.TWO_PI));
    }
}
