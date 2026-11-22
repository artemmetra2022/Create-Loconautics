package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.trainphysics.DerailReason;
import com.lycoris.loconautics.common.train.TrainStatusChat;
import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.trainphysics.glue.CarriageRoll;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.state.CartEvent;
import com.lycoris.loconautics.trainphysics.state.CartStateStore;
import com.lycoris.loconautics.trainphysics.SableTrain.Car;
import com.lycoris.loconautics.trainphysics.station.TrainStations;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import java.util.Set;
import java.util.UUID;

public final class TrainDerailment {
    private TrainDerailment() {
    }

    public static final CartStateStore STORE = new CartStateStore() {
        @Override
        public void forget(SableTrain train, UUID subId, CartEvent event) {
            if (!clears(event)) {
                return;
            }
            RAILS_GONE.remove(train.id());
            TILT_OVER.remove(train.id());
            if (subId != null) {
                BUFFER_HELD.remove(subId);
            }
        }

        @Override
        public void retain(Set<UUID> liveTrains, Set<UUID> liveSubs) {
            TrainDerailment.retain(liveTrains);
            BUFFER_HELD.keySet().retainAll(liveSubs);
        }

        @Override
        public String describe(UUID trainId, UUID subId) {
            return RAILS_GONE.containsKey(trainId) || TILT_OVER.containsKey(trainId)
                    ? "railsGone=" + RAILS_GONE.get(trainId) + " tiltOver=" + TILT_OVER.get(trainId) : "";
        }

        @Override
        public boolean clears(CartEvent event) {
            return event.moved() || event == CartEvent.REERAILED || event == CartEvent.RESEATED_IN_PLACE
                    || event == CartEvent.REMOVED;
        }
    };

    static void retain(java.util.Set<UUID> live) {
        RAILS_GONE.keySet().retainAll(live);
        TILT_OVER.keySet().retainAll(live);
    }

    static void check(SableTrain train) {
        if (train.isPhysics()) {
            return;
        }
        if (train.isPoseSettling()) {
            if (train.isDerailed()) {
                com.lycoris.loconautics.trainphysics.spawn.BogeyEditTrace.phase(train, "posesettle", "");
            }
            return;
        }
        if (train.isDerailed()) {
            if (ExternalForceDerail.holdsRerail(train)) {
                com.lycoris.loconautics.trainphysics.spawn.BogeyEditTrace.phase(train, "regrip",
                        "a push in the last 40 ticks blocks every rerail");
                return;
            }
            if (com.lycoris.loconautics.trainphysics.spawn.RerailApproach.tryAutoRerail(train)) {
                TrainStatusChat.clearPartialDerail(train);
            }
            return;
        }
        // a car with one bogey loose never went through the full derail path, so nothing was putting it back
        if (train.hasOffTrackBogey() && !ExternalForceDerail.holdsRerail(train)
                && SableTrainSpawner.tryRerailOffTrackBogeys(train)
                && !train.hasOffTrackBogey()) {
            TrainStatusChat.clearPartialDerail(train);
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null) {
            return;
        }
        Car car = train.car();
        if (car.subLevelId() == null
                || !(container.getSubLevel(car.subLevelId()) instanceof ServerSubLevel sub) || sub.isRemoved()) {
            return;
        }
        if (!SubLevelBlocks.hasBlock(sub, AbstractBogeyBlock.class)) {
            train.setDerailed(true, DerailReason.BOGEYS_LOST);
            LoconauticsConstants.LOGGER.info(
                    "[sabletrain] car {} lost all its bogeys, released from the rail (now a free sub level)",
                    train.id());
            return;
        }
        if (!checkRailsGone(train, sub)) {
            return;
        }
        if (checkAttitudeDerail(train)) {
            return;
        }
        if (ExternalForceDerail.check(train, sub)) {
            return;
        }
        if (LoconauticsConfig.DERAIL_AT_TRACK_END.get() && SableTrainDriver.DEAD_END.contains(sub.getUniqueId())
                && (runOffEnd(train, car) || train.hasOffTrackBogey())) {
            return;
        }
        if (!TrainPropulsion.isMonorail(train) && checkCouplingStress(train, sub)) {
            return;
        }
    }

    static final int RAILS_GONE_GRACE_TICKS = 40;

    static final java.util.Map<UUID, Integer> RAILS_GONE = new java.util.HashMap<>();
    // keyed by body, the sign of the speed that carried the car into the buffer, the drive may only pull it back out
    static final java.util.Map<UUID, Integer> BUFFER_HELD = new java.util.HashMap<>();

