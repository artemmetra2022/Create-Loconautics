package com.lycoris.loconautics.content.boiler;

import com.lycoris.loconautics.config.LoconauticsConfig;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record BoilerStructure(
        BlockPos controllerPos,
        List<BlockPos> fireboxPositions,
        List<BlockPos> boilerPositions,
        @Nullable BlockPos stackPos,
        @Nullable BlockPos injectorPos,
        @Nullable BlockPos takeoffPos,
        @Nullable BlockPos safetyValvePos,
        List<BlockPos> connectorPositions,
        float fireboxHeat,
        float boilerCapacity,
        int waterCapacity
) {
    public static final float OPTIMAL_BODY_PER_FIREBOX = 3f;

    public static final int MIN_BODY_BLOCKS = 1;

    public static final int MAX_COUNTED_FIREBOXES = 3;

    public static final int MAX_COUNTED_BODY_BLOCKS = 9;

    // a firebox counts as a quarter of a body for size and water
    public static final float FIREBOX_BODY_SHARE = 0.25f;

    public int sizeLevels() {
        return BoilerLevel.sizeLevels(boilerCapacity);
    }

    public int fireLevels(FuelGrade grade) {
        return BoilerLevel.fireLevels(fireboxHeat, grade.levelsPerFirebox());
    }

    public int level(FuelGrade grade) {
        return BoilerLevel.level(sizeLevels(), fireLevels(grade), LoconauticsConfig.BOILER_MAX_LEVEL.get());
    }

    public float optimalHeat() {
        return boilerCapacity / OPTIMAL_BODY_PER_FIREBOX;
    }

    public float heatRatio() {
        float optimal = optimalHeat();
        return optimal <= 0f ? 0f : fireboxHeat / optimal;
    }

    public boolean isUnderfired() {
        return heatRatio() < 1f;
    }

    public boolean isOverfired() {
        return heatRatio() > 1f;
    }

    public float temperatureCap() {
        return Math.min(1f, heatRatio());
    }

    public float burnMultiplier() {
        return Math.max(1f, heatRatio());
    }

    public boolean hasStack() {
        return stackPos != null;
    }

    public boolean hasWaterInlet() {
        return injectorPos != null || !connectorPositions.isEmpty();
    }

    public boolean hasTakeoff() {
        return takeoffPos != null;
    }

    public boolean hasSafetyValve() {
        return safetyValvePos != null;
    }
}
