package com.lycoris.loconautics.content.brakes;

import com.lycoris.loconautics.content.combustion.SmallEngineBlock;
import com.lycoris.loconautics.content.frequency.FrequencyBindUtil;
import com.lycoris.loconautics.content.transmission.TransmissionBlock;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.AbstractEncasedShaftBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * An inline brake on the drive shaft. It passes rotation straight through, one end to the other, and scales the
 * speed of the output side by how far the brake signal (a Redstone Link frequency, usually an Analog Controller
 * lever) is pulled. It only works as the link between an engine and a Transmission: the shaft has to run from an
 * engine output into the controller and on into the Transmission, all on one axis.
 */
public class BrakeControllerBlock extends AbstractEncasedShaftBlock implements IBE<BrakeControllerBlockEntity> {

    /** lit while the signal is above zero, picks the powered model */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public static final String BIND_KEY = "Brake";

    /** suffixes of loconautics.brake_controller.problem.* */
    public static final String NO_ENGINE = "no_engine";
    public static final String NO_TRANSMISSION = "no_transmission";
    public static final String WRONG_AXIS = "wrong_axis";

    private static final VoxelShape SHAPE = Shapes.block();

    public BrakeControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState placed = super.getStateForPlacement(context);
        if (placed == null) {
            return null;
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction.Axis fit = fittingAxis(level, pos, context.getClickedFace().getAxis());
        if (fit == null) {
            // only the client shows the hint, the server just refuses the placement
            if (level.isClientSide && context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(
                        Component.translatable("loconautics.brake_controller.bad_placement"), true);
            }
            return null;
        }
        return placed.setValue(BlockStateProperties.AXIS, fit).setValue(POWERED, false);
    }

    /** the axis that has a Transmission on one end and an engine on the other, the clicked one first */
    @Nullable
    public static Direction.Axis fittingAxis(LevelReader level, BlockPos pos, Direction.Axis preferred) {
        if (problem(level, pos, preferred) == null) {
            return preferred;
        }
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis != preferred && problem(level, pos, axis) == null) {
                return axis;
            }
        }
        return null;
    }

    /**
     * Checks the neighbours along the given axis. Returns null when one end is a Transmission lined up with the
     * shaft and the other is an engine whose output faces the controller, otherwise the problem key suffix.
     */
    @Nullable
    public static String problem(LevelReader level, BlockPos pos, Direction.Axis axis) {
        Direction plus = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        Direction minus = plus.getOpposite();
        boolean forward = isTransmission(level, pos.relative(plus), axis) && isEngine(level, pos.relative(minus), plus);
        boolean backward = isTransmission(level, pos.relative(minus), axis) && isEngine(level, pos.relative(plus), minus);
        if (forward || backward) {
            return null;
        }
        boolean transmissionOnAxis = isTransmission(level, pos.relative(plus), axis)
                || isTransmission(level, pos.relative(minus), axis);
        if (transmissionOnAxis) {
            return NO_ENGINE;
        }
        for (Direction side : Direction.values()) {
            if (side.getAxis() != axis && level.getBlockState(pos.relative(side)).getBlock() instanceof TransmissionBlock) {
                return WRONG_AXIS;
            }
            if (side.getAxis() == axis && isTransmissionBlock(level, pos.relative(side))) {
                return WRONG_AXIS;
            }
        }
        return NO_TRANSMISSION;
    }

    private static boolean isTransmissionBlock(LevelReader level, BlockPos at) {
        return level.getBlockState(at).getBlock() instanceof TransmissionBlock;
    }

    private static boolean isTransmission(LevelReader level, BlockPos at, Direction.Axis axis) {
        BlockState state = level.getBlockState(at);
        return state.getBlock() instanceof TransmissionBlock && state.getValue(BlockStateProperties.AXIS) == axis;
    }

    /** @param shaftFace the face of the engine that has to carry the shaft, the one looking at the controller */
    private static boolean isEngine(LevelReader level, BlockPos at, Direction shaftFace) {
        BlockState state = level.getBlockState(at);
        return state.getBlock() instanceof SmallEngineBlock engine
                && engine.hasShaftTowards(level, at, state, shaftFace);
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
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof BrakeControllerBlockEntity be) {
            be.queueValidation();
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
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
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BrakeControllerBlockEntity brake) {
            player.openMenu(brake, brake::sendToMenu);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return false;
    }

    /** takes the block out of the kinetic network, used before its speed modifier changes */
    public void detachKinetics(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof KineticBlockEntity kbe) {
            RotationPropagator.handleRemoved(level, pos, kbe);
        }
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
