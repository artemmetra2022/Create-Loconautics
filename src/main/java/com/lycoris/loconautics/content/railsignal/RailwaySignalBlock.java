package com.lycoris.loconautics.content.railsignal;

import com.lycoris.loconautics.registry.LoconauticsRegistries;
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
        RED, YELLOW, GREEN;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Aspect> ASPECT = EnumProperty.create("aspect", Aspect.class);

    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(5, 0, 5, 11, 1, 11),
            Block.box(7, 1, 7, 9, 8, 9),
            Block.box(2, 2, 4, 14, 16, 12));
    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            SHAPES[direction.get2DDataValue()] = rotateShape(NORTH_SHAPE, direction);
        }
    }

    private static VoxelShape rotateShape(VoxelShape shape, Direction facing) {
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
                .setValue(ASPECT, Aspect.RED));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(FACING, ASPECT));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(FACING, context.getHorizontalDirection().getOpposite());
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
        return SHAPES[state.getValue(FACING).get2DDataValue()];
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
