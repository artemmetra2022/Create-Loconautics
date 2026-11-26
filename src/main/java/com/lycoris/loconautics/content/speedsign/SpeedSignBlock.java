package com.lycoris.loconautics.content.speedsign;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A speed limit sign on a post, two blocks tall. It is only informative: the limit is shown on the
 * sign and changes with a wrench. Wrench the sign (upper half) for the next limit, the post
 * (lower half) for the previous one. Sneak-wrench dismantles it as usual.
 */
public class SpeedSignBlock extends Block implements IWrenchable {

    /** Limits in km/h, in the order the wrench cycles through them. Index is the SPEED state. */
    public static final int[] LIMITS = {10, 20, 25, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 140, 160, 180};
    public static final int DEFAULT_INDEX = 6; // 60 km/h

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final IntegerProperty SPEED = IntegerProperty.create("speed", 0, LIMITS.length - 1);

    public static final MapCodec<SpeedSignBlock> CODEC = simpleCodec(SpeedSignBlock::new);

    // shapes for FACING = NORTH (sign looks toward -Z), rotated for the other directions
    private static final VoxelShape POST = Block.box(7.0, 0.0, 7.0, 9.0, 16.0, 9.0);
    private static final VoxelShape BASE = Block.box(6.0, 0.0, 6.0, 10.0, 1.0, 10.0);
    private static final VoxelShape POST_TOP = Block.box(7.0, 0.0, 7.0, 9.0, 14.0, 9.0);
    private static final VoxelShape PLATE = Block.box(1.0, 1.0, 6.0, 15.0, 15.0, 7.0);

    private static final VoxelShape[] LOWER_SHAPES = new VoxelShape[4];
    private static final VoxelShape[] UPPER_SHAPES = new VoxelShape[4];

    static {
        VoxelShape lower = Shapes.or(POST, BASE);
        VoxelShape upper = Shapes.or(POST_TOP, PLATE);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            LOWER_SHAPES[dir.get2DDataValue()] = rotate(lower, dir);
            UPPER_SHAPES[dir.get2DDataValue()] = rotate(upper, dir);
        }
    }

    public SpeedSignBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(SPEED, DEFAULT_INDEX));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, SPEED);
    }

    public static int limitOf(BlockState state) {
        return LIMITS[state.getValue(SPEED)];
    }

    // ---- shape ----

    private static VoxelShape rotate(VoxelShape north, Direction facing) {
        int turns = switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        VoxelShape[] result = {north};
        for (int i = 0; i < turns; i++) {
            VoxelShape[] next = {Shapes.empty()};
            result[0].forAllBoxes((x1, y1, z1, x2, y2, z2) ->
                    // 90 degrees clockwise around Y: (x, z) -> (1 - z, x)
                    next[0] = Shapes.or(next[0], Shapes.box(1 - z2, y1, x1, 1 - z1, y2, x2)));
            result[0] = next[0];
        }
        return result[0];
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int index = state.getValue(FACING).get2DDataValue();
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_SHAPES[index] : UPPER_SHAPES[index];
    }

    // ---- placement, two-block handling ----

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(SPEED, DEFAULT_INDEX);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return belowState.is(this) && belowState.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
        return belowState.isFaceSturdy(level, below, Direction.UP)
                || Block.canSupportCenter(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        boolean towardsOtherHalf = direction.getAxis() == Direction.Axis.Y
                && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP);
        if (towardsOtherHalf) {
            if (neighborState.is(this) && neighborState.getValue(HALF) != half) {
                // the halves always agree on facing and limit
                return state.setValue(FACING, neighborState.getValue(FACING))
                        .setValue(SPEED, neighborState.getValue(SPEED));
            }
            return Blocks.AIR.defaultBlockState();
        }
        if (half == DoubleBlockHalf.LOWER && direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // creative: breaking the top must not leave a dropped item from the bottom
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);
            if (belowState.is(this) && belowState.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_ALL);
                level.levelEvent(player, 2001, below, Block.getId(belowState));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // ---- wrench: choose the limit ----

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos();
        int step = state.getValue(HALF) == DoubleBlockHalf.UPPER ? 1 : -1;
        int next = Math.floorMod(state.getValue(SPEED) + step, LIMITS.length);

        BlockPos lowerPos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        setLimit(level, lowerPos, next);

        Player player = context.getPlayer();
        if (player != null) {
            player.displayClientMessage(
                    Component.translatable("loconautics.gui.speed_sign.limit", LIMITS[next]), true);
        }
        level.playSound(null, pos, SoundEvents.COPPER_PLACE, SoundSource.BLOCKS, 0.7f, 0.8f + next * 0.03f);
        return InteractionResult.SUCCESS;
    }

    private void setLimit(Level level, BlockPos lowerPos, int index) {
        for (BlockPos at : new BlockPos[]{lowerPos, lowerPos.above()}) {
            BlockState current = level.getBlockState(at);
            if (current.is(this)) {
                level.setBlock(at, current.setValue(SPEED, index), Block.UPDATE_ALL);
            }
        }
    }

    // ---- rotation ----

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
