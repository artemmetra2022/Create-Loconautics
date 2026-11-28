package com.lycoris.loconautics.content.airbrake;

import com.lycoris.loconautics.content.controls.panel.PanelDriveBus;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Locale;

public class DriverBrakeValveBlock extends HorizontalDirectionalBlock implements IBE<DriverBrakeValveBlockEntity> {

    public static final com.mojang.serialization.MapCodec<DriverBrakeValveBlock> CODEC =
            simpleCodec(DriverBrakeValveBlock::new);

    public DriverBrakeValveBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof DriverBrakeValveBlockEntity be) {
            if (PanelDriveBus.isDriven(level, pos)) {
                if (player.isShiftKeyDown() && be.getNotch() == 0) {
                    be.setIsolated(!be.isIsolated());
                    player.displayClientMessage(Component.translatable(
                            be.isIsolated()
                                    ? "loconautics.driver_brake_valve.isolated"
                                    : "loconautics.driver_brake_valve.notch",
                            be.getNotch(),
                            AirBrakeTuning.MAX_NOTCH,
                            String.format(Locale.ROOT, "%.1f", be.getPipePressure())), true);
                } else {
                    player.displayClientMessage(
                            Component.translatable("loconautics.driver_brake_valve.panel_controlled"), true);
                }
                return InteractionResult.CONSUME;
            }
            int delta = player.isShiftKeyDown() ? -1 : 1;
            be.stepNotch(delta);
            if (be.isIsolated()) {
                player.displayClientMessage(
                        Component.translatable("loconautics.driver_brake_valve.isolated"), true);
            } else {
                player.displayClientMessage(
                        Component.translatable("loconautics.driver_brake_valve.notch",
                                be.getNotch(),
                                AirBrakeTuning.MAX_NOTCH,
                                String.format(Locale.ROOT, "%.1f", be.getPipePressure())),
                        true);
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public Class<DriverBrakeValveBlockEntity> getBlockEntityClass() {
        return DriverBrakeValveBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends DriverBrakeValveBlockEntity> getBlockEntityType() {
        return AirBrakeBlocks.DRIVER_BRAKE_VALVE_BE.get();
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof DriverBrakeValveBlockEntity be) {
            return be.isIsolated() ? 0
                    : net.minecraft.util.Mth.clamp(be.getNotch(), 0, AirBrakeTuning.MAX_NOTCH);
        }
        return 0;
    }
}
