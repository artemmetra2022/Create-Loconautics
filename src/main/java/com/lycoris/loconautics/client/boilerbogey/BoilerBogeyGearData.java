package com.lycoris.loconautics.client.boilerbogey;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// client only, written by the gen boiler bogey gear script, do not edit by hand
@OnlyIn(Dist.CLIENT)
public final class BoilerBogeyGearData {

    public static final float LENGTH = 2.4f;
    public static final int MAX_BONES = 43;
    // indexed by the wheel pair count
    public static final BoilerBogeyGearVariant[] BY_PAIRS = {BoilerBogeyGearPairs0.VARIANT, BoilerBogeyGearPairs1.VARIANT, BoilerBogeyGearPairs2.VARIANT, BoilerBogeyGearPairs3.VARIANT, BoilerBogeyGearPairs4.VARIANT};

    private BoilerBogeyGearData() {
    }
}
