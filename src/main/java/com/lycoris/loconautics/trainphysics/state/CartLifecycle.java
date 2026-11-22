package com.lycoris.loconautics.trainphysics.state;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.signal.TrainSignals;
import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CartLifecycle {

    private static final List<CartStateStore> STORES = new ArrayList<>();
    private static final int ROUTE_WATCH_NEAR_TICKS = 40;
    private static final int ROUTE_WATCH_FAR_TICKS = 200;

    private static volatile boolean watching = com.lycoris.loconautics.config.LoconauticsConfig.DEBUG;

    private CartLifecycle() {
    }

    public static void register(CartStateStore... stores) {
        STORES.addAll(List.of(stores));
    }

    public static boolean watching() {
        return watching;
    }

    public static void setWatching(boolean on) {
        watching = on;
    }

    public static void beforeTeleport(SableTrain train) {
        GhostWheelBodies.releaseAll(train);
        SableTrainDriver.forgetBodyState(train);
    }

    public static void fire(SableTrain train, CartEvent event) {
        if (train.car() == null || train.car().subLevelId() == null) {
            return;
        }
        UUID id = train.id();
        UUID subId = train.car().subLevelId();
        String held = String.join(",", holders(id, subId, false, event));
        for (CartStateStore store : STORES) {
            store.forget(train, subId, event);
        }
        if (event.moved()) {
            train.beginPoseSettle(TrainResidency.POSE_SETTLE_TICKS);
        }
        LoconauticsConstants.TRACE.warn("[cartreset] cart={} event={} held={}", id, event, held);
        if (watching) {
            for (String stale : holders(id, subId, true, event)) {
                LoconauticsConstants.LOGGER.warn("[cartstate] stale {} after {}: {}", stale, event,
                        describeOne(stale, id, subId));
            }
        }
        if (event == CartEvent.RELOCATED || event == CartEvent.TELEPORTED) {
            logRoute(train, "at move");
            watchRoute(train, ROUTE_WATCH_NEAR_TICKS);
            watchRoute(train, ROUTE_WATCH_FAR_TICKS);
        }
    }

    public static List<String> describe(SableTrain train) {
        List<String> lines = new ArrayList<>();
        UUID subId = train.car() == null ? null : train.car().subLevelId();
        if (subId == null) {
            return lines;
        }
        for (CartStateStore store : STORES) {
            String text = store.describe(train.id(), subId);
            if (!text.isEmpty()) {
                lines.add(nameOf(store) + ": " + text);
            }
        }
        return lines;
    }

    private static List<String> holders(UUID id, UUID subId, boolean onlyClearing, CartEvent event) {
        List<String> names = new ArrayList<>();
        for (CartStateStore store : STORES) {
            if ((!onlyClearing || store.clears(event)) && !store.describe(id, subId).isEmpty()) {
                names.add(nameOf(store));
            }
        }
        return names;
    }

    private static String describeOne(String name, UUID id, UUID subId) {
        for (CartStateStore store : STORES) {
            if (nameOf(store).equals(name)) {
                return store.describe(id, subId);
            }
        }
        return "";
    }

    private static String nameOf(CartStateStore store) {
        Class<?> owner = store.getClass().getEnclosingClass();
        return (owner == null ? store.getClass() : owner).getSimpleName();
    }

    private static void watchRoute(SableTrain train, int delay) {
        MinecraftServer server = train.level().getServer();
        server.tell(new TickTask(server.getTickCount() + delay, () -> logRoute(train, "after " + delay + " ticks")));
    }

    private static void logRoute(SableTrain train, String when) {
        SableScheduleRuntime runtime = SableScheduleRuntime.get(train.id());
        GlobalStation target = runtime == null ? null : runtime.targetStation();
        LoconauticsConstants.TRACE.warn("[relocreset] cart={} {} target={} runtimeState={} heldReason={} "
                        + "signalHeld={} signalGroups={} stationState={}", train.id(), when,
                target == null ? "none" : target.name, runtime == null ? "none" : runtime.state,
                runtime == null ? "none" : runtime.heldReason(), TrainSignals.isHeld(train.id()),
                TrainSignals.groupsOf(train.id()).size(), train.stationState());
    }
}
