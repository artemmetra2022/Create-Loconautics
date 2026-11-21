package com.lycoris.loconautics.content.brakes;

import com.lycoris.loconautics.client.ClientAccess;
import com.lycoris.loconautics.content.frequency.BindFrequencyMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;

public class BrakeControllerMenu extends BindFrequencyMenu<BrakeControllerBlockEntity> {

    public static final String BRAKE_KEY = "gui.loconautics.bind_frequency.brake";

    public BrakeControllerMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(type, id, inv, extraData, ComponentSerialization.TRUSTED_STREAM_CODEC.decode(extraData));
    }

    public BrakeControllerMenu(MenuType<?> type, int id, Inventory inv, BrakeControllerBlockEntity be) {
        super(type, id, inv, be, Component.translatable(BRAKE_KEY));
    }

    @Override
    protected int rowCount() {
        return 1;
    }

    @Override
    protected BrakeControllerBlockEntity createOnClient(RegistryFriendlyByteBuf extraData) {
        BlockEntity be = ClientAccess.level().getBlockEntity(extraData.readBlockPos());
        if (be instanceof BrakeControllerBlockEntity brake) {
            brake.readClient(extraData.readNbt(), extraData.registryAccess());
            return brake;
        }
        return null;
    }

    @Override
    protected ItemStackHandler createGhostInventory() {
        ItemStackHandler handler = new ItemStackHandler(2);
        if (contentHolder != null) {
            handler.setStackInSlot(0, contentHolder.getFreqFirst().copy());
            handler.setStackInSlot(1, contentHolder.getFreqSecond().copy());
        }
        return handler;
    }

    @Override
    protected void saveData(BrakeControllerBlockEntity be) {
        if (be == null) return;
        be.setFrequency(ghostInventory.getStackInSlot(0), ghostInventory.getStackInSlot(1));
    }
}
