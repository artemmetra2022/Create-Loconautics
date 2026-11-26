package com.lycoris.loconautics.content.driverhint;

import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.content.airbrake.AirBrakeTuning;
import com.lycoris.loconautics.content.airbrake.AirPipeBus;
import com.lycoris.loconautics.content.airbrake.DriverBrakeValveBlockEntity;
import com.lycoris.loconautics.content.airbrake.EmergencyBrakeBlockEntity;
import com.lycoris.loconautics.content.dispatcher.DispatcherTrainControl;
import com.lycoris.loconautics.content.safety.SafetyBlockEntity;
import com.lycoris.loconautics.network.packets.driverhint.DriverHintPacket;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.signal.TrainSignals;
import com.lycoris.loconautics.trainphysics.state.CreateTrainShadow;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.common.sable.SubLevelData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3dc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Evaluates why a Loconautics train will not move when the driver commands traction,
 * and pushes an orange silent HUD hint to nearby players.
 */
public final class DriverHintService {

    private static final double INTENT_TARGET = 1.0e-4;
    private static final double ALERT_RANGE_SQ = 32.0 * 32.0;
    private static final float LOW_PIPE_BAR = 3.5f;
    private static final int TICK_PERIOD = 5;

    private static final Map<UUID, DriverHint> lastSent = new HashMap<>();

    private DriverHintService() {
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % TICK_PERIOD != 0) {
            return;
        }
        if (SableTrainRegistry.isEmpty()) {
            lastSent.clear();
            return;
        }

        Map<UUID, DriverHint> active = new HashMap<>();
        for (SableTrain train : SableTrainRegistry.all()) {
            if (train == null || train.isGhost() || train.level() == null) {
                continue;
            }
            // one evaluation per consist lead (skip trailing ghost mates of the same shadow)
            if (!isConsistLead(train)) {
                continue;
            }
            if (!wantsToMove(train)) {
                continue;
            }
            DriverHint hint = evaluate(train);
            if (hint == DriverHint.NONE) {
                continue;
            }
            active.put(train.id(), hint);
            broadcast(train, hint);
        }

