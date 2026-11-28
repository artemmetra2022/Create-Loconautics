package com.lycoris.loconautics.content.railsignal;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.trackswitch.TrackSwitchBlockEntity;
import com.lycoris.loconautics.trainphysics.signal.TrainSignals;
import com.simibubi.create.content.trains.signal.SignalBlockEntity;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import java.util.UUID;
import net.minecraft.core.BlockPos;
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

    /** What the next signal ahead shows; refreshed every few ticks for four-aspect signalling. */
    private RailwaySignalBlock.Aspect nextAspect;
    private int scanCooldown;

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

    /**
     * Keeps the route indicator on the mast in sync: it lights while the signal is not red and, next
     * to a Track Switch, shows that switch's branch number and stays dark until a branch is set.
     */
    private BlockState updateRouteIndicator(Level level, BlockState state, RailwaySignalBlock.Aspect aspect) {
        int route = state.getValue(RailwaySignalBlock.ROUTE);
        if (route == 0) {
            return state.getValue(RailwaySignalBlock.ROUTE_LIT)
                    ? state.setValue(RailwaySignalBlock.ROUTE_LIT, false) : state;
        }
        boolean switchSeen = false;
        int branch = 0;
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = worldPosition.relative(direction);
            if (level.isLoaded(neighbour) && level.getBlockEntity(neighbour) instanceof TrackSwitchBlockEntity trackSwitch) {
                switchSeen = true;
                branch = Math.max(branch, trackSwitch.branchNumber());
            }
        }
        if (branch > 0) {
            route = Math.min(branch, RouteIndicatorBlock.MAX_BRANCH_SYMBOL);
        }
        boolean lit = aspect != RailwaySignalBlock.Aspect.RED && (!switchSeen || branch > 0);
        return state.setValue(RailwaySignalBlock.ROUTE, route).setValue(RailwaySignalBlock.ROUTE_LIT, lit);
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
        // Must agree with RailwaySignalBlock#getStateForPlacement (lamps face the placing player);
        // the opposite sign made the signal flip 180 degrees on the first tick after placement.
        double approachSign = edgePoint.getTargetDirection() == AxisDirection.POSITIVE ? 1.0 : -1.0;
        return Direction.getNearest(trackDirection.x * approachSign, 0, trackDirection.z * approachSign);
    }

    private void scanNextSignal(Level level) {
        nextAspect = null;
        if (edgePoint == null || !edgePoint.hasValidTrack()) {
            return;
        }
        nextAspect = SignalAspects.lookAhead(level, edgePoint.getGlobalPosition(),
                edgePoint.getTargetDirection(), edgePoint.getTargetBezier(), getSignal());
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

        // Four-aspect automatic block: green = three or more free sections, yellow-green = two,
        // yellow = one, red = occupied. Never freer than Create's own state says.
        if (aspect != RailwaySignalBlock.Aspect.RED && LoconauticsConfig.FOUR_ASPECT_SIGNALS.get()) {
            if (scanCooldown-- <= 0) {
                scanNextSignal(level);
                scanCooldown = 5;
            }
            if (nextAspect != null) {
                RailwaySignalBlock.Aspect derived = switch (nextAspect) {
                    case RED -> RailwaySignalBlock.Aspect.YELLOW;
                    case YELLOW -> RailwaySignalBlock.Aspect.YELLOW_GREEN;
                    case YELLOW_GREEN, GREEN -> RailwaySignalBlock.Aspect.GREEN;
                };
                if (derived.ordinal() < aspect.ordinal()) {
                    aspect = derived;
                }
            }
        }

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
        updated = updateRouteIndicator(level, updated, aspect);
        if (updated != current) {
            level.setBlock(worldPosition, updated, Block.UPDATE_CLIENTS);
            if (updated.getBlock() instanceof TallRailwaySignalBlock) {
                TallRailwaySignalBlock.syncTop(level, worldPosition, updated);
            }
        }
    }
}
