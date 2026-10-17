package com.naturaldisasters.entity;

import com.naturaldisasters.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.levelgen.Heightmap;

/** Fire whirl: born when a tornado crosses lava or a large fire. Ignites everything it touches and spreads fire. */
public class FirenadoEntity extends TornadoEntity {
    public FirenadoEntity(EntityType<? extends TornadoEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public TornadoVariant getVariant() {
        return TornadoVariant.FIRENADO;
    }

    @Override
    protected boolean canBecomeFirenado() {
        return false;
    }

    @Override
    protected void onEntityInVortex(ServerLevel level, Entity e, double distance, float core) {
        super.onEntityInVortex(level, e, distance, core);
        if (distance < core * 1.3) {
            e.igniteForSeconds(6f);
            if (e instanceof LivingEntity && life % 20 == 0) e.hurt(level.damageSources().inFire(), 1f);
        }
    }

    @Override
    protected void variantTick(ServerLevel level) {
        if (!Config.FIRENADO_FIRE.get() || life % 4 != 0 || getScale() < 0.3f || !canDestroyBlocks(level)) return;
        float radius = getCoreRadius() * 1.4f;
        for (int i = 0; i < 2; i++) {
            double a = random.nextDouble() * Math.PI * 2, rr = Math.sqrt(random.nextDouble()) * radius;
            int x = Mth.floor(getX() + Math.cos(a) * rr), z = Mth.floor(getZ() + Math.sin(a) * rr);
            BlockPos col = new BlockPos(x, (int) getY(), z);
            if (!level.isLoaded(col)) continue;
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z);
            if (level.getBlockState(pos).isAir() && BaseFireBlock.canBePlacedAt(level, pos, Direction.UP)) {
                level.setBlock(pos, BaseFireBlock.getState(level, pos), 11);
            }
        }
    }
}
