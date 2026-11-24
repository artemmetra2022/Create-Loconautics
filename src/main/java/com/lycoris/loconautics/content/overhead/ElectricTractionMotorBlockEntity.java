package com.lycoris.loconautics.content.overhead;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class ElectricTractionMotorBlockEntity extends GeneratingKineticBlockEntity
        implements IHaveGoggleInformation {

    private static final float MAX_RPM = 256f;
    private static final float STRESS_PER_RPM = 0.5f;
    private int inputSignal;
    private float outputRpm;

    public ElectricTractionMotorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;

        int signal = level.getBestNeighborSignal(worldPosition);
        float rpm = signal == 0 ? 0f : MAX_RPM * signal / 15f;
        if (signal == inputSignal && rpm == outputRpm) return;

        inputSignal = signal;
        outputRpm = rpm;
        updateGeneratedRotation();
        sendData();
    }

    @Override
    public float getGeneratedSpeed() {
        if (outputRpm <= 0f) return 0f;
        Direction facing = getBlockState().getValue(ElectricTractionMotorBlock.HORIZONTAL_FACING);
        return convertToDirection(outputRpm, facing);
    }

    @Override
    public float calculateAddedStressCapacity() {
        return outputRpm * STRESS_PER_RPM;
    }

    public boolean isPowered() {
        return outputRpm > 0f;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.translatable("gui.goggles.loconautics_electric_motor")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(isPowered()
                        ? "gui.goggles.loconautics_electric_motor_running"
                        : "gui.goggles.loconautics_electric_motor_stopped",
                Math.round(outputRpm), inputSignal)
                .withStyle(isPowered() ? ChatFormatting.GREEN : ChatFormatting.RED));
        return true;
    }
}
