package com.lycoris.loconautics.content.dispatcher;

import java.util.UUID;

/** A small, immutable server snapshot; no client code holds live train objects. */
public record DispatcherTrainEntry(
        UUID id,
        String type,
        String name,
        String dimension,
        int x,
        int y,
        int z,
        double speed,
        int state,
        int occupiedSections,
        int reservedSections,
        boolean dispatcherHeld) {

    public static final int STOPPED = 0;
    public static final int MOVING = 1;
    public static final int WAITING = 2;
    public static final int DERAILED = 3;
    public static final int DISPATCH_HELD = 4;
}
