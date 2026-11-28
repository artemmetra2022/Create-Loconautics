package com.lycoris.loconautics.content.hose.flow;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class HosePipeFlow {

    public record Driven(boolean inbound, FluidStack fluid, long tick) {
    }

    private record Spot(BlockPos pos, Direction side) {
    }

    private static final long LINGER_TICKS = 5;
    private static final int PURGE_SIZE = 512;
    private static final Map<Level, Map<Spot, Driven>> DRIVEN = new WeakHashMap<>();

    private HosePipeFlow() {
    }

    public static void drive(Level level, BlockPos pos, Direction side, boolean inbound, FluidStack fluid) {
        Map<Spot, Driven> map = DRIVEN.computeIfAbsent(level, l -> new HashMap<>());
        if (map.size() > PURGE_SIZE) {
            long now = level.getGameTime();
            map.values().removeIf(d -> now - d.tick() > LINGER_TICKS);
        }
        map.put(new Spot(pos.immutable(), side), new Driven(inbound, fluid.copyWithAmount(1), level.getGameTime()));
    }

    @Nullable
    public static Driven get(Level level, BlockPos pos, Direction side) {
        Map<Spot, Driven> map = DRIVEN.get(level);
        if (map == null || map.isEmpty()) {
            return null;
        }
        Driven driven = map.get(new Spot(pos, side));
        if (driven == null) {
            return null;
        }
        if (level.getGameTime() - driven.tick() > LINGER_TICKS) {
            map.remove(new Spot(pos, side));
            return null;
        }
        return driven;
    }
}
