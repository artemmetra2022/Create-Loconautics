package com.lycoris.loconautics.content.airbrake;

import net.minecraft.util.Mth;

public final class AirBrakeTuning {

    public static final float MAX_MAIN_RESERVOIR_BAR = 8.5f;
    public static final float COMPRESSOR_RESUME_BAR = 7.0f;

    public static final float WORKING_PIPE_BAR = 5.0f;
    public static final float MIN_SERVICE_PIPE_BAR = 3.5f;
    public static final float EMERGENCY_PIPE_BAR = 2.5f;

    public static final float MAX_AUX_RESERVOIR_BAR = 5.0f;

    /** Positions 1-9 are graduated service braking; position 10 is emergency. */
    public static final int LAST_SERVICE_NOTCH = 9;
    public static final int EMERGENCY_NOTCH = 10;
    public static final int MAX_NOTCH = EMERGENCY_NOTCH;

    /** Marker used to migrate older saved valves from the original 0-15 range. */
    public static final int NOTCH_DATA_VERSION = 1;
    private static final int LEGACY_LAST_SERVICE_NOTCH = 14;
    private static final int LEGACY_EMERGENCY_NOTCH = 15;

    /** Peak pneumatic service-brake deceleration at the reference consist mass, in m/s². */
    public static final double SERVICE_BRAKE_MAX_DECEL_MPS2 = 0.9;

    /** Peak pneumatic emergency-brake deceleration at the reference consist mass, in m/s². */
    public static final double EMERGENCY_BRAKE_MAX_DECEL_MPS2 = 1.6;

    private AirBrakeTuning() {
    }

    /**
     * Converts a previously saved 0-15 valve setting to the current 0-10 scale.
     * Old emergency position 15 remains emergency; service positions are
     * proportionally mapped to positions 1-9 to preserve their approximate
     * reduction in brake-pipe pressure.
     */
    public static int migrateLegacyNotch(int legacyNotch) {
        int legacy = Mth.clamp(legacyNotch, 0, LEGACY_EMERGENCY_NOTCH);
        if (legacy == 0) return 0;
        if (legacy >= LEGACY_EMERGENCY_NOTCH) return EMERGENCY_NOTCH;
        return Mth.clamp(Math.round(legacy * LAST_SERVICE_NOTCH
                / (float) LEGACY_LAST_SERVICE_NOTCH), 1, LAST_SERVICE_NOTCH);
    }

    public static float targetPipeBarForNotch(int notch) {
        int clamped = Mth.clamp(notch, 0, MAX_NOTCH);
        if (clamped == 0) {
            return WORKING_PIPE_BAR;
        }
        if (clamped >= EMERGENCY_NOTCH) {
            return 0.0f;
        }
        float t = (clamped - 1) / (float) (LAST_SERVICE_NOTCH - 1);
        return Mth.lerp(t, WORKING_PIPE_BAR - 0.2f, MIN_SERVICE_PIPE_BAR);
    }
}
