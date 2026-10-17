package com.naturaldisasters.client;

import com.mojang.blaze3d.shaders.FogShape;
import com.naturaldisasters.ClientConfig;
import com.naturaldisasters.NaturalDisasters;
import com.naturaldisasters.block.AbstractFloodBlock;
import com.naturaldisasters.client.particle.ParticleSprites;
import com.naturaldisasters.client.particle.WindParticle;
import com.naturaldisasters.client.render.StormCloudRenderer;
import com.naturaldisasters.client.sound.SandstormSound;
import com.naturaldisasters.client.sound.TsunamiSound;
import com.naturaldisasters.registry.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = NaturalDisasters.MODID, value = Dist.CLIENT)
public final class ClientGameEvents {
    private static SandstormSound sandstormSound;
    private static TsunamiSound tsunamiSound;

    private ClientGameEvents() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        MobEffectInstance sand = player.getEffect(ModRegistry.SAND_BLINDNESS);
        ClientWeatherState.tick(sand != null, sand == null ? 0 : sand.getAmplifier());

        if (ClientWeatherState.latest != null && !ClientWeatherState.latest.tsunamis().isEmpty()
                && (tsunamiSound == null || tsunamiSound.isStopped())) {
            tsunamiSound = new TsunamiSound();
            mc.getSoundManager().play(tsunamiSound);
        }

        float fog = ClientWeatherState.sandFog;
        if (fog > 0.02f && !mc.isPaused()) {
            if (sandstormSound == null || sandstormSound.isStopped()) {
                sandstormSound = new SandstormSound();
                mc.getSoundManager().play(sandstormSound);
            }
            spawnSandParticles(mc, player, fog);
        }
    }

    private static void spawnSandParticles(Minecraft mc, LocalPlayer player, float fog) {
        if (ParticleSprites.SAND == null) return;
        RandomSource r = player.getRandom();
        float wind = ClientWeatherState.windAngle();
        double wx = Math.cos(wind), wz = Math.sin(wind);
        int count = Math.round(40 * fog * ClientConfig.PARTICLE_DENSITY.get().floatValue());
        for (int i = 0; i < count; i++) {
            double x = player.getX() + (r.nextDouble() - 0.5) * 24 - wx * 6;
            double y = player.getY() + (r.nextDouble() - 0.3) * 6;
            double z = player.getZ() + (r.nextDouble() - 0.5) * 24 - wz * 6;
            double speed = 0.5 + r.nextDouble() * 0.6;
            mc.particleEngine.add(new WindParticle(mc.level, x, y, z, wx * speed, (r.nextDouble() - 0.5) * 0.05, wz * speed,
                    ParticleSprites.SAND, 0.86f, 0.74f, 0.5f, 0.18f));
        }
    }

    @SubscribeEvent
    static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!ClientConfig.CAMERA_SHAKE.get()) return;
        float partial = (float) event.getPartialTick();
        float shake = ClientWeatherState.shake(partial);
        if (shake <= 0.001f) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float t = mc.level.getGameTime() + partial;
        float amp = shake * ClientConfig.SHAKE_STRENGTH.get().floatValue() * 4f;
        event.setPitch(event.getPitch() + (Mth.sin(t * 1.9f) * 0.6f + Mth.sin(t * 4.3f + 1.3f) * 0.4f) * amp);
        event.setYaw(event.getYaw() + (Mth.cos(t * 2.3f) * 0.6f + Mth.sin(t * 5.1f + 0.7f) * 0.4f) * amp * 0.7f);
        event.setRoll(event.getRoll() + Mth.sin(t * 3.1f + 2.1f) * amp * 0.5f);
    }

    /** True while the camera is below the surface of a flood block. */
    private static boolean cameraInFlood(ViewportEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        Vec3 cam = event.getCamera().getPosition();
        BlockPos pos = BlockPos.containing(cam);
        BlockState state = mc.level.getBlockState(pos);
        return state.getBlock() instanceof AbstractFloodBlock && cam.y < AbstractFloodBlock.surface(pos, state);
    }

    @SubscribeEvent
    static void onRenderFog(ViewportEvent.RenderFog event) {
        if (cameraInFlood(event)) {
            event.setNearPlaneDistance(0f);
            event.setFarPlaneDistance(24f);
            event.setFogShape(FogShape.SPHERE);
            event.setCanceled(true);
            return;
        }
        float fog = ClientWeatherState.sandFog((float) event.getPartialTick());
        if (fog <= 0.001f) return;
        float target = ClientWeatherState.sandAmplifier >= 1 ? 3.5f : 7f;
        event.setNearPlaneDistance(Mth.lerp(fog, event.getNearPlaneDistance(), 0f));
        event.setFarPlaneDistance(Mth.lerp(fog, event.getFarPlaneDistance(), target));
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (cameraInFlood(event)) {
            event.setRed(0.22f);
            event.setGreen(0.36f);
            event.setBlue(0.5f);
            return;
        }
        float fog = ClientWeatherState.sandFog((float) event.getPartialTick());
        if (fog <= 0.001f) return;
        event.setRed(Mth.lerp(fog, event.getRed(), 0.78f));
        event.setGreen(Mth.lerp(fog, event.getGreen(), 0.66f));
        event.setBlue(Mth.lerp(fog, event.getBlue(), 0.45f));
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        StormCloudRenderer.render(event);
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientWeatherState.reset();
        sandstormSound = null;
        tsunamiSound = null;
    }
}
