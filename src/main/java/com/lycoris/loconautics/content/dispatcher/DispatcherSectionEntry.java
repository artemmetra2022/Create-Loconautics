package com.lycoris.loconautics.content.dispatcher;

import java.util.UUID;

/** One Create signal-edge group / Loconautics block section for the dispatcher list. */
public record DispatcherSectionEntry(
        UUID id,
        int state,
        String trains,
        String dimension,
        int x,
        int y,
        int z,
        boolean held) {

    public static final int CLEAR = 0;
    public static final int RESERVED = 1;
    public static final int OCCUPIED = 2;
    public static final int CONFLICT = 3;
}
