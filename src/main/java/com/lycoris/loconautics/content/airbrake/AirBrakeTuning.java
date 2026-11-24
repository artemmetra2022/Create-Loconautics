package com.lycoris.loconautics.content.airbrake;

import net.minecraft.util.Mth;

public final class AirBrakeTuning {

    public static final float MAX_MAIN_RESERVOIR_BAR = 8.5f;
    public static final float COMPRESSOR_RESUME_BAR = 7.0f;

    public static final float WORKING_PIPE_BAR = 5.0f;
    public static final float MIN_SERVICE_PIPE_BAR = 3.5f;
    public static final float EMERGENCY_PIPE_BAR = 2.5f;

    public static final float MAX_AUX_RESERVOIR_BAR = 5.0f;

    public static final int LAST_SERVICE_NOTCH = 14;
    public static final int EMERGENCY_NOTCH = 15;
    public static final int MAX_NOTCH = EMERGENCY_NOTCH;

    /** Peak pneumatic service-brake deceleration at the reference consist mass, in m/s². */
    public static final double SERVICE_BRAKE_MAX_DECEL_MPS2 = 0.9;

    /** Peak pneumatic emergency-brake deceleration at the reference consist mass, in m/s². */
    public static final double EMERGENCY_BRAKE_MAX_DECEL_MPS2 = 1.6;

    private AirBrakeTuning() {
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
