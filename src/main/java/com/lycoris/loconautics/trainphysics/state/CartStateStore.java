package com.lycoris.loconautics.trainphysics.state;

import com.lycoris.loconautics.trainphysics.SableTrain;

import java.util.Set;
import java.util.UUID;

public interface CartStateStore {

    void forget(SableTrain train, UUID subId, CartEvent event);

    void retain(Set<UUID> liveTrains, Set<UUID> liveSubs);

    String describe(UUID trainId, UUID subId);

    default boolean clears(CartEvent event) {
        return true;
    }
}
