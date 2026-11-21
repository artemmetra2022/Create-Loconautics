package com.lycoris.loconautics.content.brakes;

import com.lycoris.loconautics.common.ShiftPlacement;
import com.lycoris.loconautics.content.frequency.FrequencyBindUtil;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.phys.BlockHitResult;

/**
 * A brake that listens to a Redstone Link frequency, usually the one an Analog Controller lever sends.
 * The higher the lever position (signal 0 to 15), the harder it brakes the carriage it sits on.
 */
public class BrakeControllerBlock extends Block implements IBE<BrakeControllerBlockEntity>, IWrenchable {

    /** lit while the signal is above zero, picks the powered model */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public static final String BIND_KEY = "Brake";

    public static final MapCodec<BrakeControllerBlock> CODEC = simpleCodec(BrakeControllerBlock::new);

    public BrakeControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, ShiftPlacement.facing(context))
                .setValue(POWERED, false);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !FrequencyBindUtil.isBound(stack)
                || !(level.getBlockEntity(pos) instanceof BrakeControllerBlockEntity be)) {
            return;
        }
        ItemStack[] brake = FrequencyBindUtil.read(stack, level.registryAccess(), BIND_KEY);
        if (brake != null) {
            be.setFrequency(brake[0], brake[1]);
        }
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
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                            Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof BrakeControllerBlockEntity be) {
            player.openMenu(be, be::sendToMenu);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public Class<BrakeControllerBlockEntity> getBlockEntityClass() {
        return BrakeControllerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BrakeControllerBlockEntity> getBlockEntityType() {
        return LoconauticsRegistries.BRAKE_CONTROLLER_BE.get();
    }
}
