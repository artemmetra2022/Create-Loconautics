package com.lycoris.loconautics.content.hose.flow;

import com.simibubi.create.content.fluids.OpenEndedPipe;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class HoseOpenEnds {

    private record Spot(BlockPos pos, Direction side) {
    }

    private static final Map<Level, Map<Spot, OpenEndedPipe>> ENDS = new WeakHashMap<>();

    private HoseOpenEnds() {
    }

    @Nullable
    public static IFluidHandler handler(Level level, BlockPos pos, Direction side) {
        OpenEndedPipe end = ENDS.computeIfAbsent(level, l -> new HashMap<>())
                .computeIfAbsent(new Spot(pos.immutable(), side), s -> new OpenEndedPipe(new BlockFace(s.pos(), s.side())));
        end.manageSource(level, null);
        return end.provideHandler() == null ? null : end.provideHandler().getCapability();
    }
}
