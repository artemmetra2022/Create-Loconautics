package com.lycoris.loconautics.trainphysics.spawn;

import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.drive.ExternalForceDerail;
import com.lycoris.loconautics.common.train.TrainStatusChat;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.state.CartEvent;
import com.lycoris.loconautics.trainphysics.state.CartStateStore;
import com.lycoris.loconautics.trainphysics.glue.CarriageRoll;
import com.lycoris.loconautics.trainphysics.glue.RailGauge;
import com.lycoris.loconautics.trainphysics.glue.RailSnap;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public final class RerailBalance {

    private RerailBalance() {
    }

    public static final CartStateStore STORE = new CartStateStore() {
        @Override
        public void forget(SableTrain train, UUID subId, CartEvent event) {
            if (clears(event)) {
                RerailBalance.forget(train.id());
            } else if (event == CartEvent.DERAILED && train.derailedByLift()) {
                ServerSubLevel sub = SubLevelData.resolve(train.level(), subId);
                if (sub != null) {
                    noteRolledOff(sub, train);
                }
            }
        }

        @Override
        public void retain(Set<UUID> liveTrains, Set<UUID> liveSubs) {
            RerailBalance.retain(liveTrains);
        }

        @Override
        public String describe(UUID trainId, UUID subId) {
            return TOLD.containsKey(trainId) || LOOPING.containsKey(trainId) || AUTO_RERAILED.containsKey(trainId)
                    || LOOPS.containsKey(trainId)
                    ? "told=" + TOLD.containsKey(trainId) + " looping=" + LOOPING.containsKey(trainId)
                    + " autoRerailed=" + AUTO_RERAILED.containsKey(trainId) + " loops=" + LOOPS.get(trainId) : "";
        }

        @Override
        public boolean clears(CartEvent event) {
            return event.moved() || event == CartEvent.REMOVED;
        }
    };

    private static final long LOOP_WINDOW_TICKS = 100L;
    private static final int LOOP_LIMIT = 2;
    private static final double MASS_CHANGE = 0.05;
    private static final double CENTER_CHANGE = 0.1;

    private record Snapshot(double mass, Vector3d center) {
        boolean differsFrom(double otherMass, Vector3d otherCenter) {
            return Math.abs(otherMass - mass) > MASS_CHANGE * mass || otherCenter.distance(center) > CENTER_CHANGE;
        }
    }

    private record Balance(double offset, double height, double lift) {
    }

    private static final Map<UUID, Snapshot> TOLD = new ConcurrentHashMap<>();
    private static final Map<UUID, Snapshot> LOOPING = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> AUTO_RERAILED = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LOOPS = new ConcurrentHashMap<>();

    static boolean refuses(ServerLevel level, ServerSubLevel sub, SableTrain train) {
        MassData md = sub.getMassTracker();
        if (md == null || md.isInvalid()) {
            return false;
        }
        UUID id = train.id();
        double mass = md.getMass();
        Vector3d center = new Vector3d(md.getCenterOfMass());
        Snapshot looping = LOOPING.get(id);
        if (looping != null) {
            if (!looping.differsFrom(mass, center)) {
                BogeyEditTrace.refuse(train, "rerail loop");
                return true;
            }
            forget(id);
        }
        Balance balance = balanceAt(level, sub, train, md);
        if (balance == null || balance.lift() < CarriageRoll.holdAngle()) {
            TOLD.remove(id);
            return false;
        }
        BogeyEditTrace.refuse(train, "too_lopsided");
        Snapshot told = TOLD.get(id);
        if (told == null || told.differsFrom(mass, center)) {
            TOLD.put(id, new Snapshot(mass, center));
            TrainStatusChat.tooLopsided(train);
        }
        return true;
    }

    private static Balance balanceAt(ServerLevel level, ServerSubLevel sub, SableTrain train, MassData md) {
        SableTrain.Car car = train.car();
        RailCarriage carriage = car == null ? null : car.carriage();
        if (carriage == null || carriage.isOnMonorail() || carriage.trackMaterial() == null) {
            return null;
        }
        Vector3d anchor = RailSnap.glueAnchorLocal(sub, car);
        Vector3d forward0 = carriage.referenceForward();
        Vector3d up0 = carriage.referenceUp();
        Vec3 normal = carriage.upNormal();
        Vec3 along = carriage.forward();
        if (anchor == null || forward0 == null || up0 == null || normal == null || along == null
                || along.lengthSqr() < 1.0e-9) {
            return null;
        }
        Vector3d tangent = new Vector3d(along.x, along.y, along.z).normalize();
        if (tangent.dot(SubLevelData.directionOf(sub, forward0)) < 0.0) {
            tangent.negate();
        }
        Vector3d up = new Vector3d(normal.x, normal.y, normal.z);
        up.sub(new Vector3d(tangent).mul(up.dot(tangent)));
        if (up.lengthSquared() < 1.0e-9) {
            return null;
        }
        up.normalize();
        Vector3d lateral = new Vector3d(tangent).cross(up).normalize();
        Vector3d gravity = new Vector3d(DimensionPhysicsData.getGravity(level));
        if (!CarriageRoll.hasGravity(-gravity.dot(up))) {
            return null;
        }
        Vector3d lateral0 = new Vector3d(forward0).cross(up0).normalize();
        Vector3d relative = new Vector3d(md.getCenterOfMass()).sub(anchor).fma(CarriageRoll.SEAT_DROP, up0);
        double offset = relative.dot(lateral0);
        double height = relative.dot(up0);
        double half = RailGauge.halfGauge(carriage.trackMaterial());
        return new Balance(offset, height, CarriageRoll.predictedLift(gravity, up, lateral, half, offset, height));
    }

    static void noteAutoRerail(SableTrain train, long gameTime) {
        AUTO_RERAILED.put(train.id(), gameTime);
    }

    // the second roll off soon after an auto rerail ends the auto rerails
    private static void noteRolledOff(ServerSubLevel sub, SableTrain train) {
        UUID id = train.id();
        long now = sub.getLevel().getGameTime();
        Long rerailedAt = AUTO_RERAILED.remove(id);
        if (rerailedAt == null || now - rerailedAt > LOOP_WINDOW_TICKS) {
            LOOPS.remove(id);
            return;
        }
        long staff = ExternalForceDerail.staffAge(sub, train);
        if (staff <= ExternalForceDerail.STAFF_GRACE_TICKS) {
            LoconauticsConstants.TRACE.warn("[rerailwire] roll off not counted car {}, physics staff held it {} ticks ago",
                    id, staff);
            return;
        }
        int loops = LOOPS.merge(id, 1, Integer::sum);
        if (loops < LOOP_LIMIT) {
            return;
        }
        MassData md = sub.getMassTracker();
        if (md != null && !md.isInvalid()) {
            LOOPING.put(id, new Snapshot(md.getMass(), new Vector3d(md.getCenterOfMass())));
        }
        LoconauticsConstants.TRACE.warn("[rerailwire] loop guard tripped car {} after {} roll offs, {} ticks after the rerail, staff age {}",
                id, loops, now - rerailedAt, staff);
        TrainStatusChat.tooLopsided(train);
        RerailApproach.giveUp(train, now);
    }

    public static void forget(UUID id) {
        TOLD.remove(id);
        LOOPING.remove(id);
        AUTO_RERAILED.remove(id);
        LOOPS.remove(id);
    }

    public static void retain(Set<UUID> live) {
        TOLD.keySet().retainAll(live);
        LOOPING.keySet().retainAll(live);
        AUTO_RERAILED.keySet().retainAll(live);
        LOOPS.keySet().retainAll(live);
    }

    public static void clearAll() {
        TOLD.clear();
        LOOPING.clear();
        AUTO_RERAILED.clear();
        LOOPS.clear();
    }
}
