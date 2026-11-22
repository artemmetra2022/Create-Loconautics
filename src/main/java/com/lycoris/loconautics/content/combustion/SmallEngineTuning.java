package com.lycoris.loconautics.content.combustion;

import com.lycoris.loconautics.common.signal.SignalSpeed;

public final class SmallEngineTuning {

    private SmallEngineTuning() {}

    public static final float TOP_RPM = 256f;

    public static final float SU_PER_ENGINE = 8192f;

    public static final float SU_PER_RPM = SU_PER_ENGINE / TOP_RPM;

    public static final int MAX_TANDEM = 3;

    public static final int TANK_CAPACITY = 1000;

    public static final int OFF_SIGNAL = SignalSpeed.OFF_SIGNAL;

    public static final int SLOWEST_SIGNAL = 14;

    public static boolean isOff(int signal) {
        return SignalSpeed.isOff(signal);
    }

    public static float speedFraction(int signal) {
        return SignalSpeed.fraction(signal);
    }
}
