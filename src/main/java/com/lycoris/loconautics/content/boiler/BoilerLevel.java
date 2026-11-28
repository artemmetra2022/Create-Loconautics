package com.lycoris.loconautics.content.boiler;

public final class BoilerLevel {

    // 0.25 steps add up in floats, so a full level must not be lost to rounding
    private static final float EPSILON = 0.001f;

    private BoilerLevel() {
    }

    public static int sizeLevels(float capacity) {
        return (int) Math.floor(capacity + EPSILON);
    }

    public static int fireLevels(float heat, int levelsPerFirebox) {
        return (int) Math.floor(heat * levelsPerFirebox + EPSILON);
    }

    public static int level(int sizeLevels, int fireLevels, int maxLevel) {
        return Math.max(0, Math.min(Math.min(sizeLevels, fireLevels), maxLevel));
    }

    public static float poolSu(int level, float suPerLevel, float pressureFraction) {
        return level * suPerLevel * Math.max(0f, Math.min(1f, pressureFraction));
    }

    // base covers a boiler of three size levels, every level above adds the same extra time
    public static int warmupTicks(int sizeLevels, int baseTicks, int perSizeLevelTicks) {
        return baseTicks + perSizeLevelTicks * Math.max(0, sizeLevels - 3);
    }
}
