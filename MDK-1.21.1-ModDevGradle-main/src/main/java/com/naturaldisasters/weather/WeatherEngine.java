package com.naturaldisasters.weather;

import com.naturaldisasters.Config;
import com.naturaldisasters.disaster.Earthquake;
import com.naturaldisasters.disaster.Sandstorm;
import com.naturaldisasters.disaster.Tsunami;
import com.naturaldisasters.entity.TornadoEntity;
import com.naturaldisasters.entity.TornadoVariant;
import com.naturaldisasters.network.WeatherSyncPayload;
import com.naturaldisasters.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Drives every weather system of a dimension: wind, storm formation from frontal boundaries, storm life cycle
 * (rain → thunderstorm → wall cloud → tornado warning → touchdown), lightning, vanilla rain, alerts, client
 * sync, and the earthquake / sandstorm / flood managers.
 */
public final class WeatherEngine {
    private static final double SYNC_RANGE = 1200;

    private WeatherEngine() {}

    public static boolean isSupported(ServerLevel level) {
        return level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling();
    }

    public static void tick(ServerLevel level) {
        DisasterData data = DisasterData.get(level);
        long time = level.getGameTime();

        data.earthquakes.removeIf(q -> !q.tick(level));
        data.tsunamis.removeIf(t -> !t.tick(level, data.flood, data::newId));
        data.sandstorms.removeIf(s -> !s.tick(level, data.windAngle, data.windSpeed));
        data.flood.tick(level, data.storms, data::newId);

        if (time % 20 == 0) {
            updateWind(data, level.random);
            if (Config.WEATHER_ENGINE.get()) {
                updateStorms(level, data);
                controlVanillaWeather(level, data);
                if (Config.STORM_ALERTS.get()) sendAlerts(level, data);
            }
            data.tornadoes.values().removeIf(t -> t.lastSeen() < time - 100);
            syncClients(level, data);
            data.setDirty();
        }
        if (time % 200 == 0) {
            data.enso.tick(level, DisasterData.day(level), level.random);
            naturalSpawns(level, data);
        }
    }

    // ------------------------------------------------------------------ wind

    private static void updateWind(DisasterData data, RandomSource r) {
        data.windAngle += (r.nextFloat() - 0.5f) * 0.03f;
        float target = 0.08f + (Mth.sin(data.windAngle * 3.1f) * 0.5f + 0.5f) * 0.12f;
        data.windSpeed += (target - data.windSpeed) * 0.02f;
    }

    // ------------------------------------------------------------------ storms

    public static StormSystem spawnStorm(ServerLevel level, DisasterData data, double x, double z, boolean supercell, float potential) {
        RandomSource r = level.random;
        StormSystem s = new StormSystem();
        s.id = data.newId();
        s.x = x;
        s.z = z;
        s.supercell = supercell;
        s.potential = Mth.clamp(potential, 0.2f, 1f);
        s.energy = 0.05f;
        s.maxAge = (supercell ? 14000 : 8000) + r.nextInt(6000);
        // supercells are "right movers": they deviate from the mean wind
        float angle = data.windAngle + (supercell ? 0.35f : 0f) + (r.nextFloat() - 0.5f) * 0.3f;
        double speed = data.windSpeed * (supercell ? 0.9 : 1.1);
        s.vx = Math.cos(angle) * speed;
        s.vz = Math.sin(angle) * speed;
        s.updateDerived();
        data.storms.add(s);
        return s;
    }

