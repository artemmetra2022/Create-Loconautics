package com.lycoris.loconautics.content.boiler.bogey;

import com.lycoris.loconautics.client.ClientAccess;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerMenu;
import com.lycoris.loconautics.content.frequency.BindFrequencyMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;

public class BoilerBogeyMenu extends BindFrequencyMenu<BoilerBogeyBlockEntity> {

    public BoilerBogeyMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(type, id, inv, extraData,
                Component.translatable(AnalogControllerMenu.FORWARD_KEY),
                Component.translatable(AnalogControllerMenu.BACKWARD_KEY),
                Component.translatable(AnalogControllerMenu.LEFT_KEY),
                Component.translatable(AnalogControllerMenu.RIGHT_KEY));
    }

    public BoilerBogeyMenu(MenuType<?> type, int id, Inventory inv, BoilerBogeyBlockEntity be) {
        super(type, id, inv, be,
                Component.translatable(AnalogControllerMenu.FORWARD_KEY),
                Component.translatable(AnalogControllerMenu.BACKWARD_KEY),
                Component.translatable(AnalogControllerMenu.LEFT_KEY),
                Component.translatable(AnalogControllerMenu.RIGHT_KEY));
    }

    @Override
    protected int rowCount() {
        return 4;
    }

    @Override
    protected BoilerBogeyBlockEntity createOnClient(RegistryFriendlyByteBuf extraData) {
        BlockEntity be = ClientAccess.level().getBlockEntity(extraData.readBlockPos());
        if (be instanceof BoilerBogeyBlockEntity bogey) {
            bogey.loadWithComponents(extraData.readNbt(), extraData.registryAccess());
            return bogey;
        }
        return null;
    }

    @Override
    protected ItemStackHandler createGhostInventory() {
        ItemStackHandler handler = new ItemStackHandler(8);
        if (contentHolder != null) {
            handler.setStackInSlot(0, contentHolder.throttleLinks().getFirstFreqA().copy());
            handler.setStackInSlot(1, contentHolder.throttleLinks().getFirstFreqB().copy());
            handler.setStackInSlot(2, contentHolder.throttleLinks().getSecondFreqA().copy());
            handler.setStackInSlot(3, contentHolder.throttleLinks().getSecondFreqB().copy());
            handler.setStackInSlot(4, contentHolder.steerLinks().getFirstFreqA().copy());
            handler.setStackInSlot(5, contentHolder.steerLinks().getFirstFreqB().copy());
            handler.setStackInSlot(6, contentHolder.steerLinks().getSecondFreqA().copy());
            handler.setStackInSlot(7, contentHolder.steerLinks().getSecondFreqB().copy());
        }
        return handler;
    }

    @Override
    protected void saveData(BoilerBogeyBlockEntity be) {
        if (be == null) return;
        be.setSpeedFrequency(ghostInventory.getStackInSlot(0), ghostInventory.getStackInSlot(1));
        be.setDirectionFrequency(ghostInventory.getStackInSlot(2), ghostInventory.getStackInSlot(3));
        be.setLeftFrequency(ghostInventory.getStackInSlot(4), ghostInventory.getStackInSlot(5));
        be.setRightFrequency(ghostInventory.getStackInSlot(6), ghostInventory.getStackInSlot(7));
    }
}
