package com.lycoris.loconautics.content.headstock;

import com.lycoris.loconautics.common.copycat.CopycatBlockLogic;
import com.lycoris.loconautics.common.copycat.CopycatPlacement;
import com.lycoris.loconautics.foundation.math.VoxelShapes;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.HitResult;

import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public class CopycatHeadstockBlock extends DirectionalBlock
        implements IBE<CopycatHeadstockBlockEntity>, IWrenchable {

    public static final MapCodec<CopycatHeadstockBlock> CODEC = simpleCodec(CopycatHeadstockBlock::new);

    private static final Map<CopycatPlacement, Map<Direction, VoxelShape>> SHAPES = new EnumMap<>(CopycatPlacement.class);

    static {
        for (CopycatPlacement placement : CopycatPlacement.values()) {
            VoxelShape north = Block.box(0, 4, 13, 16, 16, 16).move(0.0, placement.blocks(), 0.0);
            Map<Direction, VoxelShape> byFacing = new EnumMap<>(Direction.class);
            byFacing.putAll(VoxelShapes.byHorizontalFacing(north));
            byFacing.put(Direction.DOWN, tiltedFromNorth(north, true));
            byFacing.put(Direction.UP, tiltedFromNorth(north, false));
            SHAPES.put(placement, byFacing);
        }
    }

    private static VoxelShape tiltedFromNorth(VoxelShape north, boolean down) {
        VoxelShape result = Shapes.empty();
        for (var box : north.toAabbs()) {
            result = down
                    ? Shapes.or(result, Shapes.box(box.minX, box.minZ, 1 - box.maxY, box.maxX, box.maxZ, 1 - box.minY))
                    : Shapes.or(result, Shapes.box(box.minX, 1 - box.maxZ, box.minY, box.maxX, 1 - box.minZ, box.maxY));
        }
        return result.optimize();
    }

    public CopycatHeadstockBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(CopycatPlacement.PLACEMENT, CopycatPlacement.TOP));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CopycatPlacement.PLACEMENT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace();
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(CopycatPlacement.PLACEMENT, facing.getAxis() == Direction.Axis.Y
                        ? aimedAlongDepth(context, facing)
                        : CopycatPlacement.aimedAt(context));
    }

    private static CopycatPlacement aimedAlongDepth(BlockPlaceContext context, Direction facing) {
        double depth = context.getClickLocation().z - context.getClickedPos().getZ();
        if (facing == Direction.DOWN) {
            depth = 1.0 - depth;
        }
        if (depth > 2.0 / 3.0) {
            return CopycatPlacement.TOP;
        }
        return depth > 1.0 / 3.0 ? CopycatPlacement.CENTER : CopycatPlacement.BOTTOM;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(CopycatPlacement.PLACEMENT)).get(state.getValue(FACING));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        return CopycatBlockLogic.useItemOn(stack, level, pos, player, hit.getDirection());
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return CopycatBlockLogic.cyclePlacement(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        InteractionResult reclaimed = CopycatBlockLogic.reclaim(state, context.getLevel(),
                context.getClickedPos(), context.getPlayer());
        if (reclaimed != InteractionResult.PASS) {
            return reclaimed;
        }
        return IWrenchable.super.onSneakWrenched(state, context);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        CopycatBlockLogic.placedBy(level, pos, placer);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !movedByPiston) {
            CopycatBlockLogic.popConsumed(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public Class<CopycatHeadstockBlockEntity> getBlockEntityClass() {
        return CopycatHeadstockBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CopycatHeadstockBlockEntity> getBlockEntityType() {
        return LoconauticsRegistries.COPYCAT_HEADSTOCK_BE.get();
    }

    @Override
    public BlockState getAppearance(BlockState state, BlockAndTintGetter level, BlockPos pos, Direction side,
                                    @Nullable BlockState queryState, @Nullable BlockPos queryPos) {
        return CopycatBlockLogic.appearance(state, level, pos, queryPos);
    }

    @Override
    public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        return CopycatBlockLogic.materialAt(level, pos).getSoundType();
    }

    @Override
    public float getFriction(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        return CopycatBlockLogic.materialAt(level, pos).getFriction(level, pos, entity);
    }

    @Override
    public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) {
        return CopycatBlockLogic.materialAt(level, pos).canHarvestBlock(level, pos, player);
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return CopycatBlockLogic.materialAt(level, pos).getExplosionResistance(level, pos, explosion);
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return CopycatBlockLogic.materialAt(level, pos).getDestroyProgress(player, level, pos);
    }

    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return CopycatBlockLogic.materialAt(level, pos).canEntityDestroy(level, pos, entity);
    }

    @Override
    public float getEnchantPowerBonus(BlockState state, LevelReader level, BlockPos pos) {
        return CopycatBlockLogic.materialAt(level, pos).getEnchantPowerBonus(level, pos);
    }

    @Override
    public boolean addLandingEffects(BlockState state, ServerLevel level, BlockPos pos, BlockState sameState,
                                     LivingEntity entity, int particles) {
        return CopycatBlockLogic.materialAt(level, pos).addLandingEffects(level, pos, sameState, entity, particles);
    }

    @Override
    public boolean addRunningEffects(BlockState state, Level level, BlockPos pos, Entity entity) {
        return CopycatBlockLogic.materialAt(level, pos).addRunningEffects(level, pos, entity);
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float distance) {
        BlockState material = CopycatBlockLogic.materialAt(level, pos);
        material.getBlock().fallOn(level, material, pos, entity, distance);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos,
                                       Player player) {
        return CopycatBlockLogic.cloneStack(new ItemStack(this), target, level, pos, player);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        CopycatBlockLogic.clearConsumedInCreative(level, pos, player);
        return super.playerWillDestroy(level, pos, state, player);
    }
}
