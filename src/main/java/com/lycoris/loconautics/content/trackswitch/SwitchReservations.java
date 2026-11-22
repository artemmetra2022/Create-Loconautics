package com.lycoris.loconautics.content.trackswitch;

import com.lycoris.loconautics.common.railway.PointOverrides;
import com.simibubi.create.content.trains.graph.TrackNode;
import net.createmod.catnip.data.Couple;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Lets a scheduled train set the switch stands in auto mode along the stretch of route ahead of it. */
public final class SwitchReservations {

    /** how many hops of the route are looked at */
    private static final int LOOKAHEAD = 12;

    private SwitchReservations() {
    }

    private static String owner(UUID train) {
        return "train:" + train;
    }

    /** takes the auto junctions ahead on the route, and lets go of the ones that are behind now */
    public static void sync(UUID train, List<Couple<TrackNode>> route) {
        Set<String> wanted = new HashSet<>();
        if (route != null && PointOverrides.hasAuto()) {
            int limit = Math.min(route.size(), LOOKAHEAD);
            for (int i = 0; i < limit; i++) {
                Couple<TrackNode> hop = route.get(i);
                String junction = PointOverrides.nodeId(hop.getFirst().getLocation());
                if (PointOverrides.isAuto(junction)
                        && PointOverrides.reserve(junction, PointOverrides.nodeId(hop.getSecond().getLocation()),
                        owner(train))) {
                    wanted.add(junction);
                }
            }
        }
        PointOverrides.releaseExcept(owner(train), wanted);
    }

    public static void release(UUID train) {
        PointOverrides.releaseAll(owner(train));
    }
}
