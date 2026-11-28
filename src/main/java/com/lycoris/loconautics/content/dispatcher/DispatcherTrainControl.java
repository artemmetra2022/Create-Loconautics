package com.lycoris.loconautics.content.dispatcher;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-side dispatcher brake requests for Loconautics/Sable trains. */
public final class DispatcherTrainControl {

    /** A moderate service-brake demand, expressed as a fraction of the available brake force. */
    public static final double SERVICE_BRAKE_DEMAND = 0.05;

    private static final Set<UUID> HELD_TRAINS = ConcurrentHashMap.newKeySet();

    private DispatcherTrainControl() {
    }

    public static void setHeld(UUID trainId, boolean held) {
        if (trainId == null) return;
        if (held) HELD_TRAINS.add(trainId);
        else HELD_TRAINS.remove(trainId);
    }

    public static boolean isHeld(UUID trainId) {
        return trainId != null && HELD_TRAINS.contains(trainId);
    }

    public static double brakeDemand(UUID trainId) {
        return isHeld(trainId) ? SERVICE_BRAKE_DEMAND : 0.0;
    }

    public static void clear() {
        HELD_TRAINS.clear();
    }
}
