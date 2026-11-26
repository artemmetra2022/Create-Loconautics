package com.lycoris.loconautics.compat.computercraft;

import com.lycoris.loconautics.content.boiler.firebox.FireboxBlockEntity;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

import java.util.Map;
import java.util.WeakHashMap;

public class FireboxPeripheral implements IPeripheral {

    private static final Map<FireboxBlockEntity, FireboxPeripheral> CACHE = new WeakHashMap<>();

    private final FireboxBlockEntity firebox;

    private FireboxPeripheral(FireboxBlockEntity firebox) {
        this.firebox = firebox;
    }

    public static FireboxPeripheral of(FireboxBlockEntity firebox) {
        return CACHE.computeIfAbsent(firebox, FireboxPeripheral::new);
    }

    @Override
    public String getType() {
        return "loconautics:firebox";
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof FireboxPeripheral p && p.firebox == firebox;
    }

    @Override
    public void attach(IComputerAccess computer) {
    }

    @Override
    public void detach(IComputerAccess computer) {
    }

    @LuaFunction(mainThread = true)
    public final boolean isValid() {
        return firebox.isDisplayValid();
    }

    @LuaFunction(mainThread = true)
    public final boolean isIgnited() {
        return firebox.isIgnited();
    }

    @LuaFunction(mainThread = true)
    public final String getState() {
        return firebox.getBoilerState().name().toLowerCase();
    }

    @LuaFunction(mainThread = true)
    public final int getLevel() {
        return firebox.getDisplayLevel();
    }

    @LuaFunction(mainThread = true)
    public final int getMaxLevel() {
        return firebox.getDisplayMaxLevel();
    }

    @LuaFunction(mainThread = true)
    public final double getPressure() {
        return firebox.getDisplayPressure();
    }

    @LuaFunction(mainThread = true)
    public final double getTemperature() {
        return firebox.temperatureFraction();
    }

    @LuaFunction(mainThread = true)
    public final int getFuelSeconds() {
        return firebox.getDisplayRemaining();
    }

    @LuaFunction(mainThread = true)
    public final int getWater() {
        return firebox.getDisplayWater();
    }

    @LuaFunction(mainThread = true)
    public final int getWaterCapacity() {
        return firebox.getWaterCapacity();
    }

    @LuaFunction(mainThread = true)
    public final double getOutputSu() {
        return firebox.getDisplaySu();
    }

    @LuaFunction(mainThread = true)
    public final double getMaxSu() {
        return firebox.getDisplayMaxSu();
    }

    @LuaFunction(mainThread = true)
    public final double getBogeyPower() {
        return firebox.getDisplayBogeyDemand();
    }
}
