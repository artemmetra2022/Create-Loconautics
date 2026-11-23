package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlock;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlockEntity;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.SableTrain.Car;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class TrainPropulsion {
    static final double MASS_EXPONENT = 0.5;
    static final double MIN_ACCELERATION = 0.75;
    static final double MAX_ACCELERATION = 6.0;


    public static final double MPS2_TO_BPT2 = 0.05 * 0.05;

    static double tractionAccelLimit(SableTrain train, double consistMass) {
        double massRatio = LoconauticsConfig.DYNAMICS_REFERENCE_MASS.get() / Math.max(1.0, consistMass);
        double scaled = LoconauticsConfig.DYNAMICS_BASE_ACCELERATION.get()
                * Math.pow(massRatio, MASS_EXPONENT);
        double limit = Math.max(MIN_ACCELERATION,
                Math.min(scaled, MAX_ACCELERATION));
        double boosted = limit * ConsistPower.driverBoost(train) * drivePowerFraction(train);
        double mechanical = isMonorail(train) ? boosted * MONORAIL_TRACTION_BOOST : boosted;
        return Math.min(mechanical, adhesionAccelLimitMps2(train));
    }

    /**
     * Maximum acceleration before powered wheelsets exceed their share of the
     * consist's adhesive weight. Wheelspin progressively reduces usable adhesion.
     */
    public static double adhesionAccelLimitMps2(SableTrain train) {
        ConsistPower.Result result = ConsistPower.resultFor(train);
        if (result == null || result.axles().isEmpty() || result.totalMass() <= 0.0) {
            return MAX_ACCELERATION;
        }

        Set<UUID> drivenCars = new HashSet<>();
        int poweredAxles = 0;
        int slippingAxles = 0;
        for (ConsistPower.Axle axle : result.axles()) {
            if (!axle.powered()) {
                continue;
            }
            poweredAxles++;
            SableTrain member = SableTrainRegistry.bySubLevel(axle.subLevelId());
            if (member != null && member.slippingBogeys().contains(axle.bogeyLocalPos())) {
                slippingAxles++;
            }
            drivenCars.add(axle.subLevelId());
        }
        if (drivenCars.isEmpty()) {
            return MAX_ACCELERATION;
        }

        double drivenMass = 0.0;
        for (UUID id : drivenCars) {
            ServerSubLevel sub = SubLevelData.resolve(train.level(), id);
            MassData mass = sub == null ? null : sub.getMassTracker();
            if (mass != null && !mass.isInvalid()) {
                drivenMass += Math.max(0.0, mass.getMass());
            }
        }
        if (drivenMass <= 0.0) {
            return MAX_ACCELERATION;
        }

        double totalMass = Math.max(1.0, result.totalMass());
        double adhesiveShare = Math.max(0.01, Math.min(1.0, drivenMass / totalMass));
        double slipRatio = poweredAxles <= 0 ? 0.0 : (double) slippingAxles / poweredAxles;
        double slipFactor = Math.max(0.25, 1.0 - 0.70 * slipRatio);
        double railAdhesion = isMonorail(train) ? 0.35 : 0.25;
        return Math.max(0.15, railAdhesion * 9.81 * adhesiveShare * slipFactor);
    }

    /** Share of powered wheelsets currently reporting wheelspin, from 0 to 1. */
    public static double poweredAxleSlipRatio(SableTrain train) {
        ConsistPower.Result result = ConsistPower.resultFor(train);
        if (result == null) {
            return 0.0;
        }
        int powered = 0;
        int slipping = 0;
        for (ConsistPower.Axle axle : result.axles()) {
            if (!axle.powered()) {
                continue;
            }
            powered++;
            SableTrain member = SableTrainRegistry.bySubLevel(axle.subLevelId());
            if (member != null && member.slippingBogeys().contains(axle.bogeyLocalPos())) {
                slipping++;
            }
        }
        return powered == 0 ? 0.0 : (double) slipping / powered;
    }

    /** Track gradient, signed in the current direction of travel. */
    public static double gradePercent(SableTrain train) {
        if (train == null || train.car() == null || train.car().carriage() == null) {
            return 0.0;
        }
        Vec3 forward = train.car().carriage().forward();
        double horizontal = Math.sqrt(forward.x * forward.x + forward.z * forward.z);
        if (horizontal < 1.0e-6) {
            return 0.0;
        }
        double direction = Math.abs(train.speed()) > 1.0e-4
                ? Math.signum(train.speed()) : Math.signum(train.targetSpeed());
        if (direction == 0.0) {
            direction = 1.0;
        }
        return 100.0 * forward.y / horizontal * direction;
    }

    static final double MONORAIL_TRACTION_BOOST = 3.0;

    public static boolean isMonorail(SableTrain train) {
        return train.car().carriage() != null && train.car().carriage().isOnMonorail();
    }

    public static double brakeDecelLimit() {
        return LoconauticsConfig.DYNAMICS_BASE_DECELERATION.get();
    }

    static boolean isBraking(double target, double current) {
        // A lower speed target is not itself a brake command: let rolling resistance
        // and gravity change the speed naturally. A direction reversal is braking.
        return target * current < 0.0;
    }

    static double brakeLimit(SableTrain train, double target, double current) {
        boolean countering = ConsistPower.isManual(train) && target * current < 0.0;
        return brakeDecelLimit() * brakingMassFactor(train, LoconauticsConfig.DYNAMICS_REFERENCE_MASS.get())
                * (countering ? ManualDrive.COUNTER_BRAKE : 1.0);
    }

    /**
     * A fixed brake force slows a heavier consist less. The square-root curve keeps the
     * difference visible without making long trains almost impossible to stop.
     */
    public static double brakingMassFactor(SableTrain train, double fallbackMass) {
        double referenceMass = Math.max(1.0, LoconauticsConfig.DYNAMICS_REFERENCE_MASS.get());
        double consistMass = ConsistPower.consistMass(train,
                fallbackMass > 0.0 ? fallbackMass : referenceMass);
        double ratio = Math.sqrt(referenceMass / Math.max(1.0, consistMass));
        return Math.max(0.25, Math.min(2.0, ratio));
    }

    /**
     * Whether this carriage advances from the train's kinematic speed rather than
     * from the Sable rigid-body/rail constraint.
     */
    public static boolean usesKinematicRailMotion(SableTrain train) {
        return train != null && train.car() != null && !train.isPoseSettling()
                && !SableTrainDriver.useForceGlue(train, train.car());
    }

    /**
     * Gravity projected onto the track direction, expressed in blocks per tick squared.
     * Physical rail-glued carriages get this from Sable's world gravity and must not
     * receive a second, manually integrated copy.
     */
    public static double slopeAccelerationBpt2(SableTrain train) {
        if (!LoconauticsConfig.SLOPE_EFFECTS_ENABLED.get() || !usesKinematicRailMotion(train)
                || train.car().carriage() == null || train.level() == null) {
            return 0.0;
        }
        Vec3 forward = train.car().carriage().forward();
        double length = forward.length();
        if (length < 1.0e-9) {
            return 0.0;
        }
        Vector3d tangent = new Vector3d(forward.x, forward.y, forward.z).div(length);
        return DimensionPhysicsData.getGravity(train.level()).dot(tangent) * MPS2_TO_BPT2;
    }

    /**
     * Net along-track acceleration for the kinematic fallback. Static resistance
     * keeps a train on a shallow grade at rest; once gravity exceeds it, the train
     * starts rolling. Brake force is reduced by total consist mass.
     */
    public static double kinematicTrackAccelerationBpt2(SableTrain train) {
        if (!usesKinematicRailMotion(train) || train.car().carriage() == null) {
            return 0.0;
        }
        double referenceMass = Math.max(1.0, LoconauticsConfig.DYNAMICS_REFERENCE_MASS.get());
        boolean pulling = train.isPowered() && Math.abs(train.targetSpeed()) > 1.0e-4
                && train.speed() * train.targetSpeed() >= 0.0
                && Math.abs(train.speed()) < Math.abs(train.targetSpeed());
        double rollingResistance = pulling ? 0.0
                : LoconauticsConfig.DYNAMICS_ROLLING_RESISTANCE.get();

        double brakeDeceleration = 0.0;
        if (train.brakeHold()) {
            brakeDeceleration = TrainBrakes.BRAKE_DECEL;
        } else if (train.isBraked()) {
            brakeDeceleration = TrainBrakes.BRAKE_DECEL * train.brakeScale();
        } else if (train.servoBrake()
                && Math.abs(train.targetSpeed()) < Math.max(STANDSTILL_SPEED, Math.abs(train.speed()))) {
            brakeDeceleration = brakeDecelLimit();
        }
        brakeDeceleration *= brakingMassFactor(train, referenceMass);

        double gravity = slopeAccelerationBpt2(train) / MPS2_TO_BPT2;
        double resistance = rollingResistance + brakeDeceleration;
        double net;
        if (Math.abs(train.speed()) <= STANDSTILL_SPEED) {
            net = Math.abs(gravity) <= resistance ? 0.0
                    : gravity - Math.signum(gravity) * resistance;
        } else {
            net = gravity - Math.signum(train.speed()) * resistance;
        }
        return net * MPS2_TO_BPT2;
    }

    static double inclineDegrees(SableTrain train) {
        Vec3 forward = train.car().carriage().forward();
        double length = forward.length();
        if (length < 1.0e-9) {
            return 0.0;
        }
        return Math.toDegrees(Math.asin(Math.min(1.0, Math.abs(forward.y) / length)));
    }

    static boolean isClimbing(SableTrain train, double rpm) {
        Vec3 forward = train.car().carriage().forward();
        double travelDir = Math.abs(train.speed()) > 1.0e-4 ? Math.signum(train.speed()) : (rpm >= 0.0 ? 1.0 : -1.0);
        return travelDir * forward.y > 1.0e-4;
    }

    static final double SAFETY_MAX_SPEED_MPS = 200.0;

    /** 99.5 km/h, a train that reaches 99.9 km/h comes off the rails, so no config goes above this */
    public static final double DERAIL_SAFE_MAX_SPEED_MPS = 99.5 / 3.6;

    public static double speedCeilingMps() {
        return Math.min(DERAIL_SAFE_MAX_SPEED_MPS, AxleGearing.speedCeiling(LoconauticsConfig.TRAIN_MAX_SPEED.get(),
                SAFETY_MAX_SPEED_MPS, LoconauticsConfig.GEARING_CAP_AT_CREATE_TOP_SPEED.get(), createTopSpeedMps()));
    }

    public static double gearingCeilingRatio() {
        return AxleGearing.ceilingRatio(bearingAxleCapMps(), speedCeilingMps());
    }

    public static double bodySpeedCapMps(SableTrain train) {
        return Math.min(speedCeilingMps(), Math.max(bearingAxleCapMps(), ConsistPower.peakTopBpt(train) * 20.0));
    }

    public static double gearedTopBpt(SableTrain train) {
        ConsistPower.Result power = ConsistPower.resultFor(train);
        return power == null ? bearingAxleCapBpt() : power.gearedTopBpt();
    }

    static void updateAxleRedstone(SableTrain train) {
        if (train.isDerailed() || train.isPhysics()) {
            return;
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
        ServerLevel subLevel = sub.getLevel();
        for (BearingAxleBlockEntity axle
                : com.lycoris.loconautics.common.sable.SubLevelBlocks.findAll(sub, BearingAxleBlockEntity.class)) {
            BlockPos pos = axle.getBlockPos();
            BlockState state = subLevel.getBlockState(pos);
            if (!(state.getBlock() instanceof BearingAxleBlock)) {
                continue;
            }
            Direction plusFace  = BearingAxleBlock.getPlusFace(state);
            Direction minusFace = plusFace.getOpposite();
            axle.setRightRedstone(subLevel.getSignal(pos.relative(plusFace),  plusFace)  > 0);
            axle.setLeftRedstone(subLevel.getSignal(pos.relative(minusFace), minusFace) > 0);
            axle.setGearSignal(BearingAxleBlock.gearSignal(subLevel, pos, state));
        }
    }

    static final double STANDSTILL_SPEED = 1.0e-3;

    static void applyDynamics(SableTrain train) {
        if (train.isDerailed() || train.isPhysics()) {
            return;
        }
        boolean braking = isBraking(train.targetSpeed(), train.speed())
                || train.servoBrake() && Math.abs(train.targetSpeed()) < Math.abs(train.speed());
        double limitMps2 = train.brakeHold() || train.isBraked()
                ? TrainBrakes.BRAKE_DECEL * brakingMassFactor(train,
                        LoconauticsConfig.DYNAMICS_REFERENCE_MASS.get())
                : braking ? brakeLimit(train, train.targetSpeed(), train.speed())
                : tractionAccelLimit(train, ConsistPower.tractionMass(train, 1.0));
        train.setAccel(limitMps2 * MPS2_TO_BPT2);
    }

    @org.jetbrains.annotations.Nullable
    public static Vec3 axleDriveHeading(SableTrain train) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null || train.car() == null || train.car().subLevelId() == null) {
            return null;
        }
        if (!(container.getSubLevel(train.car().subLevelId()) instanceof ServerSubLevel sub) || sub.isRemoved()) {
            return null;
        }
        TrainDriveSource axle = driveSourceIn(sub);
        Direction front = DriveSources.front(axle, sub);
        if (axle == null || front == null) {
            return null;
        }
        double rpm = axle.effectiveRpm();
        if (Math.abs(rpm) < 1.0e-3) {
            return null;
        }
        return new Vec3(front.getStepX(), 0.0, front.getStepZ()).scale(Math.signum(rpm));
    }

    public static double axleSign(SableTrain train) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null || train.car() == null || train.car().subLevelId() == null) {
            return train.axleSign();
        }
        if (!(container.getSubLevel(train.car().subLevelId()) instanceof ServerSubLevel sub) || sub.isRemoved()) {
            return train.axleSign();
        }
        return axleSign(train, sub, driveSourceIn(sub));
    }

    static double drivePowerFraction(SableTrain train) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null || train.car() == null || train.car().subLevelId() == null) {
            return 1.0;
        }
        if (!(container.getSubLevel(train.car().subLevelId()) instanceof ServerSubLevel sub) || sub.isRemoved()) {
            return 1.0;
        }
        TrainDriveSource axle = driveSourceIn(sub);
        return axle == null ? 1.0 : axle.powerFraction();
    }

    public static double axleRpm(SableTrain train) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null || train.car() == null || train.car().subLevelId() == null) {
            return 0.0;
        }
        if (!(container.getSubLevel(train.car().subLevelId()) instanceof ServerSubLevel sub) || sub.isRemoved()) {
            return 0.0;
        }
        TrainDriveSource axle = driveSourceIn(sub);
        return axle == null ? 0.0 : axle.effectiveRpm();
    }

    public static double axleSign(SableTrain train, ServerSubLevel sub, TrainDriveSource axle) {
        Direction front = DriveSources.front(axle, sub);
        RailCarriage rail = train.leadingBogeyRail();
        if (front == null || rail == null) {
            return train.axleSign();
        }
        if (train.isPoseSettling()) {
            return train.axleSign();
        }
        Vec3 forward = TrainRailAdvance.bodyForward(rail);
        if (forward == null) {
            return train.axleSign();
        }
        double sign = front.getStepX() * forward.x + front.getStepZ() * forward.z < 0.0 ? -1.0 : 1.0;
        train.setAxleSign(sign);
        return sign;
    }

    static void applyPropulsion(SableTrain train) {
        if (train.isDerailed()) {
            train.setPowered(false);
            train.setWheelDrive(derailedWheelDrive(train));
            return;
        }
        // wheeldrive is only consumed while derailed, clear it here so a rerailed car never carries a stale value
        train.setWheelDrive(0.0);
        if (train.brakeHold()) {
            train.setPowered(false);
            train.setTargetSpeed(0.0);
            if (Math.abs(train.speed()) <= STANDSTILL_SPEED) {
                train.setSpeed(0.0);
            }
            return;
        }
        if (consistHeldByStaff(train)) {
            train.setPowered(false);
            train.setTargetSpeed(0.0);
            return;
        }
        ConsistPower.Result power = ConsistPower.resultFor(train);
        boolean driven = power != null && ConsistPower.isManual(train) && !power.axles().isEmpty();
        if (power == null || !power.anyPowered() && !driven) {
            train.setPowered(false);
            train.setTargetSpeed(0.0);
            return;
        }
        train.setPowered(true);
        train.setTargetSpeed(ConsistPower.targetFor(train));
        traceReload301(train, power, driven);
    }

    // temporary, tag reload301, why a reloaded train keeps driving with nobody at the controls
    private static void traceReload301(SableTrain train, ConsistPower.Result power, boolean driven) {
        if (train.level().getGameTime() % 20 != 0 || Math.abs(train.targetSpeed()) < 1.0e-6) {
            return;
        }
        com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity controller =
                SableTrainDriver.controllerIn(train);
        com.lycoris.loconautics.LoconauticsConstants.LOGGER.info(
                "[reload301] train {} target {} speed {} scheduleDriven {} station {} stationState {} manual {} driven {} "
                        + "anyPowered {} controller {} power {} scheduleControlled {} ghostCruise {}",
                train.id().toString().substring(0, 8), train.targetSpeed(), train.speed(), train.isScheduleDriven(),
                train.currentStation() == null ? "none" : "set", train.stationState(), ConsistPower.isManual(train),
                driven, power.anyPowered(), controller == null ? "none" : "present",
                controller == null ? "n/a" : controller.getCurrentPower(),
                controller != null && controller.isScheduleControlled(), train.ghostCruise());
    }

    private static boolean consistHeldByStaff(SableTrain train) {
        ServerLevel level = train.level();
        if (level == null || train.car() == null) {
            return false;
        }
        for (UUID id : TrainConsist.connectedThisTick(level, train.car().subLevelId())) {
            ServerSubLevel sub = SubLevelData.resolve(level, id);
            if (sub != null && ExternalForceDerail.staffHolds(sub)) {
                return true;
            }
        }
        return false;
    }

    public static double createTopSpeedMps() {
        return AllConfigs.server().trains.trainTopSpeed.get();
    }

    static double bearingAxleCapMps() {
        double cfg = LoconauticsConfig.BEARING_AXLE_MAX_SPEED.get();
        return cfg < 0.0 ? createTopSpeedMps() : cfg;
    }

    public static double bearingAxleCapBpt() {
        return bearingAxleCapMps() / 20.0;
    }

    static double derailedWheelDrive(SableTrain train) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null) {
            return 0.0;
        }
        Car car = train.car();
        if (car.subLevelId() == null
                || !(container.getSubLevel(car.subLevelId()) instanceof ServerSubLevel sub) || sub.isRemoved()) {
            return 0.0;
        }
        TrainDriveSource axle = driveSourceIn(sub);
        if (axle == null) {
            return 0.0;
        }
        if (!SableTrainDriver.anyBogeyAlive(sub, car)) {
            return 0.0;
        }
        double rpm = axle.effectiveRpm();
        double cap = speedCeilingMps() / 20.0;
        return Math.max(-cap, Math.min(cap, AxleGearing.gearedSpeed(bearingAxleCapBpt(), axle.gearRatio(), rpm)));
    }

    static TrainDriveSource findDriveSource(SableTrain train, ServerSubLevelContainer container) {
        Car car = train.car();
        if (car.subLevelId() == null
                || !(container.getSubLevel(car.subLevelId()) instanceof ServerSubLevel sub) || sub.isRemoved()) {
            return null;
        }
        return driveSourceIn(sub);
    }

    static double liveMass(ServerSubLevel sub) {
        return TrainConsist.subLevelMass(sub);
    }

    static TrainDriveSource driveSourceIn(ServerSubLevel sub) {
        return DriveSources.resolve(sub);
    }

    private TrainPropulsion() {
    }
}