    // false means stop here
    static boolean checkRailsGone(SableTrain train, ServerSubLevel sub) {
        if (TrainRailAdvance.railsValid(train)) {
            RAILS_GONE.remove(train.id());
            return true;
        }
        if (SableTrainSpawner.reseatOnRail(train, sub)) {
            RAILS_GONE.remove(train.id());
            return true;
        }
        // the grace is for a graph rebuilt under track that is still there, broken track releases the car at once
        int missed = SableTrainSpawner.trackUnderRailedBogey(train, sub)
                ? RAILS_GONE.merge(train.id(), 1, Integer::sum) : RAILS_GONE_GRACE_TICKS;
        if (missed == 1 || missed == RAILS_GONE_GRACE_TICKS) {
            StringBuilder bogeys = new StringBuilder();
            for (SableTrain.Bogey bogey : train.car().bogeys()) {
                bogeys.append(' ').append(bogey.localPos().toShortString()).append(" offTrack=")
                        .append(train.isBogeyOffTrack(bogey.localPos())).append(" edgesValid=")
                        .append(bogey.rail().edgesValid());
            }
            LoconauticsConstants.TRACE.warn("[rerailwire] rails gone car {} missed {} of {}{}", train.id(), missed,
                    RAILS_GONE_GRACE_TICKS, bogeys);
        }
        if (missed < RAILS_GONE_GRACE_TICKS) {
            return false;
        }
        RAILS_GONE.remove(train.id());
        train.setTargetSpeed(0.0);
        train.setDerailed(true, DerailReason.TRACK_MISSING);
        LoconauticsConstants.LOGGER.info(
                "[sabletrain] car {} has had no rail under it for {} ticks, released from the graph", train.id(),
                RAILS_GONE_GRACE_TICKS);
        return false;
    }

    // an overturned rail has to hold the angle rather than catch one bad frame while a weld or a remap lands
    static final int TILT_DERAIL_TICKS = 5;

    static final java.util.Map<UUID, Integer> TILT_OVER = new java.util.HashMap<>();

    static boolean checkAttitudeDerail(SableTrain train) {
        if (CarriageRoll.isRolling(train.car().subLevelId())) {
            TILT_OVER.remove(train.id());
            return false;
        }
        double limit = LoconauticsConfig.DERAIL_MAX_TILT_DEGREES.get();
        double tilt = Math.toDegrees(train.car().carriage().hostTilt());
        if (limit >= 180.0 || tilt <= limit) {
            TILT_OVER.remove(train.id());
            return false;
        }
        if (TILT_OVER.merge(train.id(), 1, Integer::sum) < TILT_DERAIL_TICKS) {
            return false;
        }
        TILT_OVER.remove(train.id());
        train.setTargetSpeed(0.0);
        train.setDerailed(true, DerailReason.OVERTURNED);
        LoconauticsConstants.LOGGER.info("[sabletrain] car {} fell off a rail leaning {} deg, past {}",
                train.id(), SableTrainDriver.f(tilt), SableTrainDriver.f(limit));
        return true;
    }

    static boolean runOffEnd(SableTrain train, Car car) {
        boolean any = false;
        boolean full = false;
        for (SableTrain.Bogey bogey : car.bogeys()) {
            if (!train.isBogeyOffTrack(bogey.localPos()) && bogey.rail().reachedEnd()) {
                any = true;
                full |= derailBogey(train, car, bogey, DerailReason.END_OF_TRACK);
            }
        }
        if (full) {
            LoconauticsConstants.LOGGER.info("[sabletrain] car {} ran every bogey off the track end at {} b/s",
                    train.id(), SableTrainDriver.f(train.speed() * 20.0));
        }
        return any;
    }

    static boolean derailBogey(SableTrain train, Car car, SableTrain.Bogey bogey, DerailReason reason) {
        train.setDerailReason(reason);
        if (car.bogeys().size() < 2 || bogey == null) {
            train.setDerailed(true, reason);
            return true;
        }
        if (train.markBogeyOffTrack(bogey.localPos())) {
            TrainStatusChat.partialDerail(train);
            LoconauticsConstants.LOGGER.info("[sabletrain] car {} bogey {} derailed ({}), car pivots on the rest",
                    train.id(), bogey.localPos().toShortString(), reason);
        }
        if (train.offTrackBogeyCount() < car.bogeys().size()) {
            return false;
        }
        train.setDerailed(true, reason);
        return true;
    }

    static SableTrain.Bogey nextRailedBogey(SableTrain train) {
        java.util.List<SableTrain.Bogey> onRail = new java.util.ArrayList<>();
        for (SableTrain.Bogey bogey : train.car().bogeys()) {
            if (!train.isBogeyOffTrack(bogey.localPos())) {
                onRail.add(bogey);
            }
        }
        return onRail.isEmpty() ? null : TrainStations.frontBogey(train, onRail);
    }

    // create gives up at 4, we shed the one bogey being dragged apart well before that
    static final double MAX_COUPLING_STRESS = 1.5;

    static boolean checkCouplingStress(SableTrain train, ServerSubLevel sub) {
        TrainRailAdvance.Stress stress = TrainRailAdvance.takeMaxStress(sub.getUniqueId());
        if (stress == null || stress.amount() <= MAX_COUPLING_STRESS) {
            return false;
        }
        SableTrain.Bogey culprit = null;
        for (SableTrain.Bogey bogey : train.car().bogeys()) {
            if (bogey.localPos().equals(stress.bogey()) && !train.isBogeyOffTrack(bogey.localPos())) {
                culprit = bogey;
            }
        }
        if (culprit == null) {
            return false;
        }
        if (!derailBogey(train, train.car(), culprit, DerailReason.COUPLING_STRESS)) {
            TrainStatusChat.highStress(train);
        }
        LoconauticsConstants.LOGGER.info(
                "[sabletrain] car {} bogey {} derailed, coupling stress {} > {}",
                train.id(), culprit.localPos().toShortString(), SableTrainDriver.f(stress.amount()), SableTrainDriver.f(MAX_COUPLING_STRESS));
        return true;
    }
}
