package com.lycoris.loconautics.content.airbrake;

import net.minecraft.util.Mth;

public final class AirBrakeTuning {

    public static final float MAX_MAIN_RESERVOIR_BAR = 8.5f;
    public static final float COMPRESSOR_RESUME_BAR = 7.0f;

    public static final float WORKING_PIPE_BAR = 5.0f;
    public static final float MIN_SERVICE_PIPE_BAR = 3.5f;
    public static final float EMERGENCY_PIPE_BAR = 2.5f;

    public static final float MAX_AUX_RESERVOIR_BAR = 5.0f;

    public static final int MAX_NOTCH = 15;

    /**
     * Service air brakes cap below full wheel-lock decel; emergency uses full BRAKE_DECEL.
     */
    public static final double SERVICE_BRAKE_MAX_SCALE = 0.65;

    private AirBrakeTuning() {
    }

    public static float targetPipeBarForNotch(int notch) {
        int clamped = Mth.clamp(notch, 0, MAX_NOTCH);
        if (clamped == 0) {
            return WORKING_PIPE_BAR;
        }
        if (clamped >= MAX_NOTCH) {
            return 0.0f;
        }
        float t = (clamped - 1) / (float) (MAX_NOTCH - 2);
        return Mth.lerp(t, WORKING_PIPE_BAR - 0.2f, MIN_SERVICE_PIPE_BAR);
    }
}
