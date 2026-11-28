package com.lycoris.loconautics.content.boiler;

import net.minecraft.nbt.CompoundTag;

// server only, the boiler of a train while it is unloaded
public final class BoilerSummary {

    public static final String NO_FUEL = "no_fuel";
    public static final String NO_WATER = "no_water";
    public static final String NOT_LIT = "not_lit";

    private boolean ignited;
    private float fuel;
    private float burnPerTick;
    private int water;
    private float waterPerTick;
    private float spentFuel;
    private float spentWater;
    private long syncedAt = -1L;

    public BoilerSummary() {
    }

    public BoilerSummary(boolean ignited, float fuel, float burnPerTick, int water, float waterPerTick) {
        this.ignited = ignited;
        this.fuel = fuel;
        this.burnPerTick = burnPerTick;
        this.water = water;
        this.waterPerTick = waterPerTick;
    }

    public void stamp(long gameTime) {
        syncedAt = gameTime;
    }

    public long ticksAway(long gameTime) {
        return syncedAt < 0L ? -1L : gameTime - syncedAt;
    }

    public boolean fireOut() {
        return !ignited || fuel <= 0f;
    }

    public float burnedTicks() {
        return burnPerTick <= 0f ? 0f : spentFuel / burnPerTick;
    }

    public boolean hasSpent() {
        return spentFuel > 0f || spentWater > 0f;
    }

    public float takeSpentFuel() {
        float taken = spentFuel;
        spentFuel = 0f;
        return taken;
    }

    public int takeSpentWater() {
        int taken = Math.round(spentWater);
        spentWater = 0f;
        return taken;
    }

    // null while it can keep going, otherwise the lang suffix that says why it cannot
    public String problem() {
        if (!ignited) return NOT_LIT;
        if (fuel <= 0f) return NO_FUEL;
        if (waterPerTick > 0f && water <= 0) return NO_WATER;
        return null;
    }

    public void spendTick() {
        if (problem() != null) return;
        fuel -= burnPerTick;
        spentFuel += burnPerTick;
        if (waterPerTick > 0f) {
            float used = Math.min(waterPerTick, water);
            water = Math.max(0, Math.round(water - used));
            spentWater += used;
        }
    }

    public String describe() {
        return "ignited=" + ignited + " fuel=" + fuel + " burn=" + burnPerTick + " water=" + water
                + " waterPerTick=" + waterPerTick + " spentFuel=" + spentFuel + " spentWater=" + spentWater;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Ignited", ignited);
        tag.putFloat("Fuel", fuel);
        tag.putFloat("BurnPerTick", burnPerTick);
        tag.putInt("Water", water);
        tag.putFloat("WaterPerTick", waterPerTick);
        tag.putFloat("SpentFuel", spentFuel);
        tag.putFloat("SpentWater", spentWater);
        tag.putLong("SyncedAt", syncedAt);
        return tag;
    }

    public static BoilerSummary load(CompoundTag tag) {
        BoilerSummary summary = new BoilerSummary(tag.getBoolean("Ignited"), tag.getFloat("Fuel"),
                tag.getFloat("BurnPerTick"), tag.getInt("Water"), tag.getFloat("WaterPerTick"));
        summary.spentFuel = tag.getFloat("SpentFuel");
        summary.spentWater = tag.getFloat("SpentWater");
        summary.syncedAt = tag.contains("SyncedAt") ? tag.getLong("SyncedAt") : -1L;
        return summary;
    }
}
