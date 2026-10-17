package com.naturaldisasters.client;

import com.naturaldisasters.ClientConfig;
import com.naturaldisasters.block.SirenBlock;
import com.naturaldisasters.block.SirenBlockEntity;
import com.naturaldisasters.client.particle.BlockDebrisParticle;
import com.naturaldisasters.client.particle.InflowParticle;
import com.naturaldisasters.client.particle.ParticleSprites;
import com.naturaldisasters.client.particle.VortexParticle;
import com.naturaldisasters.client.sound.SirenSound;
import com.naturaldisasters.client.sound.TornadoSound;
import com.naturaldisasters.entity.MultivortexEntity;
import com.naturaldisasters.entity.TornadoEntity;
import com.naturaldisasters.entity.WaterspoutEntity;
import com.naturaldisasters.network.RadarGridPayload;
import com.naturaldisasters.network.ShakePayload;
import com.naturaldisasters.network.WeatherSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.SpriteSet;
import com.naturaldisasters.client.sound.LoopingSound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Entry points called from common code; only ever invoked on the logical client. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void handleShake(ShakePayload payload) {
        ClientWeatherState.addShake(payload.intensity(), payload.duration());
    }

    public static void handleWeatherSync(WeatherSyncPayload payload) {
        ClientWeatherState.latest = payload;
        Minecraft mc = Minecraft.getInstance();
        ClientWeatherState.lastSyncTime = mc.level == null ? 0 : mc.level.getGameTime();
    }

    public static void handleRadarGrid(RadarGridPayload payload) {
        ClientWeatherState.grid = payload;
    }

    // ------------------------------------------------------------------ sounds

    /** True when a looping sound handle is missing, stopped, or silently failed to start (retried after 2 s). */
    public static boolean needsRestart(Object handle) {
        if (!(handle instanceof LoopingSound sound) || sound.isStopped()) return true;
        SoundManager sm = Minecraft.getInstance().getSoundManager();
        return !sm.isActive(sound) && sound.age() > 40;
    }

    public static void tickSiren(SirenBlockEntity siren, BlockState state) {
        if (!state.getValue(SirenBlock.ACTIVE)) return;
        if (needsRestart(siren.clientSound)) {
            SirenSound sound = new SirenSound(siren);
            siren.clientSound = sound;
            Minecraft.getInstance().getSoundManager().play(sound);
        }
    }

    // ------------------------------------------------------------------ tornado visuals

    public static void tickTornado(TornadoEntity tornado) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(tornado.level() instanceof ClientLevel level)) return;
        if (tornado.getScale() > 0.05f && needsRestart(tornado.clientSound)) {
            TornadoSound sound = new TornadoSound(tornado);
            tornado.clientSound = sound;
            mc.getSoundManager().play(sound);
        }

        double dx = mc.player.getX() - tornado.getX(), dz = mc.player.getZ() - tornado.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 400 || ParticleSprites.DUST == null) return;
        float density = ClientConfig.PARTICLE_DENSITY.get().floatValue();
        float distFactor = dist < 96 ? 1f : (float) (96 / dist);
        int count = Math.round((6 + tornado.getIntensity() * 5) * tornado.getScale() * density * distFactor);
        RandomSource r = level.random;
        ParticleEngine engine = mc.particleEngine;

        for (int i = 0; i < count; i++) {
            int sub = -1;
            if (tornado instanceof MultivortexEntity m && r.nextFloat() < 0.5f) sub = r.nextInt(m.getSubVortexCount());
            double t = sub >= 0 ? r.nextDouble() * 0.5 : Math.pow(r.nextDouble(), 1.4);
            float radiusFrac = 0.75f + r.nextFloat() * 0.5f;
            float angle = r.nextFloat() * Mth.TWO_PI;
            switch (tornado.getVariant()) {
                case FIRENADO -> {
                    if (r.nextFloat() < 0.55f) {
                        engine.add(new VortexParticle(level, tornado, VortexParticle.Kind.FLAME, ParticleSprites.FLAME, t * 0.7, radiusFrac * 0.8f, angle, sub));
                    } else {
                        engine.add(new VortexParticle(level, tornado, VortexParticle.Kind.SMOKE, ParticleSprites.DUST, t, radiusFrac, angle, sub));
                    }
                }
                case WATERSPOUT -> {
                    if (r.nextFloat() < 0.4f) {
                        engine.add(new VortexParticle(level, tornado, VortexParticle.Kind.SPRAY, ParticleSprites.SPRAY, r.nextDouble() * 0.12, 1.2f + r.nextFloat(), angle, sub));
                    } else {
                        engine.add(new VortexParticle(level, tornado, VortexParticle.Kind.MIST, ParticleSprites.DUST, t, radiusFrac, angle, sub));
                    }
                }
                default -> engine.add(new VortexParticle(level, tornado, VortexParticle.Kind.DUST, ParticleSprites.DUST, t, radiusFrac, angle, sub));
            }
        }

        if (!(tornado instanceof WaterspoutEntity)) {
            spawnBlockDebris(mc, level, tornado, Math.round(count * 0.5f), r);
            kickUpGround(level, tornado, Math.round(count * 0.5f), r);
            // inflow: dust dragged along the ground towards the funnel from well outside it
            int inflow = Math.round(count * 0.35f);
            float pull = tornado.getPullRadius();
            for (int i = 0; i < inflow; i++) {
                engine.add(new InflowParticle(level, tornado, ParticleSprites.DUST, pull * (0.75f + r.nextFloat() * 0.35f),
                        r.nextFloat() * Mth.TWO_PI, 0.56f, 0.5f, 0.42f));
            }
        }

        // ground debris ring swirling at the base
        if (!(tornado instanceof WaterspoutEntity)) {
            SpriteSet debris = ParticleSprites.DEBRIS;
            int ring = Math.round(count * 0.6f);
            for (int i = 0; i < ring; i++) {
                engine.add(new VortexParticle(level, tornado, VortexParticle.Kind.DEBRIS, debris,
                        r.nextDouble() * 0.08, 1.1f + r.nextFloat() * 1.2f, r.nextFloat() * Mth.TWO_PI, -1));
            }
        }
    }

    /** Pieces of whatever the tornado is passing over (grass, dirt, sand, planks, leaves...), with biome tint. */
    private static void spawnBlockDebris(Minecraft mc, ClientLevel level, TornadoEntity tornado, int count, RandomSource r) {
        float radius = tornado.getCoreRadius() * 1.2f;
        for (int i = 0; i < count; i++) {
            double a = r.nextDouble() * Mth.TWO_PI, d = Math.sqrt(r.nextDouble()) * radius;
            int x = Mth.floor(tornado.getX() + Math.cos(a) * d), z = Mth.floor(tornado.getZ() + Math.sin(a) * d);
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            TextureAtlasSprite sprite = mc.getBlockRenderer().getBlockModelShaper().getParticleIcon(state);
            int tint = mc.getBlockColors().getColor(state, level, pos, 0);
            float cr = 1f, cg = 1f, cb = 1f;
            if (tint != -1) {
                cr = ((tint >> 16) & 0xFF) / 255f;
                cg = ((tint >> 8) & 0xFF) / 255f;
                cb = (tint & 0xFF) / 255f;
            }
            float shade = 0.75f + r.nextFloat() * 0.25f;
            mc.particleEngine.add(new BlockDebrisParticle(level, tornado, sprite, cr * shade, cg * shade, cb * shade,
                    r.nextDouble() * 0.08, 0.7f + r.nextFloat() * 0.8f, (float) a));
        }
    }

    /** Bursts of the ground block (dirt, grass, sand...) thrown up and around just outside the core. */
    private static void kickUpGround(ClientLevel level, TornadoEntity tornado, int count, RandomSource r) {
        float core = tornado.getCoreRadius();
        float dir = tornado.spinDirection();
        for (int i = 0; i < count; i++) {
            double a = r.nextDouble() * Mth.TWO_PI, d = core * (0.8 + r.nextDouble() * 1.2);
            double x = tornado.getX() + Math.cos(a) * d, z = tornado.getZ() + Math.sin(a) * d;
            BlockPos pos = new BlockPos(Mth.floor(x), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z)) - 1, Mth.floor(z));
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            double speed = 0.3 + r.nextDouble() * 0.4;
            double tx = -Math.sin(a) * dir * speed, tz = Math.cos(a) * dir * speed; // swept along with the rotation
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), x, pos.getY() + 1.05, z,
                    tx, 0.25 + r.nextDouble() * 0.45, tz);
        }
    }
}
