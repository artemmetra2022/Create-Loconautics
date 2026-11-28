package com.lycoris.loconautics.trainphysics.glue;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.SableTrain.Car;
import com.lycoris.loconautics.trainphysics.debug.force.ForceSource;
import com.lycoris.loconautics.trainphysics.debug.force.ForceTrace;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.drive.TrackHostMotion;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import static com.lycoris.loconautics.foundation.LogFormat.f;
import static com.lycoris.loconautics.foundation.LogFormat.shortId;

public final class RollDynamics {
    static final double WHEEL_LIFT_DEGREES = 8.0;
    static final double WHEEL_LIFT_DAMPING = 0.35;


    private RollDynamics() {
    }

    public static final double CURVE_CURVATURE = 0.01;
    private static final long CURVE_HOLD_TICKS = 40L;
    private static final double CURVATURE_TAU = 0.5;

    public record Step(ServerSubLevel sub, Car car, MassData md, RigidBodyHandle handle, Pose3d pose,
                       RollFrame frame, WheelSupports.Prepared prepared, Vector3d massCenter, double dt,
                       ForceTotal forces) {
    }

    // the unclamped linear hinge stiffness, the torque that holds a centered car at the reference lift
    public static double hingeStiffness(double mass, double gN, double half) {
        double phiRef = Math.toRadians(WHEEL_LIFT_DEGREES);
        return mass * gN * half / phiRef;
    }

    // inertia about the hinge line through the side rail, or the center line for side 0
    public static double hingeInertia(MassData md, Pose3d pose, RollFrame frame, int side, Vector3d massCenter) {
        Vector3d hinge = pose.transformPosition(frame.pointLocal(side), new Vector3d());
        Vector3d arm = new Vector3d(massCenter).sub(hinge);
        Vector3d armPerp = arm.sub(new Vector3d(frame.tangent).mul(arm.dot(frame.tangent)));
        Vector3d tangentLocal = new Quaterniond(pose.orientation()).conjugate().transform(new Vector3d(frame.tangent));
        return tangentLocal.dot(md.getInertiaTensor().transform(tangentLocal, new Vector3d()))
                + md.getMass() * armPerp.lengthSquared();
    }

    // a linear, critically damped torsion about the hinge rail pulls the lifted wheels back down
    static boolean torsion(Step s, double phi, int side) {
        CarriageRoll.Roll roll = CarriageRoll.of(s.sub().getUniqueId());
        roll.torque = 0.0;
        if (side == 0) {
            return false;
        }
        RollFrame frame = s.frame();
        double mass = s.md().getMass();
        double gN = -gravity(s).dot(frame.up);
        if (gN <= 0.0) {
            return false;
        }
        double inertia = hingeInertia(s.md(), s.pose(), frame, side, s.massCenter());
        if (!(inertia > 1.0e-6)) {
            return false;
        }
        double dt = s.dt();
        double stiffness = Math.min(hingeStiffness(mass, gN, frame.half), 0.25 * inertia / (dt * dt));
        double ratio = WHEEL_LIFT_DAMPING;
        double damping = Math.min(2.0 * ratio * Math.sqrt(stiffness * inertia), 0.5 * inertia / dt);
        double rate = s.handle().getAngularVelocity(new Vector3d()).dot(frame.tangent);
        double lift = Math.min(Math.max(0.0, side * phi), CarriageRoll.holdAngle());
        boolean freeInCurve = LoconauticsConfig.DERAIL_ON_CURVE.get()
                && roll.curveHeld(s.sub().getLevel().getGameTime());
        double torque = Math.max(0.0, (freeInCurve ? 0.0 : stiffness * lift) + damping * side * rate);
        roll.torque = torque;
        if (torque <= 0.0) {
            return false;
        }
        WheelSupports.Wheel[] wheels = s.prepared().wheels();
        int lifted = 0;
        for (int i = 0; i < wheels.length; i++) {
            if (wheels[i].side() == -side && roll.y[i] > 0.0) {
                lifted++;
            }
        }
        if (lifted == 0) {
            return false;
        }
        double force = torque / (2.0 * frame.half) / lifted;
        for (int i = 0; i < wheels.length; i++) {
            WheelSupports.Wheel wheel = wheels[i];
            if (wheel.side() != -side || roll.y[i] <= 0.0) {
                continue;
            }
            Vector3d worldImpulse = new Vector3d(wheel.normal()).mul(-force * dt);
            s.forces().applyImpulseAtPoint(s.md(), wheel.local(), bodyInverse(s).transform(new Vector3d(worldImpulse)));
            TrackHostMotion.pushHost(wheel.host(), wheel.rail(), worldImpulse);
            ForceTrace.impulse(s.sub(), ForceSource.HINGE, worldImpulse, wheel.rail(),
                    "wheel " + i + " y=" + f(roll.y[i]) + " torque=" + f(torque));
        }
        return true;
    }

    // the window outlives the curve, the roll it built is still swinging at the exit
    private static boolean noteCurve(CarriageRoll.Roll roll, long now, double curvature, double phi) {
        if (Math.abs(curvature) < CURVE_CURVATURE) {
            return false;
        }
        boolean opened = !roll.curveHeld(now);
        if (opened) {
            roll.curveBase = Math.abs(phi);
            roll.curveClamped = false;
        }
        roll.curveUntil = now + CURVE_HOLD_TICKS;
        return opened;
    }

