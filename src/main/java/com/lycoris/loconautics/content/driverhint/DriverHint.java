package com.lycoris.loconautics.content.driverhint;

/**
 * Orange, silent cab hints shown only when the driver tries to move while blocked.
 * Lower ordinal = higher priority. Append only — ids go on the wire.
 */
public enum DriverHint {

    NONE("none"),

    /** emergency stop-cock pulled on the consist */
    STOP_COCK("stop_cock"),

    /** driver brake valve in emergency notch */
    BRAKE_EMERGENCY("brake_emergency"),

    /** consist is derailed */
    DERAILED("derailed"),

    /** safety unit still latched after a trip */
    SAFETY_LATCHED("safety_latched"),

    /** dispatcher hold */
    DISPATCHER_HOLD("dispatcher_hold"),

    /** held at a red signal / forced red */
    SIGNAL_HOLD("signal_hold"),

    /** service brake / brake-hold still applied */
    BRAKE_APPLIED("brake_applied"),

    /** brake pipe pressure too low to release */
    LOW_PIPE("low_pipe"),

    /** passenger doors open */
    DOORS_OPEN("doors_open");

    public static final int COUNT = values().length;

    private final String key;

    DriverHint(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public String langKey() {
        return "loconautics.driver_hint." + key;
    }

    public static DriverHint byId(int id) {
        DriverHint[] all = values();
        if (id < 0 || id >= all.length) {
            return NONE;
        }
        return all[id];
    }
}
