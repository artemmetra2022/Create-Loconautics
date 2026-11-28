package com.lycoris.loconautics.content.sand;

import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.content.controls.panel.PanelDriveBus;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class SandStorageBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    public static final int CAPACITY = 64;
    public static final int SANDING_DURATION_TICKS = 40;

    private int sand;
    private long sandingUntil;
    private boolean buttonDown;

    public SandStorageBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // This storage has no kinetic or fluid behaviours; it is operated by a panel button.
    }

    public int getSand() {
        return sand;
    }

    public boolean addSand(int amount) {
        if (amount <= 0 || sand >= CAPACITY) {
            return false;
        }
        sand = Math.min(CAPACITY, sand + amount);
        setChanged();
        syncVisualLevel();
        return true;
    }

    private void syncVisualLevel() {
        if (level == null || level.isClientSide || !(getBlockState().getBlock() instanceof SandStorageBlock)) {
            return;
        }
        int visualLevel = SandStorageBlock.fillLevelFor(sand);
        BlockState state = getBlockState();
        if (state.getValue(SandStorageBlock.FILL_LEVEL) != visualLevel) {
            level.setBlock(worldPosition, state.setValue(SandStorageBlock.FILL_LEVEL, visualLevel), Block.UPDATE_ALL);
        }
    }

    public boolean isSanding() {
        return level != null && level.getGameTime() < sandingUntil;
    }

    /**
     * Sanding is local to the carriage carrying the reservoir. The shared sublevel
     * block-entity cache avoids scanning every block in the carriage each wheel tick.
     */
    public static boolean hasActiveSanding(ServerSubLevel sub) {
        if (sub == null || sub.isRemoved()) {
            return false;
        }
        for (SandStorageBlockEntity storage : SubLevelBlocks.findAll(sub, SandStorageBlockEntity.class)) {
            if (storage.isSanding()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }

        // Also repairs the visual state after loading saved block-entity data.
        syncVisualLevel();

        boolean powered = PanelDriveBus.signal(level, worldPosition) > 0f;
        if (powered && !buttonDown && sand > 0) {
            sand--;
            sandingUntil = Math.max(sandingUntil, level.getGameTime()) + SANDING_DURATION_TICKS;
            setChanged();
            syncVisualLevel();
            sendData();
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
            level.playSound(null, worldPosition, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 0.7f, 0.72f);
        }
        buttonDown = powered;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("loconautics.sand_storage.goggles.header")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("loconautics.sand_storage.goggles.amount", sand, CAPACITY)
                .withStyle(sand > 0 ? ChatFormatting.YELLOW : ChatFormatting.RED));
        tooltip.add(Component.translatable(isSanding()
                        ? "loconautics.sand_storage.goggles.active"
                        : "loconautics.sand_storage.goggles.idle")
                .withStyle(isSanding() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Sand", sand);
        tag.putLong("SandingUntil", sandingUntil);
        tag.putBoolean("ButtonDown", buttonDown);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        sand = Math.max(0, Math.min(CAPACITY, tag.getInt("Sand")));
        sandingUntil = tag.getLong("SandingUntil");
        buttonDown = tag.getBoolean("ButtonDown");
    }
}
