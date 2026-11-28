package com.lycoris.loconautics.content.boiler;

import com.lycoris.loconautics.content.boiler.barrel.BarrelPipeConnector;
import com.lycoris.loconautics.content.boiler.firebox.FireboxBlockEntity;
import com.lycoris.loconautics.content.boiler.firebox.FireboxGroups;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

// server only, reads tags only so every body and firebox block from a datapack is found the same way
public final class BoilerLookup {

    private BoilerLookup() {
    }

    @Nullable
    public static BlockPos controllerPos(Level level, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        Queue<BlockPos> frontier = new ArrayDeque<>();
        seen.add(start);
        frontier.add(start);
        while (!frontier.isEmpty() && seen.size() <= BoilerMultiblockValidator.MAX_BODY_BLOCKS) {
            BlockPos current = frontier.poll();
            BlockState currentState = level.getBlockState(current);
            if (currentState.is(BoilerTags.BOILER_FIREBOX)) {
                BlockPos owner = FireboxGroups.controllerOf(level, current);
                if (owner != null) return owner;
            }
            Direction.Axis axis = BarrelPipeConnector.bodyAxis(currentState);
            for (Direction dir : Direction.values()) {
                BlockPos next = current.relative(dir);
                BlockState state = level.getBlockState(next);
                if (state.is(BoilerTags.BOILER_FIREBOX)) {
                    BlockPos owner = FireboxGroups.controllerOf(level, next);
                    if (owner != null) return owner;
                } else if (axis != null && dir.getAxis() == axis && offsetFirebox(level, next, dir) != null) {
                    return offsetFirebox(level, next, dir);
                } else if (state.is(BoilerTags.BOILER_BODY) && (axis == null || dir.getAxis() == axis)
                        && seen.add(next)) {
                    frontier.add(next);
                }
            }
        }
        return null;
    }

    @Nullable
    public static FireboxBlockEntity controllerFor(Level level, BlockPos start) {
        if (level.isClientSide) return null;
        BlockPos pos = controllerPos(level, start);
        return pos != null && level.getBlockEntity(pos) instanceof FireboxBlockEntity be ? be : null;
    }

    @Nullable
    private static BlockPos offsetFirebox(Level level, BlockPos end, Direction along) {
        for (Direction side : Direction.values()) {
            if (side.getAxis() == along.getAxis()) continue;
            BlockPos beside = end.relative(side);
            if (level.getBlockState(beside).is(BoilerTags.BOILER_FIREBOX)) {
                BlockPos owner = FireboxGroups.controllerOf(level, beside);
                if (owner != null) return owner;
            }
        }
        return null;
    }
}
