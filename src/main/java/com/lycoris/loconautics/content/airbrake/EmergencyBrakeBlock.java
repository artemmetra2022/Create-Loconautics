package com.lycoris.loconautics.content.airbrake;

import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class EmergencyBrakeBlock extends HorizontalDirectionalBlock implements IBE<EmergencyBrakeBlockEntity> {

    public static final com.mojang.serialization.MapCodec<EmergencyBrakeBlock> CODEC =
            simpleCodec(EmergencyBrakeBlock::new);

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private static final VoxelShaper SHAPES = VoxelShaper.forDirectional(Shapes.or(
            box(5, 3, 0, 11, 13, 3),
            box(7, 5, 3, 9, 11, 6)), Direction.NORTH);

    public EmergencyBrakeBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction dir = context.getHorizontalDirection();
        if (context.getClickedFace().getAxis().isHorizontal()) {
            dir = context.getClickedFace();
        }
        return defaultBlockState().setValue(FACING, dir).setValue(POWERED, false);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING).getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        boolean nextPulled = !state.getValue(POWERED);
        level.setBlock(pos, state.setValue(POWERED, nextPulled), 3);
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.7f, nextPulled ? 0.8f : 1.2f);
        if (nextPulled) {
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.55f, 1.5f);
        }
        player.displayClientMessage(Component.translatable(
                nextPulled ? "loconautics.emergency_brake.pulled" : "loconautics.emergency_brake.reset"), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public Class<EmergencyBrakeBlockEntity> getBlockEntityClass() {
        return EmergencyBrakeBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends EmergencyBrakeBlockEntity> getBlockEntityType() {
        return AirBrakeBlocks.EMERGENCY_BRAKE_BE.get();
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return state.getValue(POWERED) ? 15 : 0;
    }
}
