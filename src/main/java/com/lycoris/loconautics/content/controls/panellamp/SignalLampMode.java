package com.lycoris.loconautics.content.controls.panellamp;

import com.lycoris.loconautics.content.airbrake.DriverBrakeValveBlockEntity;
import com.lycoris.loconautics.content.brakes.BrakeControllerBlockEntity;
import com.lycoris.loconautics.content.combustion.SmallEngineBlockEntity;
import com.lycoris.loconautics.content.safety.SafetyBlockEntity;
import com.lycoris.loconautics.content.speedometer.SpeedometerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public enum SignalLampMode {
    LOW_AIR("low_air", 0xFFFFB52E),
    ENGINE_OVERHEAT("engine_overheat", 0xFFFF3434),
    WHEEL_SLIP("wheel_slip", 0xFFFFE34A),
    SAFETY_ALARM("safety_alarm", 0xFFFFB52E),
    EMERGENCY_BRAKE("emergency_brake", 0xFFFF3434);

    /** Warn when the main reservoir falls below a useful operating reserve. */
    private static final float LOW_AIR_THRESHOLD_BAR = 6.0f;
    private static final float OVERHEAT_THRESHOLD = 0.85f;
    private static final float WHEEL_SLIP_THRESHOLD = 0.20f;

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

    @Nullable
    public static SignalLampMode forSource(@Nullable BlockEntity source) {
        if (source instanceof DriverBrakeValveBlockEntity) return LOW_AIR;
        if (source instanceof SmallEngineBlockEntity) return ENGINE_OVERHEAT;
        if (source instanceof SpeedometerBlockEntity) return WHEEL_SLIP;
        if (source instanceof SafetyBlockEntity) return SAFETY_ALARM;
        if (source instanceof BrakeControllerBlockEntity) return EMERGENCY_BRAKE;
        return null;
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
            case LOW_AIR -> source instanceof DriverBrakeValveBlockEntity;
            case ENGINE_OVERHEAT -> source instanceof SmallEngineBlockEntity;
            case WHEEL_SLIP -> source instanceof SpeedometerBlockEntity;
            case SAFETY_ALARM -> source instanceof SafetyBlockEntity;
            case EMERGENCY_BRAKE -> source instanceof BrakeControllerBlockEntity;
        };
    }

    public boolean isActive(@Nullable BlockEntity source) {
        if (!supports(source)) return false;
        return switch (this) {
            case LOW_AIR -> ((DriverBrakeValveBlockEntity) source).getMainPressure() < LOW_AIR_THRESHOLD_BAR;
            case ENGINE_OVERHEAT -> ((SmallEngineBlockEntity) source).getHeatFraction() >= OVERHEAT_THRESHOLD;
            case WHEEL_SLIP -> ((SpeedometerBlockEntity) source).getPoweredAxleSlip() >= WHEEL_SLIP_THRESHOLD;
            case SAFETY_ALARM -> ((SafetyBlockEntity) source).alarmState() != SafetyBlockEntity.ALARM_NONE;
            case EMERGENCY_BRAKE -> ((BrakeControllerBlockEntity) source).isEmergency();
        };
    }

    public int color(@Nullable BlockEntity source) {
        if (this == SAFETY_ALARM && source instanceof SafetyBlockEntity safety
                && safety.alarmState() == SafetyBlockEntity.ALARM_APPROACHING_LIMIT) {
            return 0xFFFFB52E;
        }
        return color;
    }

    public String statusKey(@Nullable BlockEntity source) {
        return isActive(source) ? "gui.goggles.signal_lamp.active" : "gui.goggles.signal_lamp.normal";
    }
}
