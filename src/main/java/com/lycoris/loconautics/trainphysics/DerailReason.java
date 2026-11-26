package com.lycoris.loconautics.trainphysics;

import java.util.Locale;

public enum DerailReason {
    TRACK_MISSING,
    BOGEYS_LOST,
    END_OF_TRACK,
    COUPLING_STRESS,
    OVERTURNED,
    ROLLED_OFF,
    LIFTED_OFF,
    GLUE_REFUSED,
    COLLISION,
    TELEPORT,
    COMMAND;

    public String statusKey() {
        return name().toLowerCase(Locale.ROOT);
    }
}
