package com.lycoris.loconautics.content.controls.paneldisplay;

import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.content.airbrake.AirReservoirBlockEntity;
import com.lycoris.loconautics.content.airbrake.DriverBrakeValveBlockEntity;
import com.lycoris.loconautics.content.airbrake.TripleValveBlockEntity;
import com.lycoris.loconautics.content.brakes.BrakeControllerBlockEntity;
import com.lycoris.loconautics.content.combustion.SmallEngineBlockEntity;
import com.lycoris.loconautics.content.safety.SafetyBlockEntity;
import com.lycoris.loconautics.content.speedometer.SpeedometerBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;

public record LocomotiveStatusData(
        boolean trainPresent, boolean enginePresent, boolean brakePresent,
        float speedKmh, float acceleration, float consistMass, float gradePercent,
        float adhesionLimit, float wheelSlipPercent, float brakeDemandPercent,
        float brakePadWearPercent, float brakePadTemperatureC, float mainAirBar, float brakePipeBar, float engineRpm, float engineOutputSu,
        float fuelPercent, float engineHeatPercent, int safetyAlarm, boolean emergency) {

    public static LocomotiveStatusData sample(PanelBlockEntity panel) {
        SubLevel found = Sable.HELPER.getContaining(panel);
        if (!(found instanceof ServerSubLevel sub) || sub.isRemoved()) return empty();

        SpeedometerBlockEntity speedometer = null;
        SmallEngineBlockEntity engine = null;
        DriverBrakeValveBlockEntity brakeValve = null;
        BrakeControllerBlockEntity brakeController = null;
        AirReservoirBlockEntity reservoir = null;
        SafetyBlockEntity safety = null;
        TripleValveBlockEntity padValve = null;

        for (BlockEntity be : SubLevelBlocks.blockEntities(sub)) {
            if (be instanceof SpeedometerBlockEntity candidate) {
                if (speedometer == null || Math.abs(candidate.getSpeed()) > Math.abs(speedometer.getSpeed())) speedometer = candidate;
            } else if (be instanceof SmallEngineBlockEntity candidate) {
                if (candidate.isHead() && (engine == null || candidate.getOutputSu() > engine.getOutputSu())) engine = candidate;
            } else if (be instanceof DriverBrakeValveBlockEntity candidate) {
                if (brakeValve == null || candidate.getMainPressure() > brakeValve.getMainPressure()) brakeValve = candidate;
            } else if (be instanceof BrakeControllerBlockEntity candidate) {
                if (brakeController == null || candidate.isEmergency()) brakeController = candidate;
            } else if (be instanceof AirReservoirBlockEntity candidate) {
                if (reservoir == null || candidate.getPressureBar() > reservoir.getPressureBar()) reservoir = candidate;
            } else if (be instanceof SafetyBlockEntity candidate) {
                if (safety == null || candidate.alarmState() > safety.alarmState()) safety = candidate;
            } else if (be instanceof TripleValveBlockEntity candidate) {
                if (padValve == null || candidate.getBrakePadWear() > padValve.getBrakePadWear()
                        || candidate.getBrakePadTemperatureC() > padValve.getBrakePadTemperatureC()) padValve = candidate;
            }
        }

        boolean train = SableTrainRegistry.bySubLevel(sub.getUniqueId()) != null;
        boolean hasEngine = engine != null;
        boolean hasBrakes = brakeValve != null || reservoir != null || padValve != null;
        float mainAir = brakeValve != null ? brakeValve.getMainPressure()
                : reservoir == null ? 0f : reservoir.getPressureBar();
        float pipeAir = brakeValve == null ? 0f : brakeValve.getPipePressure();
        int alarm = safety == null ? SafetyBlockEntity.ALARM_NONE : safety.alarmState();
        boolean emergency = (safety != null && safety.isEmergency())
                || (brakeController != null && brakeController.isEmergency());

        return new LocomotiveStatusData(
                train, hasEngine, hasBrakes,
                speedometer == null ? 0f : speedometer.getSpeed() * 3.6f,
                speedometer == null ? 0f : speedometer.getAcceleration(),
                speedometer == null ? 0f : speedometer.getConsistMass(),
                speedometer == null ? 0f : speedometer.getTrackGrade(),
                speedometer == null ? 0f : speedometer.getAdhesionLimit(),
                speedometer == null ? 0f : speedometer.getPoweredAxleSlip() * 100f,
                speedometer == null ? 0f : speedometer.getBrakeDemand() * 100f,
                padValve == null ? 0f : padValve.getBrakePadWear() * 100f,
                padValve == null ? 20f : padValve.getBrakePadTemperatureC(),
                mainAir, pipeAir,
                engine == null ? 0f : engine.getOutputRpm(),
                engine == null ? 0f : engine.getOutputSu(),
                engine == null || engine.getFuelCapacity() <= 0 ? 0f
                        : 100f * engine.getFuelAmount() / engine.getFuelCapacity(),
                engine == null ? 0f : engine.getHeatFraction() * 100f,
                alarm, emergency);
    }

    public static LocomotiveStatusData empty() {
        return new LocomotiveStatusData(false, false, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                SafetyBlockEntity.ALARM_NONE, false);
    }

    public int severity() {
        if (emergency || safetyAlarm >= SafetyBlockEntity.ALARM_ANTI_ROLLBACK) return 2;
        if (safetyAlarm != SafetyBlockEntity.ALARM_NONE || wheelSlipPercent >= 20f
                || (brakePresent && mainAirBar < 6f) || engineHeatPercent >= 85f
                || brakePadWearPercent >= 90f || brakePadTemperatureC >= 550f) return 1;
        return 0;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(trainPresent);
        buf.writeBoolean(enginePresent);
        buf.writeBoolean(brakePresent);
        buf.writeFloat(speedKmh);
        buf.writeFloat(acceleration);
        buf.writeFloat(consistMass);
        buf.writeFloat(gradePercent);
        buf.writeFloat(adhesionLimit);
        buf.writeFloat(wheelSlipPercent);
        buf.writeFloat(brakeDemandPercent);
        buf.writeFloat(brakePadWearPercent);
        buf.writeFloat(brakePadTemperatureC);
        buf.writeFloat(mainAirBar);
        buf.writeFloat(brakePipeBar);
        buf.writeFloat(engineRpm);
        buf.writeFloat(engineOutputSu);
        buf.writeFloat(fuelPercent);
        buf.writeFloat(engineHeatPercent);
        buf.writeVarInt(safetyAlarm);
        buf.writeBoolean(emergency);
    }

    public static LocomotiveStatusData read(RegistryFriendlyByteBuf buf) {
        return new LocomotiveStatusData(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readBoolean());
    }
}
