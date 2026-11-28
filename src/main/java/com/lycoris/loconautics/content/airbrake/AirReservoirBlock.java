package com.lycoris.loconautics.content.airbrake;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public class AirReservoirBlock extends DirectionalBlock implements IBE<AirReservoirBlockEntity> {

    public static final com.mojang.serialization.MapCodec<AirReservoirBlock> CODEC =
            simpleCodec(AirReservoirBlock::new);

    public AirReservoirBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    public Class<AirReservoirBlockEntity> getBlockEntityClass() {
        return AirReservoirBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends AirReservoirBlockEntity> getBlockEntityType() {
        return AirBrakeBlocks.AIR_RESERVOIR_BE.get();
    }

    @Override
    protected boolean hasAnalogOutputSignal(net.minecraft.world.level.block.state.BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(net.minecraft.world.level.block.state.BlockState state,
                                        net.minecraft.world.level.Level level,
                                        net.minecraft.core.BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof AirReservoirBlockEntity be) {
            return net.minecraft.util.Mth.clamp(
                    Math.round((be.getPressureBar() / AirBrakeTuning.MAX_MAIN_RESERVOIR_BAR) * 15f), 0, 15);
        }
        return 0;
    }
}
