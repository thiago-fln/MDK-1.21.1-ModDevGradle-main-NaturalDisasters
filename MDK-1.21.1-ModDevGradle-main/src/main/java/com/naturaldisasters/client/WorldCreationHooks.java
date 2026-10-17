package com.naturaldisasters.client;

import com.naturaldisasters.Config;
import com.naturaldisasters.NaturalDisasters;
import com.naturaldisasters.weather.HemisphereSetting;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Adds a "Hemisphere: North/South" toggle to the Create World screen (bottom-right corner). */
@EventBusSubscriber(modid = NaturalDisasters.MODID, value = Dist.CLIENT)
public final class WorldCreationHooks {
    private static Screen current;
    private static boolean south;

    private WorldCreationHooks() {}

    @SubscribeEvent
    static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof CreateWorldScreen screen)) return;
        if (current != screen) { // a fresh Create World screen starts from the configured default
            current = screen;
            south = Config.DEFAULT_HEMISPHERE.get() == Config.Hemisphere.SOUTH;
        }
        HemisphereSetting.setPending(south);
        // bottom-right corner, just above the Create/Cancel footer, so it never covers the tabs or tab content
        Component label = Component.translatable("hemisphere.naturaldisasters.label");
        int textWidth = 0;
        for (String key : new String[]{"hemisphere.naturaldisasters.north", "hemisphere.naturaldisasters.south"}) {
            textWidth = Math.max(textWidth, screen.getMinecraft().font.width(
                    Component.translatable("options.generic_value", label, Component.translatable(key))));
        }
        int width = Math.max(90, textWidth + 16);
        CycleButton<Boolean> button = CycleButton.<Boolean>builder(v -> Component.translatable(
                        v ? "hemisphere.naturaldisasters.south" : "hemisphere.naturaldisasters.north"))
                .withValues(false, true)
                .withInitialValue(south)
                .withTooltip(v -> Tooltip.create(Component.translatable("hemisphere.naturaldisasters.tooltip")))
                .create(screen.width - width - 6, screen.height - 33 - 24, width, 20, label,
                        (b, value) -> {
                            south = value;
                            HemisphereSetting.setPending(value);
                        });
        event.addListener(button);
    }
}
