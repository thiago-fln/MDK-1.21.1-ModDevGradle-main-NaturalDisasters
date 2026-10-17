package com.naturaldisasters.block;

import com.naturaldisasters.Config;
import com.naturaldisasters.client.ClientHooks;
import com.naturaldisasters.registry.ModRegistry;
import com.naturaldisasters.weather.WeatherEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SirenBlockEntity extends BlockEntity {
    /** Client-side looping sound handle (kept as Object so this class stays server-safe). */
    public Object clientSound;

    public SirenBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.SIREN_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SirenBlockEntity siren) {
        if (level.isClientSide) {
            ClientHooks.tickSiren(siren, state);
            return;
        }
        if (Math.floorMod(level.getGameTime() + pos.hashCode(), 20) != 0) return;
        boolean active = WeatherEngine.isTornadoThreat((ServerLevel) level, pos,
                Config.SIREN_RADIUS.get(), Config.SIREN_MIN_CATEGORY.get());
        if (active != state.getValue(SirenBlock.ACTIVE)) {
            level.setBlock(pos, state.setValue(SirenBlock.ACTIVE, active), 3);
        }
    }
}
