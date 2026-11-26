package com.lycoris.loconautics.content.overhead;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CatenaryMastBlock extends Block {

    public enum Part implements StringRepresentable {
        BASE("base"), SHAFT("shaft"), HEAD("head");
        private final String name;
        Part(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final DirectionProperty FACING =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape BASE_SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 3, 15),
            Block.box(5, 3, 5, 11, 16, 11));
    private static final VoxelShape SHAFT_SHAPE = Shapes.or(
            Block.box(6, 0, 6, 10, 16, 10),
            Block.box(4, 2, 7, 12, 3, 9),
            Block.box(4, 13, 7, 12, 14, 9));
    private static final VoxelShape HEAD_NORTH = Shapes.or(
            Block.box(6, 0, 6, 10, 16, 10),
            Block.box(7, 12, -8, 9, 15, 9),
            Block.box(5, 13, -10, 11, 16, -7),
            Block.box(6, 15, -13, 10, 16, -11));

    public CatenaryMastBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(PART, Part.BASE).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(PART)) {
            case BASE -> BASE_SHAPE;
            case SHAFT -> SHAFT_SHAPE;
            case HEAD -> rotateHead(state.getValue(FACING));
        };
    }

    private static VoxelShape rotateHead(Direction facing) {
        return switch (facing) {
            case NORTH -> HEAD_NORTH;
            case EAST -> Shapes.or(Block.box(6, 0, 6, 10, 16, 10),
                    Block.box(7, 12, 7, 24, 15, 9), Block.box(22, 13, 5, 25, 16, 11));
            case SOUTH -> Shapes.or(Block.box(6, 0, 6, 10, 16, 10),
                    Block.box(7, 12, 7, 9, 15, 24), Block.box(5, 13, 22, 11, 16, 25));
            case WEST -> Shapes.or(Block.box(6, 0, 6, 10, 16, 10),
                    Block.box(-8, 12, 7, 9, 15, 9), Block.box(-9, 13, 5, -6, 16, 11));
            default -> HEAD_NORTH;
        };
    }
}
