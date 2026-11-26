package com.lycoris.loconautics.content.boiler;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum FuelGrade implements StringRepresentable {
    POOR, NORMAL, PREMIUM;

    public static final Codec<FuelGrade> CODEC = StringRepresentable.fromEnum(FuelGrade::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase();
    }

    public int levelsPerFirebox() {
        return switch (this) {
            case POOR -> LoconauticsConfig.FIRE_LEVELS_POOR.get();
            case NORMAL -> LoconauticsConfig.FIRE_LEVELS_NORMAL.get();
            case PREMIUM -> LoconauticsConfig.FIRE_LEVELS_PREMIUM.get();
        };
    }
}
