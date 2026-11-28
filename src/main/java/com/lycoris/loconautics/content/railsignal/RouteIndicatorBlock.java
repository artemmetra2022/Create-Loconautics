package com.lycoris.loconautics.content.railsignal;

import com.lycoris.loconautics.content.trackswitch.TrackSwitchBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Route indicator: a lit box that shows which route a signal lets the train take.
 * <p>
 * Symbols 0-7 are the digits 1-8, 8 and 9 are left and right arrows, 10 is a straight-ahead arrow.
 * Right-click cycles the symbol (sneak cycles backwards). Next to a Track Switch the indicator
 * copies the number of the branch the switch is set to and only lights while a branch is set.
 * Next to a signal it lights while that signal is not red. Redstone power lights it as well.
 */
public class RouteIndicatorBlock extends Block {

    public static final MapCodec<RouteIndicatorBlock> CODEC = simpleCodec(RouteIndicatorBlock::new);

    public static final int SYMBOL_COUNT = 11;
    public static final int MAX_BRANCH_SYMBOL = 8;

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty SYMBOL = IntegerProperty.create("symbol", 0, SYMBOL_COUNT - 1);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private static final int REFRESH_TICKS = 10;

    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(4, 0, 4, 12, 1, 12),
            Block.box(3, 1, 4.5, 13, 16, 10));
    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            SHAPES[direction.get2DDataValue()] = RailwaySignalBlock.rotateShape(NORTH_SHAPE, direction);
        }
    }

    public RouteIndicatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(SYMBOL, 0)
                .setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SYMBOL, LIT);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide && !oldState.is(this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState updated = evaluate(state, level, pos);
        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_ALL);
        }
        level.scheduleTick(pos, this, REFRESH_TICKS);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        int step = player.isShiftKeyDown() ? -1 : 1;
        int next = Math.floorMod(state.getValue(SYMBOL) + step, SYMBOL_COUNT);
        level.setBlock(pos, state.setValue(SYMBOL, next), Block.UPDATE_ALL);
        return InteractionResult.CONSUME;
    }

    private static BlockState evaluate(BlockState state, Level level, BlockPos pos) {
        boolean signalSeen = false;
        boolean signalOpen = false;
        boolean switchSeen = false;
        int branch = 0;
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            if (!level.isLoaded(neighbour)) {
                continue;
            }
            BlockState neighbourState = level.getBlockState(neighbour);
            if (neighbourState.getBlock() instanceof RailwaySignalBlock) {
                signalSeen = true;
                if (neighbourState.getValue(RailwaySignalBlock.ASPECT) != RailwaySignalBlock.Aspect.RED) {
                    signalOpen = true;
                }
            } else if (level.getBlockEntity(neighbour) instanceof TrackSwitchBlockEntity trackSwitch) {
                switchSeen = true;
                branch = Math.max(branch, trackSwitch.branchNumber());
            }
        }
        BlockState result = state;
        if (branch > 0) {
            result = result.setValue(SYMBOL, Math.min(branch, MAX_BRANCH_SYMBOL) - 1);
        }
        boolean routeSet = !switchSeen || branch > 0;
        boolean lit = level.hasNeighborSignal(pos)
                || (signalSeen ? signalOpen && routeSet : switchSeen && branch > 0);
        return result.setValue(LIT, lit);
    }
}
