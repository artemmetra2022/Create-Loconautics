package com.lycoris.loconautics.content.airbrake;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
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
        tooltip.add(Component.literal(" Аварийный резервуар: "
                        + String.format(Locale.ROOT, "%.2f бар", emergencyReservoirPressure))
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.literal(" Тормозной цилиндр: "
                        + String.format(Locale.ROOT, "%.2f бар (%d%%)", brakeCylinderPressure,
                        Math.round(getCylinderFraction() * 100f)))
                .withStyle(emergency ? ChatFormatting.RED
                        : brakeCylinderPressure > 0f ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        tooltip.add(Component.literal(" Режим: " + switch (mode) {
                    case CHARGE -> "зарядка";
                    case SERVICE -> "служебное торможение";
                    case LAP -> "перекрыша";
                    case RELEASE -> "ступенчатый отпуск";
                    case EMERGENCY -> "экстренное торможение";
                }).withStyle(emergency ? ChatFormatting.RED : ChatFormatting.GRAY));
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

        syncedAux = auxPressure;
        syncedEmergencyReservoir = emergencyReservoirPressure;
        syncedPipe = pipePressure;
        syncedCylinder = brakeCylinderPressure;
        syncedMode = mode;
    }
}
