package com.lycoris.loconautics.content.brakes;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.LinkHolder;
import com.lycoris.loconautics.common.RedstoneLinkPair;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The kinetic body of the Brake Controller. Like a Transmission it is a split shaft: whatever comes in on one end
 * leaves on the other, and the output side is slowed by the brake coefficient, 1 with the lever released down to 0
 * with it pulled all the way. The brake signal still arrives through a Redstone Link frequency.
 */
public class BrakeControllerBlockEntity extends SplitShaftBlockEntity implements MenuProvider, LinkHolder {

    public static final int MAX_SIGNAL = 15;

    private static final int VALIDATE_INTERVAL = 10;

    // only the first frequency pair is used, the second one stays empty
    private final RedstoneLinkPair links = new RedstoneLinkPair(this, this::setBrakeSignal, s -> { });

    private int brakeSignal;

    private boolean stateDirty;

    private boolean kineticRefreshQueued;

    private boolean reattachPending;

    private boolean validationQueued = true;

    /** null while the block sits between an engine and a Transmission, otherwise why it does not */
    @Nullable
    private String problem;

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

    /** the brake only acts while it is installed between an engine and a Transmission */
    public boolean isActive() {
        return problem == null;
    }

    @Nullable
    public String getProblem() {
        return problem;
    }

    /** the signal that counts, zero for a controller standing in the wrong place */
    public int getEffectiveBrakeSignal() {
        return isActive() ? brakeSignal : 0;
    }

    /** 1 passes the speed on untouched, 0 holds the output still */
    public float getBrakeCoefficient() {
        return 1f - getEffectiveBrakeSignal() / (float) MAX_SIGNAL;
    }

    public void setBrakeSignal(int signal) {
        int clamped = Math.max(0, Math.min(MAX_SIGNAL, signal));
        if (brakeSignal == clamped) return;
        brakeSignal = clamped;
        // the link callback runs inside create's network walk, the kinetic change rides the tick instead
        kineticRefreshQueued = true;
        setChanged();
        sendData();
    }

    public void queueValidation() {
        validationQueued = true;
    }

    @Override
    public float getRotationSpeedModifier(Direction face) {
        if (!hasSource()) return 1.0f;
        if (face == getSourceFacing()) return 1.0f;
        return getBrakeCoefficient();
    }

    @Override
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        if (level == null || level.isClientSide || !hasNetwork()) return;
        getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
    }

    /** braking is paid for in stress, the harder it bites the more the shaft has to push against it */
    @Override
    public float calculateStressApplied() {
        float impact = LoconauticsConfig.BRAKE_CONTROLLER_SU_IMPACT.get().floatValue();
        float applied = impact * (1f - getBrakeCoefficient());
        this.lastStressApplied = applied;
        return applied;
    }

    @Override
    public float calculateAddedStressCapacity() {
        return 0f;
    }

    @Override
    public void tick() {
        if (reattachPending && level != null && !level.isClientSide) {
            reattachPending = false;
            try {
                RotationPropagator.handleAdded(level, worldPosition, this);
            } catch (StackOverflowError overflow) {
                // an output that loops back never settles, drop the pass not the server
                LoconauticsConstants.LOGGER.error("[brake] reattach at {} overflowed, speed {} hasSource {}",
                        worldPosition.toShortString(), getSpeed(), hasSource());
            }
        }
        super.tick();
        if (level == null || level.isClientSide) return;

        if (validationQueued || level.getGameTime() % VALIDATE_INTERVAL == 0) {
            validationQueued = false;
            validate();
        }
        if (!kineticRefreshQueued) return;
        kineticRefreshQueued = false;
        applyKineticState();
    }

    private void validate() {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof BrakeControllerBlock)) return;
        String found = BrakeControllerBlock.problem(level, worldPosition, state.getValue(BlockStateProperties.AXIS));
        if (java.util.Objects.equals(found, problem)) return;
        problem = found;
        // a controller that stops being valid must let the shaft run again, and the reverse
        kineticRefreshQueued = true;
        setChanged();
        sendData();
    }

    // detach before the speed modifier changes, and never from a link callback, that runs inside creates network
    // walk. the reattach rides the next tick and is saved, so an unload between the two cannot strand the block.
    private void applyKineticState() {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof BrakeControllerBlock block)) return;
        block.detachKinetics(level, worldPosition);
        reattachPending = true;
        setChanged();
        BlockState updated = state.setValue(BrakeControllerBlock.POWERED, getEffectiveBrakeSignal() > 0);
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
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("loconautics.brake_controller.goggles.header")
                .withStyle(ChatFormatting.GRAY));
        if (problem != null) {
            tooltip.add(Component.literal(" ").append(
                    Component.translatable("loconautics.brake_controller.problem." + problem))
                    .withStyle(ChatFormatting.RED));
        } else {
            tooltip.add(Component.literal(" ").append(
                    Component.translatable("loconautics.brake_controller.goggles.signal", brakeSignal, MAX_SIGNAL))
                    .withStyle(ChatFormatting.AQUA));
        }
        return true;
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
        if (problem != null) tag.putString("Problem", problem);
        if (!clientPacket && reattachPending) tag.putBoolean("ReattachPending", true);
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
        problem = tag.contains("Problem") ? tag.getString("Problem") : null;
        if (!clientPacket) {
            reattachPending = tag.getBoolean("ReattachPending");
        }
        if (tag.contains("Frequency")) {
            CompoundTag freq = tag.getCompound("Frequency");
            links.setFirstFrequency(
                    ItemStack.parseOptional(registries, freq.getCompound("First")),
                    ItemStack.parseOptional(registries, freq.getCompound("Second")));
        }
    }
}
