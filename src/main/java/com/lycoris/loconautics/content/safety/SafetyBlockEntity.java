package com.lycoris.loconautics.content.safety;

import com.lycoris.loconautics.common.LinkHolder;
import com.lycoris.loconautics.common.RedstoneLinkPair;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.content.speedometer.SpeedometerBlock;
import com.lycoris.loconautics.content.speedometer.SpeedometerBlockEntity;
import com.lycoris.loconautics.content.transmission.TransmissionBlock;
import com.lycoris.loconautics.content.transmission.TransmissionBlockEntity;
import com.lycoris.loconautics.network.packets.safety.SafetyAlertPacket;
import com.lycoris.loconautics.network.packets.safety.SafetyOpenPacket;
import com.lycoris.loconautics.content.dispatcher.DispatcherJournal;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.signal.TrainSignalAccessor;
import com.lycoris.loconautics.trainphysics.signal.TrainSignalAccessor.SignalAhead;
import com.lycoris.loconautics.trainphysics.state.CreateTrainShadow;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The brain of the Safety Unit. Every system can be switched in the screen and, with a Redstone Link frequency
 * bound to it, by a toggle: a bound system runs only while its frequency has a signal. A tripped system holds the
 * emergency brake (see TrainBrakes) until the carriage has stood still for a moment.
 * <p>
 * Speed comes from a Speedometer on the same carriage, the reverser position from a Transmission on it. A system
 * that misses one of them says so in the goggles and does nothing.
 */
public class SafetyBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, LinkHolder {

    public static final int DEFAULT_SPEED_LIMIT = 80;
    public static final int MIN_SPEED_LIMIT = 10;
    public static final int MAX_SPEED_LIMIT = 400;

    public static final int ALARM_NONE = 0;
    public static final int ALARM_APPROACHING_LIMIT = 1;
    public static final int ALARM_ANTI_ROLLBACK = 2;
    public static final int ALARM_OVERSPEED = 3;
    public static final int ALARM_DERAILMENT = 4;
    public static final int ALARM_RED_SIGNAL = 5;

    /** problem bits, shown in the goggles */
    public static final int PROBLEM_NO_SPEEDOMETER = 1;
    public static final int PROBLEM_NO_TRANSMISSION = 2;

    private static final int SAMPLE_TICKS = 2;
    private static final int SCAN_TICKS = 20;
    private static final double APPROACHING_RATIO = 0.9;
    private static final double ALERT_RANGE_SQ = 32.0 * 32.0;
    private static final int ALERT_REFRESH_SAMPLES = 10;

    /** blocks per second, below this the train counts as standing (about 1.8 km/h) */
    private static final double ROLLBACK_MIN_SPEED = 0.5;
    private static final double RELEASE_SPEED = 0.2;
    /** samples at rest before the emergency brake lets go, 10 samples are one second */
    private static final int RELEASE_SAMPLES = 10;

    private final RedstoneLinkPair linksLow = new RedstoneLinkPair(this, s -> setSignal(0, s), s -> setSignal(1, s));
    private final RedstoneLinkPair linksHigh = new RedstoneLinkPair(this, s -> setSignal(2, s), s -> setSignal(3, s));
    private final RedstoneLinkPair linksExtra = new RedstoneLinkPair(this, s -> setSignal(4, s), s -> {});

    // saved settings
    private int enabledMask = (1 << SafetyDevice.COUNT) - 1;
    private int speedLimit = DEFAULT_SPEED_LIMIT;
    private boolean invertDirection;

    // live state, synced for the goggles and the screen
    private final int[] signal = new int[SafetyDevice.COUNT];
    private boolean latched;
    private int latchReason = -1;
    private boolean approachingLimit;
    private int activeMask;
    private int problemMask;
    private int calmSamples;

    private boolean syncedLatched;
    private int syncedReason = -1;
    private boolean syncedApproaching;
    private int syncedActive;
    private int syncedProblem;

