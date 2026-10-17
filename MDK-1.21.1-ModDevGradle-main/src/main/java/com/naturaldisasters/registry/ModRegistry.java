package com.naturaldisasters.registry;

import com.naturaldisasters.NaturalDisasters;
import com.naturaldisasters.block.FloodBlock;
import com.naturaldisasters.block.FloodFillerBlock;
import com.naturaldisasters.block.RadarBlock;
import com.naturaldisasters.block.SandLayerBlock;
import com.naturaldisasters.block.SirenBlock;
import com.naturaldisasters.block.SirenBlockEntity;
import com.naturaldisasters.effect.SandBlindnessEffect;
import com.naturaldisasters.entity.ElRenoEntity;
import com.naturaldisasters.entity.FirenadoEntity;
import com.naturaldisasters.entity.LandspoutEntity;
import com.naturaldisasters.entity.MultivortexEntity;
import com.naturaldisasters.entity.RopeTornadoEntity;
import com.naturaldisasters.entity.TornadoEntity;
import com.naturaldisasters.entity.WaterspoutEntity;
import com.naturaldisasters.entity.WedgeTornadoEntity;
import com.naturaldisasters.menu.RadarMenu;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.naturaldisasters.NaturalDisasters.MODID;

public final class ModRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MODID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // ------------------------------------------------------------------ blocks
    public static final DeferredBlock<RadarBlock> WEATHER_RADAR = BLOCKS.registerBlock("weather_radar", RadarBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 6f).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops().lightLevel(s -> 6));
    public static final DeferredBlock<SirenBlock> TORNADO_SIREN = BLOCKS.registerBlock("tornado_siren", SirenBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(3.5f, 6f).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .lightLevel(s -> s.getValue(SirenBlock.ACTIVE) ? 10 : 0));
    public static final DeferredBlock<SandLayerBlock> SAND_LAYER = BLOCKS.registerBlock("sand_layer", SandLayerBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.SAND).replaceable().forceSolidOff().strength(0.2f)
                    .sound(SoundType.SAND).pushReaction(PushReaction.DESTROY)
                    .isViewBlocking((state, level, pos) -> state.getValue(SandLayerBlock.LAYERS) >= 8));

    public static final DeferredBlock<FloodBlock> FLOOD_BLOCK = BLOCKS.registerBlock("flood_block", FloodBlock::new, floodProperties());
    public static final DeferredBlock<FloodFillerBlock> FLOOD_FILLER = BLOCKS.registerBlock("flood_filler", FloodFillerBlock::new, floodProperties());

    private static BlockBehaviour.Properties floodProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(-1f, 3600000f).noLootTable().noOcclusion()
                .noCollission().forceSolidOff().pushReaction(PushReaction.BLOCK).sound(SoundType.WET_GRASS)
                .isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false)
                .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false);
    }

    public static final DeferredItem<BlockItem> WEATHER_RADAR_ITEM = ITEMS.registerSimpleBlockItem(WEATHER_RADAR);
    public static final DeferredItem<BlockItem> TORNADO_SIREN_ITEM = ITEMS.registerSimpleBlockItem(TORNADO_SIREN);
    public static final DeferredItem<BlockItem> SAND_LAYER_ITEM = ITEMS.registerSimpleBlockItem(SAND_LAYER);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SirenBlockEntity>> SIREN_BE = BLOCK_ENTITIES.register("tornado_siren",
            () -> BlockEntityType.Builder.of(SirenBlockEntity::new, TORNADO_SIREN.get()).build(null));

    // ------------------------------------------------------------------ entities
    public static final DeferredHolder<EntityType<?>, EntityType<TornadoEntity>> TORNADO = ENTITIES.register("tornado",
            () -> EntityType.Builder.<TornadoEntity>of(TornadoEntity::new, MobCategory.MISC)
                    .sized(4f, 10f).clientTrackingRange(32).updateInterval(2).fireImmune().build(MODID + ":tornado"));
    public static final DeferredHolder<EntityType<?>, EntityType<MultivortexEntity>> MULTIVORTEX = ENTITIES.register("multivortex",
            () -> EntityType.Builder.<MultivortexEntity>of(MultivortexEntity::new, MobCategory.MISC)
                    .sized(4f, 10f).clientTrackingRange(32).updateInterval(2).fireImmune().build(MODID + ":multivortex"));
    public static final DeferredHolder<EntityType<?>, EntityType<FirenadoEntity>> FIRENADO = ENTITIES.register("firenado",
            () -> EntityType.Builder.<FirenadoEntity>of(FirenadoEntity::new, MobCategory.MISC)
                    .sized(4f, 10f).clientTrackingRange(32).updateInterval(2).fireImmune().build(MODID + ":firenado"));
    public static final DeferredHolder<EntityType<?>, EntityType<WaterspoutEntity>> WATERSPOUT = ENTITIES.register("waterspout",
            () -> EntityType.Builder.<WaterspoutEntity>of(WaterspoutEntity::new, MobCategory.MISC)
                    .sized(4f, 10f).clientTrackingRange(32).updateInterval(2).fireImmune().build(MODID + ":waterspout"));

    public static final DeferredHolder<EntityType<?>, EntityType<RopeTornadoEntity>> ROPE = tornadoType("rope_tornado", RopeTornadoEntity::new);
    public static final DeferredHolder<EntityType<?>, EntityType<WedgeTornadoEntity>> WEDGE = tornadoType("wedge_tornado", WedgeTornadoEntity::new);
    public static final DeferredHolder<EntityType<?>, EntityType<LandspoutEntity>> LANDSPOUT = tornadoType("landspout", LandspoutEntity::new);
    public static final DeferredHolder<EntityType<?>, EntityType<ElRenoEntity>> EL_RENO = tornadoType("el_reno", ElRenoEntity::new);

    private static <T extends TornadoEntity> DeferredHolder<EntityType<?>, EntityType<T>> tornadoType(String name, EntityType.EntityFactory<T> factory) {
        return ENTITIES.register(name, () -> EntityType.Builder.of(factory, MobCategory.MISC)
                .sized(4f, 10f).clientTrackingRange(32).updateInterval(2).fireImmune().build(MODID + ":" + name));
    }

    // ------------------------------------------------------------------ menus
    public static final DeferredHolder<MenuType<?>, MenuType<RadarMenu>> RADAR_MENU = MENUS.register("weather_radar",
            () -> IMenuTypeExtension.create((id, inv, buf) -> new RadarMenu(id, inv, buf.readBlockPos())));

    // ------------------------------------------------------------------ sounds
    public static final DeferredHolder<SoundEvent, SoundEvent> TORNADO_ROAR = sound("tornado_roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> TORNADO_SIREN_SOUND = sound("tornado_siren");
    public static final DeferredHolder<SoundEvent, SoundEvent> EARTHQUAKE_RUMBLE = sound("earthquake_rumble");
    public static final DeferredHolder<SoundEvent, SoundEvent> SANDSTORM_WIND = sound("sandstorm_wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> WEATHER_ALERT = sound("weather_alert");
    public static final DeferredHolder<SoundEvent, SoundEvent> TSUNAMI_ROAR = sound("tsunami_roar");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(NaturalDisasters.id(name)));
    }

    // ------------------------------------------------------------------ particles (sprites only; spawned client side)
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FUNNEL_DUST = PARTICLES.register("funnel_dust", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FUNNEL_DEBRIS = PARTICLES.register("funnel_debris", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FUNNEL_FLAME = PARTICLES.register("funnel_flame", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FUNNEL_SPRAY = PARTICLES.register("funnel_spray", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SAND_GUST = PARTICLES.register("sand_gust", () -> new SimpleParticleType(false));

    // ------------------------------------------------------------------ effects
    public static final DeferredHolder<MobEffect, MobEffect> SAND_BLINDNESS = EFFECTS.register("sand_blindness",
            () -> new SandBlindnessEffect().addAttributeModifier(Attributes.MOVEMENT_SPEED,
                    NaturalDisasters.id("sand_blindness_slowness"), -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    // ------------------------------------------------------------------ creative tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.naturaldisasters"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> WEATHER_RADAR_ITEM.get().getDefaultInstance())
            .displayItems((params, output) -> {
                output.accept(WEATHER_RADAR_ITEM.get());
                output.accept(TORNADO_SIREN_ITEM.get());
                output.accept(SAND_LAYER_ITEM.get());
            }).build());

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ENTITIES.register(bus);
        MENUS.register(bus);
        SOUNDS.register(bus);
        PARTICLES.register(bus);
        EFFECTS.register(bus);
        TABS.register(bus);
    }

    private ModRegistry() {}
}
