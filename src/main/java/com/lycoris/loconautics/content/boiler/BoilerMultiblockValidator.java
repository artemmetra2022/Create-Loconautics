package com.lycoris.loconautics.content.boiler;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.boiler.barrel.BarrelPipeConnector;
import com.lycoris.loconautics.content.boiler.firebox.FireboxGroups;
import com.lycoris.loconautics.content.boiler.safetyvalve.SafetyValveBlock;
import com.lycoris.loconautics.content.boiler.steamtakeoff.SteamTakeoffBlock;
import com.lycoris.loconautics.content.hose.link.HoseConnectorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public final class BoilerMultiblockValidator {

    public static final int MAX_BODY_BLOCKS = 256;

    private BoilerMultiblockValidator() {}

    public record Scan(@Nullable BoilerStructure structure, @Nullable String missingKey) {}

    public static Scan scan(Level level, BlockPos controllerPos) {
        if (level == null || level.isClientSide) return new Scan(null, null);

        Set<BlockPos> fireboxes = FireboxGroups.members(level, controllerPos);

        float fireboxHeat = 0f;
        int countedFireboxes = 0;
        for (BlockPos pos : fireboxes) {
            if (countedFireboxes++ >= BoilerStructure.MAX_COUNTED_FIREBOXES) break;
            fireboxHeat += BoilerContributions.fireboxHeatOf(level.getBlockState(pos));
        }
        int sharingFireboxes = Math.min(fireboxes.size(), BoilerStructure.MAX_COUNTED_FIREBOXES);

        Set<BlockPos> bodySet = new HashSet<>();
        Queue<BlockPos> frontier = new ArrayDeque<>();
        for (BlockPos firebox : fireboxes) {
            for (Direction dir : Direction.values()) {
                seedBody(level, firebox.relative(dir), dir.getAxis(), true, bodySet, frontier);
                for (Direction side : Direction.values()) {
                    if (side.getAxis() != dir.getAxis()) {
                        seedBody(level, firebox.relative(dir).relative(side), dir.getAxis(), false, bodySet, frontier);
                    }
                }
            }
        }

        List<BlockPos> bodies = new ArrayList<>();
        Direction.Axis bodyAxis = null;
        BlockPos[] attached = new BlockPos[4];
        for (BlockPos firebox : fireboxes) {
            for (Direction dir : Direction.values()) {
                claim(attached, level.getBlockState(firebox.relative(dir)), firebox.relative(dir), dir);
            }
        }
        float boilerCapacity = sharingFireboxes * BoilerStructure.FIREBOX_BODY_SHARE;
        int waterCapacity = Math.round(sharingFireboxes * BoilerStructure.FIREBOX_BODY_SHARE
                * LoconauticsConfig.BODY_WATER_CAPACITY.get());

        while (!frontier.isEmpty() && bodies.size() < MAX_BODY_BLOCKS) {
            BlockPos current = frontier.poll();
            BlockState currentState = level.getBlockState(current);
            Direction.Axis axis = BarrelPipeConnector.bodyAxis(currentState);
            if (axis != null) {
                if (bodyAxis != null && bodyAxis != axis) return new Scan(null, "align_boiler_body");
                bodyAxis = axis;
            }
            if (bodies.size() < BoilerStructure.MAX_COUNTED_BODY_BLOCKS) {
                boilerCapacity += BoilerContributions.boilerCapacityOf(currentState);
                waterCapacity += BoilerContributions.waterCapacityOf(currentState);
            }
            bodies.add(current);

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                BlockState ns = level.getBlockState(neighbor);
                boolean alongRun = axis == null || dir.getAxis() == axis;

                if (ns.is(BoilerTags.BOILER_BODY)) {
                    if (alongRun && bodySet.add(neighbor)) frontier.add(neighbor);
                } else {
                    claim(attached, ns, neighbor, dir);
                }
            }
        }

        if (!frontier.isEmpty() || spanAlong(bodies, bodyAxis) > LoconauticsConfig.BOILER_MAX_LENGTH.get()) {
            return new Scan(null, "too_large");
        }
        if (bodies.size() < BoilerStructure.MIN_BODY_BLOCKS) return new Scan(null, "add_boiler_body");

        return new Scan(new BoilerStructure(
                controllerPos,
                List.copyOf(fireboxes),
                List.copyOf(bodies),
                attached[STACK],
                attached[INJECTOR],
                attached[TAKEOFF],
                attached[VALVE],
                scanConnectors(level, bodies),
                fireboxHeat,
                boilerCapacity,
                waterCapacity
        ), null);
    }

    private static final int STACK = 0;
    private static final int INJECTOR = 1;
    private static final int TAKEOFF = 2;
    private static final int VALVE = 3;

    // an attachment counts on any side of a firebox or a body, the first of each kind wins
    private static void claim(BlockPos[] attached, BlockState state, BlockPos pos, Direction dir) {
        if (attached[STACK] == null && dir != Direction.DOWN && state.is(BoilerTags.SMOKESTACK)) {
            attached[STACK] = pos;
        } else if (attached[INJECTOR] == null && state.is(BoilerTags.HATCHES)) {
            attached[INJECTOR] = pos;
        } else if (attached[TAKEOFF] == null && state.getBlock() instanceof SteamTakeoffBlock) {
            attached[TAKEOFF] = pos;
        } else if (attached[VALVE] == null && state.getBlock() instanceof SafetyValveBlock) {
            attached[VALVE] = pos;
        }
    }

    private static void seedBody(Level level, BlockPos pos, Direction.Axis reach, boolean flush,
                                 Set<BlockPos> bodySet, Queue<BlockPos> frontier) {
        BlockState state = level.getBlockState(pos);
        Direction.Axis axis = BarrelPipeConnector.bodyAxis(state);
        boolean fits = flush ? axis == null || axis == reach : axis == reach;
        if (state.is(BoilerTags.BOILER_BODY) && fits && bodySet.add(pos)) {
            frontier.add(pos);
        }
    }

    private static int spanAlong(List<BlockPos> bodies, Direction.Axis axis) {
        if (axis == null || bodies.isEmpty()) return bodies.size();
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (BlockPos pos : bodies) {
            int at = pos.get(axis);
            min = Math.min(min, at);
            max = Math.max(max, at);
        }
        return max - min + 1;
    }

    private static List<BlockPos> scanConnectors(Level level, List<BlockPos> bodies) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos body : bodies) {
            Direction.Axis length = BarrelPipeConnector
                    .bodyAxis(level.getBlockState(body));
            if (length == null) continue;
            for (Direction face : Direction.values()) {
                BlockState beside = level.getBlockState(body.relative(face));
                if (beside.getBlock() instanceof HoseConnectorBlock
                        && beside.getValue(HoseConnectorBlock.FACING) == face.getOpposite()) {
                    found.add(body.relative(face));
                    continue;
                }
                if (face.getAxis() == length) continue;
                if (BarrelPipeConnector
                        .ensureConnector(level, body, face)) {
                    found.add(body.relative(face));
                }
            }
        }
        return List.copyOf(found);
    }

}
