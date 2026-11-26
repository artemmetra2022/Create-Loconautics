package com.lycoris.loconautics.content.safety;

import com.lycoris.loconautics.common.ShiftPlacement;
import com.lycoris.loconautics.network.packets.safety.SafetyOpenPacket;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Runs the safety systems of the carriage it sits on, anywhere in the consist, like the Speedometer. A wrench
 * right click opens the list of systems. The front of the block (FACING) is the direction the train drives
 * forward, the anti rollback system needs it. A tripped unit brakes the carriage as hard as the emergency
 * switch of a Brake Controller, see TrainBrakes.
 */
public class SafetyBlock extends Block implements IBE<SafetyBlockEntity>, IWrenchable {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** lit while the unit holds the emergency brake */
    public static final BooleanProperty TRIPPED = BooleanProperty.create("tripped");

    public static final MapCodec<SafetyBlock> CODEC = simpleCodec(SafetyBlock::new);

    public SafetyBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TRIPPED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TRIPPED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, ShiftPlacement.facing(context));
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // the wrench opens the screen, a sneaking wrench still picks the block up like any other create block
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos();
        if (context.getPlayer() instanceof ServerPlayer player
                && level.getBlockEntity(pos) instanceof SafetyBlockEntity be) {
            PacketDistributor.sendToPlayer(player, be.openPacket());
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof SafetyBlockEntity be && be.isEmergency() ? 15 : 0;
    }

    @Override
    public Class<SafetyBlockEntity> getBlockEntityClass() {
        return SafetyBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SafetyBlockEntity> getBlockEntityType() {
        return LoconauticsRegistries.SAFETY_BE.get();
    }
}
