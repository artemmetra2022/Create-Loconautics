package com.lycoris.loconautics.content.airbrake;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

/**
 * Air distributor for one car.
 *
 * The legacy TripleValve class and registry id are intentionally retained so
 * existing worlds keep their block entities and saved pressure state.
 */
public class TripleValveBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    private static final float AUX_CHARGE_RATE = 0.035f;
    private static final float EMERGENCY_CHARGE_RATE = 0.025f;
    private static final float SERVICE_APPLY_RATE = 0.055f;
    private static final float EMERGENCY_APPLY_RATE = 0.12f;
    private static final float CYLINDER_RELEASE_RATE = 0.085f;

    private static final float AUX_TO_CYLINDER_RATIO = 0.30f;
    private static final float EMERGENCY_TO_CYLINDER_RATIO = 0.18f;

    private static final float FULL_SERVICE_DROP_BAR = 1.5f;
    private static final float MAX_SERVICE_CYLINDER_BAR = 3.8f;
    private static final float MAX_EMERGENCY_CYLINDER_BAR = 4.5f;
    private static final float MIN_TRIGGER_DROP_BAR = 0.06f;
    private static final float RAPID_PIPE_DROP_BAR = 0.20f;
    private static final float EMERGENCY_RESET_PIPE_BAR = 4.35f;
    private static final float SYNC_STEP = 0.05f;

    private enum DistributorMode {
        CHARGE,
        SERVICE,
        LAP,
        RELEASE,
        EMERGENCY
    }

    private float auxPressure;
    private float emergencyReservoirPressure;
    private float pipePressure;
    private float brakeCylinderPressure;
    private float referencePipePressure = AirBrakeTuning.WORKING_PIPE_BAR;
    private float previousPipePressure = AirBrakeTuning.WORKING_PIPE_BAR;

    private boolean emergency;
    private DistributorMode mode = DistributorMode.CHARGE;

    private float syncedAux;
    private float syncedEmergencyReservoir;
    private float syncedPipe;
    private float syncedCylinder;
    private DistributorMode syncedMode = DistributorMode.CHARGE;
    private boolean restoredToBus;
    private float brakePadWear;
    private float brakePadTemperatureC = 20.0f;
    private float syncedBrakePadWear;
    private float syncedBrakePadTemperatureC = 20.0f;
    private int lastBrakePadWarning;

    public TripleValveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public float getAuxPressure() {
        return auxPressure;
    }

    public float getEmergencyReservoirPressure() {
        return emergencyReservoirPressure;
    }

    public float getPipePressure() {
        return pipePressure;
    }

    public float getBrakeCylinderPressure() {
        return brakeCylinderPressure;
    }

    /**
     * TrainBrakes applies SERVICE_BRAKE_MAX_SCALE to this normalized value.
     * Normalize service pressure to the full service-cylinder pressure; the
     * emergency flag separately bypasses that service cap in TrainBrakes.
     */
    public float getCylinderFraction() {
        return Mth.clamp(brakeCylinderPressure / MAX_SERVICE_CYLINDER_BAR, 0f, 1f);
    }

    public boolean isEmergency() {
        return emergency;
    }

    public float getBrakePadWear() { return brakePadWear; }
    public float getBrakePadTemperatureC() { return brakePadTemperatureC; }

    public float getBrakePadEfficiency() {
        if (!LoconauticsConfig.BRAKE_PAD_WEAR_ENABLED.get()) return 1.0f;
        float wearFactor;
        if (brakePadWear < 0.40f) wearFactor = 1.0f;
        else if (brakePadWear < 0.60f) wearFactor = Mth.lerp((brakePadWear - 0.40f) / 0.20f, 1.0f, 0.95f);
        else if (brakePadWear < 0.75f) wearFactor = Mth.lerp((brakePadWear - 0.60f) / 0.15f, 0.95f, 0.85f);
        else if (brakePadWear < 0.90f) wearFactor = Mth.lerp((brakePadWear - 0.75f) / 0.15f, 0.85f, 0.70f);
        else if (brakePadWear < 1.0f) wearFactor = Mth.lerp((brakePadWear - 0.90f) / 0.10f, 0.70f, 0.50f);
        else wearFactor = 0.0f;
        float heatFactor;
        if (brakePadTemperatureC <= 300.0f) heatFactor = 1.0f;
        else if (brakePadTemperatureC < 550.0f) heatFactor = Mth.lerp((brakePadTemperatureC - 300.0f) / 250.0f, 1.0f, 0.70f);
        else heatFactor = Mth.clamp(0.70f - (brakePadTemperatureC - 550.0f) * 0.00175f, 0.25f, 0.70f);
        return Mth.clamp(wearFactor * heatFactor, 0.0f, 1.0f);
    }

    /** Called by carriage physics at five-tick intervals; speed is in blocks per second. */
    public void tickBrakePadCondition(double speed, double appliedBrake) {
        if (level == null || level.isClientSide || !LoconauticsConfig.BRAKE_PAD_WEAR_ENABLED.get()
                || level.getGameTime() % 5L != 0L) return;
        float biomeTemperature = level.getBiome(worldPosition).value().getBaseTemperature();
        float ambient = Mth.clamp(biomeTemperature * 25.0f - 10.0f, -10.0f, 40.0f);
        boolean raining = level.isRainingAt(worldPosition);
        if (raining) ambient -= 4.0f;
        float oldWear = brakePadWear;
        float oldTemperature = brakePadTemperatureC;
        float brake = Mth.clamp((float) appliedBrake, 0.0f, 1.0f);
        if (speed > 0.1 && brake > 0.01f && brakePadWear < 1.0f) {
            brakePadWear = Mth.clamp(brakePadWear + (float) (speed * brake * 0.0000033
                    * LoconauticsConfig.BRAKE_PAD_WEAR_RATE.get()), 0.0f, 1.0f);
            brakePadTemperatureC = Mth.clamp(brakePadTemperatureC + (float) (speed * brake * 0.012), ambient, 900.0f);
        } else {
            float cooling = Math.max(0.0f, brakePadTemperatureC - ambient) * 0.00035f;
            if (raining) cooling *= 1.5f;
            brakePadTemperatureC = Math.max(ambient, brakePadTemperatureC - cooling);
        }
        if (Math.abs(brakePadWear - oldWear) >= 0.000001f || Math.abs(brakePadTemperatureC - oldTemperature) >= 0.5f
                || Math.abs(brakePadWear - syncedBrakePadWear) >= 0.001f
                || Math.abs(brakePadTemperatureC - syncedBrakePadTemperatureC) >= 1.0f) {
            syncedBrakePadWear = brakePadWear;
            syncedBrakePadTemperatureC = brakePadTemperatureC;
            setChanged();
            sendData();
        }
    }

    /** Returns a warning code only when a new severity threshold is crossed. */
    public int pollBrakePadWarning() {
        int warning = brakePadWear >= 1.0f ? 4
                : brakePadTemperatureC >= 550.0f ? 3
                : brakePadWear >= 0.90f ? 2
                : brakePadWear >= 0.75f ? 1 : 0;
        if (warning < lastBrakePadWarning) {
            lastBrakePadWarning = warning;
            return 0;
        }
        if (warning > lastBrakePadWarning) {
            lastBrakePadWarning = warning;
            return warning;
        }
        return 0;
    }

    public void repairBrakePads() {
        brakePadWear = 0.0f;
        lastBrakePadWarning = 0;
        float biomeTemperature = level == null ? 20.0f : level.getBiome(worldPosition).value().getBaseTemperature() * 25.0f - 10.0f;
        brakePadTemperatureC = Mth.clamp(biomeTemperature, -10.0f, 40.0f);
        syncedBrakePadWear = brakePadWear;
        syncedBrakePadTemperatureC = brakePadTemperatureC;
        setChanged();
        sendData();
    }

    /**
     * Simulates the manual release valve. It exhausts both car reservoirs and
     * the cylinder. The reservoirs recharge only after brake-pipe pressure is
     * restored, so a low pipe cannot immediately re-apply the brake.
     */
    public void releaseManually() {
        auxPressure = 0f;
        emergencyReservoirPressure = 0f;
        brakeCylinderPressure = 0f;
        emergency = false;
        mode = DistributorMode.RELEASE;
        syncValues(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }

        if (!restoredToBus) {
            restoredToBus = true;
            if (pipePressure > 0f) {
                AirPipeBus.writePipePressure(this,
                        Math.max(pipePressure, AirPipeBus.readPipePressure(this)));
            }
        }

        float currentPipe = Mth.clamp(AirPipeBus.readPipePressure(this),
                0f, AirBrakeTuning.WORKING_PIPE_BAR);
        float pipeDropThisTick = previousPipePressure - currentPipe;

        if (currentPipe > referencePipePressure) {
            referencePipePressure = currentPipe;
        }

        // A deep pressure loss or a rapid train-line rupture latches emergency
        // until the pipe has been substantially recharged.
        if (currentPipe < AirBrakeTuning.EMERGENCY_PIPE_BAR
                || pipeDropThisTick >= RAPID_PIPE_DROP_BAR) {
            emergency = true;
        } else if (emergency && currentPipe >= EMERGENCY_RESET_PIPE_BAR) {
            emergency = false;
        }

        // The distributor charges both reservoirs from the brake pipe. The
        // emergency reservoir intentionally has a lower charging rate.
        if (currentPipe > auxPressure) {
            auxPressure = Math.min(AirBrakeTuning.MAX_AUX_RESERVOIR_BAR,
                    auxPressure + Math.min(AUX_CHARGE_RATE, currentPipe - auxPressure));
        }
        if (currentPipe > emergencyReservoirPressure) {
            emergencyReservoirPressure = Math.min(AirBrakeTuning.MAX_AUX_RESERVOIR_BAR,
                    emergencyReservoirPressure
                            + Math.min(EMERGENCY_CHARGE_RATE, currentPipe - emergencyReservoirPressure));
        }

        float serviceTarget = Mth.clamp(
                (referencePipePressure - currentPipe) / FULL_SERVICE_DROP_BAR,
                0f, 1f) * MAX_SERVICE_CYLINDER_BAR;
        float targetCylinder = emergency ? MAX_EMERGENCY_CYLINDER_BAR : serviceTarget;

        if (targetCylinder > brakeCylinderPressure + MIN_TRIGGER_DROP_BAR) {
            if (emergency) {
                applyEmergencyBrake(targetCylinder);
            } else {
                applyServiceBrake(targetCylinder);
            }
        } else if (targetCylinder < brakeCylinderPressure - MIN_TRIGGER_DROP_BAR) {
            // Rising brake-pipe pressure commands a graduated release.
            brakeCylinderPressure = Math.max(targetCylinder,
                    brakeCylinderPressure - CYLINDER_RELEASE_RATE);
        }

        updateMode(targetCylinder);
        pipePressure = currentPipe;
        previousPipePressure = currentPipe;
        syncValues(false);
    }

    private void applyServiceBrake(float targetCylinder) {
        float remaining = Math.min(SERVICE_APPLY_RATE,
                targetCylinder - brakeCylinderPressure);
        // Air is drawn from the auxiliary reservoir; it is not limited by the
        // instantaneous pressure difference between the pipe and reservoir.
        float available = auxPressure * AUX_TO_CYLINDER_RATIO;
        float applied = Math.min(remaining, available);
        if (applied <= 0f) {
            return;
        }
        brakeCylinderPressure += applied;
        auxPressure = Math.max(0f, auxPressure - applied * AUX_TO_CYLINDER_RATIO);
    }

    private void applyEmergencyBrake(float targetCylinder) {
        float remaining = Math.min(EMERGENCY_APPLY_RATE,
                targetCylinder - brakeCylinderPressure);

        // Emergency-reservoir air is used first, then the auxiliary reservoir
        // supplements it if the emergency reservoir is depleted.
        float fromEmergency = Math.min(remaining,
                emergencyReservoirPressure * EMERGENCY_TO_CYLINDER_RATIO);
        if (fromEmergency > 0f) {
            brakeCylinderPressure += fromEmergency;
            emergencyReservoirPressure = Math.max(0f, emergencyReservoirPressure
                    - fromEmergency * EMERGENCY_TO_CYLINDER_RATIO);
            remaining -= fromEmergency;
        }

        if (remaining > 0f) {
            float fromAuxiliary = Math.min(remaining,
                    auxPressure * AUX_TO_CYLINDER_RATIO);
            if (fromAuxiliary > 0f) {
                brakeCylinderPressure += fromAuxiliary;
                auxPressure = Math.max(0f, auxPressure
                        - fromAuxiliary * AUX_TO_CYLINDER_RATIO);
            }
        }

        brakeCylinderPressure = Math.min(brakeCylinderPressure, MAX_EMERGENCY_CYLINDER_BAR);
    }

    private void updateMode(float targetCylinder) {
        if (emergency) {
            mode = DistributorMode.EMERGENCY;
        } else if (brakeCylinderPressure > targetCylinder + MIN_TRIGGER_DROP_BAR) {
            mode = DistributorMode.RELEASE;
        } else if (brakeCylinderPressure + MIN_TRIGGER_DROP_BAR < targetCylinder) {
            mode = auxPressure > 0.02f ? DistributorMode.SERVICE : DistributorMode.LAP;
        } else if (brakeCylinderPressure > 0.03f) {
            mode = DistributorMode.LAP;
        } else {
            mode = DistributorMode.CHARGE;
        }
    }

    private void syncValues(boolean force) {
        if (force
                || Math.abs(auxPressure - syncedAux) >= SYNC_STEP
                || Math.abs(emergencyReservoirPressure - syncedEmergencyReservoir) >= SYNC_STEP
                || Math.abs(pipePressure - syncedPipe) >= SYNC_STEP
                || Math.abs(brakeCylinderPressure - syncedCylinder) >= SYNC_STEP
                || mode != syncedMode) {
            syncedAux = auxPressure;
            syncedEmergencyReservoir = emergencyReservoirPressure;
            syncedPipe = pipePressure;
            syncedCylinder = brakeCylinderPressure;
            syncedMode = mode;
            setChanged();
            sendData();
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("loconautics.triple_valve.goggles.header")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(" ")
                .append(Component.translatable("loconautics.triple_valve.goggles.aux",
                        String.format(Locale.ROOT, "%.2f", auxPressure)))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal(" ")
                .append(Component.translatable("loconautics.triple_valve.goggles.emergency_reservoir",
                        String.format(Locale.ROOT, "%.2f", emergencyReservoirPressure)))
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.literal(" ")
                .append(Component.translatable("loconautics.triple_valve.goggles.cylinder_detail",
                        String.format(Locale.ROOT, "%.2f", brakeCylinderPressure),
                        Math.round(getCylinderFraction() * 100f)))
                .withStyle(emergency ? ChatFormatting.RED
                        : brakeCylinderPressure > 0f ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        String modeKey = switch (mode) {
            case CHARGE -> "charge";
            case SERVICE -> "service";
            case LAP -> "lap";
            case RELEASE -> "release";
            case EMERGENCY -> "emergency";
        };
        tooltip.add(Component.literal(" ")
                .append(Component.translatable("loconautics.triple_valve.goggles.mode",
                        Component.translatable("loconautics.triple_valve.mode." + modeKey)))
                .withStyle(emergency ? ChatFormatting.RED : ChatFormatting.GRAY));
        tooltip.add(Component.literal(" ").append(Component.translatable("loconautics.brake_pads.wear", Math.round(brakePadWear * 100.0f)))
                .withStyle(brakePadWear >= 0.90f ? ChatFormatting.RED : brakePadWear >= 0.75f ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        tooltip.add(Component.literal(" ").append(Component.translatable("loconautics.brake_pads.temperature", Math.round(brakePadTemperatureC)))
                .withStyle(brakePadTemperatureC >= 550.0f ? ChatFormatting.RED : brakePadTemperatureC >= 300.0f ? ChatFormatting.GOLD : ChatFormatting.AQUA));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("AuxPressure", auxPressure);
        tag.putFloat("EmergencyReservoirPressure", emergencyReservoirPressure);
        tag.putFloat("PipePressure", pipePressure);
        tag.putFloat("BrakeCylinderPressure", brakeCylinderPressure);
        tag.putFloat("ReferencePipePressure", referencePipePressure);
        tag.putFloat("PreviousPipePressure", previousPipePressure);
        tag.putBoolean("Emergency", emergency);
        tag.putFloat("BrakePadWear", brakePadWear);
        tag.putFloat("BrakePadTemperatureC", brakePadTemperatureC);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        auxPressure = Mth.clamp(tag.getFloat("AuxPressure"),
                0f, AirBrakeTuning.MAX_AUX_RESERVOIR_BAR);

        if (tag.contains("EmergencyReservoirPressure")) {
            emergencyReservoirPressure = Mth.clamp(tag.getFloat("EmergencyReservoirPressure"),
                    0f, AirBrakeTuning.MAX_AUX_RESERVOIR_BAR);
        } else {
            // Older saves had one reservoir. Seed the new emergency reservoir
            // from that saved pressure instead of deleting the stored air.
            emergencyReservoirPressure = auxPressure;
        }

        pipePressure = Mth.clamp(tag.getFloat("PipePressure"),
                0f, AirBrakeTuning.WORKING_PIPE_BAR);
        if (tag.contains("BrakeCylinderPressure")) {
            brakeCylinderPressure = Mth.clamp(tag.getFloat("BrakeCylinderPressure"),
                    0f, MAX_EMERGENCY_CYLINDER_BAR);
        } else {
            // Migrate the former normalized cylinder fraction to bar.
            brakeCylinderPressure = Mth.clamp(tag.getFloat("Cylinder"), 0f, 1f)
                    * MAX_EMERGENCY_CYLINDER_BAR;
        }
        referencePipePressure = tag.contains("ReferencePipePressure")
                ? Mth.clamp(tag.getFloat("ReferencePipePressure"), 0f, AirBrakeTuning.WORKING_PIPE_BAR)
                : AirBrakeTuning.WORKING_PIPE_BAR;
        previousPipePressure = tag.contains("PreviousPipePressure")
                ? Mth.clamp(tag.getFloat("PreviousPipePressure"), 0f, AirBrakeTuning.WORKING_PIPE_BAR)
                : pipePressure;
        emergency = tag.getBoolean("Emergency");
        brakePadWear = Mth.clamp(tag.getFloat("BrakePadWear"), 0.0f, 1.0f);
        brakePadTemperatureC = Mth.clamp(tag.contains("BrakePadTemperatureC") ? tag.getFloat("BrakePadTemperatureC") : 20.0f, -100.0f, 900.0f);

        syncedAux = auxPressure;
        syncedEmergencyReservoir = emergencyReservoirPressure;
        syncedPipe = pipePressure;
        syncedCylinder = brakeCylinderPressure;
        syncedMode = mode;
        syncedBrakePadWear = brakePadWear;
        syncedBrakePadTemperatureC = brakePadTemperatureC;
    }
}
