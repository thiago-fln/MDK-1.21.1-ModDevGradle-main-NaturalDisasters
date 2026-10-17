package com.naturaldisasters.client.sound;

import com.naturaldisasters.entity.TornadoEntity;
import com.naturaldisasters.registry.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/** Low roar of a tornado, audible from far away and louder for stronger categories. */
public class TornadoSound extends LoopingSound {
    private final TornadoEntity tornado;

    public TornadoSound(TornadoEntity tornado) {
        super(ModRegistry.TORNADO_ROAR.get(), SoundSource.WEATHER);
        this.tornado = tornado;
        this.x = tornado.getX();
        this.y = tornado.getY();
        this.z = tornado.getZ();
        update();
    }

    @Override
    protected void update() {
        if (tornado.isRemoved()) {
            finish();
            return;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        x = tornado.getX();
        y = Math.min(player.getY() + 6, tornado.getY() + 10);
        z = tornado.getZ();
        double dx = player.getX() - x, dz = player.getZ() - z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        double audible = 140 + tornado.getCoreRadius() * 6;
        float near = (float) Mth.clamp(1 - dist / audible, 0, 1);
        volume = Math.max(0.001f, near * near * (0.5f + tornado.getIntensity() * 0.15f) * tornado.getScale());
        pitch = 0.5f + (5f - tornado.getIntensity()) * 0.05f;
    }
}