        // clear HUD for trains that no longer need a hint (players near previous positions
        // still get NONE via per-train lastSent change only when a train was active)
        for (Map.Entry<UUID, DriverHint> previous : lastSent.entrySet()) {
            if (!active.containsKey(previous.getKey()) && previous.getValue() != DriverHint.NONE) {
                SableTrain train = SableTrainRegistry.get(previous.getKey());
                if (train != null && train.level() instanceof ServerLevel) {
                    broadcast(train, DriverHint.NONE);
                }
            }
        }
        lastSent.clear();
        lastSent.putAll(active);
    }

    public static void clear() {
        lastSent.clear();
    }

    private static boolean isConsistLead(SableTrain train) {
        List<SableTrain> mates = CreateTrainShadow.ghostMates(train);
        if (mates.isEmpty()) {
            return true;
        }
        return mates.get(0).id().equals(train.id());
    }

    private static boolean wantsToMove(SableTrain train) {
        List<SableTrain> mates = CreateTrainShadow.ghostMates(train);
        if (mates.isEmpty()) {
            mates = List.of(train);
        }
        for (SableTrain car : mates) {
            if (Math.abs(car.targetSpeed()) > INTENT_TARGET) {
                return true;
            }
        }
        return false;
    }

    private static DriverHint evaluate(SableTrain train) {
        List<SableTrain> mates = CreateTrainShadow.ghostMates(train);
        if (mates.isEmpty()) {
            mates = List.of(train);
        }

        boolean stopCock = false;
        boolean brakeEmergency = false;
        boolean safetyLatched = false;
        boolean lowPipe = false;
        boolean doorsOpen = false;
        boolean derailed = false;
        boolean dispatcherHold = false;
        boolean signalHold = false;
        boolean brakeApplied = false;

        for (SableTrain car : mates) {
            if (car.isDerailed()) {
                derailed = true;
            }
            if (DispatcherTrainControl.isHeld(car.id())) {
                dispatcherHold = true;
            }
            if (TrainSignals.isHeld(car.id())) {
                signalHold = true;
            }
            if (car.brakeHold() || car.isBraked()) {
                brakeApplied = true;
            }
            if (car.doorsOpen()) {
                doorsOpen = true;
            }

            ServerSubLevel sub = null;
            if (car.level() instanceof ServerLevel serverLevel && car.car() != null && car.car().subLevelId() != null) {
                sub = SubLevelData.resolve(serverLevel, car.car().subLevelId());
            }
            if (sub == null || sub.isRemoved()) {
                continue;
            }
            for (EmergencyBrakeBlockEntity cock : SubLevelBlocks.findAll(sub, EmergencyBrakeBlockEntity.class)) {
                if (cock.isPulled()) {
                    stopCock = true;
                }
            }
            for (DriverBrakeValveBlockEntity valve : SubLevelBlocks.findAll(sub, DriverBrakeValveBlockEntity.class)) {
                if (valve.getNotch() >= AirBrakeTuning.EMERGENCY_NOTCH) {
                    brakeEmergency = true;
                }
                float pipe = AirPipeBus.readPipePressure(valve);
                if (pipe >= 0f && pipe < LOW_PIPE_BAR && valve.getNotch() > 0) {
                    lowPipe = true;
                }
            }
            for (SafetyBlockEntity safety : SubLevelBlocks.findAll(sub, SafetyBlockEntity.class)) {
                if (safety.isEmergency()) {
                    safetyLatched = true;
                }
            }
            // pipe low without a valve present: any emergency cock still vents the bus
            if (!lowPipe) {
                for (EmergencyBrakeBlockEntity cock : SubLevelBlocks.findAll(sub, EmergencyBrakeBlockEntity.class)) {
                    float pipe = AirPipeBus.readPipePressure(cock);
                    if (pipe >= 0f && pipe < LOW_PIPE_BAR) {
                        lowPipe = true;
                        break;
                    }
                }
            }
        }

        if (stopCock) return DriverHint.STOP_COCK;
        if (brakeEmergency) return DriverHint.BRAKE_EMERGENCY;
        if (derailed) return DriverHint.DERAILED;
        if (safetyLatched) return DriverHint.SAFETY_LATCHED;
        if (dispatcherHold) return DriverHint.DISPATCHER_HOLD;
        if (signalHold) return DriverHint.SIGNAL_HOLD;
        if (brakeApplied) return DriverHint.BRAKE_APPLIED;
        if (lowPipe) return DriverHint.LOW_PIPE;
        if (doorsOpen) return DriverHint.DOORS_OPEN;
        return DriverHint.NONE;
    }

    private static void broadcast(SableTrain train, DriverHint hint) {
        DriverHint previous = lastSent.get(train.id());
        if (previous == hint) {
            // still refresh keep-alive on clients periodically
            if (hint == DriverHint.NONE) {
                return;
            }
        }

        Vec3 pos = positionOf(train);
        if (pos == null || !(train.level() instanceof ServerLevel level)) {
            return;
        }

        DriverHintPacket packet = DriverHintPacket.of(hint);
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            if (player.position().distanceToSqr(pos) <= ALERT_RANGE_SQ) {
                PacketDistributor.sendToPlayer(player, packet);
            }
        }
    }

    private static Vec3 positionOf(SableTrain train) {
        Vector3dc known = train.lastKnownPos();
        if (known != null && Double.isFinite(known.x()) && Double.isFinite(known.y()) && Double.isFinite(known.z())) {
            return new Vec3(known.x(), known.y(), known.z());
        }
        if (train.car() != null && train.car().carriage() != null) {
            Vec3 center = train.car().carriage().moveCenter();
            if (center != null) return center;
        }
        return null;
    }
}