    // the radius comes from creates own curve, the sign from the measured bend, a bend with no radius uses the measure
    private static double trackCurvature(Car car, double measured) {
        double sum = 0.0;
        int counted = 0;
        for (SableTrain.Bogey bogey : car.chain()) {
            RailCarriage rail = bogey.rail();
            TravellingPoint point = rail == null ? null : rail.leading();
            if (point == null || point.edge == null) {
                continue;
            }
            counted++;
            if (!point.edge.isTurn()) {
                continue;
            }
            double radius = point.edge.getTurn().getRadius();
            if (!(radius > 1.0e-3)) {
                return measured;
            }
            sum += 1.0 / radius;
        }
        return counted == 0 ? measured : Math.copySign(sum / counted, measured);
    }

    private static double smoothCurvature(CarriageRoll.Roll roll, double target, double dt) {
        if (!roll.curvatureSeeded) {
            roll.curvatureSeeded = true;
            return target;
        }
        return roll.curvature + dt / (CURVATURE_TAU + dt) * (target - roll.curvature);
    }

    // relief share of the centripetal force acts at the center of mass, the guide carries the rest
    static boolean curveRelief(Step s, double phi, int side) {
        CarriageRoll.Roll roll = CarriageRoll.of(s.sub().getUniqueId());
        roll.relief = 0.0;
        roll.need = 0.0;
        RollFrame frame = s.frame();
        WheelSupports.Prepared prepared = s.prepared();
        double span = prepared.first().c().distance(prepared.last().c());
        if (span < 1.0e-3) {
            return false;
        }
        Vector3d bend = new Vector3d(prepared.last().t()).sub(prepared.first().t()).div(span);
        double curvature = smoothCurvature(roll, trackCurvature(s.car(), bend.dot(frame.lateral)), s.dt());
        roll.curvature = curvature;
        Vector3d railVelocity = SableTrainDriver.railVelocityUnder(s.sub(), s.car());
        double speed = s.handle().getLinearVelocity(new Vector3d()).sub(railVelocity).dot(frame.tangent);
        long now = s.sub().getLevel().getGameTime();
        boolean derailOnCurve = LoconauticsConfig.DERAIL_ON_CURVE.get();
        if (noteCurve(roll, now, curvature, phi)) {
            double tipGravity = Math.max(0.0, -gravity(s).dot(frame.up));
            double tipSpeed = Math.sqrt(tipGravity * Math.max(0.0, frame.half - Math.abs(roll.offset))
                    / (Math.abs(curvature) * Math.max(roll.height, 0.05)));
            LoconauticsConstants.TRACE.warn("[rocktrace] car={} curve open k={} speed={} tipSpeed={} mode={}",
                    shortId(s.sub().getUniqueId()), f(curvature), f(speed), f(tipSpeed),
                    derailOnCurve ? "real overturn" : "lean out");
        }
        double need = speed * speed * curvature;
        if (Math.abs(need) < 1.0e-6 || derailOnCurve) {
            return false;
        }
        roll.need = need;
        double mass = s.md().getMass();
        Vector3d gravity = gravity(s);
        double gN = -gravity.dot(frame.up);
        int outside = curvature > 0.0 ? -1 : 1;
        Vector3d relative = new Vector3d(s.md().getCenterOfMass()).sub(frame.gLocal);
        double offset = relative.dot(frame.lateral0);
        double height = relative.dot(frame.up0);
        double gSide = gravity.dot(frame.lateral) * outside;
        double hold = mass * (gN * (frame.half - outside * offset) - gSide * height);
        Vector3d guide = s.pose().transformPosition(
                side != 0 && Math.abs(phi) > CarriageRoll.PHI_EPS ? frame.pointLocal(side) : frame.gLocal,
                new Vector3d());
        double guideHeight = Math.max(0.0, new Vector3d(s.massCenter()).sub(guide).dot(frame.up));
        double overturn = mass * Math.abs(need) * guideHeight;
        if (overturn < 1.0e-6) {
            return false;
        }
        double relief = 1.0 - Math.max(0.0, Math.min(1.0, hold / overturn));
        roll.relief = relief;
        if (relief <= 0.0) {
            return false;
        }
        Vector3d worldImpulse = new Vector3d(frame.lateral).mul(relief * mass * need * s.dt());
        s.forces().applyLinearImpulse(bodyInverse(s).transform(new Vector3d(worldImpulse)));
        TrackHostMotion.pushHost(TrainRailAdvance.leadingActiveSub(s.car()), frame.reference, worldImpulse);
        ForceTrace.impulse(s.sub(), ForceSource.CURVE_RELIEF, worldImpulse, s.massCenter(),
                "curve relief " + f(relief) + " need=" + f(need) + " overturn=" + f(overturn) + " hold="
                        + f(hold));
        return true;
    }

    private static Quaterniond bodyInverse(Step s) {
        return new Quaterniond(s.pose().orientation()).conjugate();
    }

    private static Vector3d gravity(Step s) {
        return new Vector3d(DimensionPhysicsData.getGravity(s.sub().getLevel()));
    }
}
