package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.trainphysics.DerailReason;
import com.lycoris.loconautics.trainphysics.glue.CarriageRoll;
import com.lycoris.loconautics.trainphysics.glue.RailGlue;
import com.lycoris.loconautics.trainphysics.debug.force.ForceSource;
import com.lycoris.loconautics.trainphysics.debug.force.ForceTrace;
import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.mixin.sable.physicsstaff.PhysicsStaffDragSessionAccessor;
import com.lycoris.loconautics.mixin.sable.physicsstaff.PhysicsStaffServerHandlerAccessor;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.state.CartEvent;
import com.lycoris.loconautics.trainphysics.state.CartStateStore;
import com.lycoris.loconautics.trainphysics.SableTrain.Car;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.spawn.SubLevelMergeWatcher;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.lycoris.loconautics.foundation.LogFormat.shortId;
import static com.lycoris.loconautics.foundation.math.VecMath.perpendicularUp;

public final class ExternalForceDerail {


    private ExternalForceDerail() {
    }

    public static final CartStateStore STORE = new CartStateStore() {
        @Override
        public void forget(SableTrain train, UUID subId, CartEvent event) {
            if (event == CartEvent.DERAILED && train.derailedByLift()) {
                holdAfterRoll(train);
                return;
            }
            if (!clears(event)) {
                return;
            }
            PEAK.remove(subId);
            PLUNGER_PEAK.remove(subId);
            PLUNGER_PULL.remove(subId);
            LIFTED.remove(train.id());
            PLUNGER_LIFTED.remove(train.id());
            REGRIP.remove(train.id());
            RELEASED.remove(train.id());
        }

        @Override
        public void retain(Set<UUID> liveTrains, Set<UUID> liveSubs) {
            ExternalForceDerail.retain(liveTrains, liveSubs);
        }

        @Override
        public String describe(UUID trainId, UUID subId) {
            StringBuilder out = new StringBuilder();
            note(out, "peak", PEAK.containsKey(subId));
            note(out, "plungerPeak", PLUNGER_PEAK.containsKey(subId));
            note(out, "plungerPull", PLUNGER_PULL.containsKey(subId));
            note(out, "lifted", LIFTED.containsKey(trainId));
            note(out, "plungerLifted", PLUNGER_LIFTED.containsKey(trainId));
            note(out, "regrip", REGRIP.containsKey(trainId));
            note(out, "released", RELEASED.containsKey(trainId));
            return out.toString().trim();
        }

        private void note(StringBuilder out, String name, boolean held) {
            if (held) {
                out.append(name).append(' ');
            }
        }

        @Override
        public boolean clears(CartEvent event) {
            return event.moved() || event == CartEvent.REERAILED || event == CartEvent.RESEATED_IN_PLACE
                    || event == CartEvent.REMOVED;
        }
    };

    private static final int LIFT_TICKS = 5;
    private static final double NOISE = 1.0e-6;
    private static final long STALE_TICKS = 1;
    private static final int REGRIP_TICKS = 40;
    private static final long TRACE_TICKS = 10;
    public static final long STAFF_GRACE_TICKS = 20;

    private record Push(long gameTime, double lift) {

        Push worse(long now, double otherLift) {
            return gameTime == now ? new Push(now, Math.max(lift, otherLift)) : new Push(now, otherLift);
        }
    }

    private static final double PLUNGER_RATIO = 1.0;

    private record Pull(long gameTime, Vector3d accel) {
    }

    private static final Map<UUID, Pull> PLUNGER_PULL = new HashMap<>();
    private static final Map<UUID, Push> PLUNGER_PEAK = new HashMap<>();
    private static final Map<UUID, Push> PEAK = new HashMap<>();
    private static final Map<UUID, Integer> LIFTED = new HashMap<>();
    private static final Map<UUID, Integer> PLUNGER_LIFTED = new HashMap<>();
    private static final Map<UUID, Long> REGRIP = new HashMap<>();
    private static final Map<UUID, Long> TRACED = new HashMap<>();
    private static final Map<UUID, Release> RELEASED = new HashMap<>();
    private static final Map<UUID, Long> STAFF_SEEN = new HashMap<>();

