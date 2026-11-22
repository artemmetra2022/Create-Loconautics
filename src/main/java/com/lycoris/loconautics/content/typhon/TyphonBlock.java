package com.lycoris.loconautics.content.typhon;

import com.lycoris.loconautics.common.ShiftPlacement;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TyphonBlock extends Block implements IBE<TyphonBlockEntity>, IWrenchable {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty SOUNDING = BooleanProperty.create("sounding");

    public static final MapCodec<TyphonBlock> CODEC = simpleCodec(TyphonBlock::new);

    private static final VoxelShape SHAPE_NS = Shapes.or(
            Block.box(4, 0, 3, 12, 4, 13),
            Block.box(5, 4, 1, 11, 10, 14)
    );
    private static final VoxelShape SHAPE_EW = Shapes.or(
            Block.box(3, 0, 4, 13, 4, 12),
            Block.box(2, 4, 5, 15, 10, 11)
    );

    public TyphonBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SOUNDING, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SOUNDING);
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
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return facing.getAxis() == Direction.Axis.X ? SHAPE_EW : SHAPE_NS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (hand != InteractionHand.MAIN_HAND) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        ItemStack first = player.getItemInHand(InteractionHand.MAIN_HAND).copyWithCount(1);
        ItemStack second = player.getItemInHand(InteractionHand.OFF_HAND).copyWithCount(1);
        if (first.isEmpty() && second.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof TyphonBlockEntity be) {
            be.setFrequency(first, second);
            if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("loconautics.typhon.bound"), true);
            }
        }
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof TyphonBlockEntity be) {
            if (player.isShiftKeyDown()) {
                if (be.hasFrequency()) {
                    be.setFrequency(ItemStack.EMPTY, ItemStack.EMPTY);
                    if (player instanceof ServerPlayer sp) {
                        sp.displayClientMessage(Component.translatable("loconautics.typhon.unbound"), true);
                    }
                } else if (player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.translatable("loconautics.typhon.bind_hint"), true);
                }
            } else {
                be.triggerManualPulse();
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public Class<TyphonBlockEntity> getBlockEntityClass() {
        return TyphonBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends TyphonBlockEntity> getBlockEntityType() {
        return LoconauticsRegistries.TYPHON_BE.get();
    }
}
