package com.lycoris.loconautics.common.signal;

public final class SignalSpeed {
    public static final int OFF_SIGNAL = 15;

    private static final int SPEED_STEPS = 16;

    private SignalSpeed() {
    }

    public static boolean isOff(int signal) {
        return signal >= OFF_SIGNAL;
    }

    public static float fraction(int signal) {
        return isOff(signal) ? 0f : (SPEED_STEPS - Math.max(0, signal)) / (float) SPEED_STEPS;
    }

    public static float fractionWithBank(int signal, float banked) {
        return isOff(signal) ? banked : fraction(signal);
    }
}
