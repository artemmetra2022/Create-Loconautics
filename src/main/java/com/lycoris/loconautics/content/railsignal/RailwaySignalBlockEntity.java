package com.lycoris.loconautics.content.railsignal;

import com.lycoris.loconautics.trainphysics.signal.TrainSignals;
import com.simibubi.create.content.trains.signal.SignalBlockEntity;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Uses Create's native signal propagation for green/yellow/red aspects and additionally includes
 * Loconautics' simulated Sable trains in the displayed occupancy state.
 */
public class RailwaySignalBlockEntity extends SignalBlockEntity {

    public RailwaySignalBlockEntity(BlockEntityType<?> type, net.minecraft.core.BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Include Sable occupancy in the block's comparator output as well as its rendered aspect. */
    @Override
    public boolean isPowered() {
        return super.isPowered() || isSectionBlockedBySableTraffic();
    }

    private boolean isSectionBlockedBySableTraffic() {
        if (edgePoint == null) {
            return false;
        }
        SignalBoundary boundary = getSignal();
        if (boundary == null) {
            return false;
        }

        UUID group = null;
        if (boundary.blockEntities.getFirst().containsKey(worldPosition)) {
            group = boundary.groups.get(true);
        } else if (boundary.blockEntities.getSecond().containsKey(worldPosition)) {
            group = boundary.groups.get(false);
        }
        // Native Create trains are already counted by SignalBoundary. Add Sable train occupancy
        // and reservations, which live in Loconautics' own physics registry.
        return group != null && (TrainSignals.occupied(group) || TrainSignals.reserved(group));
    }

    private Direction getSignalFacingFromTrack(Level level) {
        if (edgePoint == null || !edgePoint.hasValidTrack()) {
            return null;
        }
        var trackPos = edgePoint.getGlobalPosition();
        var axes = edgePoint.getTrack().getTrackAxes(level, trackPos, level.getBlockState(trackPos));
        if (axes.isEmpty()) {
            return null;
        }
        Vec3 trackDirection = axes.get(0);
        double approachSign = edgePoint.getTargetDirection() == AxisDirection.POSITIVE ? -1.0 : 1.0;
        return Direction.getNearest(trackDirection.x * approachSign, 0, trackDirection.z * approachSign);
    }

    @Override
    public void tick() {
        super.tick();

        Level level = getLevel();
        if (level == null || level.isClientSide || (level.getGameTime() & 1L) != 0L) {
            return;
        }

        SignalState signalState = isSectionBlockedBySableTraffic() ? SignalState.RED : getState();

        RailwaySignalBlock.Aspect aspect = switch (signalState) {
            case GREEN -> RailwaySignalBlock.Aspect.GREEN;
            case YELLOW -> RailwaySignalBlock.Aspect.YELLOW;
            // Invalid/unlinked signals fail safe to red, as do occupied or reserved sections.
            case RED, INVALID -> RailwaySignalBlock.Aspect.RED;
        };

        BlockState current = getBlockState();
        if (!(current.getBlock() instanceof RailwaySignalBlock)) {
            return;
        }

        BlockState updated = current;
        Direction facing = getSignalFacingFromTrack(level);
        if (facing != null && updated.getValue(RailwaySignalBlock.FACING) != facing) {
            updated = updated.setValue(RailwaySignalBlock.FACING, facing);
        }
        if (updated.getValue(RailwaySignalBlock.ASPECT) != aspect) {
            updated = updated.setValue(RailwaySignalBlock.ASPECT, aspect);
        }
        if (updated != current) {
            level.setBlock(worldPosition, updated, Block.UPDATE_CLIENTS);
        }
    }
}
