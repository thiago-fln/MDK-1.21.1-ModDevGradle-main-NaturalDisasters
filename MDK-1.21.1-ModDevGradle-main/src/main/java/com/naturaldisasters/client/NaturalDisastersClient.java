package com.naturaldisasters.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.naturaldisasters.ClientConfig;
import com.naturaldisasters.NaturalDisasters;
import com.naturaldisasters.client.particle.ParticleSprites;
import com.naturaldisasters.client.particle.WindParticle;
import com.naturaldisasters.client.render.TornadoRenderer;
import com.naturaldisasters.client.screen.RadarScreen;
import com.naturaldisasters.registry.ModRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import org.joml.Matrix4f;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import java.util.function.Consumer;

@Mod(value = NaturalDisasters.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = NaturalDisasters.MODID, value = Dist.CLIENT)
public class NaturalDisastersClient {
    public NaturalDisastersClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    /** Overworld sky effects whose vanilla clouds are skipped when the mod's own clouds replace them. */
    @SubscribeEvent
    static void onRegisterSkyEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(BuiltinDimensionTypes.OVERWORLD_EFFECTS, new DimensionSpecialEffects.OverworldEffects() {
            @Override
            public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack,
                                        double camX, double camY, double camZ, Matrix4f modelView, Matrix4f projection) {
                return ClientConfig.REPLACE_VANILLA_CLOUDS.get();
            }
        });
    }

    /** Flood water takes the biome's water colour, pulled towards grey-blue like a murky flood. */
    @SubscribeEvent
    static void onBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, getter, pos, tint) -> {
            int c = getter != null && pos != null ? BiomeColors.getAverageWaterColor(getter, pos) : 0x3F76E4;
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            r = (int) (r * 0.6f + 0x9F * 0.4f);
            g = (int) (g * 0.6f + 0xB2 * 0.4f);
            b = (int) (b * 0.6f + 0xBF * 0.4f);
            return (r << 16) | (g << 8) | b;
        }, ModRegistry.FLOOD_BLOCK.get());
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistry.TORNADO.get(), TornadoRenderer::new);
        event.registerEntityRenderer(ModRegistry.MULTIVORTEX.get(), TornadoRenderer::new);
        event.registerEntityRenderer(ModRegistry.FIRENADO.get(), TornadoRenderer::new);
        event.registerEntityRenderer(ModRegistry.WATERSPOUT.get(), TornadoRenderer::new);
        event.registerEntityRenderer(ModRegistry.ROPE.get(), TornadoRenderer::new);
        event.registerEntityRenderer(ModRegistry.WEDGE.get(), TornadoRenderer::new);
        event.registerEntityRenderer(ModRegistry.LANDSPOUT.get(), TornadoRenderer::new);
        event.registerEntityRenderer(ModRegistry.EL_RENO.get(), TornadoRenderer::new);
    }

    @SubscribeEvent
    static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModRegistry.RADAR_MENU.get(), RadarScreen::new);
    }

    @SubscribeEvent
    static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModRegistry.FUNNEL_DUST.get(), sprites -> provider(sprites, s -> ParticleSprites.DUST = s, 0.5f, 0.47f, 0.43f, 1.5f));
        event.registerSpriteSet(ModRegistry.FUNNEL_DEBRIS.get(), sprites -> provider(sprites, s -> ParticleSprites.DEBRIS = s, 0.33f, 0.27f, 0.2f, 0.25f));
        event.registerSpriteSet(ModRegistry.FUNNEL_FLAME.get(), sprites -> provider(sprites, s -> ParticleSprites.FLAME = s, 1f, 1f, 1f, 0.8f));
        event.registerSpriteSet(ModRegistry.FUNNEL_SPRAY.get(), sprites -> provider(sprites, s -> ParticleSprites.SPRAY = s, 0.85f, 0.92f, 1f, 0.5f));
        event.registerSpriteSet(ModRegistry.SAND_GUST.get(), sprites -> provider(sprites, s -> ParticleSprites.SAND = s, 0.86f, 0.74f, 0.5f, 0.18f));
    }

    /** Stores the sprite set for direct use and returns a provider so the particle also works with /particle. */
    private static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites, Consumer<SpriteSet> sink,
                                                                float r, float g, float b, float size) {
        sink.accept(sprites);
        return (type, level, x, y, z, dx, dy, dz) -> new WindParticle(level, x, y, z, dx, dy, dz, sprites, r, g, b, size);
    }
}