    private record Release(long until, Vector3d linear, Vector3d angular) {
    }
    private static final int RELEASE_TICKS = 10;
    private static final double RELEASE_SPEED = 4.0;
    private static final double RELEASE_SPIN = 2.0;

    public static void notePlunger(ServerSubLevel sub, Vector3d localImpulse, double dt) {
        MassData mass = sub.getMassTracker();
        if (dt <= 0.0 || mass == null || mass.isInvalid() || mass.getMass() <= 0.0) {
            return;
        }
        Vector3d accel = sub.logicalPose().transformNormal(localImpulse, new Vector3d()).mul(1.0 / (mass.getMass() * dt));
        PLUNGER_PULL.merge(sub.getUniqueId(), new Pull(sub.getLevel().getGameTime(), accel),
                (old, fresh) -> old.gameTime() == fresh.gameTime() ? new Pull(fresh.gameTime(), old.accel().add(fresh.accel(), new Vector3d())) : fresh);
    }

    private static void samplePlunger(ServerSubLevel sub, SableTrain train, Vector3d tangent, long now) {
        Pull pull = PLUNGER_PULL.get(sub.getUniqueId());
        if (pull == null || now - pull.gameTime() > STALE_TICKS) {
            return;
        }
        double along = pull.accel().dot(tangent);
        double across = new Vector3d(pull.accel()).fma(-along, tangent).length();
        if (throttle(sub, now)) {
            LoconauticsConstants.LOGGER.info("[plungerpull] car={} accel={} b/s2 along={} across={} limit={} derailed={}",
                    shortId(train.id()), SableTrainDriver.f(pull.accel().length()), SableTrainDriver.f(along),
                    SableTrainDriver.f(across), SableTrainDriver.f(DimensionPhysicsData.getGravity(sub.getLevel()).length() * PLUNGER_RATIO),
                    train.isDerailed());
        }
        Push seen = PLUNGER_PEAK.get(sub.getUniqueId());
        PLUNGER_PEAK.put(sub.getUniqueId(), seen == null ? new Push(now, across) : seen.worse(now, across));
    }

    static void sample(ServerSubLevel sub, Car car, SableTrain train, double dt) {
        long now = sub.getLevel().getGameTime();
        boolean dragged = staffDrags(sub);
        if (dragged || staffLocked(sub)) {
            STAFF_SEEN.put(train.id(), now);
        }
        if (!LoconauticsConfig.DERAIL_ON_EXTERNAL_FORCE.get() || dt <= 0.0 || TrainPropulsion.isMonorail(train)) {
            return;
        }
        Vector3d[] ref = TrainRailAdvance.bogeyReference(car);
        Vector3d tangent = ref != null ? ref[1] : SableTrainDriver.railTangent(car);
        if (tangent != null) {
            samplePlunger(sub, train, tangent, now);
        }
        if (CarriageRoll.isRolling(sub.getUniqueId()) && (!dragged || liftRatio(sub) <= 0.0)) {
            PEAK.remove(sub.getUniqueId());
            traceSkip(sub, train, "rolling car, the wheel hinge owns the lift", now);
            return;
        }
        if (SubLevelMergeWatcher.merging(sub.getLevel(), sub.getUniqueId())) {
            PEAK.remove(sub.getUniqueId());
            traceSkip(sub, train, "merging glue pull", now);
            return;
        }
        if (staffHolds(sub) && !dragged) {
            clearLiftCount(train.id());
            PEAK.remove(sub.getUniqueId());
            traceSkip(sub, train, "held by the physics staff", now);
            return;
        }
        MassData mass = sub.getMassTracker();
        if (mass == null || mass.isInvalid() || mass.getMass() <= 0.0) {
            traceSkip(sub, train, "no mass", now);
            return;
        }
        if (tangent == null) {
            traceSkip(sub, train, "no rail tangent", now);
            return;
        }
        Vector3d up = perpendicularUp(tangent);
        Vector3d where = ref != null ? ref[0] : SubLevelData.position(sub);

        double lift = 0.0;
        Vector3d push = queuedPush(sub);
        if (push != null) {
            Vector3d accel = new Vector3d(push).mul(1.0 / (mass.getMass() * dt));
            lift = accel.dot(up);
            ForceTrace.impulse(sub, ForceSource.EXTERNAL, push, where,
                    "queued force groups, lift " + SableTrainDriver.f(lift) + " b/s2");
        }

        Vector3d held = train.hasOffTrackBogey() ? null : jointAccel(sub, tangent, up, mass.getMass(), dt);
        double pull = held == null ? 0.0 : held.dot(up);
        lift = Math.max(lift, pull);
        trace(sub, train, up, mass.getMass(), pull, lift, now, dragged, push != null);
        if (lift == 0.0) {
            return;
        }

        double gravity = DimensionPhysicsData.getGravity(sub.getLevel()).length();
        double ratio = liftRatio(sub);
        if (ratio > 0.0 && lift > gravity * ratio) {
            REGRIP.put(train.id(), now + REGRIP_TICKS);
        }
        Push seen = PEAK.get(sub.getUniqueId());
        PEAK.put(sub.getUniqueId(), seen == null ? new Push(now, lift) : seen.worse(now, lift));
    }

