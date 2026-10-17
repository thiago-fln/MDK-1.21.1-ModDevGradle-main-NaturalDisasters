package com.naturaldisasters.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Shared behaviour of flood blocks: unbreakable, can't be targeted or built over, and entities wade through them
 * slowly and float up to the surface. {@link #LEVEL} is the water height in half blocks (1 = half, 2 = full).
 */
public abstract class AbstractFloodBlock extends Block {
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 1, 2);

    protected AbstractFloodBlock(Properties properties) {
        super(properties);
    }

    public static double surface(BlockPos pos, BlockState state) {
        return pos.getY() + state.getValue(LEVEL) * 0.5;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return false;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!pos.equals(entity.blockPosition())) return; // apply once per tick, not once per overlapped block
        double surface = surface(pos, state);
        if (entity.getY() >= surface) return;
        entity.clearFire();
        entity.resetFallDistance();
        Vec3 v = entity.getDeltaMovement();
        double vy = v.y;
        if (entity.getEyeY() < surface + 0.3) vy = Math.min(vy + 0.06, 0.14); // buoyancy
        entity.setDeltaMovement(v.x * 0.7, vy, v.z * 0.7);
    }
}
