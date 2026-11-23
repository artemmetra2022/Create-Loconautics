package com.lycoris.loconautics.content.airbrake;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

public class EmergencyBrakeBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    public EmergencyBrakeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public boolean isPulled() {
        return getBlockState().getOptionalValue(EmergencyBrakeBlock.POWERED).orElse(false);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel serverLevel) || serverLevel.isClientSide) {
            return;
        }
        if (!isPulled()) {
            return;
        }

        float before = AirPipeBus.readPipePressure(this);
        AirPipeBus.markVented(this);

        if (before > 0.15f && serverLevel.getGameTime() % 4 == 0) {
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    worldPosition.getX() + 0.5,
                    worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5,
                    2, 0.12, 0.12, 0.12, 0.03);
            if (serverLevel.getGameTime() % 12 == 0) {
                serverLevel.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH,
                        SoundSource.BLOCKS, 0.35f, 1.65f);
            }
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean pulled = isPulled();
        float pipe = AirPipeBus.readPipePressure(this);
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("loconautics.emergency_brake.goggles.header")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(" ")
                .append(Component.translatable(pulled
                        ? "loconautics.emergency_brake.goggles.pulled"
                        : "loconautics.emergency_brake.goggles.ready"))
                .withStyle(pulled ? ChatFormatting.RED : ChatFormatting.GREEN));
        tooltip.add(Component.literal(" ")
                .append(Component.translatable("loconautics.air_brake.goggles.pressure",
                        String.format(Locale.ROOT, "%.2f", pipe)))
                .withStyle(ChatFormatting.AQUA));
        return true;
    }
}
