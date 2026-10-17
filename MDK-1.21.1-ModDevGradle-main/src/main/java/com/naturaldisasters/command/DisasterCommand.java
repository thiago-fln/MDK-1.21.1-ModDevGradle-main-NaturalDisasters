package com.naturaldisasters.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.naturaldisasters.disaster.FloodManager;
import com.naturaldisasters.entity.TornadoEntity;
import com.naturaldisasters.entity.TornadoVariant;
import com.naturaldisasters.Config;
import com.naturaldisasters.weather.Atmosphere;
import com.naturaldisasters.weather.Enso;
import com.naturaldisasters.weather.ForcedWeather;
import com.naturaldisasters.weather.DisasterData;
import com.naturaldisasters.weather.StormSystem;
import com.naturaldisasters.weather.WeatherEngine;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.List;

/** /disaster tornado|storm|earthquake|sandstorm|flood|clear|info */
public final class DisasterCommand {
    private static final List<String> VARIANTS = Arrays.stream(TornadoVariant.values()).map(TornadoVariant::id).toList();
    private static final List<String> STARTS = List.of("touchdown", "formed");

    private DisasterCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = dispatcher.register(Commands.literal("disaster").requires(s -> s.hasPermission(2))
                .then(Commands.literal("tornado")
                        .executes(c -> tornado(c, 3, TornadoVariant.CLASSIC, 48, false))
                        .then(Commands.argument("category", IntegerArgumentType.integer(1, 5))
                                .executes(c -> tornado(c, cat(c), TornadoVariant.CLASSIC, 48, false))
                                .then(Commands.argument("variant", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(VARIANTS, b))
                                        .executes(c -> tornado(c, cat(c), variant(c), 48, false))
                                        .then(Commands.argument("distance", IntegerArgumentType.integer(0, 512))
                                                .executes(c -> tornado(c, cat(c), variant(c), IntegerArgumentType.getInteger(c, "distance"), false))
                                                .then(Commands.argument("start", StringArgumentType.word())
                                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(STARTS, b))
                                                        .executes(c -> tornado(c, cat(c), variant(c), IntegerArgumentType.getInteger(c, "distance"),
                                                                isFormedStart(StringArgumentType.getString(c, "start")))))))))
                .then(Commands.literal("storm")
                        .executes(c -> storm(c, true))
                        .then(Commands.literal("supercell").executes(c -> storm(c, true)))
                        .then(Commands.literal("rain").executes(c -> storm(c, false))))
                .then(Commands.literal("earthquake")
                        .executes(c -> earthquake(c, 6f))
                        .then(Commands.argument("magnitude", FloatArgumentType.floatArg(1f, 10f))
                                .executes(c -> earthquake(c, FloatArgumentType.getFloat(c, "magnitude")))))
                .then(Commands.literal("sandstorm").executes(DisasterCommand::sandstorm))
                .then(Commands.literal("tsunami")
                        .executes(c -> tsunami(c, 12, 260))
                        .then(Commands.argument("height", IntegerArgumentType.integer(3, 40))
                                .executes(c -> tsunami(c, IntegerArgumentType.getInteger(c, "height"), 260))
                                .then(Commands.argument("distance", IntegerArgumentType.integer(64, 1000))
                                        .executes(c -> tsunami(c, IntegerArgumentType.getInteger(c, "height"),
                                                IntegerArgumentType.getInteger(c, "distance"))))))
                .then(Commands.literal("flood")
                        .executes(c -> flood(c, 6, 30))
                        .then(Commands.literal("stop").executes(DisasterCommand::floodStop))
                        .then(Commands.argument("height", IntegerArgumentType.integer(1, 60))
                                .executes(c -> flood(c, IntegerArgumentType.getInteger(c, "height"), 30))
                                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 300))
                                        .executes(c -> flood(c, IntegerArgumentType.getInteger(c, "height"),
                                                IntegerArgumentType.getInteger(c, "limit"))))))
                .then(Commands.literal("enso")
                        .executes(DisasterCommand::ensoInfo)
                        .then(Commands.argument("phase", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("el_nino", "la_nina", "neutral"), b))
                                .executes(c -> enso(c, StringArgumentType.getString(c, "phase"), -1))
                                .then(Commands.argument("months", IntegerArgumentType.integer(1, 60))
                                        .executes(c -> enso(c, StringArgumentType.getString(c, "phase"),
                                                IntegerArgumentType.getInteger(c, "months"))))))
                .then(Commands.literal("hemisphere")
                        .executes(DisasterCommand::hemisphereInfo)
                        .then(Commands.literal("north").executes(c -> hemisphere(c, false)))
                        .then(Commands.literal("south").executes(c -> hemisphere(c, true))))
                .then(Commands.literal("weather")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(ForcedWeather.values()).map(ForcedWeather::id).toList(), b))
                                .executes(c -> weather(c, StringArgumentType.getString(c, "type"), 10))
                                .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 1440))
                                        .executes(c -> weather(c, StringArgumentType.getString(c, "type"),
                                                IntegerArgumentType.getInteger(c, "minutes"))))))
                .then(Commands.literal("clear").executes(DisasterCommand::clear))
                .then(Commands.literal("info").executes(DisasterCommand::info)));
        // "/disasters ..." works too
        dispatcher.register(Commands.literal("disasters").requires(src -> src.hasPermission(2)).redirect(root));
    }

    private static int cat(CommandContext<CommandSourceStack> c) {
        return IntegerArgumentType.getInteger(c, "category");
    }

    private static TornadoVariant variant(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String name = StringArgumentType.getString(c, "variant");
        for (TornadoVariant v : TornadoVariant.values()) if (v.id().equalsIgnoreCase(name)) return v;
        throw new SimpleCommandExceptionType(Component.translatable("command.naturaldisasters.unknown_variant", name)).create();
    }

    private static boolean isFormedStart(String start) {
        return start.equalsIgnoreCase("formed") || start.equalsIgnoreCase("formado");
    }

    /**
     * @param distance blocks in front of the player where it spawns (it then drifts towards the player)
     * @param formed   true: appears at full size; false: touches down as a thin funnel and grows
     */
    /**
     * Forces the weather for a while: sun / cloudy clear away storm cells, rain / thunder put a storm cell over
     * every player (visible on the radar). AUTO hands control back to the weather engine.
     */
    private static int weather(CommandContext<CommandSourceStack> c, String name, int minutes) throws CommandSyntaxException {
        ForcedWeather type = null;
        for (ForcedWeather w : ForcedWeather.values()) if (w.id().equalsIgnoreCase(name)) type = w;
        if (type == null) {
            throw new SimpleCommandExceptionType(Component.translatable("command.naturaldisasters.unknown_weather", name)).create();
        }
        ServerLevel level = c.getSource().getLevel();
        DisasterData data = DisasterData.get(level);
        int ticks = minutes * 1200;
        data.forcedWeather = type;
        data.forcedUntil = level.getGameTime() + ticks;
        switch (type) {
            case SUN, CLOUDY -> data.storms.clear();
            case RAIN, THUNDER -> {
                boolean thunder = type == ForcedWeather.THUNDER;
                data.storms.removeIf(s -> !s.supercell);
                for (ServerPlayer p : level.players()) {
                    StormSystem s = WeatherEngine.spawnStorm(level, data, p.getX(), p.getZ(), false, thunder ? 0.5f : 0.28f);
                    s.energy = thunder ? 0.48f : 0.22f;
                    s.vx *= 0.3;
                    s.vz *= 0.3;
                    s.maxAge = ticks + 2400;
                    s.updateDerived();
                }
            }
            default -> {}
        }
        data.setDirty();
        ForcedWeather shown = type;
        c.getSource().sendSuccess(() -> shown == ForcedWeather.AUTO
                ? Component.translatable("command.naturaldisasters.weather_auto")
                : Component.translatable("command.naturaldisasters.weather", Component.translatable("weather.naturaldisasters." + shown.id()), minutes), true);
        return 1;
    }

    private static int tornado(CommandContext<CommandSourceStack> c, int category, TornadoVariant variant, int distance,
                               boolean formed) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = p.serverLevel();
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : flat.normalize();
        double x = p.getX() + flat.x * distance, z = p.getZ() + flat.z * distance;
        if (variant == TornadoVariant.WATERSPOUT) {
            BlockPos water = findWater(level, BlockPos.containing(x, p.getY(), z), 96);
            if (water == null) {
                c.getSource().sendFailure(Component.translatable("command.naturaldisasters.no_water"));
                return 0;
            }
            x = water.getX() + 0.5;
            z = water.getZ() + 0.5;
        }
        double dx = p.getX() - x, dz = p.getZ() - z;
        double heading = dx * dx + dz * dz < 1 ? Math.atan2(flat.z, flat.x) : Math.atan2(dz, dx); // drifts towards the player
        TornadoEntity t = TornadoEntity.spawn(level, variant, x, z, category + 0.5f, heading, 3000, -1);
        if (t == null) return 0;
        if (formed) t.skipFormation();
        int shown = t.getCategory();
        c.getSource().sendSuccess(() -> Component.translatable("command.naturaldisasters.tornado", "EF" + shown,
                Component.translatable(variant.entityType().getDescriptionId())), true);
        return 1;
    }

    /** Nearest water surface column (spiral search) for waterspouts. */
    private static BlockPos findWater(ServerLevel level, BlockPos origin, int radius) {
        for (int r = 0; r <= radius; r += 4) {
            for (int i = -r; i <= r; i += 4) {
                for (int[] d : new int[][]{{i, -r}, {i, r}, {-r, i}, {r, i}}) {
                    int x = origin.getX() + d[0], z = origin.getZ() + d[1];
                    if (!level.isLoaded(new BlockPos(x, 0, z))) continue;
                    BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
                    if (level.getFluidState(top).is(FluidTags.WATER)) return top;
                }
            }
        }
        return null;
    }

    private static int storm(CommandContext<CommandSourceStack> c, boolean supercell) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = p.serverLevel();
        DisasterData data = DisasterData.get(level);
        Vec3 pos = WeatherEngine.upwindSpawn(data, p.getX(), p.getZ(), level.random);
        double x = p.getX() + (pos.x - p.getX()) * 0.6, z = p.getZ() + (pos.z - p.getZ()) * 0.6;
        StormSystem s = WeatherEngine.spawnStorm(level, data, x, z, supercell, supercell ? 0.97f : 0.5f);
        s.energy = supercell ? 0.3f : 0.15f;
        s.updateDerived();
        c.getSource().sendSuccess(() -> Component.translatable(supercell ? "command.naturaldisasters.supercell" : "command.naturaldisasters.storm",
                (int) Math.sqrt(s.distanceSqr(p.getX(), p.getZ())),
                Component.translatable(WeatherEngine.compassKey(p.getX(), p.getZ(), s.x, s.z))), true);
        return 1;
    }

    private static int earthquake(CommandContext<CommandSourceStack> c, float magnitude) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        WeatherEngine.startEarthquake(p.serverLevel(), DisasterData.get(p.serverLevel()), p.blockPosition(), magnitude);
        c.getSource().sendSuccess(() -> Component.translatable("command.naturaldisasters.earthquake", String.format("%.1f", magnitude)), true);
        return 1;
    }

    private static int tsunami(CommandContext<CommandSourceStack> c, int height, int distance) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        var t = WeatherEngine.startTsunami(p.serverLevel(), DisasterData.get(p.serverLevel()), p, height, distance, 260);
        if (t == null) {
            c.getSource().sendFailure(Component.translatable("command.naturaldisasters.no_ocean", distance));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.naturaldisasters.tsunami", height, distance), true);
        return 1;
    }

    private static int sandstorm(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        WeatherEngine.startSandstorm(p.serverLevel(), DisasterData.get(p.serverLevel()), p.getX(), p.getZ());
        c.getSource().sendSuccess(() -> Component.translatable("command.naturaldisasters.sandstorm"), true);
        return 1;
    }

    /**
     * Starts a flood around the player that rises from the lowest ground of the area.
     *
     * @param height how many blocks the water rises (rounded down to a multiple of 3)
     * @param limit  how far it spreads from the player in blocks (rounded down to a multiple of 3)
     */
    private static int flood(CommandContext<CommandSourceStack> c, int height, int limit) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = p.serverLevel();
        DisasterData data = DisasterData.get(level);
        int h = FloodManager.roundToTiles(height), l = FloodManager.roundToTiles(limit);
        data.flood.startFlood(level, data.newId(), p.blockPosition(), h, l, 6000);
        c.getSource().sendSuccess(() -> Component.translatable("command.naturaldisasters.flood", h, l), true);
        return 1;
    }

    private static int floodStop(CommandContext<CommandSourceStack> c) {
        DisasterData data = DisasterData.get(c.getSource().getLevel());
        int n = data.flood.floods.size();
        data.flood.drainAll();
        c.getSource().sendSuccess(() -> Component.translatable("command.naturaldisasters.flood_stop", n), true);
        return 1;
    }

    private static int enso(CommandContext<CommandSourceStack> c, String name, int months) throws CommandSyntaxException {
        ServerLevel level = c.getSource().getLevel();
        DisasterData data = DisasterData.get(level);
        Enso.Phase phase = switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "el_nino", "elnino" -> Enso.Phase.EL_NINO;
            case "la_nina", "lanina" -> Enso.Phase.LA_NINA;
            case "neutral" -> Enso.Phase.NEUTRAL;
            default -> throw new SimpleCommandExceptionType(Component.translatable("command.naturaldisasters.unknown_enso", name)).create();
        };
        data.enso.start(level, phase, DisasterData.day(level), months, level.random);
        // a forced episode starts at full strength (skips the build-up month) so its effects show right away
        data.enso.startDay -= Config.MONTH_DAYS.get();
        data.enso.durationDays += Config.MONTH_DAYS.get();
        data.setDirty();
        return ensoInfo(c);
    }

    private static int ensoInfo(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        DisasterData data = DisasterData.get(level);
        long day = DisasterData.day(level);
        int monthsLeft = (int) Math.ceil((data.enso.startDay + data.enso.durationDays - day) / (double) Config.MONTH_DAYS.get());
        c.getSource().sendSuccess(() -> Component.translatable("command.naturaldisasters.enso_info",
                Component.translatable(data.enso.phase.key()), Math.round(data.enso.strength(day) * 100), Math.max(0, monthsLeft),
                Component.translatable(data.southern ? "hemisphere.naturaldisasters.south_short" : "hemisphere.naturaldisasters.north_short")), false);
        return 1;
    }

    private static int hemisphere(CommandContext<CommandSourceStack> c, boolean south) {
        DisasterData data = DisasterData.get(c.getSource().getLevel());
        data.southern = south;
        data.setDirty();
        return hemisphereInfo(c);
    }

    private static int hemisphereInfo(CommandContext<CommandSourceStack> c) {
        DisasterData data = DisasterData.get(c.getSource().getLevel());
        c.getSource().sendSuccess(() -> Component.translatable("command.naturaldisasters.hemisphere",
                Component.translatable(data.southern ? "hemisphere.naturaldisasters.south_short" : "hemisphere.naturaldisasters.north_short")), true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        DisasterData data = DisasterData.get(level);
        data.storms.clear();
        data.earthquakes.clear();
        data.sandstorms.clear();
        data.tsunamis.clear();
        data.flood.drainAll();
        int removed = 0;
        for (TornadoEntity t : level.getEntities(EntityTypeTest.forClass(TornadoEntity.class), e -> true)) {
            t.discard();
            removed++;
        }
        data.tornadoes.clear();
        data.setDirty();
        int count = removed;
        c.getSource().sendSuccess(() -> Component.translatable("command.naturaldisasters.clear", count), true);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        Vec3 pos = c.getSource().getPosition();
        Atmosphere.Reading r = Atmosphere.sample(level, pos.x, pos.z);
        Atmosphere.FrontAnalysis fa = Atmosphere.analyzeFronts(level, pos.x, pos.z);
        DisasterData data = DisasterData.get(level);
        c.getSource().sendSuccess(() -> Component.translatable("command.naturaldisasters.info",
                String.format("%.1f", r.temperature()), Math.round(r.humidity() * 100), Math.round(r.pressure()),
                String.format("%.2f", fa.instability()), data.storms.size(), data.tornadoes.size(),
                String.format("%.1f", data.flood.levelAt(pos.x, pos.z)), data.flood.floods.size()), false);
        return 1;
    }
}
