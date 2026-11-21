package com.lycoris.loconautics.content.brakes;

import com.lycoris.loconautics.common.LinkHolder;
import com.lycoris.loconautics.common.RedstoneLinkPair;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BrakeControllerBlockEntity extends SmartBlockEntity implements MenuProvider, LinkHolder {

    public static final int MAX_SIGNAL = 15;

    // only the first frequency pair is used, the second one stays empty
    private final RedstoneLinkPair links = new RedstoneLinkPair(this, this::setBrakeSignal, s -> { });

    private int brakeSignal;

    private boolean stateDirty;

    public BrakeControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level == null || level.isClientSide) return;
        links.register(level);
    }

    @Override
    public void releaseLinks() {
        if (level != null) links.unregister(level);
    }

    @Override
    public void invalidate() {
        if (level != null) links.unregister(level);
        super.invalidate();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null) links.unregister(level);
    }

    /** 0 is released, 15 is the lever pulled all the way */
    public int getBrakeSignal() {
        return brakeSignal;
    }

    public void setBrakeSignal(int signal) {
        int clamped = Math.max(0, Math.min(MAX_SIGNAL, signal));
        if (brakeSignal == clamped) return;
        brakeSignal = clamped;
        // the link callback runs inside create's network walk, so the block state is written on the tick
        stateDirty = true;
        setChanged();
        sendData();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || !stateDirty) return;
        stateDirty = false;
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof BrakeControllerBlock)) return;
        BlockState updated = state.setValue(BrakeControllerBlock.POWERED, brakeSignal > 0);
        if (!updated.equals(state)) {
            level.setBlock(worldPosition, updated, 2);
        }
    }

    public void setFrequency(ItemStack first, ItemStack second) {
        links.setFirstFrequency(first, second);
        if (level != null && !level.isClientSide) links.register(level);
        setChanged();
        sendData();
    }

    public ItemStack getFreqFirst() {
        return links.getFirstFreqA();
    }

    public ItemStack getFreqSecond() {
        return links.getFirstFreqB();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.loconautics.brake_controller");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new BrakeControllerMenu(LoconauticsRegistries.BRAKE_CONTROLLER_MENU.get(), id, inventory, this);
    }

    public void sendToMenu(RegistryFriendlyByteBuf buffer) {
        ComponentSerialization.TRUSTED_STREAM_CODEC
                .encode(buffer, Component.translatable(BrakeControllerMenu.BRAKE_KEY));
        super.sendToMenu(buffer);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("BrakeSignal", brakeSignal);
        writeFrequency(tag, registries);
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        writeFrequency(tag, registries);
    }

    private void writeFrequency(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag freq = new CompoundTag();
        freq.put("First", links.getFirstFreqA().saveOptional(registries));
        freq.put("Second", links.getFirstFreqB().saveOptional(registries));
        tag.put("Frequency", freq);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        brakeSignal = tag.getInt("BrakeSignal");
        if (tag.contains("Frequency")) {
            CompoundTag freq = tag.getCompound("Frequency");
            links.setFirstFrequency(
                    ItemStack.parseOptional(registries, freq.getCompound("First")),
                    ItemStack.parseOptional(registries, freq.getCompound("Second")));
        }
    }
}
