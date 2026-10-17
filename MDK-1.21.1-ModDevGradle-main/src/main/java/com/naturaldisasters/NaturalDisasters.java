package com.naturaldisasters;

import com.mojang.logging.LogUtils;
import com.naturaldisasters.network.ModNetwork;
import com.naturaldisasters.registry.ModRegistry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(NaturalDisasters.MODID)
public class NaturalDisasters {
    public static final String MODID = "naturaldisasters";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NaturalDisasters(IEventBus modBus, ModContainer container) {
        ModRegistry.register(modBus);
        modBus.addListener(ModNetwork::register);
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
