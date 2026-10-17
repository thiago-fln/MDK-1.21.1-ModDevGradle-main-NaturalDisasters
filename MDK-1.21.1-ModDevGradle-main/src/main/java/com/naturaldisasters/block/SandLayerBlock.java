package com.naturaldisasters.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Layered sand deposited by sandstorms. Behaves like snow layers but never melts. */
public class SandLayerBlock extends SnowLayerBlock {
    public SandLayerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // sand does not melt
    }
}
