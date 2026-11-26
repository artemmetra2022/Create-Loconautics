package com.lycoris.loconautics.content.safety;

/**
 * The safety systems a Safety Unit can run. Each one can be switched by hand in the unit's screen and, when a
 * Redstone Link frequency is bound to it, by a toggle: the system works only while the frequency has a signal.
 * The order is the wire order of the packets and the slot of the frequency, so only ever append.
 */
public enum SafetyDevice {

    /** the reverser says forward and the train rolls back (or the other way round): emergency brake */
    ANTI_ROLLBACK("anti_rollback", true),

    /** nose reaches or jumps past a red signal while still moving: emergency brake */
    RED_SIGNAL("red_signal", true),

    /** the speedometer reads more than the set limit: emergency brake */
    OVERSPEED("overspeed", true),

    /** placeholder for a dead man's switch, the driver has to prove he is awake */
    VIGILANCE("vigilance", false),

    /** the train has derailed (via SableTrainRegistry): emergency brake */
    DERAILMENT("derailment", true);

    public static final int COUNT = values().length;

    private final String key;
    private final boolean implemented;

    SafetyDevice(String key, boolean implemented) {
        this.key = key;
        this.implemented = implemented;
    }

    public String key() {
        return key;
    }

    /** false for a placeholder, it can be switched and bound but never brakes */
    public boolean implemented() {
        return implemented;
    }

    public String langKey() {
        return "loconautics.safety.device." + key;
    }

    public static SafetyDevice byIndex(int index) {
        return index >= 0 && index < COUNT ? values()[index] : null;
    }
}
