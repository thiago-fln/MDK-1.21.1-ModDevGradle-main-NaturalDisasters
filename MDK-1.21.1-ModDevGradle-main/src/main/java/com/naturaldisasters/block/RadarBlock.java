package com.naturaldisasters.block;

import com.naturaldisasters.menu.RadarMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Weather radar: opens a GUI with a reflectivity / thermal map of a 500 block radius. */
public class RadarBlock extends Block {
    public RadarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.openMenu(new SimpleMenuProvider((id, inv, p) -> new RadarMenu(id, inv, pos),
                    Component.translatable("container.naturaldisasters.weather_radar")), pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
