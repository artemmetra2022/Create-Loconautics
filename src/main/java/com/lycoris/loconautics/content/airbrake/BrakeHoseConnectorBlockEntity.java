package com.lycoris.loconautics.content.airbrake;

import com.lycoris.loconautics.common.strand.StrandAttachments;
import com.lycoris.loconautics.common.strand.StrandEndpoints;
import com.lycoris.loconautics.content.steelcable.SteelCableHolder;
import com.lycoris.loconautics.content.steelcable.SteelCableHolderBehaviour;
import com.lycoris.loconautics.content.steelcable.SteelCableStrand;
import com.lycoris.loconautics.content.steelcable.SteelCableStrandManager;
import com.lycoris.loconautics.content.steelcable.SteelCableType;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class BrakeHoseConnectorBlockEntity extends SmartBlockEntity
        implements SteelCableHolder, IHaveGoggleInformation {

    public BrakeHoseConnectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new SteelCableHolderBehaviour(this, strandCapacity()));
    }

    public boolean isCockOpen() {
        return getBlockState().getOptionalValue(BrakeHoseConnectorBlock.OPEN).orElse(false);
    }

    @Override
    public boolean acceptsPlainCables() {
        return false;
    }

    @Override
    public boolean accepts(SteelCableType type) {
        return type == SteelCableType.BRAKE_HOSE;
    }

    @Override
    public void onStrandAttached(SteelCableStrand strand) {
        super.onStrandAttached(strand);
        if (level != null && !level.isClientSide && !isCockOpen()) {
            BlockState state = getBlockState();
            if (state.hasProperty(BrakeHoseConnectorBlock.OPEN)) {
                level.setBlock(worldPosition, state.setValue(BrakeHoseConnectorBlock.OPEN, true), 3);
            }
        }
    }

    @Override
    public Vec3 getAttachmentPoint() {
        Direction open = getBlockState().getValue(BrakeHoseConnectorBlock.FACING).getOpposite();
        return Vec3.atCenterOf(getBlockPos()).add(
                open.getStepX() * 0.25,
                open.getStepY() * 0.25,
                open.getStepZ() * 0.25);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel serverLevel) || serverLevel.isClientSide) {
            return;
        }
        if (!isCockOpen()) {
            return;
        }

        List<UUID> attached = StrandAttachments.strands(this, SteelCableHolderBehaviour.TYPE);
        if (attached.isEmpty()) {
            float before = AirPipeBus.readPipePressure(this);
            AirPipeBus.markVented(this);
            if (before > 0.15f && serverLevel.getGameTime() % 4 == 0) {
                Vec3 pt = getAttachmentPoint();
                serverLevel.sendParticles(ParticleTypes.CLOUD, pt.x, pt.y, pt.z,
                        2, 0.08, 0.08, 0.08, 0.03);
                if (serverLevel.getGameTime() % 12 == 0) {
                    serverLevel.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH,
                            SoundSource.BLOCKS, 0.35f, 1.65f);
                }
            }
            return;
        }

        SteelCableStrandManager strands = SteelCableStrandManager.get(serverLevel);
        if (strands == null) {
            return;
        }
        SubLevel selfSub = Sable.HELPER.getContaining(this);
        UUID selfSubId = selfSub != null ? selfSub.getUniqueId() : null;
        SubLevelContainer container = SubLevelContainer.getContainer(serverLevel);

        for (UUID id : attached) {
            SteelCableStrand strand = strands.get(id);
            if (strand == null || strand.type != SteelCableType.BRAKE_HOSE) {
                continue;
            }
            if (!strand.isOwner(selfSubId, worldPosition)) {
                continue;
            }
            BlockEntity otherBe = StrandEndpoints.blockEntity(serverLevel, container, strand.subB, strand.localB);
            if (otherBe instanceof BrakeHoseConnectorBlockEntity other && other.isCockOpen()) {
                AirPipeBus.equalize(this, other);
            }
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean open = isCockOpen();
        float pipe = AirPipeBus.readPipePressure(this);
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("loconautics.brake_hose_connector.goggles.header")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(" ")
                .append(Component.translatable(open
                        ? "loconautics.brake_hose_connector.goggles.open"
                        : "loconautics.brake_hose_connector.goggles.closed"))
                .withStyle(open ? ChatFormatting.GREEN : ChatFormatting.GOLD));
        tooltip.add(Component.literal(" ")
                .append(Component.translatable("loconautics.air_brake.goggles.pressure",
                        String.format(Locale.ROOT, "%.2f", pipe)))
                .withStyle(ChatFormatting.AQUA));
        return true;
    }
}
