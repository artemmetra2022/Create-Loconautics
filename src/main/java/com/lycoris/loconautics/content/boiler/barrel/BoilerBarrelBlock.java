package com.lycoris.loconautics.content.boiler.barrel;

import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.boiler.BoilerPlacement;
import com.lycoris.loconautics.content.boiler.BoilerShapes;
import com.lycoris.loconautics.content.boiler.firebox.FireboxBlock;
import com.lycoris.loconautics.foundation.pick.OversizeShape;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.UnaryOperator;

public class BoilerBarrelBlock extends DirectionalBlock
        implements IWrenchable, EntityBlock, BlockSubLevelCollisionShape, OversizeShape {

    public static final BooleanProperty CONNECTED_POS = BooleanProperty.create("connected_pos");
    public static final BooleanProperty CONNECTED_NEG = BooleanProperty.create("connected_neg");
    public static final BooleanProperty WRENCHED_POS = BooleanProperty.create("wrenched_pos");
    public static final BooleanProperty WRENCHED_NEG = BooleanProperty.create("wrenched_neg");
    public static final BooleanProperty SMOKE_POS = BooleanProperty.create("smoke_pos");
    public static final BooleanProperty SMOKE_NEG = BooleanProperty.create("smoke_neg");

    private static final int SMALL_HELPER_ID = PlacementHelpers.register(placement(false));
    private static final int BIG_HELPER_ID = PlacementHelpers.register(placement(true));

    private static BoilerPlacement placement(boolean big) {
        return new BoilerPlacement(
                state -> state.getBlock() instanceof BoilerBarrelBlock b && b.big == big,
                stack -> stack.getItem() instanceof BlockItem item
                        && item.getBlock() instanceof BoilerBarrelBlock b && b.big == big,
                FACING);
    }

    public static final MapCodec<BoilerBarrelBlock> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            propertiesCodec(),
            Codec.STRING.fieldOf("color").forGetter(b -> b.color),
            Codec.BOOL.fieldOf("big").forGetter(b -> b.big)
    ).apply(inst, BoilerBarrelBlock::new));

    public final String color;
    public final boolean big;

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    public BoilerBarrelBlock(Properties properties, String color, boolean big) {
        super(properties);
        this.color = color;
        this.big = big;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(CONNECTED_POS, false).setValue(CONNECTED_NEG, false)
                .setValue(WRENCHED_POS, false).setValue(WRENCHED_NEG, false)
                .setValue(SMOKE_POS, false).setValue(SMOKE_NEG, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CONNECTED_POS, CONNECTED_NEG, WRENCHED_POS, WRENCHED_NEG, SMOKE_POS, SMOKE_NEG);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return BoilerShapes.visual(state.getValue(FACING).getAxis(), big);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return BoilerShapes.collision(state.getValue(FACING).getAxis(), big);
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        return BoilerShapes.collision(state.getValue(FACING).getAxis(), big);
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    private boolean sameFamily(LevelAccessor level, BlockPos pos, Direction.Axis axis, Direction end) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof BoilerBarrelBlock other
                && other.big == this.big
                && s.getValue(FACING).getAxis() == axis
                || fireboxBack(s, end);
    }

    // a firebox joins a barrel at its back, the door faces away from the barrel
    private boolean fireboxBack(BlockState neighbor, Direction end) {
        return !big && neighbor.getBlock() instanceof FireboxBlock
                && neighbor.getValue(BlockStateProperties.HORIZONTAL_FACING) == end;
    }

    private boolean autoJoins(LevelAccessor level, BlockPos pos, Direction.Axis axis, Direction end) {
        BlockState s = level.getBlockState(pos);
        return fireboxBack(s, end) || sameFamily(level, pos, axis, end) && s.getBlock() == this;
    }

    static Direction positiveOf(Direction.Axis axis) {
        return Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
    }

    static BooleanProperty connectedFor(Direction.Axis axis, Direction toNeighbor) {
        return toNeighbor == positiveOf(axis) ? CONNECTED_POS : CONNECTED_NEG;
    }

    static BooleanProperty wrenchedFor(Direction.Axis axis, Direction toNeighbor) {
        return toNeighbor == positiveOf(axis) ? WRENCHED_POS : WRENCHED_NEG;
    }

    static BooleanProperty smokeEndFor(Direction.Axis axis, Direction toEnd) {
        return toEnd == positiveOf(axis) ? SMOKE_POS : SMOKE_NEG;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return turnEnds(state, rotation::rotate);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        Rotation turn = mirror.getRotation(state.getValue(FACING));
        return turnEnds(state, turn::rotate);
    }

    // the end flags are tied to the positive and negative end of the axis, so a turn can swap them
    private static BlockState turnEnds(BlockState state, UnaryOperator<Direction> turn) {
        Direction facing = state.getValue(FACING);
        boolean swapped = turn.apply(positiveOf(facing.getAxis())).getAxisDirection() != Direction.AxisDirection.POSITIVE;
        return state.setValue(FACING, turn.apply(facing))
                .setValue(CONNECTED_POS, state.getValue(swapped ? CONNECTED_NEG : CONNECTED_POS))
                .setValue(CONNECTED_NEG, state.getValue(swapped ? CONNECTED_POS : CONNECTED_NEG))
                .setValue(WRENCHED_POS, state.getValue(swapped ? WRENCHED_NEG : WRENCHED_POS))
                .setValue(WRENCHED_NEG, state.getValue(swapped ? WRENCHED_POS : WRENCHED_NEG))
                .setValue(SMOKE_POS, state.getValue(swapped ? SMOKE_NEG : SMOKE_POS))
                .setValue(SMOKE_NEG, state.getValue(swapped ? SMOKE_POS : SMOKE_NEG));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction look = context.getNearestLookingDirection();
        Direction facing = look.getOpposite();
        Direction.Axis axis = facing.getAxis();
        BlockState state = defaultBlockState().setValue(FACING, facing)
                .setValue(smokeEndFor(axis, look), true);
        for (Direction end : new Direction[]{positiveOf(axis), positiveOf(axis).getOpposite()}) {
            boolean join = autoJoins(context.getLevel(), context.getClickedPos().relative(end), axis, end);
            state = state.setValue(connectedFor(axis, end), join);
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction.Axis axis = state.getValue(FACING).getAxis();
        if (direction.getAxis() != axis) {
            return state;
        }
        BooleanProperty connected = connectedFor(axis, direction);
        BooleanProperty wrenched = wrenchedFor(axis, direction);
        if (!sameFamily(level, neighborPos, axis, direction)) {
            return state.setValue(connected, false).setValue(wrenched, false);
        }
        return state.setValue(connected, autoJoins(level, neighborPos, axis, direction) != state.getValue(wrenched));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BoilerBarrelBlockEntity(BoilerBlocks.BOILER_BARREL_BE.get(), pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        IPlacementHelper helper = PlacementHelpers.get(big ? BIG_HELPER_ID : SMALL_HELPER_ID);
        if (!helper.matchesItem(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return helper.getOffset(player, level, state, pos, hit)
                .placeInWorld(level, (BlockItem) stack.getItem(), player, hand, hit);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Direction.Axis axis = state.getValue(FACING).getAxis();
        Direction clicked = context.getClickedFace();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (clicked.getAxis() == axis) {
            if (!level.isClientSide()) {
                BooleanProperty smoke = smokeEndFor(axis, clicked);
                level.setBlock(pos, state.cycle(smoke), Block.UPDATE_CLIENTS);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        Direction end = aimedEnd(state, context);
        BlockPos neighborPos = pos.relative(end);
        if (!sameFamily(level, neighborPos, axis, end)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            boolean connect = !state.getValue(connectedFor(axis, end));
            toggleSeam(level, pos, neighborPos, axis, end, connect);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private Direction aimedEnd(BlockState state, UseOnContext context) {
        Direction.Axis axis = state.getValue(FACING).getAxis();
        Vec3 rel = context.getClickLocation().subtract(Vec3.atCenterOf(context.getClickedPos()));
        double comp = axis.choose(rel.x, rel.y, rel.z);
        Direction pos = positiveOf(axis);
        return comp >= 0 ? pos : pos.getOpposite();
    }

    private void toggleSeam(Level level, BlockPos pos, BlockPos neighborPos, Direction.Axis axis, Direction end, boolean connect) {
        boolean wrenched = connect != autoJoins(level, neighborPos, axis, end);
        BlockState here = level.getBlockState(pos);
        BlockState there = level.getBlockState(neighborPos);
        Direction back = end.getOpposite();

        level.setBlock(pos, here
                .setValue(connectedFor(axis, end), connect)
                .setValue(wrenchedFor(axis, end), wrenched), Block.UPDATE_CLIENTS);
        if (!(there.getBlock() instanceof BoilerBarrelBlock)) return;
        level.setBlock(neighborPos, there
                .setValue(connectedFor(axis, back), connect)
                .setValue(wrenchedFor(axis, back), wrenched), Block.UPDATE_CLIENTS);
    }
}
