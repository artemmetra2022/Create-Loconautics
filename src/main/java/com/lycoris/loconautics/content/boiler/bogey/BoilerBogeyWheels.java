package com.lycoris.loconautics.content.boiler.bogey;

import net.minecraft.nbt.CompoundTag;

// both sides, the wheel count lives in the bogey data that create already saves and syncs
public final class BoilerBogeyWheels {

    public static final String KEY = "TrainWheels";
    public static final String PISTONS_REAR_KEY = "PistonsRear";
    public static final int MIN_PAIRS = 0;
    public static final int MAX_PAIRS = 4;

    private BoilerBogeyWheels() {
    }

    public static boolean pistonsRear(CompoundTag bogeyData) {
        return bogeyData.getBoolean(PISTONS_REAR_KEY);
    }

    public static int read(CompoundTag bogeyData) {
        if (!bogeyData.contains(KEY)) return MIN_PAIRS;
        return Math.max(MIN_PAIRS, Math.min(MAX_PAIRS, bogeyData.getInt(KEY)));
    }
}
