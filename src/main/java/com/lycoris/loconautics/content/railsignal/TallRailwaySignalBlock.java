package com.lycoris.loconautics.content.railsignal;

import com.mojang.serialization.MapCodec;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Railway signal on a two block mast. This lower block is the real signal (block entity, track edge point,
 * block sections); the head sits in the {@link TallSignalTopBlock} above it, which only mirrors the aspect.
 */
public class TallRailwaySignalBlock extends RailwaySignalBlock {

    public static final MapCodec<TallRailwaySignalBlock> CODEC = simpleCodec(TallRailwaySignalBlock::new);

    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(4, 0, 4, 12, 1.5, 12),
            Block.box(6.5, 1.5, 6.5, 9.5, 16, 9.5));
    /** Same route box as the standard signal, on the mast in front of the pole. */
    private static final VoxelShape NORTH_SHAPE_ROUTE = Shapes.or(NORTH_SHAPE,
            Block.box(4.6, 2.6, 5.0, 11.4, 10, 6.5));
    private static final VoxelShape[] SHAPES = new VoxelShape[4];
    private static final VoxelShape[] SHAPES_ROUTE = new VoxelShape[4];

    static {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            SHAPES[direction.get2DDataValue()] = rotateShape(NORTH_SHAPE, direction);
            SHAPES_ROUTE[direction.get2DDataValue()] = rotateShape(NORTH_SHAPE_ROUTE, direction);
        }
    }

    public TallRailwaySignalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int index = state.getValue(FACING).get2DDataValue();
        return state.getValue(ROUTE) > 0 ? SHAPES_ROUTE[index] : SHAPES[index];
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return super.getStateForPlacement(context);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        syncTop(level, pos, state);
    }

    /** Places or refreshes the head block above so it shows the same facing and aspect as the signal. */
    public static void syncTop(Level level, BlockPos pos, BlockState lower) {
        BlockPos above = pos.above();
        BlockState current = level.getBlockState(above);
        BlockState top = LoconauticsRegistries.TALL_RAIL_SIGNAL_TOP.get().defaultBlockState()
                .setValue(FACING, lower.getValue(FACING))
                .setValue(ASPECT, lower.getValue(ASPECT));
        if (current.getBlock() instanceof TallSignalTopBlock) {
            if (!current.equals(top)) {
                level.setBlock(above, top, Block.UPDATE_CLIENTS);
            }
        } else if (current.canBeReplaced()) {
            level.setBlock(above, top, Block.UPDATE_ALL);
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.UP && !(neighborState.getBlock() instanceof TallSignalTopBlock)
                && !level.isClientSide() && level.getBlockState(pos.above()).isAir()) {
            // the head was broken away, the mast goes with it
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }
}
