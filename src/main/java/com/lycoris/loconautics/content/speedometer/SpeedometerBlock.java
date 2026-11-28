package com.lycoris.loconautics.content.speedometer;

import com.lycoris.loconautics.common.ShiftPlacement;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Measures how fast the carriage it sits on is moving. The speed shows in the goggles tooltip
 * and comes out as a comparator signal, 0 when standing and 15 at {@link SpeedometerBlockEntity#FULL_SCALE}.
 */
public class SpeedometerBlock extends Block implements IBE<SpeedometerBlockEntity>, IWrenchable {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public static final MapCodec<SpeedometerBlock> CODEC = simpleCodec(SpeedometerBlock::new);

    public SpeedometerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, ShiftPlacement.facing(context));
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                            Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof SpeedometerBlockEntity be) {
            be.resetTrip();
            player.displayClientMessage(Component.translatable("loconautics.gui.speedometer.trip_reset"), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof SpeedometerBlockEntity be ? be.getSignal() : 0;
    }

    @Override
    public Class<SpeedometerBlockEntity> getBlockEntityClass() {
        return SpeedometerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SpeedometerBlockEntity> getBlockEntityType() {
        return LoconauticsRegistries.SPEEDOMETER_BE.get();
    }
}
