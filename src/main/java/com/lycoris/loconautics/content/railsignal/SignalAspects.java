package com.lycoris.loconautics.content.railsignal;

import com.lycoris.loconautics.trainphysics.rail.RailFollower;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.entity.TravellingPoint.SteerDirection;
import com.simibubi.create.content.trains.graph.TrackGraphHelper;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.signal.SignalBlockEntity.SignalState;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.track.BezierTrackPointLocation;
import com.simibubi.create.content.trains.track.ITrackBlock;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Shared helpers for reading signal aspects along the track (used by signals and repeaters). */
public final class SignalAspects {

    /** How far ahead of a signal the next signal is searched, in blocks of track. */
    public static final double LOOKAHEAD = 384.0;

    private SignalAspects() {
    }

    /** Whether the signal has at least one head on the side that faces the given travel direction. */
    public static boolean hasHead(SignalBoundary signal, boolean primary) {
        return !(primary ? signal.blockEntities.getFirst() : signal.blockEntities.getSecond()).isEmpty();
    }

    /**
     * What a signal boundary currently shows to a train travelling in the direction of
     * {@code primary}. Loconautics heads are read from their block state, so the result includes
     * four-aspect and Sable-occupancy logic; other signals fall back to Create's cached state.
     */
    public static RailwaySignalBlock.Aspect displayed(Level level, SignalBoundary signal, boolean primary) {
        if (signal.isForcedRed(primary)) {
            return RailwaySignalBlock.Aspect.RED;
        }
        Map<BlockPos, Boolean> heads = primary ? signal.blockEntities.getFirst() : signal.blockEntities.getSecond();
        for (BlockPos pos : heads.keySet()) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof RailwaySignalBlock) {
                return state.getValue(RailwaySignalBlock.ASPECT);
            }
        }
        SignalState cached = signal.cachedStates.get(primary);
        if (cached == null) {
            return RailwaySignalBlock.Aspect.RED;
        }
        return switch (cached) {
            case GREEN -> RailwaySignalBlock.Aspect.GREEN;
            case YELLOW -> RailwaySignalBlock.Aspect.YELLOW;
            default -> RailwaySignalBlock.Aspect.RED;
        };
    }

    /**
     * Finds the next signal in front of a track position and returns what it shows, or
     * {@code null} when no other signal lies within {@link #LOOKAHEAD}.
     */
    @Nullable
    public static RailwaySignalBlock.Aspect lookAhead(Level level, BlockPos trackPos, AxisDirection dir,
                                               @Nullable BezierTrackPointLocation bezier,
                                               @Nullable SignalBoundary self) {
        TrackGraphLocation loc = null;
        if (bezier != null) {
            loc = TrackGraphHelper.getBezierGraphLocationAt(level, trackPos, dir, bezier);
        } else {
            BlockState trackState = level.getBlockState(trackPos);
            if (trackState.getBlock() instanceof ITrackBlock track) {
                List<Vec3> axes = track.getTrackAxes(level, trackPos, trackState);
                if (!axes.isEmpty()) {
                    loc = TrackGraphHelper.getGraphLocationAt(level, trackPos, dir, axes.get(0));
                }
            }
        }
        if (loc == null || loc.graph == null) {
            return null;
        }
        TravellingPoint scout = RailFollower.pointAt(loc);
        if (scout == null) {
            return null;
        }
        SignalBoundary[] found = {null};
        boolean[] primary = {true};
        scout.travel(loc.graph, LOOKAHEAD,
                scout.steer(SteerDirection.NONE, new Vec3(0, 1, 0)),
                (distance, pair) -> {
                    if (!(pair.getFirst() instanceof SignalBoundary signal)) return false;
                    if (Math.abs(distance) < 1.0 || signal == self) return false;
                    found[0] = signal;
                    primary[0] = signal.isPrimary(pair.getSecond().getSecond());
                    return true;
                },
                scout.ignoreTurns(), scout.ignorePortals());
        return found[0] == null ? null : displayed(level, found[0], primary[0]);
    }
}
