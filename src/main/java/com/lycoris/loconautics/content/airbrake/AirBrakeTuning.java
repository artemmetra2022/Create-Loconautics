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

    /** Service cylinder pressure at a full 1.5 bar pipe drop, the old service ceiling. */
    public static final float BASE_SERVICE_CYLINDER_BAR = 3.8f;
    /** Cylinder pressure at the deepest service step (position 9); positions 8 and 9 brake harder than 2-7. */
    public static final float HEAVY_SERVICE_CYLINDER_BAR = 5.0f;
    private static final float FULL_SERVICE_DROP_BAR = 1.5f;
    private static final float HEAVY_START_DROP_BAR = 1.18f;
    private static final float POSITION_8_DROP_BAR = 1.43f;
    private static final float POSITION_9_DROP_BAR = 1.68f;
    private static final float POSITION_8_CYLINDER_BAR = 4.2f;

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

    /**
     * Service brake cylinder pressure for a brake pipe drop below its reference. Positions 2-7 keep the
     * original linear curve; past position 7 the curve keeps rising through 8 (4.2 bar) up to 9 (5.0 bar),
     * so the last two steps brake clearly harder than the rest without reaching emergency braking.
     */
    public static float serviceCylinderBar(float pipeDropBar) {
        float drop = Math.max(0f, pipeDropBar);
        if (drop <= HEAVY_START_DROP_BAR) {
            return BASE_SERVICE_CYLINDER_BAR * drop / FULL_SERVICE_DROP_BAR;
        }
        float start = BASE_SERVICE_CYLINDER_BAR * HEAVY_START_DROP_BAR / FULL_SERVICE_DROP_BAR;
        if (drop <= POSITION_8_DROP_BAR) {
            return Mth.lerp((drop - HEAVY_START_DROP_BAR) / (POSITION_8_DROP_BAR - HEAVY_START_DROP_BAR),
                    start, POSITION_8_CYLINDER_BAR);
        }
        float t = Mth.clamp((drop - POSITION_8_DROP_BAR) / (POSITION_9_DROP_BAR - POSITION_8_DROP_BAR), 0f, 1f);
        return Mth.lerp(t, POSITION_8_CYLINDER_BAR, HEAVY_SERVICE_CYLINDER_BAR);
    }

    public static float targetPipeBarForNotch(int notch) {
        int clamped = Mth.clamp(notch, 0, MAX_NOTCH);
        if (clamped <= CHARGE_NOTCH) {
            return WORKING_PIPE_BAR;
        }
        if (clamped >= EMERGENCY_NOTCH) {
            return 0.0f;
        }
        // Calibrated service curve: positions 2-7 remain progressive while
        // 8-9 give a more decisive reduction for stronger service braking.
        return switch (clamped) {
            case 2 -> 4.80f;
            case 3 -> 4.62f;
            case 4 -> 4.43f;
            case 5 -> 4.23f;
            case 6 -> 4.02f;
            case 7 -> 3.82f;
            case 8 -> 3.57f;
            case 9 -> 3.32f;
            default -> WORKING_PIPE_BAR;
        };
    }
}
