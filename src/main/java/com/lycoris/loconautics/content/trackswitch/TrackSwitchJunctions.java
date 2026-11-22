package com.lycoris.loconautics.content.trackswitch;

import com.lycoris.loconautics.common.railway.PointOverrides;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Finds track junctions near a switch stand and lists the branches that leave them. */
public final class TrackSwitchJunctions {

    /** how far from the stand a junction node may be, in blocks */
    public static final double REACH = 4.0;

    public record Branch(String id, Vec3 at) {
    }

    public record Junction(String id, Vec3 at, List<Branch> branches) {
    }

    private TrackSwitchJunctions() {
    }

    /** nearest junction node (three or more connections) within {@link #REACH}, or null */
    public static Junction nearest(ServerLevel level, BlockPos pos) {
        if (Create.RAILWAYS == null) {
            return null;
        }
        Vec3 center = Vec3.atCenterOf(pos);
        Junction best = null;
        double bestDistance = REACH * REACH;
        for (TrackGraph graph : new ArrayList<>(Create.RAILWAYS.trackNetworks.values())) {
            for (TrackNodeLocation location : graph.getNodes()) {
                if (!location.getDimension().equals(level.dimension())) {
                    continue;
                }
                double distance = location.getLocation().distanceToSqr(center);
                if (distance > bestDistance) {
                    continue;
                }
                Junction junction = junction(graph, location);
                if (junction != null) {
                    best = junction;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    /** the branches of a junction as they are now, or null when the node is gone or is not a junction */
    public static Junction find(String junctionId) {
        if (Create.RAILWAYS == null) {
            return null;
        }
        for (TrackGraph graph : new ArrayList<>(Create.RAILWAYS.trackNetworks.values())) {
            for (TrackNodeLocation location : graph.getNodes()) {
                if (PointOverrides.nodeId(location).equals(junctionId)) {
                    return junction(graph, location);
                }
            }
        }
        return null;
    }

    private static Junction junction(TrackGraph graph, TrackNodeLocation location) {
        TrackNode node = graph.locateNode(location);
        if (node == null) {
            return null;
        }
        Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(node);
        if (connections == null || connections.size() < 3) {
            return null;
        }
        List<Branch> branches = new ArrayList<>();
        for (TrackNode next : connections.keySet()) {
            branches.add(new Branch(PointOverrides.nodeId(next.getLocation()), next.getLocation().getLocation()));
        }
        branches.sort(Comparator.comparing(Branch::id));
        return new Junction(PointOverrides.nodeId(location), location.getLocation(), branches);
    }
}
