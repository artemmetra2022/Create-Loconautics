package com.lycoris.loconautics.trainphysics.state;

public enum CartEvent {
    RELOCATED, REERAILED, RESEATED_IN_PLACE, TELEPORTED, HANDED_OVER, EMBODIED, GHOSTED, SPLIT, MERGED, DERAILED,
    REMOVED;

    public boolean moved() {
        return this == RELOCATED || this == TELEPORTED || this == HANDED_OVER || this == EMBODIED
                || this == SPLIT || this == MERGED;
    }
}
