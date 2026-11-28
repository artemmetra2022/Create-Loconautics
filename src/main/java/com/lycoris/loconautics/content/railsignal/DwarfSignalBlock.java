package com.lycoris.loconautics.content.railsignal;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Low "dwarf" three-aspect signal for yards and station throats. It behaves exactly like the
 * mast {@link RailwaySignalBlock} (same block entity, same Create signal boundary) and only
 * differs in its model and hitbox.
 */
public class DwarfSignalBlock extends RailwaySignalBlock {

    public static final MapCodec<DwarfSignalBlock> CODEC = simpleCodec(DwarfSignalBlock::new);

    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(4, 0, 4, 12, 2, 12),
            Block.box(3.5, 2, 3.4, 12.5, 15.6, 9.5));
    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            SHAPES[direction.get2DDataValue()] = rotateShape(NORTH_SHAPE, direction);
        }
    }

    public DwarfSignalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }
}
