package com.lycoris.loconautics.content.overhead;

import com.simibubi.create.foundation.block.IBE;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PantographBlock extends HorizontalDirectionalBlock implements IBE<PantographBlockEntity> {

    public static final BooleanProperty CONTACT = BooleanProperty.create("contact");
    /** 0, 1, 2 correspond to three-, four-, and five-block extension heights. */
    public static final IntegerProperty REACH = IntegerProperty.create("reach", 0, 2);

    private static final VoxelShape BASE = Shapes.or(
            Block.box(1, 0, 2, 15, 3, 14),
            Block.box(3, 3, 4, 5, 7, 6),
            Block.box(11, 3, 10, 13, 7, 12));

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(PantographBlock::new);
    }

    public PantographBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH)
                .setValue(CONTACT, false).setValue(REACH, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CONTACT, REACH);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.getValue(CONTACT)) return BASE;
        int height = switch (state.getValue(REACH)) {
            case 0 -> 64;
            case 1 -> 80;
            default -> 96;
        };
        return Shapes.or(BASE, Block.box(3, 3, 3, 13, height, 13));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        // Moving linkage is visual equipment on the train and must not collide with the carriage itself.
        return BASE;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(CONTACT) ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 0;
    }

    @Override
    public Class<PantographBlockEntity> getBlockEntityClass() {
        return PantographBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PantographBlockEntity> getBlockEntityType() {
        return OverheadBlocks.PANTOGRAPH_BE.get();
    }
}