    private static Vector3d queuedPush(ServerSubLevel sub) {
        Map<ForceGroup, QueuedForceGroup> groups = sub.getQueuedForceGroups();
        if (groups == null || groups.isEmpty()) {
            return null;
        }
        Vector3d local = new Vector3d();
        for (QueuedForceGroup group : groups.values()) {
            local.add(group.getForceTotal().getLocalForce());
        }
        if (local.lengthSquared() < NOISE) {
            return null;
        }
        return sub.logicalPose().transformNormal(local, new Vector3d());
    }

    private static Vector3d jointAccel(ServerSubLevel sub, Vector3d tangent, Vector3d up, double mass, double dt) {
        GenericConstraintHandle glue = RailGlue.CONSTRAINTS.get(sub.getUniqueId());
        if (glue == null || !glue.isValid()) {
            return null;
        }
        Vector3d linear = new Vector3d();
        glue.getJointImpulses(linear, new Vector3d());
        // rapier keeps the impulse in the joint frame, the basis gluebody built out of these same three axes
        Vector3d lateral = new Vector3d(tangent).cross(up).normalize();
        return RailGlue.basisQuaternion(tangent, up, lateral).transform(linear, new Vector3d())
                .mul(1.0 / (mass * dt));
    }

    private static void holdAfterRoll(SableTrain train) {
        if (train.level() == null) {
            return;
        }
        long now = train.level().getGameTime();
        REGRIP.put(train.id(), now + REGRIP_TICKS);
    }

    // only what the rope stored against the glue is taken off, the speed the car already had stays
    public static void dampRelease(ServerSubLevel sub, SableTrain train) {
        Release release = RELEASED.get(train.id());
        if (release == null) {
            return;
        }
        if (sub.getLevel().getGameTime() > release.until()) {
            RELEASED.remove(train.id());
            return;
        }
        RigidBodyHandle handle = RigidBodyHandle.of(sub);
        if (handle == null) {
            return;
        }
        Vector3d slowLinear = overspill(handle.getLinearVelocity(new Vector3d()).sub(release.linear()), RELEASE_SPEED);
        Vector3d slowAngular = overspill(handle.getAngularVelocity(new Vector3d()).sub(release.angular()), RELEASE_SPIN);
        if (slowLinear.lengthSquared() > NOISE || slowAngular.lengthSquared() > NOISE) {
            handle.addLinearAndAngularVelocity(slowLinear, slowAngular);
        }
    }

