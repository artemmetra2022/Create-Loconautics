package com.lycoris.loconautics.trainphysics.wheels;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.compat.computercraft.CcControl;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.content.sand.SandStorageBlockEntity;
import com.lycoris.loconautics.trainphysics.drive.ExternalForceDerail;
import com.lycoris.loconautics.trainphysics.signal.TrainSignalAccessor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import org.joml.Vector3d;

public final class WheelSlip {

    private static final int STALL_DELAY_TICKS = 4;

    private static final int RELEASE_TICKS = 3;

    private static final double STALL_SPEED = 0.01;

    private static final double STALL_COMMAND = 0.02;
    private static final double SLIP_SPEED_THRESHOLD = 0.04;
    private static final double SLIP_RATIO_THRESHOLD = 0.25;
    private static final int SPIN_DELAY_TICKS = 3;

    private static final double SLIP_FRACTION = 0.05;

    private static final double SLIP_MAX_SPIN = 0.05;

    private static final double SLIP_MIN_GRIP = 0.3;

    private static final double SLIP_PULSE_RATE = 0.5;

    private WheelSlip() {
    }

    public static double carSpeed(SableTrain train, ServerSubLevel sub) {
        // in the rail hosts own frame, which for a sub level is plot coordinates and does not move when the host
        Vector3d now = inRailFrame(train, new Vector3d(sub.logicalPose().position()));
        Vector3d last = train.lastBodyPos();
        train.setLastBodyPos(now);
        if (last == null) {
            return train.speed();
        }
        double moved = Math.min(now.distance(last), Math.abs(train.speed()));
        return train.speed() < 0.0 ? -moved : moved;
    }

    private static Vector3d inRailFrame(SableTrain train, Vector3d worldPos) {
        SableTrain.Car car = train.car();
        ServerSubLevel host = car == null || car.carriage() == null ? null : car.carriage().activeSub();
        return host == null ? worldPos : host.logicalPose().transformPositionInverse(worldPos, worldPos);
    }

    public static double bite(long gameTime, BlockPos bogeyLocal) {
        // the wheel grabs and lets go in a pulse, the client applies this on top of the slip speed
        double phase = gameTime * SLIP_PULSE_RATE + bogeyLocal.hashCode();
        return SLIP_MIN_GRIP + (1.0 - SLIP_MIN_GRIP) * Math.abs(Math.sin(phase));
    }

    public static boolean parked(SableTrain train) {
        return train.stationState() == SableTrain.StationState.STOPPED || TrainSignalAccessor.isHeldAtRed(train);
    }

    public static double surfaceSpeed(SableTrain train, ServerSubLevel sub, BlockPos bogeyLocal, double commanded,
                                      double carSpeed) {
        boolean computerBraking = CcControl.held(train.id()) && train.brakeScale() > 0.0;
        boolean locked = train.brakeHard() || train.brakeScale() >= 1.0 || computerBraking || parked(train)
                || ExternalForceDerail.staffHolds(sub);
        // commanded is the shafts rpm, a computer holding the train at 0 overrides the speed but never the shaft
        boolean computerIdle = CcControl.held(train.id()) && !CcControl.isDriving(train.id());
        boolean gaining = train.gainingSpeed(train.level().getGameTime(), carSpeed);
        boolean sanding = SandStorageBlockEntity.hasActiveSanding(sub);
        double wheelSpeed = Math.abs(commanded);
        double groundSpeed = Math.abs(carSpeed);
        double slipSpeedThreshold = sanding ? SLIP_SPEED_THRESHOLD * 1.5 : SLIP_SPEED_THRESHOLD;
        double slipRatioThreshold = sanding ? Math.min(0.8, SLIP_RATIO_THRESHOLD * 2.0) : SLIP_RATIO_THRESHOLD;
        int sandingDelay = sanding ? 2 : 0;
        boolean wheelspin = !locked && !computerIdle && wheelSpeed > STALL_COMMAND
                && wheelSpeed - groundSpeed > Math.max(slipSpeedThreshold, wheelSpeed * slipRatioThreshold);
        boolean stuck = !locked && !computerIdle && !gaining
                && wheelSpeed > STALL_COMMAND && groundSpeed < STALL_SPEED;
        int was = train.wheelStall().getOrDefault(bogeyLocal, 0);
        boolean losingGrip = wheelspin || stuck;
        int stall = locked || computerIdle ? 0
                : losingGrip ? Math.min(was + 1, STALL_DELAY_TICKS + RELEASE_TICKS + sandingDelay)
                : Math.max(was - (sanding && !losingGrip ? 2 : 1), 0);
        train.wheelStall().put(bogeyLocal, stall);
        int threshold = wheelspin ? SPIN_DELAY_TICKS + sandingDelay : STALL_DELAY_TICKS + sandingDelay;
        boolean slipping = stall >= threshold || stall > 0 && train.slippingBogeys().contains(bogeyLocal);
        if (slipping != train.slippingBogeys().contains(bogeyLocal)) {
          if (LoconauticsConstants.LOGGER.isDebugEnabled()) {
            LoconauticsConstants.LOGGER.debug("[slipdbg] car {} bogey {} slipping {} stall {} commanded {} carSpeed {} trainSpeed {} locked {} computerIdle {}",
                    train.id().toString().substring(0, 8), bogeyLocal.toShortString(), slipping, stall, commanded,
                    carSpeed, train.speed(), locked, computerIdle);
            LoconauticsConstants.LOGGER.debug("[issue289] car {} bogey {} slipping {} brakeScale {} braked {} brakeHard {} brakeHold {} handbrake {} ccHeld {} ccDriving {} target {} powered {}",
                    train.id().toString().substring(0, 8), bogeyLocal.toShortString(), slipping, train.brakeScale(),
                    train.isBraked(), train.brakeHard(), train.brakeHold(), train.handbrake(), CcControl.held(train.id()),
                    CcControl.isDriving(train.id()), train.targetSpeed(), train.isPowered());
          }
        }
        if (slipping) {
            train.slippingBogeys().add(bogeyLocal);
        } else {
            train.slippingBogeys().remove(bogeyLocal);
        }
        double sandingSpinScale = sanding ? 0.5 : 1.0;
        // A slipping wheel must spin faster than the train is moving, not crawl at a
        // fixed 0.05 blocks/tick. The old cap unintentionally acted as a speed limiter:
        // once a bogey slipped, its surface speed could remain almost zero at any train speed.
        double slipExcess = Math.max(0.0, wheelSpeed - groundSpeed);
        double visibleSpin = Math.min(slipExcess,
                Math.max(SLIP_MAX_SPIN, wheelSpeed * SLIP_FRACTION) * sandingSpinScale);
        double w = slipping
                ? Math.copySign(groundSpeed + visibleSpin, commanded)
                : carSpeed;
        train.wheelSurface().put(bogeyLocal, w);
        return w;
    }
}