    private int lastAlertSent = ALARM_NONE;
    private int alertRefreshSamples;

    private final Map<UUID, RedWatch> redWatch = new HashMap<>();

    private record RedWatch(UUID boundary, double gap, boolean danger) {
    }

    // carriage blocks found by the last scan
    private ServerSubLevel scannedSub;
    private long scannedAt = Long.MIN_VALUE;
    private final List<BlockPos> speedometers = new ArrayList<>();
    private final List<BlockPos> transmissions = new ArrayList<>();

    public SafetyBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    // ---- links ----

    @Override
    public void initialize() {
        super.initialize();
        if (level == null || level.isClientSide) return;
        linksLow.register(level);
        linksHigh.register(level);
        linksExtra.register(level);
    }

    @Override
    public void releaseLinks() {
        if (level == null) return;
        linksLow.unregister(level);
        linksHigh.unregister(level);
        linksExtra.unregister(level);
    }

    @Override
    public void invalidate() {
        releaseLinks();
        super.invalidate();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        releaseLinks();
    }

    // the link callback runs inside create's network walk, so it only stores the number
    private void setSignal(int device, int strength) {
        if (signal[device] == strength) return;
        signal[device] = strength;
        setChanged();
    }

    private RedstoneLinkPair pairOf(int device) {
        if (device < 2) return linksLow;
        if (device < 4) return linksHigh;
        return linksExtra;
    }

    public ItemStack freqFirst(int device) {
        RedstoneLinkPair pair = pairOf(device);
        return device % 2 == 0 ? pair.getFirstFreqA() : pair.getSecondFreqA();
    }

    public ItemStack freqSecond(int device) {
        RedstoneLinkPair pair = pairOf(device);
        return device % 2 == 0 ? pair.getFirstFreqB() : pair.getSecondFreqB();
    }

    public boolean hasFrequency(int device) {
        return !freqFirst(device).isEmpty() || !freqSecond(device).isEmpty();
    }

    public int frequencyMask() {
        int mask = 0;
        for (int i = 0; i < SafetyDevice.COUNT; i++) {
            if (hasFrequency(i)) mask |= 1 << i;
        }
        return mask;
    }

    private void storeFrequency(int device, ItemStack first, ItemStack second) {
        RedstoneLinkPair pair = pairOf(device);
        if (device % 2 == 0) {
            pair.setFirstFrequency(first, second);
        } else {
            pair.setSecondFrequency(first, second);
        }
    }

    /** binds the toggle of one system, two empty stacks unbind it */
    public void setFrequency(int device, ItemStack first, ItemStack second) {
        if (device < 0 || device >= SafetyDevice.COUNT) return;
        storeFrequency(device, first, second);
        signal[device] = 0;
        if (level != null && !level.isClientSide) {
            pairOf(device).register(level);
        }
        setChanged();
        sendData();
    }

    // ---- settings ----

    public int enabledMask() {
        return enabledMask;
    }

    public boolean isEnabled(SafetyDevice device) {
        return (enabledMask & (1 << device.ordinal())) != 0;
    }

    public int speedLimit() {
        return speedLimit;
    }

    public boolean invertDirection() {
        return invertDirection;
    }

    public void applyConfiguration(int enabled, int limit, boolean invert) {
        enabledMask = enabled & ((1 << SafetyDevice.COUNT) - 1);
        speedLimit = Mth.clamp(limit, MIN_SPEED_LIMIT, MAX_SPEED_LIMIT);
        invertDirection = invert;
        setChanged();
        sendData();
    }

    public SafetyOpenPacket openPacket() {
        return new SafetyOpenPacket(worldPosition, enabledMask, speedLimit, invertDirection, frequencyMask(),
                activeMask, problemMask);
    }

    // ---- state ----

    /** the system is switched on by hand and, if it has a toggle bound, the toggle is on */
    private boolean isRunning(SafetyDevice device) {
        int i = device.ordinal();
        return device.implemented() && isEnabled(device) && (!hasFrequency(i) || signal[i] > 0);
    }

