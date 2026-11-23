package com.lycoris.loconautics.content.airbrake;

import com.lycoris.loconautics.common.strand.StrandAttachments;
import com.lycoris.loconautics.common.strand.StrandRemap;
import com.lycoris.loconautics.content.steelcable.SteelCableHolderBehaviour;
import com.lycoris.loconautics.content.steelcable.SteelCableStrand;
import com.lycoris.loconautics.content.steelcable.SteelCableStrandManager;
import com.lycoris.loconautics.content.steelcable.rope.SteelCableRopeManager;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.UUID;

public class BrakeHoseConnectorBlock extends Block
        implements IBE<BrakeHoseConnectorBlockEntity>, IWrenchable, BlockSubLevelAssemblyListener {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    private static final VoxelShaper SHAPES = VoxelShaper.forDirectional(Shapes.or(
            box(4, 4, 0, 12, 12, 3),
            box(5, 5, 3, 11, 11, 8)), Direction.NORTH);

    public BrakeHoseConnectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getClickedFace().getOpposite())
                .setValue(OPEN, false);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        Direction side = facing.getAxis() == Direction.Axis.Y ? facing.getOpposite() : facing;
        return SHAPES.get(side);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        boolean nextOpen = !state.getValue(OPEN);
        level.setBlock(pos, state.setValue(OPEN, nextOpen), 3);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5f, nextOpen ? 1.1f : 0.85f);
        player.displayClientMessage(
                Component.translatable(nextOpen
                        ? "loconautics.brake_hose_connector.opened"
                        : "loconautics.brake_hose_connector.closed"),
                true);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos();
        if (level.getBlockEntity(pos) instanceof BrakeHoseConnectorBlockEntity be) {
            List<UUID> attached = StrandAttachments.strands(be, SteelCableHolderBehaviour.TYPE);
            if (!attached.isEmpty()) {
                UUID id = attached.get(attached.size() - 1);
                SteelCableStrandManager strands = SteelCableStrandManager.get(level);
                SteelCableRopeManager ropes = SteelCableRopeManager.get(level);
                if (strands != null && ropes != null) {
                    SteelCableStrand strand = strands.get(id);
                    if (strand != null) {
                        ServerPlayer player = context.getPlayer() instanceof ServerPlayer sp ? sp : null;
                        com.lycoris.loconautics.content.steelcable.SteelCableStrandDriver.detach(
                                level, strands, ropes, strand, player);
                        // Do not close either cock on separation: an open end must vent
                        // the pipe until the player closes it manually.
                        return InteractionResult.SUCCESS;
                    }
                }
            }
        }
        boolean drop = !(context.getPlayer() instanceof Player p && p.isCreative());
        level.destroyBlock(pos, drop);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState,
                          BlockPos oldPos, BlockPos newPos) {
        StrandRemap.afterMove(originLevel, resultingLevel, oldPos, newPos);
    }

    @Override
    public Class<BrakeHoseConnectorBlockEntity> getBlockEntityClass() {
        return BrakeHoseConnectorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BrakeHoseConnectorBlockEntity> getBlockEntityType() {
        return AirBrakeBlocks.BRAKE_HOSE_CONNECTOR_BE.get();
    }
}
