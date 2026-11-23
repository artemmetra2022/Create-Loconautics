package com.lycoris.loconautics.content.speedometer;

import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.drive.ConsistPower;
import com.lycoris.loconautics.trainphysics.drive.TrainPropulsion;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

import java.util.List;
import java.util.Locale;

public class SpeedometerBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    /** blocks per second that give a full 15 comparator signal, 50 is 180 km/h */
    public static final double FULL_SCALE = 50.0;

    private static final int SAMPLE_TICKS = 2;
    private static final float SMOOTHING = 0.5f;
    private static final float SYNC_STEP = 0.05f;

    private static final double DISTANCE_SYNC_STEP = 5.0;

    /** full dial scale for acceleration, in m/s^2; matches a brisk locomotive start/stop */
    public static final double ACCELERATION_FULL_SCALE = 1.5;
    private static final float ACCELERATION_SMOOTHING = 0.3f;
    private static final float ACCELERATION_SYNC_STEP = 0.02f;

    private float speed;
    private float syncedSpeed;
    private float acceleration;
    private float syncedAcceleration;
    private int signal;

    // Train telemetry is sampled on the server and synced for the cab gauges.
    private float consistMass;
    private float trackGrade;
    private float adhesionLimit;
    private float poweredAxleSlip;
    private float brakeDemand;

    // meters driven, saved with the block, the trip counter can be reset by hand
    private double totalMeters;
    private double tripMeters;
    private double syncedMeters;

    public SpeedometerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    /** blocks per second */
    public float getSpeed() {
        return speed;
    }

    /** signed acceleration in m/s^2, smoothed; positive while speeding up, negative while braking */
    public float getAcceleration() {
        return acceleration;
    }

    public int getSignal() {
        return signal;
    }

    public float getConsistMass() { return consistMass; }
    public float getTrackGrade() { return trackGrade; }
    public float getAdhesionLimit() { return adhesionLimit; }
    public float getPoweredAxleSlip() { return poweredAxleSlip; }
    public float getBrakeDemand() { return brakeDemand; }

    public double getTotalMeters() {
        return totalMeters;
    }

    public double getTripMeters() {
        return tripMeters;
    }

    public void resetTrip() {
        tripMeters = 0.0;
        syncedMeters = totalMeters;
        setChanged();
        sendData();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if ((level.getGameTime() + worldPosition.asLong()) % SAMPLE_TICKS != 0) return;

        float target = (float) measure();
        double driven = target * (SAMPLE_TICKS / 20.0);
        totalMeters += driven;
        tripMeters += driven;
        float previousSpeed = speed;
        speed += (target - speed) * SMOOTHING;
        if (target <= 0f && speed < SYNC_STEP) speed = 0f;

        // change in (unsigned) speed over the sample window, smoothed again so the needle does not jitter
        float instantAccel = (speed - previousSpeed) / (SAMPLE_TICKS / 20f);
        acceleration += (instantAccel - acceleration) * ACCELERATION_SMOOTHING;

        boolean telemetryChanged = updateTrainTelemetry();
        if (Math.abs(speed - syncedSpeed) >= SYNC_STEP
                || Math.abs(totalMeters - syncedMeters) >= DISTANCE_SYNC_STEP
                || Math.abs(acceleration - syncedAcceleration) >= ACCELERATION_SYNC_STEP
                || telemetryChanged) {
            syncedSpeed = speed;
            syncedMeters = totalMeters;
            syncedAcceleration = acceleration;
            setChanged();
            sendData();
        }

        int newSignal = Mth.clamp(Math.round((float) (speed / FULL_SCALE * 15.0)), 0, 15);
        if (newSignal != signal) {
            signal = newSignal;
            setChanged();
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    /**
     * Cache consist telemetry on the speedometer so panel gauges can read it on the
     * client without resolving server-side train physics state.
     */
    private boolean updateTrainTelemetry() {
        SubLevel containing = Sable.HELPER.getContaining(this);
        SableTrain train = containing == null ? null : SableTrainRegistry.bySubLevel(containing.getUniqueId());

        float mass = 0f;
        float grade = 0f;
        float adhesion = 0f;
        float slip = 0f;
        float brake = 0f;
        if (train != null && !train.isGhost()) {
            ConsistPower.Result consist = ConsistPower.resultFor(train);
            mass = consist == null ? 0f : (float) consist.totalMass();
            grade = (float) TrainPropulsion.gradePercent(train);
            adhesion = (float) TrainPropulsion.adhesionAccelLimitMps2(train);
            slip = (float) TrainPropulsion.poweredAxleSlipRatio(train);
            brake = (float) Mth.clamp(train.brakeScale(), 0.0, 1.0);
        }

        boolean changed = Math.abs(mass - consistMass) >= Math.max(1f, consistMass * 0.01f)
                || Math.abs(grade - trackGrade) >= 0.1f
                || Math.abs(adhesion - adhesionLimit) >= 0.05f
                || Math.abs(slip - poweredAxleSlip) >= 0.01f
                || Math.abs(brake - brakeDemand) >= 0.01f;
        if (changed) {
            consistMass = mass;
            trackGrade = grade;
            adhesionLimit = adhesion;
            poweredAxleSlip = slip;
            brakeDemand = brake;
        }
        return changed;
    }

    // a speedometer standing in the world and not on a carriage reads zero
    private double measure() {
        SubLevel sub = Sable.HELPER.getContaining(this);
        if (!(sub instanceof ServerSubLevel)) return 0.0;
        Vector3d velocity = SubLevelData.linearVelocity(sub);
        return velocity == null ? 0.0 : velocity.length();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        LoconauticsLang.translate("gui.goggles.speedometer_stats").forGoggles(tooltip);
        LoconauticsLang.translate("gui.goggles.speedometer_speed")
                .style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.builder()
                .text(String.format(Locale.US, "%.1f km/h", speed * 3.6f))
                .style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        LoconauticsLang.builder()
                .text(String.format(Locale.US, "%.1f m/s", speed))
                .style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        LoconauticsLang.translate("gui.goggles.speedometer_distance")
                .style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.builder()
                .text(String.format(Locale.US, "%.2f km", totalMeters / 1000.0))
                .style(ChatFormatting.GOLD).forGoggles(tooltip, 1);
        LoconauticsLang.translate("gui.goggles.speedometer_trip")
                .style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.builder()
                .text(String.format(Locale.US, "%.2f km", tripMeters / 1000.0))
                .style(ChatFormatting.GOLD).forGoggles(tooltip, 1);

        SubLevel containing = Sable.HELPER.getContaining(this);
        SableTrain train = containing == null ? null : SableTrainRegistry.bySubLevel(containing.getUniqueId());
        if (train != null && !train.isGhost()) {
            ConsistPower.Result consist = ConsistPower.resultFor(train);
            double mass = consist == null ? 0.0 : consist.totalMass();
            tooltip.add(Component.translatable("loconautics.gui.goggles.speedometer_train")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("loconautics.gui.goggles.speedometer_mass",
                    String.format(Locale.US, "%.0f", mass)).withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("loconautics.gui.goggles.speedometer_grade",
                    String.format(Locale.US, "%+.1f", TrainPropulsion.gradePercent(train)))
                    .withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("loconautics.gui.goggles.speedometer_adhesion",
                    String.format(Locale.US, "%.2f", TrainPropulsion.adhesionAccelLimitMps2(train)))
                    .withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("loconautics.gui.goggles.speedometer_slip",
                    String.format(Locale.US, "%.0f", TrainPropulsion.poweredAxleSlipRatio(train) * 100.0))
                    .withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.translatable("loconautics.gui.goggles.speedometer_brake",
                    String.format(Locale.US, "%.0f", train.brakeScale() * 100.0))
                    .withStyle(ChatFormatting.RED));
        }
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("Speed", speed);
        tag.putFloat("Acceleration", acceleration);
        tag.putInt("Signal", signal);
        tag.putFloat("ConsistMass", consistMass);
        tag.putFloat("TrackGrade", trackGrade);
        tag.putFloat("AdhesionLimit", adhesionLimit);
        tag.putFloat("PoweredAxleSlip", poweredAxleSlip);
        tag.putFloat("BrakeDemand", brakeDemand);
        tag.putDouble("TotalMeters", totalMeters);
        tag.putDouble("TripMeters", tripMeters);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        speed = tag.getFloat("Speed");
        acceleration = tag.getFloat("Acceleration");
        signal = tag.getInt("Signal");
        consistMass = tag.getFloat("ConsistMass");
        trackGrade = tag.getFloat("TrackGrade");
        adhesionLimit = tag.getFloat("AdhesionLimit");
        poweredAxleSlip = tag.getFloat("PoweredAxleSlip");
        brakeDemand = tag.getFloat("BrakeDemand");
        syncedSpeed = speed;
        syncedAcceleration = acceleration;
        totalMeters = tag.getDouble("TotalMeters");
        tripMeters = tag.getDouble("TripMeters");
        syncedMeters = totalMeters;
    }
}
