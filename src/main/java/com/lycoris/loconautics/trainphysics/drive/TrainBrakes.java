package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.trainphysics.debug.force.ForceSource;
import com.lycoris.loconautics.trainphysics.debug.force.ForceTrace;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.airbrake.AirBrakeTuning;
import com.lycoris.loconautics.content.airbrake.TripleValveBlock;
import com.lycoris.loconautics.content.airbrake.TripleValveBlockEntity;
import com.lycoris.loconautics.content.brakes.BrakeControllerBlock;
import com.lycoris.loconautics.content.brakes.BrakeControllerBlockEntity;
import com.lycoris.loconautics.content.brakes.HandbrakeBlock;
import com.lycoris.loconautics.content.safety.SafetyBlock;
import com.lycoris.loconautics.content.safety.SafetyBlockEntity;
import com.lycoris.loconautics.content.retardertrack.RetarderTracks;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class TrainBrakes {

    public static final double BRAKE_DECEL = 60.0;

    private TrainBrakes() {
    }

    public static void holdSoft(SableTrain train) {
        train.armBrakePulse();
    }

    public static void update(SableTrain train) {
        if (train.isDerailed() || train.isPhysics()) {
            train.setBraked(false);
            train.setBrakeHard(false);
            train.setBrakeHold(false);
            return;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null) {
            return;
        }
        SableTrain.Car car = train.car();
        if (car.subLevelId() == null
                || !(container.getSubLevel(car.subLevelId()) instanceof ServerSubLevel sub) || sub.isRemoved()) {
            return;
        }
        train.tickBrakePulse();

        double hand = handbrakeStrength(sub);
        train.setHandbrake(hand);
        double handScale = Math.min(1.0, hand / Math.max(1.0, rollingWeightUnclamped(sub)));
        boolean locked = handScale * BRAKE_DECEL > WHEEL_GRIP_DECEL;
        if (locked) {
            handScale *= SKID_FACTOR;
        }
        ConsistBrake.Demand consist = ConsistBrake.demandFor(train);
        double pipe = Math.min(1.0, consist.decel() / BRAKE_DECEL);
        // a schedule hold must not full brake here or an arrived train never pulls away
        double air = airBrakeScale(sub);
        boolean airEmergency = airEmergency(sub);
        double soft = Math.max(pipe, train.brakePulseActive() ? 1.0 : 0.0);
        double scale = Math.max(handScale, Math.max(soft, air));
        TripleValveBlockEntity padValve = brakePadValve(sub);
        if (padValve != null) {
            padValve.tickBrakePadCondition(Math.abs(train.speed()), scale);
            notifyBrakePadWarning(sub, padValve, padValve.pollBrakePadWarning());
            scale *= padValve.getBrakePadEfficiency();
        }

        train.setBrakeScale(scale);
        train.setBraked(scale > 0.0);
        // the handbrake never cuts drive, the engine pulls against it and its drag shows up as axle stress instead
        // Pneumatic emergency braking must follow the cylinder pressure ramp. A brake hold here
        // would replace that ramp with the full mechanical stop force and make notch 15 instant.
        train.setBrakeHold(consist.hold() || train.brakePulseActive() || safetyStop(sub));
        train.setBrakeHard(locked || airEmergency);
    }

    static final double WHEEL_GRIP_DECEL = 30.0;
    static final double SKID_FACTOR = 0.7;

    static final double HANDBRAKE_STRESS_WEIGHT = 4.0;

    public static double handbrakeLoadMass(SableTrain train) {
        return train.handbrake() * LoconauticsConfig.DYNAMICS_REFERENCE_MASS.get() * HANDBRAKE_STRESS_WEIGHT;
    }

    static String fmt(double d) {
        return String.format(java.util.Locale.ROOT, "%.3f", d);
    }

    private static final int BRAKE_SCAN_TICKS = 10;

    private record BrakeScan(long tick, List<BlockPos> positions) {
    }

    private static final Map<ServerSubLevel, BrakeScan> BRAKE_SCANS = new WeakHashMap<>();

    // set by handbrakeStrength, true while a Safety Unit on the carriage holds the emergency brake
    private static final Map<ServerSubLevel, Boolean> SAFETY_STOPS = new WeakHashMap<>();
    private static final Map<ServerSubLevel, Double> AIR_BRAKE_SCALES = new WeakHashMap<>();
    private static final Map<ServerSubLevel, Boolean> AIR_EMERGENCY_STOPS = new WeakHashMap<>();

    private static boolean safetyStop(ServerSubLevel sub) {
        return Boolean.TRUE.equals(SAFETY_STOPS.get(sub));
    }

    private static double airBrakeScale(ServerSubLevel sub) {
        return AIR_BRAKE_SCALES.getOrDefault(sub, 0.0);
    }

    private static boolean airEmergency(ServerSubLevel sub) {
        return Boolean.TRUE.equals(AIR_EMERGENCY_STOPS.get(sub));
    }

    public static double handbrakeStrength(ServerSubLevel sub) {
        ServerLevel subLevel = sub.getLevel();
        long now = subLevel.getGameTime();
        BrakeScan scan = BRAKE_SCANS.get(sub);
        if (scan == null || now - scan.tick() >= BRAKE_SCAN_TICKS || now < scan.tick()) {
            List<BlockPos> found = new ArrayList<>();
            SubLevelBlocks.forEachBlock(sub, (pos, state) -> {
                if (state.getBlock() instanceof HandbrakeBlock || state.getBlock() instanceof BrakeControllerBlock
                        || state.getBlock() instanceof SafetyBlock || state.getBlock() instanceof TripleValveBlock) {
                    found.add(pos.immutable());
                }
                return true;
            });
            scan = new BrakeScan(now, found);
            BRAKE_SCANS.put(sub, scan);
        }
        boolean safetyStop = false;
        int bestHandbrake = 0;
        int bestController = 0;
        boolean emergency = false;
        double bestAir = 0.0;
        boolean airEmergency = false;
        for (BlockPos pos : scan.positions()) {
            BlockState state = subLevel.getBlockState(pos);
            if (state.getBlock() instanceof HandbrakeBlock && state.getValue(HandbrakeBlock.POWERED)) {
                bestHandbrake = Math.max(bestHandbrake, subLevel.getBestNeighborSignal(pos));
            } else if (state.getBlock() instanceof BrakeControllerBlock
                    && subLevel.getBlockEntity(pos) instanceof BrakeControllerBlockEntity brake) {
                // the lever position, 0 to 15, full strength only at the very top (see
                // CONTROLLER_FULL_STRENGTH_SIGNAL)
                bestController = Math.max(bestController, brake.getEffectiveBrakeSignal());
                emergency |= brake.isEmergency();
            } else if (state.getBlock() instanceof SafetyBlock
                    && subLevel.getBlockEntity(pos) instanceof SafetyBlockEntity safety && safety.isEmergency()) {
                // a tripped Safety Unit brakes like the emergency switch and also cuts the drive
                emergency = true;
                safetyStop = true;
            } else if (state.getBlock() instanceof TripleValveBlock
                    && subLevel.getBlockEntity(pos) instanceof TripleValveBlockEntity valve) {
                double cylinderFraction = valve.getCylinderFraction();
                if (valve.isEmergency()) {
                    // Emergency braking is stronger, but still follows the pressure actually
                    // built in this car's cylinder rather than forcing an immediate stop.
                    airEmergency |= cylinderFraction > 0.03;
                    bestAir = Math.max(bestAir, cylinderFraction
                            * AirBrakeTuning.EMERGENCY_BRAKE_MAX_DECEL_MPS2 / BRAKE_DECEL);
                } else {
                    // Service braking is deliberately limited to a realistic, graduated deceleration.
                    bestAir = Math.max(bestAir, cylinderFraction
                            * AirBrakeTuning.SERVICE_BRAKE_MAX_DECEL_MPS2 / BRAKE_DECEL);
                }
            }
        }
        SAFETY_STOPS.put(sub, safetyStop);
        AIR_BRAKE_SCALES.put(sub, bestAir);
        AIR_EMERGENCY_STOPS.put(sub, airEmergency);
        double handbrakeFraction = bestHandbrake / 15.0;
        double handbrakeStrength = handbrakeFraction * handbrakeFraction;

        // the curve spans the whole lever travel, so every notch brakes gentler than when it peaked at 8
        double controllerFraction = Math.min(1.0, bestController / (double) CONTROLLER_FULL_STRENGTH_SIGNAL);
        double controllerStrength = controllerFraction * controllerFraction * CONTROLLER_MAX_STRENGTH;

        // the emergency switch skips the cap and brakes as hard as a handbrake all the way up
        if (emergency) {
            controllerStrength = 1.0;
        }

        return Math.max(handbrakeStrength, controllerStrength);
    }

    private static void notifyBrakePadWarning(ServerSubLevel sub, TripleValveBlockEntity valve, int warning) {
        if (warning <= 0) return;
        ServerLevel level = sub.getLevel();
        Vec3 position = dev.ryanhcode.sable.Sable.HELPER.projectOutOfSubLevel(level,
                Vec3.atCenterOf(valve.getBlockPos()));
        Component message = Component.translatable(switch (warning) {
            case 1 -> "loconautics.brake_pads.warning.wear";
            case 2 -> "loconautics.brake_pads.warning.critical";
            case 3 -> "loconautics.brake_pads.warning.overheating";
            default -> "loconautics.brake_pads.warning.failed";
        });
        net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound = switch (warning) {
            case 1, 2 -> SoundEvents.BLOCK_NOTE_BLOCK_BASS;
            case 3 -> SoundEvents.BLOCK_FIRE_EXTINGUISH;
            default -> SoundEvents.BLOCK_ANVIL_LAND;
        };
        float pitch = warning == 1 ? 0.85f : warning == 2 ? 1.05f : warning == 3 ? 0.7f : 0.55f;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(position.x, position.y, position.z) <= 32.0 * 32.0) {
                player.displayClientMessage(message, true);
                player.playNotifySound(sound, SoundSource.BLOCKS, 0.8f, pitch);
            }
        }
    }

    private static TripleValveBlockEntity brakePadValve(ServerSubLevel sub) {
        BrakeScan scan = BRAKE_SCANS.get(sub);
        if (scan == null) return null;
        for (BlockPos pos : scan.positions()) {
            if (sub.getLevel().getBlockEntity(pos) instanceof TripleValveBlockEntity valve) return valve;
        }
        return null;
    }

    // the Brake Controller lever reaches full wheel braking strength only at this position, the same
    // as a redstone handbrake (it used to be 8, which made the train stop too fast)
    private static final int CONTROLLER_FULL_STRENGTH_SIGNAL = 15;

    // the controller never brakes harder than this share of a handbrake, even with the lever all the way up. 0.5 stays
    // at the wheel grip limit, so the wheels do not lock and skid either
    private static final double CONTROLLER_MAX_STRENGTH = 0.5;

    // Rolling resistance is configured as an equivalent deceleration and does not scale with wagon load.
    public static double rollingScale(SableTrain train, ServerSubLevel sub) {
        boolean activelyPulling = train.isPowered() && Math.abs(train.targetSpeed()) > 1.0e-4
                && train.speed() * train.targetSpeed() >= 0.0
                && Math.abs(train.speed()) < Math.abs(train.targetSpeed());
        if (activelyPulling || train.isDerailed() || train.isFollowing()) {
            return 0.0;
        }
        // Rolling resistance force grows with axle load, but so does the car's mass:
        // its resulting deceleration is therefore approximately independent of load.
        // Free-running rail vehicles should coast for a long time; keep the config value as a
        // familiar tuning knob, but apply only one fifth of the former drag to avoid an
        // artificial speed drop when a train leaves a downhill grade for level track.
        return Math.min(1.0, LoconauticsConfig.DYNAMICS_ROLLING_RESISTANCE.get() * 0.2 / BRAKE_DECEL);
    }

    private static double rollingWeightUnclamped(ServerSubLevel sub) {
        MassData md = sub == null ? null : sub.getMassTracker();
        double mass = (md != null && !md.isInvalid()) ? md.getMass() : 0.0;
        if (mass <= 0.0) {
            return 1.0;
        }
        return mass / LoconauticsConfig.DYNAMICS_REFERENCE_MASS.get();
    }

    public static void relieveSlopeGravity(ServerSubLevel sub, Vector3d railPoint, Vector3d tangent, double dt) {
        // Sable already applies world gravity to the rigid body. Do not cancel its
        // component along the rails when grade physics is enabled.
        if (LoconauticsConfig.SLOPE_EFFECTS_ENABLED.get()) {
            return;
        }
        RigidBodyHandle handle = RigidBodyHandle.of(sub);
        if (handle == null) {
            return;
        }
        double along = DimensionPhysicsData.getGravity(sub.getLevel()).dot(tangent);
        if (Math.abs(along) < 1.0e-6) {
            return;
        }
        MassData md = sub.getMassTracker();
        double mass = (md != null && !md.isInvalid()) ? md.getMass() : 1.0;
        Vector3d worldImpulse = new Vector3d(tangent).mul(-mass * along * dt);
        Vector3d localImpulse = new Quaterniond(sub.logicalPose().orientation())
                .conjugate().transform(worldImpulse, new Vector3d());
        ForceTotal forces = new ForceTotal();
        forces.applyLinearImpulse(localImpulse);
        handle.applyForcesAndReset(forces);
        ForceTrace.impulse(sub, ForceSource.SLOPE_RELIEF, worldImpulse, railPoint,
                "slope effects disabled, cancelling along-rail gravity " + fmt(along)
                        + " mass=" + fmt(mass));
    }

    public static void applyResistance(SableTrain train, ServerSubLevel sub, ServerSubLevel trackSub,
                                       Vector3d railPoint, Vector3d tangent, double dt, double scale,
                                       ForceSource source) {
        RigidBodyHandle handle = RigidBodyHandle.of(sub);
        if (handle == null) {
            return;
        }
        // a powered retarder rail under the car caps its speed, whether or not the train brakes or pulls
        double retarderLimit = RetarderTracks.limitAt(sub.getLevel(), trackSub, railPoint);
        if (scale <= 0.0 && Double.isNaN(retarderLimit)) {
            return;
        }
        Vector3d vel = handle.getLinearVelocity(new Vector3d());
        double vTan = TrackHostMotion.relativeAlongTrack(sub.getLevel(), trackSub, railPoint, vel, tangent);
        if (Math.abs(vTan) < 1.0e-6) {
            return;
        }
        MassData md = sub.getMassTracker();
        double mass = (md != null && !md.isInvalid()) ? md.getMass() : 1.0;
        double brakeMassFactor = source == ForceSource.BRAKE
                ? TrainPropulsion.brakingMassFactor(train, mass) : 1.0;
        double decel = Math.min(BRAKE_DECEL * scale * brakeMassFactor, Math.abs(vTan) / dt);
        if (!Double.isNaN(retarderLimit) && Math.abs(vTan) > retarderLimit) {
            // never brake past the limit, so a train that is already slow is left alone
            double excess = (Math.abs(vTan) - retarderLimit) / dt;
            double retarded = Math.min(RetarderTracks.RETARDER_DECEL, excess);
            if (retarded > decel) {
                decel = retarded;
                source = ForceSource.BRAKE;
            }
        }
        if (decel <= 0.0) {
            return;
        }
        Vector3d worldImpulse = new Vector3d(tangent).mul(-mass * decel * dt * Math.signum(vTan));
        Vector3d localImpulse = new Quaterniond(sub.logicalPose().orientation())
                .conjugate().transform(worldImpulse, new Vector3d());
        ForceTotal forces = new ForceTotal();
        forces.applyLinearImpulse(localImpulse);
        handle.applyForcesAndReset(forces);
        com.lycoris.loconautics.content.knuckle.KnuckleRailCoupler.noteExternal(sub.getUniqueId(),
                -decel * dt * Math.signum(vTan));
        ForceTrace.impulse(sub, source, worldImpulse, railPoint,
                "scale=" + fmt(scale) + " decel=" + fmt(decel) + " of " + fmt(BRAKE_DECEL)
                        + " against vTan=" + fmt(vTan) + " mass=" + fmt(mass));
        TrackHostMotion.pushHost(trackSub, railPoint, worldImpulse);
    }
}
