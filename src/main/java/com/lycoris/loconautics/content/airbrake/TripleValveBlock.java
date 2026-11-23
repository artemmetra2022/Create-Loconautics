package com.lycoris.loconautics.content.airbrake;

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

public class TripleValveBlock extends HorizontalDirectionalBlock implements IBE<TripleValveBlockEntity> {

    public static final com.mojang.serialization.MapCodec<TripleValveBlock> CODEC =
            simpleCodec(TripleValveBlock::new);

    public TripleValveBlock(Properties properties) {
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
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof TripleValveBlockEntity be) {
            be.releaseManually();
            player.displayClientMessage(Component.translatable("loconautics.triple_valve.released"), true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public Class<TripleValveBlockEntity> getBlockEntityClass() {
        return TripleValveBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends TripleValveBlockEntity> getBlockEntityType() {
        return AirBrakeBlocks.TRIPLE_VALVE_BE.get();
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof TripleValveBlockEntity be) {
            return net.minecraft.util.Mth.clamp(Math.round(be.getCylinderFraction() * 15f), 0, 15);
        }
        return 0;
    }
}