    private static void updateStorms(ServerLevel level, DisasterData data) {
        RandomSource r = level.random;
        double growthMul = Config.STORM_GROWTH.get();
        Iterator<StormSystem> it = data.storms.iterator();
        while (it.hasNext()) {
            StormSystem s = it.next();
            s.age += 20;

            TornadoEntity tornado = s.tornado != null && level.getEntity(s.tornado) instanceof TornadoEntity t && t.isAlive() ? t : null;
            if (tornado != null) {
                // the parent storm follows its tornado
                s.x = tornado.getX();
                s.z = tornado.getZ();
            } else {
                s.x += s.vx * 20;
                s.z += s.vz * 20;
                if (s.tornado != null && !data.tornadoes.containsKey(s.tornado)) {
                    // tornado roped out: the storm cycles back down
                    s.tornado = null;
                    s.energy = Math.min(s.energy, 0.6f);
                    s.warningTicks = 0;
                }
            }

            Atmosphere.Reading air = Atmosphere.sample(level, s.x, s.z);
            float cap = s.supercell ? s.potential : Math.min(s.potential, 0.5f);
            float growth = (float) (0.0035f * growthMul * (0.4f + air.humidity()));
            if (air.humidity() < 0.2f || air.temperature() < 4f) growth = -0.004f;
            if (s.energy > cap) growth = -0.002f;
            if (s.age > s.maxAge * 0.7f) growth = -0.006f;
            if (tornado != null) growth = Math.max(growth, 0f);
            s.energy = Mth.clamp(s.energy + growth, 0f, 1f);
            s.updateDerived();

            if ((s.energy <= 0.01f && s.age > 600) || s.age > s.maxAge) {
                it.remove();
                continue;
            }

            if (Config.STORM_LIGHTNING.get() && s.stage.ordinal() >= StormStage.THUNDERSTORM.ordinal()
                    && r.nextFloat() < 0.25f + s.energy * 0.6f) {
                strikeLightning(level, s, r);
            }

            // ordinary (non-supercell) thunderstorms occasionally drop a weak landspout
            if (!s.supercell && s.stage == StormStage.THUNDERSTORM && s.tornado == null && s.tornadoesSpawned == 0
                    && r.nextFloat() < 0.004f * growthMul) {
                spawnLandspout(level, data, s, r);
            }

            if (s.stage == StormStage.TORNADO_WARNING && s.tornado == null && s.tornadoesSpawned < 2) {
                s.warningTicks += 20;
                if (s.warningTicks >= 400) tryTouchdown(level, data, s, r);
            } else if (s.stage.ordinal() < StormStage.TORNADO_WARNING.ordinal()) {
                s.warningTicks = 0;
            }
        }
    }

    private static void tryTouchdown(ServerLevel level, DisasterData data, StormSystem s, RandomSource r) {
        BlockPos pos = BlockPos.containing(s.x, level.getSeaLevel(), s.z);
        if (!level.isPositionEntityTicking(pos)) return; // stays a radar-indicated threat until a player is near
        if (data.tornadoes.size() >= Config.MAX_TORNADOES.get()) return;
        float p = Mth.clamp((s.potential - 0.6f) / 0.4f, 0f, 1f);
        int category = Mth.clamp((int) Math.round(1 + p * 2.5 + r.nextGaussian() * 0.9), 1, Config.MAX_INTENSITY.get());
        Holder<Biome> biome = level.getBiome(pos);
        TornadoVariant variant;
        float roll = r.nextFloat();
        if (Atmosphere.isWater(biome)) variant = TornadoVariant.WATERSPOUT;
        else if (category >= 5 && s.potential > 0.92f && roll < 0.3f) variant = TornadoVariant.EL_RENO;
        else if (category >= 4 && roll < 0.35f) variant = TornadoVariant.WEDGE;
        else if (category >= 3 && roll < 0.3f) variant = TornadoVariant.MULTIVORTEX;
        else if (category <= 2 && roll < 0.25f) variant = TornadoVariant.ROPE;
        else variant = TornadoVariant.CLASSIC;
        if (variant == TornadoVariant.WATERSPOUT) category = Math.min(category, 3);
        double heading = Math.atan2(s.vz, s.vx);
        int life = (int) ((2400 + r.nextInt(4800)) * Config.TORNADO_LIFETIME.get());
        TornadoEntity t = TornadoEntity.spawn(level, variant, s.x, s.z, category + r.nextFloat() * 0.9f, heading, life, s.id);
        if (t != null) {
            s.tornado = t.getUUID();
            s.tornadoesSpawned++;
            reportTornado(level, t);
        }
    }

