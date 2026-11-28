package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.config.LoconauticsConfig;

public final class AxleImpact {

    public static final double MASS_DIVISOR = 10.0;
    public static final double BASE_IMPACT = 1.0;
    public static final double SLOPE_STRESS_PER_DEGREE = 0.5;

    private AxleImpact() {
    }

    public static double of(double massShare, double slopeAngleDeg, boolean opposed) {
        double slope = LoconauticsConfig.SLOPE_EFFECTS_ENABLED.get()
                ? SLOPE_STRESS_PER_DEGREE * slopeAngleDeg
                : 0.0;
        return AxleGearing.stressImpact(BASE_IMPACT, massShare, MASS_DIVISOR, slope,
                opposed ? LoconauticsConfig.OPPOSED_STRESS_FACTOR.get() : 1.0);
    }
}
