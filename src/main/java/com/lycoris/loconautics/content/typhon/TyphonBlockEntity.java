package com.lycoris.loconautics.content.typhon;

import com.lycoris.loconautics.common.LinkHolder;
import com.lycoris.loconautics.common.RedstoneLinkPair;
import com.lycoris.loconautics.foundation.smoke.SmokeOptions;
import com.lycoris.loconautics.foundation.smoke.SmokeStyle;
import com.lycoris.loconautics.trainphysics.fx.ParticleWorldLock;
import com.lycoris.loconautics.trainphysics.portal.PortalCutsClient;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class TyphonBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, LinkHolder {

    private static final int STEAM_COLOR = 0xF2F5F7;
    private static final int MANUAL_PULSE_TICKS = 15;

    private final RedstoneLinkPair link = new RedstoneLinkPair(this, this::setLinkSignal, s -> {});

    private int linkSignal;
    private int manualPulseTicks;
    private boolean sounding;

    public TyphonBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level == null || level.isClientSide) return;
        link.register(level);
    }

    @Override
    public void releaseLinks() {
        if (level == null) return;
        link.unregister(level);
    }

    @Override
    public void invalidate() {
        releaseLinks();
        super.invalidate();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        releaseLinks();
    }

    private void setLinkSignal(int strength) {
        if (linkSignal == strength) return;
        linkSignal = strength;
        setChanged();
    }

    public boolean hasFrequency() {
        return !link.getFirstFreqA().isEmpty() || !link.getFirstFreqB().isEmpty();
    }

    public void setFrequency(ItemStack first, ItemStack second) {
        link.setFirstFrequency(first, second);
        linkSignal = 0;
        if (level != null && !level.isClientSide) {
            link.register(level);
        }
        setChanged();
        sendData();
    }

    public void triggerManualPulse() {
        manualPulseTicks = MANUAL_PULSE_TICKS;
    }

    public boolean isSounding() {
        return sounding;
    }

    public Vec3 soundPosition() {
        return Sable.HELPER.projectOutOfSubLevel(level, localMouth());
    }

    private Vec3 mouthDirection() {
        Direction facing = getBlockState().getValue(TyphonBlock.FACING);
        return Vec3.atLowerCornerOf(facing.getNormal());
    }

    private Vec3 localMouth() {
        return Vec3.atCenterOf(worldPosition).add(mouthDirection().scale(0.5));
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) return;
        if (level.isClientSide) {
            TyphonSoundClient.tick(this, sounding);
            if (sounding) {
                emitSteam();
            }
            return;
        }

        if (manualPulseTicks > 0) {
            manualPulseTicks--;
        }

        boolean active = (hasFrequency() && linkSignal > 0)
                || level.hasNeighborSignal(worldPosition)
                || manualPulseTicks > 0;
        if (active != sounding) {
            sounding = active;
            setChanged();
            sendData();
            BlockState state = getBlockState();
            if (state.getBlock() instanceof TyphonBlock && state.getValue(TyphonBlock.SOUNDING) != sounding) {
                level.setBlock(worldPosition, state.setValue(TyphonBlock.SOUNDING, sounding), Block.UPDATE_CLIENTS);
            }
        }
    }

    private void emitSteam() {
        RandomSource random = level.random;
        Vec3 localMouth = localMouth();
        if (PortalCutsClient.hiddenAtPlot(localMouth.x, localMouth.y, localMouth.z)) {
            return;
        }
        Vec3 worldMouth = Sable.HELPER.projectOutOfSubLevel(level, localMouth);
        Vec3 worldDir = Sable.HELPER.projectOutOfSubLevel(level, localMouth.add(mouthDirection())).subtract(worldMouth);

        int count = 2 + (random.nextFloat() < 0.5f ? 1 : 0);
        double speed = 0.45;
        float scale = 1.15f;
        Vec3 spread = new Vec3(1.0 - Math.abs(worldDir.x), 1.0 - Math.abs(worldDir.y), 1.0 - Math.abs(worldDir.z));
        for (int i = 0; i < count; i++) {
            ParticleWorldLock.addParticle(level, new SmokeOptions(STEAM_COLOR, SmokeStyle.STEAM, scale),
                    worldMouth.x + (random.nextDouble() - 0.5) * 0.2 * spread.x,
                    worldMouth.y + (random.nextDouble() - 0.5) * 0.2 * spread.y,
                    worldMouth.z + (random.nextDouble() - 0.5) * 0.2 * spread.z,
                    worldDir.x * speed + (random.nextDouble() - 0.5) * 0.05 * spread.x,
                    worldDir.y * speed + 0.04 + (random.nextDouble() - 0.5) * 0.05 * spread.y,
                    worldDir.z * speed + (random.nextDouble() - 0.5) * 0.05 * spread.z);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("loconautics.typhon.goggles.header").withStyle(ChatFormatting.GRAY));
        Component state = Component.translatable(sounding
                ? "loconautics.typhon.goggles.sounding"
                : "loconautics.typhon.goggles.idle")
                .withStyle(sounding ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY);
        tooltip.add(Component.literal(" ").append(state));
        if (hasFrequency()) {
            Component freq = Component.empty()
                    .append(link.getFirstFreqA().isEmpty() ? Component.literal("-") : link.getFirstFreqA().getHoverName())
                    .append(Component.literal(" / ").withStyle(ChatFormatting.GRAY))
                    .append(link.getFirstFreqB().isEmpty() ? Component.literal("-") : link.getFirstFreqB().getHoverName());
            tooltip.add(Component.literal(" ")
                    .append(Component.translatable("loconautics.typhon.goggles.frequency").withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                    .append(freq));
        } else {
            tooltip.add(Component.literal(" ")
                    .append(Component.translatable("loconautics.typhon.goggles.unbound").withStyle(ChatFormatting.DARK_GRAY)));
        }
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("Sounding", sounding);
        writeFrequency(tag, registries);
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        writeFrequency(tag, registries);
    }

    private void writeFrequency(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag freq = new CompoundTag();
        freq.put("A", link.getFirstFreqA().saveOptional(registries));
        freq.put("B", link.getFirstFreqB().saveOptional(registries));
        tag.put("Frequency", freq);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        sounding = tag.getBoolean("Sounding");
        if (tag.contains("Frequency")) {
            CompoundTag freq = tag.getCompound("Frequency");
            link.setFirstFrequency(
                    ItemStack.parseOptional(registries, freq.getCompound("A")),
                    ItemStack.parseOptional(registries, freq.getCompound("B"))
            );
        }
    }
}
