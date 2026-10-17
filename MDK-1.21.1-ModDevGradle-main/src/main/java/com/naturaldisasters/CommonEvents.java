package com.naturaldisasters;

import com.naturaldisasters.command.DisasterCommand;
import com.naturaldisasters.weather.WeatherEngine;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = NaturalDisasters.MODID)
public final class CommonEvents {
    private CommonEvents() {}

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && WeatherEngine.isSupported(level)) {
            WeatherEngine.tick(level);
        }
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        DisasterCommand.register(event.getDispatcher());
    }
}