    private static void spawnLandspout(ServerLevel level, DisasterData data, StormSystem s, RandomSource r) {
        BlockPos pos = BlockPos.containing(s.x, level.getSeaLevel(), s.z);
        if (!level.isPositionEntityTicking(pos) || data.tornadoes.size() >= Config.MAX_TORNADOES.get()) return;
        if (Atmosphere.isWater(level.getBiome(pos))) return;
        TornadoEntity t = TornadoEntity.spawn(level, TornadoVariant.LANDSPOUT, s.x, s.z, 1f + r.nextFloat() * 0.9f,
                Math.atan2(s.vz, s.vx), (int) ((900 + r.nextInt(1200)) * Config.TORNADO_LIFETIME.get()), s.id);
        if (t != null) {
            s.tornado = t.getUUID();
            s.tornadoesSpawned++;
            reportTornado(level, t);
        }
    }

    private static void strikeLightning(ServerLevel level, StormSystem s, RandomSource r) {
        double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * s.radius * 0.7;
        int x = Mth.floor(s.x + Math.cos(a) * d), z = Mth.floor(s.z + Math.sin(a) * d);
        BlockPos col = new BlockPos(x, level.getSeaLevel(), z);
        if (!level.isPositionEntityTicking(col)) return;
        if (level.getNearestPlayer(x, col.getY(), z, 220, false) == null) return;
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, col);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        bolt.moveTo(Vec3.atBottomCenterOf(pos));
        level.addFreshEntity(bolt);
    }

    private static void controlVanillaWeather(ServerLevel level, DisasterData data) {
        ForcedWeather forced = data.forced(level);
        if (forced != ForcedWeather.AUTO) {
            boolean rain = forced == ForcedWeather.RAIN || forced == ForcedWeather.THUNDER;
            boolean thunder = forced == ForcedWeather.THUNDER;
            if (level.getLevelData().isRaining() != rain || level.getLevelData().isThundering() != thunder) {
                level.setWeatherParameters(rain ? 0 : 6000, rain ? 6000 : 0, rain, thunder);
            }
            return;
        }
        if (!Config.CONTROL_VANILLA_WEATHER.get()) return;
        boolean rain = false, thunder = false;
        for (ServerPlayer p : level.players()) {
            for (StormSystem s : data.storms) {
                double r = s.radius + 150;
                double d2 = s.distanceSqr(p.getX(), p.getZ());
                if (d2 < r * r) rain = true;
                double rt = s.radius + 100;
                if (s.stage.ordinal() >= StormStage.THUNDERSTORM.ordinal() && d2 < rt * rt) thunder = true;
            }
        }
        if (level.getLevelData().isRaining() != rain || level.getLevelData().isThundering() != thunder) {
            level.setWeatherParameters(rain ? 0 : 6000, rain ? 6000 : 0, rain, thunder);
        }
    }

    private static void sendAlerts(ServerLevel level, DisasterData data) {
        for (StormSystem s : data.storms) {
            if (s.stage.ordinal() < StormStage.WALL_CLOUD.ordinal()) continue;
            for (ServerPlayer p : level.players()) {
                double d = Math.sqrt(s.distanceSqr(p.getX(), p.getZ()));
                StormStage already = s.alerted.get(p.getUUID());
                int alreadyOrd = already == null ? -1 : already.ordinal();
                Component dir = Component.translatable(compassKey(p.getX(), p.getZ(), s.x, s.z));
                int dist = (int) d;
                if (s.stage == StormStage.TORNADO_WARNING && alreadyOrd < StormStage.TORNADO_WARNING.ordinal() && d < 600) {
                    s.alerted.put(p.getUUID(), StormStage.TORNADO_WARNING);
                    p.sendSystemMessage(Component.translatable("alert.naturaldisasters.tornado_warning", dist, dir)
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                    p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
                    p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("alert.naturaldisasters.tornado_warning.title")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
                    p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("alert.naturaldisasters.tornado_warning.subtitle", dist, dir)
                            .withStyle(ChatFormatting.GOLD)));
                    p.playNotifySound(ModRegistry.WEATHER_ALERT.get(), SoundSource.MASTER, 1f, 1f);
                } else if (s.stage == StormStage.WALL_CLOUD && alreadyOrd < StormStage.WALL_CLOUD.ordinal() && d < 450) {
                    s.alerted.put(p.getUUID(), StormStage.WALL_CLOUD);
                    p.sendSystemMessage(Component.translatable("alert.naturaldisasters.wall_cloud", dist, dir)
                            .withStyle(ChatFormatting.YELLOW));
                    p.playNotifySound(ModRegistry.WEATHER_ALERT.get(), SoundSource.MASTER, 0.6f, 1.2f);
                }
            }
        }
    }

    public static String compassKey(double fromX, double fromZ, double toX, double toZ) {
        double bearing = Math.toDegrees(Math.atan2(toX - fromX, -(toZ - fromZ)));
        int idx = Math.floorMod((int) Math.round(bearing / 45.0), 8);
        return "direction.naturaldisasters." + new String[]{"n", "ne", "e", "se", "s", "sw", "w", "nw"}[idx];
    }

    // ------------------------------------------------------------------ natural formation

    private static void naturalSpawns(ServerLevel level, DisasterData data) {
        RandomSource r = level.random;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            double px = p.getX(), pz = p.getZ();

            if (Config.WEATHER_ENGINE.get() && data.forced(level) == ForcedWeather.AUTO) {
                int nearby = 0;
                for (StormSystem s : data.storms) if (s.distanceSqr(px, pz) < 900 * 900) nearby++;
                if (nearby < Config.MAX_STORMS.get()) {
                    Atmosphere.FrontAnalysis fa = Atmosphere.analyzeFronts(level, px, pz);
                    double freq = Config.STORM_FREQUENCY.get();
                    float enso = data.ensoStrength(level);
                    double superChance = freq * fa.instability() * 0.06 * data.enso.supercellFactor(enso, data.southern);
                    double rainChance = freq * fa.avgHumidity() * 0.025 * data.enso.rainFactor(enso, data.southern);
                    double roll = r.nextDouble();
                    if (roll < superChance) {
                        Vec3 pos = upwindSpawn(data, px, pz, r);
                        float potential = 0.7f + fa.instability() * 0.3f + r.nextFloat() * 0.1f;
                        spawnStorm(level, data, pos.x, pos.z, true, potential);
                    } else if (roll < superChance + rainChance) {
                        Vec3 pos = upwindSpawn(data, px, pz, r);
                        spawnStorm(level, data, pos.x, pos.z, false, 0.3f + fa.avgHumidity() * 0.25f);
                    }
                }
            }

            if (Config.EARTHQUAKES.get() && data.earthquakes.isEmpty() && r.nextDouble() < Config.EARTHQUAKE_CHANCE.get()) {
                BlockPos c = p.blockPosition().offset(r.nextInt(129) - 64, 0, r.nextInt(129) - 64);
                Earthquake quake = startEarthquake(level, data, c, 3f + r.nextFloat() * 5.5f);
                // strong quakes near the sea can set off a tsunami
                if (Config.TSUNAMIS.get() && quake.magnitude >= 7f) {
                    startTsunami(level, data, p, (quake.magnitude - 6f) * 5f + 4f, 320, 260);
                }
            }

            if (Config.SANDSTORMS.get() && Atmosphere.isDesertLike(level.getBiome(p.blockPosition()))
                    && r.nextDouble() < Config.SANDSTORM_CHANCE.get() * data.enso.sandstormFactor(data.ensoStrength(level), data.southern)) {
                boolean near = false;
                for (Sandstorm s : data.sandstorms) {
                    double dx = s.x - px, dz = s.z - pz;
                    if (dx * dx + dz * dz < 400 * 400) near = true;
                }
                if (!near) startSandstorm(level, data, px - Math.cos(data.windAngle) * 80, pz - Math.sin(data.windAngle) * 80);
            }
        }
    }

    /** A point 250-450 blocks upwind of the player so the storm drifts towards them. */
    public static Vec3 upwindSpawn(DisasterData data, double px, double pz, RandomSource r) {
        double dist = 250 + r.nextDouble() * 200;
        double side = (r.nextDouble() - 0.5) * 300;
        double ux = -Math.cos(data.windAngle), uz = -Math.sin(data.windAngle);
        return new Vec3(px + ux * dist - uz * side, 0, pz + uz * dist + ux * side);
    }

    public static Earthquake startEarthquake(ServerLevel level, DisasterData data, BlockPos center, float magnitude) {
        Earthquake q = Earthquake.create(data.newId(), center, magnitude, level.random);
        data.earthquakes.add(q);
        return q;
    }

    /**
     * Starts a tsunami in the nearest ocean around a player, {@code distance} blocks away, heading for the player.
     * @return the tsunami, or null if there is no ocean nearby
     */
    public static Tsunami startTsunami(ServerLevel level, DisasterData data, ServerPlayer player, float height,
                                       int distance, float width) {
        double bestAngle = Double.NaN;
        for (int i = 0; i < 48; i++) {
            double a = i / 48.0 * Math.PI * 2;
            boolean open = true;
            for (double f : new double[]{1.0, 0.85, 1.15}) {
                BlockPos pos = BlockPos.containing(player.getX() + Math.cos(a) * distance * f, level.getSeaLevel(),
                        player.getZ() + Math.sin(a) * distance * f);
                if (!level.getBiome(pos).is(net.minecraft.tags.BiomeTags.IS_OCEAN)) open = false;
            }
            if (open) {
                bestAngle = a;
                break;
            }
        }
        if (Double.isNaN(bestAngle)) return null;
        double ox = player.getX() + Math.cos(bestAngle) * distance, oz = player.getZ() + Math.sin(bestAngle) * distance;
        Tsunami t = Tsunami.create(data.newId(), ox, oz, -Math.cos(bestAngle), -Math.sin(bestAngle), width, height,
                level.getSeaLevel() - 1);
        data.tsunamis.add(t);
        for (ServerPlayer p : level.players()) {
            double d = Math.sqrt(p.distanceToSqr(ox, p.getY(), oz));
            if (d > distance + 400) continue;
            Component dir = Component.translatable(compassKey(p.getX(), p.getZ(), ox, oz));
            int h = Math.round(height);
            p.sendSystemMessage(Component.translatable("alert.naturaldisasters.tsunami", h, dir).withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD));
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("alert.naturaldisasters.tsunami.title")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("alert.naturaldisasters.tsunami.subtitle", h, dir)
                    .withStyle(ChatFormatting.WHITE)));
            p.playNotifySound(ModRegistry.WEATHER_ALERT.get(), SoundSource.MASTER, 1f, 0.7f);
        }
        return t;
    }

    public static Sandstorm startSandstorm(ServerLevel level, DisasterData data, double x, double z) {
        Sandstorm s = Sandstorm.create(data.newId(), x, z, level.random);
        data.sandstorms.add(s);
        return s;
    }

    // ------------------------------------------------------------------ tornado registry

    public static void reportTornado(ServerLevel level, TornadoEntity t) {
        DisasterData data = DisasterData.get(level);
        data.tornadoes.put(t.getUUID(), new DisasterData.TornadoInfo(t.getUUID(), t.getX(), t.getZ(),
                t.getCategory(), t.getVariant(), t.getCoreRadius(), level.getGameTime()));
    }

    public static void removeTornado(ServerLevel level, UUID id) {
        DisasterData.get(level).tornadoes.remove(id);
    }

    public static void onTornadoReplaced(ServerLevel level, TornadoEntity old, TornadoEntity replacement) {
        DisasterData data = DisasterData.get(level);
        data.tornadoes.remove(old.getUUID());
        for (StormSystem s : data.storms) {
            if (old.getUUID().equals(s.tornado)) s.tornado = replacement.getUUID();
        }
        reportTornado(level, replacement);
    }

    public static boolean isTornadoThreat(ServerLevel level, BlockPos pos, int radius, int minCategory) {
        DisasterData data = DisasterData.get(level);
        double r2 = (double) radius * radius;
        for (DisasterData.TornadoInfo t : data.tornadoes.values()) {
            if (t.category() < minCategory) continue;
            double dx = t.x() - (pos.getX() + 0.5), dz = t.z() - (pos.getZ() + 0.5);
            if (dx * dx + dz * dz <= r2) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ client sync

    private static void syncClients(ServerLevel level, DisasterData data) {
        double r2 = SYNC_RANGE * SYNC_RANGE;
        for (ServerPlayer p : level.players()) {
            double px = p.getX(), pz = p.getZ();
            List<WeatherSyncPayload.Storm> storms = new ArrayList<>();
            for (StormSystem s : data.storms) {
                if (s.distanceSqr(px, pz) > r2) continue;
                storms.add(new WeatherSyncPayload.Storm(s.id, (float) s.x, (float) s.z, s.radius, s.energy,
                        s.stage.ordinal(), s.supercell, (float) s.vx, (float) s.vz));
            }
            List<WeatherSyncPayload.Tornado> tornadoes = new ArrayList<>();
            for (DisasterData.TornadoInfo t : data.tornadoes.values()) {
                double dx = t.x() - px, dz = t.z() - pz;
                if (dx * dx + dz * dz > r2) continue;
                tornadoes.add(new WeatherSyncPayload.Tornado((float) t.x(), (float) t.z(), t.radius(), t.category(), t.variant().ordinal()));
            }
            List<WeatherSyncPayload.Zone> sand = new ArrayList<>();
            for (Sandstorm s : data.sandstorms) {
                sand.add(new WeatherSyncPayload.Zone((float) s.x, (float) s.z, s.radius, s.intensity()));
            }
            List<WeatherSyncPayload.Wave> waves = new ArrayList<>();
            for (Tsunami t : data.tsunamis) {
                waves.add(new WeatherSyncPayload.Wave((float) t.frontX(), (float) t.frontZ(), (float) t.dirX, (float) t.dirZ,
                        t.width, t.height, (float) t.speed, t.seaY));
            }
            List<WeatherSyncPayload.Zone> quakes = new ArrayList<>();
            for (Earthquake q : data.earthquakes) {
                quakes.add(new WeatherSyncPayload.Zone(q.center.getX(), q.center.getZ(), Config.SHAKE_RADIUS.get(), q.magnitude));
            }
            Atmosphere.Reading air = Atmosphere.sample(level, px, pz);
            float pressure = air.pressure();
            for (StormSystem s : data.storms) {
                double d = Math.sqrt(s.distanceSqr(px, pz));
                if (d < s.radius * 2) pressure -= (float) (s.energy * 25 * (1 - d / (s.radius * 2)));
            }
            PacketDistributor.sendToPlayer(p, new WeatherSyncPayload(air.temperature(), air.humidity(), pressure,
                    data.windAngle, data.windSpeed, data.flood.levelAt(px, pz), storms, tornadoes, sand, quakes,
                    data.southern, data.enso.phase.ordinal(), data.ensoStrength(level), waves,
                    data.forced(level).ordinal()));
        }
    }
}
