package com.lycoris.loconautics.content.airbrake;

import net.minecraft.util.Mth;

public final class AirBrakeTuning {

    public static final float MAX_MAIN_RESERVOIR_BAR = 8.5f;
    public static final float COMPRESSOR_RESUME_BAR = 7.0f;

    public static final float WORKING_PIPE_BAR = 5.0f;
    public static final float MIN_SERVICE_PIPE_BAR = 3.5f;
    public static final float EMERGENCY_PIPE_BAR = 2.5f;

    public static final float MAX_AUX_RESERVOIR_BAR = 5.0f;

    /** Position 0 laps the brake pipe, 1 releases/charges, 2-9 are service steps, 10 is emergency. */
    public static final int CHARGE_NOTCH = 1;
    public static final int FIRST_SERVICE_NOTCH = 2;
    public static final int LAST_SERVICE_NOTCH = 9;
    public static final int EMERGENCY_NOTCH = 10;
    public static final int MAX_NOTCH = EMERGENCY_NOTCH;

    /** Marker used to migrate older saved valves from the original 0-15 range. */
    public static final int NOTCH_DATA_VERSION = 2;
    private static final int LEGACY_LAST_SERVICE_NOTCH = 14;
    private static final int LEGACY_EMERGENCY_NOTCH = 15;

    /** Peak pneumatic service-brake deceleration at the reference consist mass, in m/s². */
    public static final double SERVICE_BRAKE_MAX_DECEL_MPS2 = 0.9;

    /** Peak pneumatic emergency-brake deceleration at the reference consist mass, in m/s². */
    public static final double EMERGENCY_BRAKE_MAX_DECEL_MPS2 = 1.6;

    private AirBrakeTuning() {
    }

    /**
     * Migrates the original 0-15 scale: old 0 was charge, old 1-14 were
     * service braking, and old 15 was emergency.
     */
    public static int migrateLegacyNotch(int legacyNotch) {
        int legacy = Mth.clamp(legacyNotch, 0, LEGACY_EMERGENCY_NOTCH);
        if (legacy == 0) return CHARGE_NOTCH;
        if (legacy >= LEGACY_EMERGENCY_NOTCH) return EMERGENCY_NOTCH;
        float progress = (legacy - 1) / (float) (LEGACY_LAST_SERVICE_NOTCH - 1);
        return Mth.clamp(Math.round(Mth.lerp(progress, FIRST_SERVICE_NOTCH, LAST_SERVICE_NOTCH)),
                FIRST_SERVICE_NOTCH, LAST_SERVICE_NOTCH);
    }

    /** Migrates the intermediate 0-10 scale where 0 still meant charging. */
    public static int migratePreviousNotch(int previousNotch) {
        int previous = Mth.clamp(previousNotch, 0, EMERGENCY_NOTCH);
        if (previous == 0) return CHARGE_NOTCH;
        if (previous >= EMERGENCY_NOTCH) return EMERGENCY_NOTCH;
        float progress = (previous - 1) / (float) (LAST_SERVICE_NOTCH - FIRST_SERVICE_NOTCH);
        return Mth.clamp(Math.round(Mth.lerp(progress, FIRST_SERVICE_NOTCH, LAST_SERVICE_NOTCH)),
                FIRST_SERVICE_NOTCH, LAST_SERVICE_NOTCH);
    }

    public static float targetPipeBarForNotch(int notch) {
        int clamped = Mth.clamp(notch, 0, MAX_NOTCH);
        if (clamped <= CHARGE_NOTCH) {
            return WORKING_PIPE_BAR;
        }
        if (clamped >= EMERGENCY_NOTCH) {
            return 0.0f;
        }
        float t = (clamped - FIRST_SERVICE_NOTCH)
                / (float) (LAST_SERVICE_NOTCH - FIRST_SERVICE_NOTCH);
        return Mth.lerp(t, WORKING_PIPE_BAR - 0.2f, MIN_SERVICE_PIPE_BAR);
    }
}