    private static Vector3d overspill(Vector3d velocity, double limit) {
        double speed = velocity.length();
        return speed <= limit ? new Vector3d() : velocity.mul(limit / speed - 1.0);
    }

    public static boolean holdsLift(ServerSubLevel sub, SableTrain train) {
        return PEAK.containsKey(sub.getUniqueId()) || LIFTED.containsKey(train.id());
    }

    // a rolling car leaves its height free, so while the staff drags it the guide holds it down and the lift guard has a force to read.
    // with the lift guard off it still holds, or the drag alone lifts the wheels past the flange and the roll check throws the car off
    static boolean holdDownForStaff(ServerSubLevel sub) {
        return staffDrags(sub);
    }

    static boolean staffDrags(ServerSubLevel sub) {
        if (!(sub.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        try {
            PhysicsStaffServerHandler handler = PhysicsStaffServerHandler.get(level);
            for (Object session : ((PhysicsStaffServerHandlerAccessor) handler).loconautics$getDraggingSessions().values()) {
                if (((PhysicsStaffDragSessionAccessor) session).loconautics$getSubLevel() == sub) {
                    return true;
                }
            }
            return false;
        } catch (NoClassDefFoundError ignored) {
            return false;
        }
    }

    public static boolean staffLocked(ServerSubLevel sub) {
        if (!(sub.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        try {
            return PhysicsStaffServerHandler.get(level).isLocked(sub);
        } catch (NoClassDefFoundError ignored) {
            return false;
        }
    }

    public static long staffAge(ServerSubLevel sub, SableTrain train) {
        if (staffHolds(sub)) {
            return 0L;
        }
        Long seen = STAFF_SEEN.get(train.id());
        return seen == null ? Long.MAX_VALUE : sub.getLevel().getGameTime() - seen;
    }

    public static boolean staffHolds(ServerSubLevel sub) {
        if (!(sub.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        try {
            return PhysicsStaffServerHandler.get(level).isLocked(sub) || staffDrags(sub);
        } catch (NoClassDefFoundError ignored) {
            return false;
        }
    }

    // islocked needs a live handle, an unloaded body has none
    public static boolean staffLockedById(ServerLevel level, Collection<UUID> ids) {
        try {
            Map<UUID, Object> locks = ((PhysicsStaffServerHandlerAccessor) PhysicsStaffServerHandler.get(level)).loconautics$getLocks();
            for (UUID id : ids) {
                if (locks.containsKey(id)) {
                    return true;
                }
            }
            return false;
        } catch (NoClassDefFoundError ignored) {
            return false;
        }
    }

    static void tracePinned(ServerSubLevel sub, SableTrain train) {
        traceSkip(sub, train, "pinned, not glued", sub.getLevel().getGameTime());
    }

    private static void traceSkip(ServerSubLevel sub, SableTrain train, String why, long now) {
        if (!ForceTrace.enabled() || !throttle(sub, now)) {
            return;
        }
        LoconauticsConstants.LOGGER.info("[extforce] car={} not sampled, {}", shortId(train.id()), why);
    }

    private static boolean throttle(ServerSubLevel sub, long now) {
        Long last = TRACED.get(sub.getUniqueId());
        if (last != null && now - last < TRACE_TICKS) {
            return false;
        }
        TRACED.put(sub.getUniqueId(), now);
        return true;
    }

    private static void trace(ServerSubLevel sub, SableTrain train, Vector3d up, double mass,
                              double pull, double lift, long now,
                              boolean dragged, boolean queued) {
        if (!ForceTrace.enabled() || !throttle(sub, now)) {
            return;
        }
        RigidBodyHandle handle = RigidBodyHandle.of(sub);
        double vUp = handle == null ? Double.NaN : handle.getLinearVelocity(new Vector3d()).dot(up);
        LoconauticsConstants.LOGGER.info(
                "[extforce] car={} glue={} offTrack={} mass={} vUp={} lift={} railJoint={} b/s2 staffDrag={} queuedForce={}",
                shortId(train.id()), RailGlue.glueModeOf(sub), train.hasOffTrackBogey(),
                SableTrainDriver.f(mass), SableTrainDriver.f(vUp), SableTrainDriver.f(lift),
                SableTrainDriver.f(pull), dragged, queued);
    }

    public static boolean holdsRerail(SableTrain train) {
        Long until = REGRIP.get(train.id());
        return until != null && train.level() != null && train.level().getGameTime() < until;
    }

    static boolean check(SableTrain train, ServerSubLevel sub) {
        Push plunger = PLUNGER_PEAK.remove(sub.getUniqueId());
        if (plunger == null || TrainPropulsion.isMonorail(train)
                || sub.getLevel().getGameTime() - plunger.gameTime() > STALE_TICKS) {
            PLUNGER_LIFTED.remove(train.id());
        } else if (checkLift(PLUNGER_LIFTED, train, plunger,
                DimensionPhysicsData.getGravity(sub.getLevel()).length() * PLUNGER_RATIO)) {
            PEAK.remove(sub.getUniqueId());
            noteRelease(sub, train);
            return true;
        }
        Push push = PEAK.remove(sub.getUniqueId());
        if (push == null || TrainPropulsion.isMonorail(train)
                || sub.getLevel().getGameTime() - push.gameTime() > STALE_TICKS) {
            LIFTED.remove(train.id());
            return false;
        }
        double ratio = liftRatio(sub);
        if (ratio <= 0.0) {
            LIFTED.remove(train.id());
            return false;
        }
        double gravity = DimensionPhysicsData.getGravity(sub.getLevel()).length();
        if (!checkLift(LIFTED, train, push, gravity * ratio)) {
            return false;
        }
        noteRelease(sub, train);
        return true;
    }

    private static void noteRelease(ServerSubLevel sub, SableTrain train) {
        RigidBodyHandle handle = RigidBodyHandle.of(sub);
        if (handle == null) {
            return;
        }
        RELEASED.put(train.id(), new Release(sub.getLevel().getGameTime() + RELEASE_TICKS,
                handle.getLinearVelocity(new Vector3d()), handle.getAngularVelocity(new Vector3d())));
    }

    private static double liftRatio(ServerSubLevel sub) {
        return staffDrags(sub) ? LoconauticsConfig.DERAIL_STAFF_LIFT_RATIO.get()
                : LoconauticsConfig.DERAIL_EXTERNAL_LIFT_RATIO.get();
    }

    private static boolean checkLift(Map<UUID, Integer> counter, SableTrain train, Push push, double limit) {
        if (push.lift() <= limit) {
            counter.remove(train.id());
            return false;
        }
        if (counter.merge(train.id(), 1, Integer::sum) < LIFT_TICKS) {
            return false;
        }
        clearLiftCount(train.id());
        train.setDerailed(true, DerailReason.LIFTED_OFF);
        LoconauticsConstants.LOGGER.info("[sabletrain] car {} was lifted off the rail, {} b/s2 up against {}",
                train.id(), SableTrainDriver.f(push.lift()), SableTrainDriver.f(limit));
        return true;
    }

    private static void clearLiftCount(UUID trainId) {
        LIFTED.remove(trainId);
        PLUNGER_LIFTED.remove(trainId);
    }

    static void retain(Set<UUID> liveTrains, Set<UUID> liveSubLevels) {
        PEAK.keySet().retainAll(liveSubLevels);
        PLUNGER_PEAK.keySet().retainAll(liveSubLevels);
        PLUNGER_PULL.keySet().retainAll(liveSubLevels);
        LIFTED.keySet().retainAll(liveTrains);
        PLUNGER_LIFTED.keySet().retainAll(liveTrains);
        REGRIP.keySet().retainAll(liveTrains);
        TRACED.keySet().retainAll(liveSubLevels);
        RELEASED.keySet().retainAll(liveTrains);
        STAFF_SEEN.keySet().retainAll(liveTrains);
    }
}
