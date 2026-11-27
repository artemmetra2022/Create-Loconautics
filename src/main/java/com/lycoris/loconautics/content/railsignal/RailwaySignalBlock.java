package com.lycoris.loconautics.content.railsignal;

import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.trains.signal.SignalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;

/**
 * Three-aspect railway signal. The track connection and block-section graph are Create's native
 * SignalBoundary/SignalEdgeGroup system; this block only supplies a Loconautics signal head.
 */
public class RailwaySignalBlock extends SignalBlock {

    public static final MapCodec<RailwaySignalBlock> CODEC = simpleCodec(RailwaySignalBlock::new);

    public enum Aspect implements StringRepresentable {
        RED, YELLOW, YELLOW_GREEN, GREEN;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Aspect> ASPECT = EnumProperty.create("aspect", Aspect.class);

    /**
     * Route indicator clamped to the mast: 0 = none, 1..11 = symbol index + 1 (digits 1-8, left,
     * right, straight ahead - the same symbols as the {@link RouteIndicatorBlock}).
     */
    public static final IntegerProperty ROUTE = IntegerProperty.create("route", 0, RouteIndicatorBlock.SYMBOL_COUNT);
    /** Whether the mounted route indicator is lit; maintained by the signal's block entity. */
    public static final BooleanProperty ROUTE_LIT = BooleanProperty.create("route_lit");

    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(4, 0, 4, 12, 1.5, 12),
            Block.box(6.5, 1.5, 6.5, 9.5, 10, 9.5),
            Block.box(3, 10, 3.4, 13, 23, 10));
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

    /** Only the tall mast signal can carry a route indicator; dwarf and shunting signals cannot. */
    protected boolean canMountRouteIndicator() {
        return getClass() == RailwaySignalBlock.class;
    }

    static VoxelShape rotateShape(VoxelShape shape, Direction facing) {
        int turns = switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        VoxelShape[] current = {shape};
        for (int i = 0; i < turns; i++) {
            VoxelShape[] next = {Shapes.empty()};
            current[0].forAllBoxes((x1, y1, z1, x2, y2, z2) ->
                    next[0] = Shapes.or(next[0], Shapes.box(1 - z2, y1, x1, 1 - z1, y2, x2)));
            current[0] = next[0];
        }
        return current[0];
    }

    public RailwaySignalBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(ASPECT, Aspect.RED)
                .setValue(ROUTE, 0)
                .setValue(ROUTE_LIT, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(FACING, ASPECT, ROUTE, ROUTE_LIT));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        // The block entity aligns the signal with the track and the target direction. Use the
        // player's look direction here as well, otherwise the signal visibly flips 180 degrees
        // on the first tick after placement.
        return state == null ? null : state.setValue(FACING, context.getHorizontalDirection());
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
        int index = state.getValue(FACING).get2DDataValue();
        return state.getValue(ROUTE) > 0 ? SHAPES_ROUTE[index] : SHAPES[index];
    }

    /** Right-click with a Route Indicator item clamps it to the mast. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (canMountRouteIndicator() && state.getValue(ROUTE) == 0
                && stack.is(LoconauticsRegistries.ROUTE_INDICATOR_ITEM.get())) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(ROUTE, 1).setValue(ROUTE_LIT, false), Block.UPDATE_ALL);
                level.playSound(null, pos, getSoundType(state).getPlaceSound(), SoundSource.BLOCKS, 1.0f, 1.0f);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    /** Empty hand on a signal with an indicator: cycle the symbol; sneaking takes the indicator off. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!canMountRouteIndicator() || state.getValue(ROUTE) == 0) {
            return super.useWithoutItem(state, level, pos, player, hit);
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            level.setBlock(pos, state.setValue(ROUTE, 0).setValue(ROUTE_LIT, false), Block.UPDATE_ALL);
            level.playSound(null, pos, getSoundType(state).getBreakSound(), SoundSource.BLOCKS, 0.8f, 1.0f);
            ItemStack back = new ItemStack(LoconauticsRegistries.ROUTE_INDICATOR_ITEM.get());
            if (!player.getInventory().add(back)) {
                player.drop(back, false);
            }
        } else {
            int next = state.getValue(ROUTE) % RouteIndicatorBlock.SYMBOL_COUNT + 1;
            level.setBlock(pos, state.setValue(ROUTE, next), Block.UPDATE_ALL);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public Class<com.simibubi.create.content.trains.signal.SignalBlockEntity> getBlockEntityClass() {
        return com.simibubi.create.content.trains.signal.SignalBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends com.simibubi.create.content.trains.signal.SignalBlockEntity> getBlockEntityType() {
        return LoconauticsRegistries.RAIL_SIGNAL_BE.get();
    }
}
