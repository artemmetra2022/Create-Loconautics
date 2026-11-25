package com.lycoris.loconautics.content.controls.panellamp;

import com.lycoris.loconautics.content.airbrake.AirBrakeTuning;
import com.lycoris.loconautics.content.airbrake.DriverBrakeValveBlockEntity;
import com.lycoris.loconautics.content.brakes.BrakeControllerBlockEntity;
import com.lycoris.loconautics.content.combustion.SmallEngineBlockEntity;
import com.lycoris.loconautics.content.safety.SafetyBlockEntity;
import com.lycoris.loconautics.content.speedometer.SpeedometerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public enum SignalLampMode {
    LOW_AIR("low_air", 0xFFFFB52E),
    LOW_PIPE_PRESSURE("low_pipe_pressure", 0xFFFFB52E),
    EMERGENCY_BRAKE_VALVE("emergency_valve", 0xFFFF3434),
    ENGINE_OVERHEAT("engine_overheat", 0xFFFF3434),
    LOW_FUEL("low_fuel", 0xFFFFB52E),
    WHEEL_SLIP("wheel_slip", 0xFFFFE34A),
    HIGH_BRAKE_DEMAND("high_brake_demand", 0xFFFFB52E),
    HIGH_ACCELERATION("high_acceleration", 0xFFFFB52E),
    SAFETY_ALARM("safety_alarm", 0xFFFFB52E),
    SAFETY_WARNING("safety_warning", 0xFFFFB52E),
    SAFETY_EMERGENCY("safety_emergency", 0xFFFF3434),
    EMERGENCY_BRAKE("emergency_brake", 0xFFFF3434),
    SERVICE_BRAKING("service_braking", 0xFFFFB52E);

    private static final float LOW_AIR_THRESHOLD_BAR = 6.0f;
    private static final float LOW_PIPE_THRESHOLD_BAR = AirBrakeTuning.MIN_SERVICE_PIPE_BAR;
    private static final float OVERHEAT_THRESHOLD = 0.85f;
    private static final float LOW_FUEL_THRESHOLD = 0.15f;
    private static final float WHEEL_SLIP_THRESHOLD = 0.20f;
    private static final float HIGH_BRAKE_DEMAND_THRESHOLD = 0.75f;
    private static final float HIGH_ACCELERATION_THRESHOLD = 1.0f;

    private final String id;
    private final int color;

    SignalLampMode(String id, int color) {
        this.id = id;
        this.color = color;
    }

    public String id() {
        return id;
    }

    public String langKey() {
        return "gui.goggles.signal_lamp." + id;
    }

    /** All useful indicators offered by the block currently bound to the lamp. */
    public static List<SignalLampMode> choicesFor(@Nullable BlockEntity source) {
        if (source instanceof DriverBrakeValveBlockEntity) {
            return List.of(LOW_AIR, LOW_PIPE_PRESSURE, EMERGENCY_BRAKE_VALVE);
        }
        if (source instanceof SmallEngineBlockEntity) {
            return List.of(ENGINE_OVERHEAT, LOW_FUEL);
        }
        if (source instanceof SpeedometerBlockEntity) {
            return List.of(WHEEL_SLIP, HIGH_BRAKE_DEMAND, HIGH_ACCELERATION);
        }
        if (source instanceof SafetyBlockEntity) {
            return List.of(SAFETY_ALARM, SAFETY_WARNING, SAFETY_EMERGENCY);
        }
        if (source instanceof BrakeControllerBlockEntity) {
            return List.of(EMERGENCY_BRAKE, SERVICE_BRAKING);
        }
        return List.of();
    }

    @Nullable
    public static SignalLampMode forSource(@Nullable BlockEntity source) {
        List<SignalLampMode> choices = choicesFor(source);
        return choices.isEmpty() ? null : choices.get(0);
    }

    @Nullable
    public static SignalLampMode byId(@Nullable String id) {
        if (id == null || id.isEmpty()) return null;
        for (SignalLampMode mode : values()) {
            if (mode.id.equals(id) || mode.name().toLowerCase(Locale.ROOT).equals(id)) return mode;
        }
        return null;
    }

    public boolean supports(@Nullable BlockEntity source) {
        return switch (this) {
            case LOW_AIR, LOW_PIPE_PRESSURE, EMERGENCY_BRAKE_VALVE ->
                    source instanceof DriverBrakeValveBlockEntity;
            case ENGINE_OVERHEAT, LOW_FUEL -> source instanceof SmallEngineBlockEntity;
            case WHEEL_SLIP, HIGH_BRAKE_DEMAND, HIGH_ACCELERATION ->
                    source instanceof SpeedometerBlockEntity;
            case SAFETY_ALARM, SAFETY_WARNING, SAFETY_EMERGENCY -> source instanceof SafetyBlockEntity;
            case EMERGENCY_BRAKE, SERVICE_BRAKING -> source instanceof BrakeControllerBlockEntity;
        };
    }

    public boolean isActive(@Nullable BlockEntity source) {
        if (!supports(source)) return false;
        return switch (this) {
            case LOW_AIR -> ((DriverBrakeValveBlockEntity) source).getMainPressure() < LOW_AIR_THRESHOLD_BAR;
            case LOW_PIPE_PRESSURE ->
                    ((DriverBrakeValveBlockEntity) source).getPipePressure() < LOW_PIPE_THRESHOLD_BAR;
            case EMERGENCY_BRAKE_VALVE ->
                    ((DriverBrakeValveBlockEntity) source).getNotch() >= AirBrakeTuning.EMERGENCY_NOTCH;
            case ENGINE_OVERHEAT ->
                    ((SmallEngineBlockEntity) source).getHeatFraction() >= OVERHEAT_THRESHOLD;
            case LOW_FUEL -> {
                SmallEngineBlockEntity engine = (SmallEngineBlockEntity) source;
                int capacity = engine.getFuelCapacity();
                yield capacity > 0 && (float) engine.getFuelAmount() / capacity <= LOW_FUEL_THRESHOLD;
            }
            case WHEEL_SLIP ->
                    ((SpeedometerBlockEntity) source).getPoweredAxleSlip() >= WHEEL_SLIP_THRESHOLD;
            case HIGH_BRAKE_DEMAND ->
                    ((SpeedometerBlockEntity) source).getBrakeDemand() >= HIGH_BRAKE_DEMAND_THRESHOLD;
            case HIGH_ACCELERATION ->
                    Math.abs(((SpeedometerBlockEntity) source).getAcceleration()) >= HIGH_ACCELERATION_THRESHOLD;
            case SAFETY_ALARM ->
                    ((SafetyBlockEntity) source).alarmState() != SafetyBlockEntity.ALARM_NONE;
            case SAFETY_WARNING -> ((SafetyBlockEntity) source).isApproachingLimit();
            case SAFETY_EMERGENCY -> ((SafetyBlockEntity) source).isEmergency();
            case EMERGENCY_BRAKE -> ((BrakeControllerBlockEntity) source).isEmergency();
            case SERVICE_BRAKING -> {
                BrakeControllerBlockEntity brake = (BrakeControllerBlockEntity) source;
                yield brake.isBraking() && !brake.isEmergency();
            }
        };
    }

    public boolean warning() {
        return switch (this) {
            case LOW_AIR, LOW_PIPE_PRESSURE, LOW_FUEL, WHEEL_SLIP, HIGH_BRAKE_DEMAND,
                    HIGH_ACCELERATION, SAFETY_WARNING, SERVICE_BRAKING -> true;
            default -> false;
        };
    }

    public int color(@Nullable BlockEntity source) {
        if (this == SAFETY_WARNING && source instanceof SafetyBlockEntity safety
                && safety.isApproachingLimit()) {
            return 0xFFFFB52E;
        }
        return color;
    }

    public String statusKey(@Nullable BlockEntity source) {
        return isActive(source) ? "gui.goggles.signal_lamp.active" : "gui.goggles.signal_lamp.normal";
    }
}