    /** the unit holds the emergency brake */
    public boolean isEmergency() {
        return latched;
    }

    public boolean isApproachingLimit() {
        return approachingLimit;
    }

    public int latchReason() {
        return latchReason;
    }

    public int alarmState() {
        if (latched) {
            if (latchReason == SafetyDevice.ANTI_ROLLBACK.ordinal()) return ALARM_ANTI_ROLLBACK;
            if (latchReason == SafetyDevice.OVERSPEED.ordinal()) return ALARM_OVERSPEED;
            if (latchReason == SafetyDevice.DERAILMENT.ordinal()) return ALARM_DERAILMENT;
            if (latchReason == SafetyDevice.RED_SIGNAL.ordinal()) return ALARM_RED_SIGNAL;
        }
        if (approachingLimit) return ALARM_APPROACHING_LIMIT;
        return ALARM_NONE;
    }

    public static boolean isEmergencyAlarm(int alarm) {
        return alarm == ALARM_ANTI_ROLLBACK || alarm == ALARM_OVERSPEED || alarm == ALARM_DERAILMENT
                || alarm == ALARM_RED_SIGNAL;
    }

    public Vec3 worldPositionProjected() {
        return Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(worldPosition));
    }

    public int activeMask() {
        return activeMask;
    }

    public int problemMask() {
        return problemMask;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) return;
        if (level.isClientSide) {
            SafetyAlertClient.tick(this);
            return;
        }
        if ((level.getGameTime() + worldPosition.asLong()) % SAMPLE_TICKS != 0) return;

        SubLevel found = Sable.HELPER.getContaining(this);
        if (!(found instanceof ServerSubLevel sub) || sub.isRemoved()) {
            // not on a carriage, nothing to guard
            latched = false;
            latchReason = -1;
            approachingLimit = false;
            calmSamples = 0;
            problemMask = 0;
            activeMask = 0;
            redWatch.clear();
            publish();
            if (level instanceof ServerLevel sl && lastAlertSent != ALARM_NONE) {
                broadcastAlerts(sl);
            }
            return;
        }

        rescanIfNeeded(sub);
        ServerLevel sl = sub.getLevel();
        SableTrain train = SableTrainRegistry.bySubLevel(sub.getUniqueId());

        double speed = 0.0;
        boolean hasSpeedometer = false;
        for (BlockPos pos : speedometers) {
            if (sl.getBlockEntity(pos) instanceof SpeedometerBlockEntity speedometer) {
                hasSpeedometer = true;
                speed = Math.max(speed, speedometer.getSpeed());
            }
        }

        // +1 forward, -1 reverse, 0 neutral
        int intended = 0;
        boolean hasTransmission = false;
        for (BlockPos pos : transmissions) {
            if (sl.getBlockEntity(pos) instanceof TransmissionBlockEntity transmission) {
                hasTransmission = true;
                if (!transmission.isNeutral()) {
                    intended = transmission.isReversed() ? -1 : 1;
                    break;
                }
            }
        }

        // speed along the front of the block, positive when the train drives forward
        double along = 0.0;
        Vector3d velocity = SubLevelData.linearVelocity(sub);
        if (velocity != null) {
            Direction front = getBlockState().getValue(SafetyBlock.FACING);
            Vector3d forward = SubLevelData.directionOf(sub, new Vector3d(front.getStepX(), 0.0, front.getStepZ()));
            along = velocity.dot(forward) * (invertDirection ? -1.0 : 1.0);
        }

        int active = 0;
        for (SafetyDevice device : SafetyDevice.values()) {
            if (isRunning(device)) active |= 1 << device.ordinal();
        }
        activeMask = active;

        int problems = 0;
        boolean needsSpeedometer = isRunning(SafetyDevice.ANTI_ROLLBACK) || isRunning(SafetyDevice.OVERSPEED);
        if (needsSpeedometer && !hasSpeedometer) problems |= PROBLEM_NO_SPEEDOMETER;
        if (isRunning(SafetyDevice.ANTI_ROLLBACK) && !hasTransmission) problems |= PROBLEM_NO_TRANSMISSION;
        problemMask = problems;

        int reason = -1;
        if (isRunning(SafetyDevice.DERAILMENT) && train != null && train.isDerailed()) {
            reason = SafetyDevice.DERAILMENT.ordinal();
        } else if (isRunning(SafetyDevice.RED_SIGNAL) && passedRed(train)) {
            reason = SafetyDevice.RED_SIGNAL.ordinal();
        } else if (isRunning(SafetyDevice.ANTI_ROLLBACK) && hasSpeedometer && hasTransmission && intended != 0
                && speed >= ROLLBACK_MIN_SPEED && along * intended < -ROLLBACK_MIN_SPEED) {
            // reverser says one way, the carriage moves the other way
            reason = SafetyDevice.ANTI_ROLLBACK.ordinal();
        } else if (isRunning(SafetyDevice.OVERSPEED) && hasSpeedometer && speed * 3.6 > speedLimit) {
            reason = SafetyDevice.OVERSPEED.ordinal();
        }
        // VIGILANCE is still a placeholder and never trips

        int previousReason = latched ? latchReason : -1;
        if (reason >= 0) {
            latched = true;
            latchReason = reason;
            approachingLimit = false;
            calmSamples = 0;
            if (previousReason != reason && reason == SafetyDevice.RED_SIGNAL.ordinal() && train != null) {
                String label = train.name() == null || train.name().isBlank()
                        ? train.id().toString().substring(0, 8) : train.name();
                DispatcherJournal.record(DispatcherJournal.SPAD, label);
            }
        } else if (latched) {
            approachingLimit = false;
            boolean reasonStillRuns = latchReason >= 0 && (activeMask & (1 << latchReason)) != 0;
            if (!reasonStillRuns) {
                // the system that tripped was switched off, let the brake go at once
                release();
            } else if (latchReason == SafetyDevice.DERAILMENT.ordinal()
                    && train != null && !train.isDerailed() && speed < RELEASE_SPEED) {
                if (++calmSamples >= RELEASE_SAMPLES) release();
            } else if (latchReason != SafetyDevice.DERAILMENT.ordinal() && speed < RELEASE_SPEED) {
                if (++calmSamples >= RELEASE_SAMPLES) release();
            } else {
                calmSamples = 0;
            }
        } else {
            // 90% speed limit warning: warns the cab without latching the brake
            approachingLimit = isRunning(SafetyDevice.OVERSPEED) && hasSpeedometer
                    && speed * 3.6 >= speedLimit * APPROACHING_RATIO;
        }

        publish();
        broadcastAlerts(sl);
    }

    private void broadcastAlerts(ServerLevel sl) {
        int alarm = alarmState();
        boolean changed = alarm != lastAlertSent;
        if (alarm == ALARM_NONE && !changed) {
            alertRefreshSamples = 0;
            return;
        }
        if (!changed && ++alertRefreshSamples < ALERT_REFRESH_SAMPLES) {
            return;
        }
        alertRefreshSamples = 0;
        lastAlertSent = alarm;

        Vec3 worldPos = worldPositionProjected();
        Component actionBar = alarm == ALARM_NONE ? null : alertComponent(alarm);
        SafetyAlertPacket packet = new SafetyAlertPacket(alarm);

        for (ServerPlayer player : sl.players()) {
            if (player.isSpectator()) continue;
            if (player.position().distanceToSqr(worldPos) <= ALERT_RANGE_SQ) {
                PacketDistributor.sendToPlayer(player, packet);
                if (actionBar != null) {
                    player.displayClientMessage(actionBar, true);
                }
            }
        }
    }

    private static Component alertComponent(int alarm) {
        return switch (alarm) {
            case ALARM_APPROACHING_LIMIT -> Component.translatable("loconautics.safety.alert.approaching_limit")
                    .withStyle(ChatFormatting.GOLD);
            case ALARM_ANTI_ROLLBACK -> Component.translatable("loconautics.safety.alert.anti_rollback")
                    .withStyle(ChatFormatting.RED);
            case ALARM_OVERSPEED -> Component.translatable("loconautics.safety.alert.overspeed")
                    .withStyle(ChatFormatting.RED);
            case ALARM_DERAILMENT -> Component.translatable("loconautics.safety.alert.derailment")
                    .withStyle(ChatFormatting.RED);
            case ALARM_RED_SIGNAL -> Component.translatable("loconautics.safety.alert.red_signal")
                    .withStyle(ChatFormatting.RED);
            default -> Component.empty();
        };
    }


    private boolean passedRed(SableTrain train) {
        if (train == null) {
            redWatch.clear();
            return false;
        }
        List<SableTrain> cars = CreateTrainShadow.ghostMates(train);
        if (cars.isEmpty()) {
            cars = List.of(train);
        }
        boolean passed = false;
        for (SableTrain car : cars) {
            if (car == null || car.id() == null) continue;
            double blocksPerSecond = Math.abs(car.speed()) * 20.0;
            SignalAhead ahead = TrainSignalAccessor.nextSignal(car);
            boolean danger = TrainSignalAccessor.isDanger(ahead);
            UUID boundary = ahead == null ? null : ahead.signal().getId();
            double gap = ahead == null ? Double.NaN : ahead.distance();
            RedWatch previous = redWatch.get(car.id());
            if (blocksPerSecond >= RELEASE_SPEED) {
                if (danger && !Double.isNaN(gap) && gap <= 0.0) {
                    passed = true;
                } else if (previous != null && previous.danger && previous.boundary != null
                        && !previous.boundary.equals(boundary)
                        && !Double.isNaN(previous.gap)
                        && previous.gap <= Math.max(2.0, blocksPerSecond * 0.25 + 1.0)) {
                    passed = true;
                }
            }
            redWatch.put(car.id(), new RedWatch(boundary, gap, danger));
        }
        return passed;
    }

    private void release() {
        latched = false;
        latchReason = -1;
        calmSamples = 0;
    }

    private void rescanIfNeeded(ServerSubLevel sub) {
        long now = sub.getLevel().getGameTime();
        if (sub == scannedSub && now >= scannedAt && now - scannedAt < SCAN_TICKS) return;
        scannedSub = sub;
        scannedAt = now;
        speedometers.clear();
        transmissions.clear();
        SubLevelBlocks.forEachBlock(sub, (pos, state) -> {
            if (state.getBlock() instanceof SpeedometerBlock) {
                speedometers.add(pos.immutable());
            } else if (state.getBlock() instanceof TransmissionBlock) {
                transmissions.add(pos.immutable());
            }
            return true;
        });
    }

    // sends the state to the clients and lights the block, only when something changed
    private void publish() {
        boolean changed = latched != syncedLatched || latchReason != syncedReason
                || approachingLimit != syncedApproaching
                || activeMask != syncedActive || problemMask != syncedProblem;
        if (!changed) return;
        boolean latchChanged = latched != syncedLatched;
        syncedLatched = latched;
        syncedReason = latchReason;
        syncedApproaching = approachingLimit;
        syncedActive = activeMask;
        syncedProblem = problemMask;
        setChanged();
        sendData();
        if (latchChanged && level != null) {
            BlockState state = getBlockState();
            if (state.getBlock() instanceof SafetyBlock && state.getValue(SafetyBlock.TRIPPED) != latched) {
                level.setBlock(worldPosition, state.setValue(SafetyBlock.TRIPPED, latched), 2);
            }
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    // ---- goggles ----

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("loconautics.safety.goggles.header").withStyle(ChatFormatting.GRAY));
        for (SafetyDevice device : SafetyDevice.values()) {
            int i = device.ordinal();
            Component state;
            if (!device.implemented()) {
                state = Component.translatable("loconautics.safety.goggles.stub").withStyle(ChatFormatting.DARK_GRAY);
            } else if (!isEnabled(device)) {
                state = Component.translatable("loconautics.safety.goggles.off").withStyle(ChatFormatting.RED);
            } else if ((activeMask & (1 << i)) == 0) {
                state = Component.translatable("loconautics.safety.goggles.toggle_off").withStyle(ChatFormatting.GOLD);
            } else if (latched && latchReason == i) {
                state = Component.translatable("loconautics.safety.goggles.tripped").withStyle(ChatFormatting.RED);
            } else if (device == SafetyDevice.OVERSPEED && approachingLimit) {
                state = Component.translatable("loconautics.safety.goggles.warning").withStyle(ChatFormatting.GOLD);
            } else {
                state = Component.translatable("loconautics.safety.goggles.armed").withStyle(ChatFormatting.GREEN);
            }
            tooltip.add(Component.literal(" ")
                    .append(Component.translatable(device.langKey()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                    .append(state));
        }
        if ((problemMask & PROBLEM_NO_SPEEDOMETER) != 0) {
            tooltip.add(Component.literal(" ").append(
                    Component.translatable("loconautics.safety.problem.no_speedometer")).withStyle(ChatFormatting.RED));
        }
        if ((problemMask & PROBLEM_NO_TRANSMISSION) != 0) {
            tooltip.add(Component.literal(" ").append(
                    Component.translatable("loconautics.safety.problem.no_transmission")).withStyle(ChatFormatting.RED));
        }
        return true;
    }

    // ---- saving ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Enabled", enabledMask);
        tag.putInt("SpeedLimit", speedLimit);
        tag.putBoolean("Invert", invertDirection);
        tag.putBoolean("Latched", latched);
        tag.putInt("Reason", latchReason);
        tag.putBoolean("Approaching", approachingLimit);
        tag.putInt("Active", activeMask);
        tag.putInt("Problem", problemMask);
        writeFrequencies(tag, registries);
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        writeFrequencies(tag, registries);
    }

    private void writeFrequencies(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag freq = new CompoundTag();
        for (int i = 0; i < SafetyDevice.COUNT; i++) {
            freq.put("A" + i, freqFirst(i).saveOptional(registries));
            freq.put("B" + i, freqSecond(i).saveOptional(registries));
        }
        tag.put("Frequency", freq);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        enabledMask = tag.contains("Enabled") ? tag.getInt("Enabled") : (1 << SafetyDevice.COUNT) - 1;
        speedLimit = tag.contains("SpeedLimit")
                ? Mth.clamp(tag.getInt("SpeedLimit"), MIN_SPEED_LIMIT, MAX_SPEED_LIMIT) : DEFAULT_SPEED_LIMIT;
        invertDirection = tag.getBoolean("Invert");
        latched = tag.getBoolean("Latched");
        latchReason = tag.contains("Reason") ? tag.getInt("Reason") : -1;
        approachingLimit = tag.getBoolean("Approaching");
        activeMask = tag.getInt("Active");
        problemMask = tag.getInt("Problem");
        syncedLatched = latched;
        syncedReason = latchReason;
        syncedApproaching = approachingLimit;
        syncedActive = activeMask;
        syncedProblem = problemMask;
        if (tag.contains("Frequency")) {
            CompoundTag freq = tag.getCompound("Frequency");
            for (int i = 0; i < SafetyDevice.COUNT; i++) {
                storeFrequency(i,
                        ItemStack.parseOptional(registries, freq.getCompound("A" + i)),
                        ItemStack.parseOptional(registries, freq.getCompound("B" + i)));
            }
        }
    }
}
