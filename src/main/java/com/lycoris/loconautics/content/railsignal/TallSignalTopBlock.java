package com.lycoris.loconautics.content.railsignal;

import com.mojang.serialization.MapCodec;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The upper half of the {@link TallRailwaySignalBlock}: upper mast and the signal head, no block entity. */
public class TallSignalTopBlock extends Block implements IWrenchable {

    public static final MapCodec<TallSignalTopBlock> CODEC = simpleCodec(TallSignalTopBlock::new);

    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(6.5, 0, 6.5, 9.5, 10, 9.5),
            Block.box(3, 10, 3.4, 13, 23, 10));
    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            SHAPES[direction.get2DDataValue()] = RailwaySignalBlock.rotateShape(NORTH_SHAPE, direction);
        }
    }

    public TallSignalTopBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(RailwaySignalBlock.FACING, Direction.NORTH)
                .setValue(RailwaySignalBlock.ASPECT, RailwaySignalBlock.Aspect.RED));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RailwaySignalBlock.FACING, RailwaySignalBlock.ASPECT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(RailwaySignalBlock.FACING).get2DDataValue()];
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).getBlock() instanceof TallRailwaySignalBlock;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !(neighborState.getBlock() instanceof TallRailwaySignalBlock)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(LoconauticsRegistries.TALL_RAIL_SIGNAL_ITEM.get());
    }

    /** Breaking the head breaks the whole signal, with its drop unless the player is in creative. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos below = pos.below();
        if (!level.isClientSide && level.getBlockState(below).getBlock() instanceof TallRailwaySignalBlock) {
            level.destroyBlock(below, !player.isCreative(), player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos below = pos.below();
        return level.getBlockState(below).useItemOn(stack, level, player, hand, hit.withPosition(below));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        BlockPos below = pos.below();
        return level.getBlockState(below).useWithoutItem(level, player, hit.withPosition(below));
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return forwardWrench(context, false);
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        return forwardWrench(context, true);
    }

    private static InteractionResult forwardWrench(UseOnContext context, boolean sneak) {
        BlockPos clickedPos = context.getClickedPos();
        BlockPos below = clickedPos.below();
        Level level = context.getLevel();
        BlockState lower = level.getBlockState(below);
        if (!(lower.getBlock() instanceof IWrenchable wrenchable)) {
            return InteractionResult.PASS;
        }
        BlockHitResult newHitResult = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), below, false);
        UseOnContext moved = new UseOnContext(level, context.getPlayer(), context.getHand(),
                context.getItemInHand(), newHitResult);
        return sneak ? wrenchable.onSneakWrenched(lower, moved) : wrenchable.onWrenched(lower, moved);
    }
}
