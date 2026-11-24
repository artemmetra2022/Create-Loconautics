package com.lycoris.loconautics.common.railway;

import com.simibubi.create.content.trains.entity.TravellingPoint.ITrackSelector;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class PointOverrides {

    private static final Map<String, String> POINTS = new ConcurrentHashMap<>();
    /** who holds each entry of POINTS, {@link #MANUAL} for a player, a stand in forced mode or a computer */
    private static final Map<String, String> OWNERS = new ConcurrentHashMap<>();
    /** junctions whose switch stand is in auto mode, where a scheduled train may set the switch itself */
    private static final Set<String> AUTO = ConcurrentHashMap.newKeySet();

    public static final String MANUAL = "manual";

    private PointOverrides() {
    }

    public static String nodeId(TrackNodeLocation location) {
        return location.getDimension().location() + "@" + location.getX() + "," + location.getY() + ","
                + location.getZ();
    }

    public static void set(String junction, String toward) {
        POINTS.put(junction, toward);
        OWNERS.put(junction, MANUAL);
    }

    public static void clear(String junction) {
        POINTS.remove(junction);
        OWNERS.remove(junction);
    }

    public static void markAuto(String junction) {
        AUTO.add(junction);
    }

    public static void unmarkAuto(String junction) {
        AUTO.remove(junction);
    }

    public static boolean isAuto(String junction) {
        return AUTO.contains(junction);
    }

    public static boolean hasAuto() {
        return !AUTO.isEmpty();
    }

    /** a scheduled train takes the switch of an auto junction, refused while someone else holds it other way */
    public static boolean reserve(String junction, String toward, String owner) {
        if (!AUTO.contains(junction)) {
            return false;
        }
        String holder = OWNERS.get(junction);
        if (holder != null && !holder.equals(owner)) {
            return false;
        }
        POINTS.put(junction, toward);
        OWNERS.put(junction, owner);
        return true;
    }

    /** lets go of every reservation the owner holds except the kept junctions */
    public static void releaseExcept(String owner, Set<String> kept) {
        OWNERS.forEach((junction, holder) -> {
            if (holder.equals(owner) && !kept.contains(junction)) {
                POINTS.remove(junction);
                OWNERS.remove(junction);
            }
        });
    }

    public static void releaseAll(String owner) {
        releaseExcept(owner, Set.of());
    }

    public static boolean hasManualLocks() {
        return OWNERS.containsValue(MANUAL);
    }

    /** the branch a player or a stand has locked at the junction, null when none or when a train holds it */
    public static String manualLock(String junction) {
        return MANUAL.equals(OWNERS.get(junction)) ? POINTS.get(junction) : null;
    }

    public static Map<String, String> all() {
        return Map.copyOf(POINTS);
    }

    public static void clearAll() {
        POINTS.clear();
        OWNERS.clear();
        AUTO.clear();
    }

    public static ITrackSelector wrap(ITrackSelector original) {
        return (graph, pair) -> {
            Map.Entry<TrackNode, TrackEdge> forced = choose(pair.getFirst(), pair.getSecond());
            return forced != null ? forced : original.apply(graph, pair);
        };
    }

    /** the option a switch stand (or a train reserving an auto junction) holds among these, null when none */
    public static Map.Entry<TrackNode, TrackEdge> locked(List<Map.Entry<TrackNode, TrackEdge>> options) {
        return choose(true, options);
    }

    private static Map.Entry<TrackNode, TrackEdge> choose(boolean forward,
                                                           List<Map.Entry<TrackNode, TrackEdge>> options) {
        if (POINTS.isEmpty() || options.size() < 2) {
            return null;
        }
        for (Map.Entry<TrackNode, TrackEdge> option : options) {
            // Create reverses the outgoing edge orientation while travelling backwards.
            TrackNode junction = forward ? option.getValue().node1 : option.getValue().node2;
            String toward = POINTS.get(nodeId(junction.getLocation()));
            if (toward != null && toward.equals(nodeId(option.getKey().getLocation()))) {
                return option;
            }
        }
        return null;
    }
}
