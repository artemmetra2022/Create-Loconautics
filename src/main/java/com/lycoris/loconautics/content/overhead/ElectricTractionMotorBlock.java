package com.lycoris.loconautics.content.overhead;

import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ElectricTractionMotorBlock extends HorizontalKineticBlock implements IBE<ElectricTractionMotorBlockEntity> {

    public ElectricTractionMotorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(HORIZONTAL_FACING);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(HORIZONTAL_FACING).getAxis();
    }

    @Override
    public Class<ElectricTractionMotorBlockEntity> getBlockEntityClass() {
        return ElectricTractionMotorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ElectricTractionMotorBlockEntity> getBlockEntityType() {
        return OverheadBlocks.ELECTRIC_MOTOR_BE.get();
    }
}
