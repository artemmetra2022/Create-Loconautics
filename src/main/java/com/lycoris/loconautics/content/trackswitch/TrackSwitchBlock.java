package com.lycoris.loconautics.content.trackswitch;

import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.jetbrains.annotations.Nullable;

/**
 * A switch stand. Put it within a few blocks of a track junction, bind it with a sneak right click and then
 * right click, or give it a redstone pulse, to send trains down the next branch. Applies to trains that have no
 * schedule destination and no steering input at that moment, as well as manually steered ones.
 */
public class TrackSwitchBlock extends Block implements EntityBlock {

    /** lit while a branch is forced, picks the powered model */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    /** auto shows the green fork, forced shows an arrow toward the branch the stand holds */
    public static final EnumProperty<Mode> MODE = EnumProperty.create("mode", Mode.class);

    /** compass direction of the held branch as seen from the junction, only read while forced */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public enum Mode implements StringRepresentable {
        AUTO("auto"), FORCED("forced");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public TrackSwitchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(POWERED, false).setValue(MODE, Mode.AUTO).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, MODE, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrackSwitchBlockEntity(LoconauticsRegistries.TRACK_SWITCH_BE.get(), pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof TrackSwitchBlockEntity stand) {
            if (player.isShiftKeyDown()) {
                stand.rebind(player);
            } else {
                stand.cycle(player);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                   boolean moving) {
        if (level.isClientSide) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        if (powered == state.getValue(POWERED)) {
            return;
        }
        level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        if (powered && level.getBlockEntity(pos) instanceof TrackSwitchBlockEntity stand) {
            stand.cycle(null);
        }
    }
}
