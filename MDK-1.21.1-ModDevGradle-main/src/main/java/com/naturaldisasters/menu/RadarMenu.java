package com.naturaldisasters.menu;

import com.naturaldisasters.network.RadarGridPayload;
import com.naturaldisasters.registry.ModRegistry;
import com.naturaldisasters.weather.Atmosphere;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Slot-less menu for the weather radar. Server side it periodically streams a thermal grid to the viewer. */
public class RadarMenu extends AbstractContainerMenu {
    public static final int RADIUS = 500;
    public static final int GRID = 21;

    private final BlockPos pos;
    private final Player player;
    private int timer;

    public RadarMenu(int id, Inventory inventory, BlockPos pos) {
        super(ModRegistry.RADAR_MENU.get(), id);
        this.pos = pos;
        this.player = inventory.player;
    }

    public BlockPos getPos() {
        return pos;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (player instanceof ServerPlayer sp && timer++ % 100 == 0) {
            sendGrid(sp);
        }
    }

    private void sendGrid(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        float[] temp = new float[GRID * GRID], hum = new float[GRID * GRID];
        double step = (RADIUS * 2.0) / (GRID - 1);
        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) {
                Atmosphere.Reading r = Atmosphere.sample(level, pos.getX() - RADIUS + i * step, pos.getZ() - RADIUS + j * step);
                temp[j * GRID + i] = r.temperature();
                hum[j * GRID + i] = r.humidity();
            }
        }
        PacketDistributor.sendToPlayer(sp, new RadarGridPayload(pos.getX(), pos.getZ(), RADIUS, GRID, temp, hum));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).is(ModRegistry.WEATHER_RADAR.get())
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
