package com.lycoris.loconautics.content.airbrake;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Shared brake pipe pressure bus per sub-level (car) and stationary world region.
 * Connects across coupled cars through BrakeHoseConnectorBlockEntity via 1D pressure diffusion.
 */
public final class AirPipeBus {

    private static final float PIPE_DIFFUSION_RATE = 0.35f;
    private static final float VENT_EXHAUST_PER_TICK = 0.45f;
    private static final double WORLD_CLUSTER_RADIUS_SQ = 16.0 * 16.0;

    private static final class PipeNode {
        float pressureBar;
        long lastVentTick = Long.MIN_VALUE;
        long lastTick = Long.MIN_VALUE;
    }

    private static final Map<ServerSubLevel, PipeNode> SUB_LEVEL_NODES = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<BlockPos, PipeNode>> WORLD_NODES = new WeakHashMap<>();

    private AirPipeBus() {
    }

    @Nullable
    private static PipeNode getOrCreateNode(BlockEntity be) {
        Level level = be.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        SubLevel sub = Sable.HELPER.getContaining(be);
        if (sub instanceof ServerSubLevel serverSub) {
            return SUB_LEVEL_NODES.computeIfAbsent(serverSub, k -> new PipeNode());
        }
        Map<BlockPos, PipeNode> worldMap = WORLD_NODES.computeIfAbsent(serverLevel, k -> new HashMap<>());
        BlockPos pos = be.getBlockPos();
        for (Map.Entry<BlockPos, PipeNode> entry : worldMap.entrySet()) {
            if (entry.getKey().distSqr(pos) <= WORLD_CLUSTER_RADIUS_SQ) {
                return entry.getValue();
            }
        }
        PipeNode created = new PipeNode();
        worldMap.put(pos.immutable(), created);
        return created;
    }

    public static float readPipePressure(BlockEntity reader) {
        PipeNode node = getOrCreateNode(reader);
        return node == null ? 0.0f : node.pressureBar;
    }

    public static void writePipePressure(BlockEntity source, float pressureBar) {
        PipeNode node = getOrCreateNode(source);
        if (node == null || !(source.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        long now = serverLevel.getGameTime();
        // markVented() has already consumed this tick's vent flow. Do not drain it twice.
        node.pressureBar = Mth.clamp(pressureBar, 0f, AirBrakeTuning.WORKING_PIPE_BAR);
        node.lastTick = now;
    }

    public static void markVented(BlockEntity connector) {
        PipeNode node = getOrCreateNode(connector);
        if (node == null || !(connector.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        long now = serverLevel.getGameTime();
        if (node.lastVentTick != now) {
            node.lastVentTick = now;
            node.pressureBar = Math.max(0f, node.pressureBar - VENT_EXHAUST_PER_TICK);
            node.lastTick = now;
        }
    }

    public static void equalize(BlockEntity a, BlockEntity b) {
        PipeNode nodeA = getOrCreateNode(a);
        PipeNode nodeB = getOrCreateNode(b);
        if (nodeA == null || nodeB == null || nodeA == nodeB) {
            return;
        }
        float diff = nodeA.pressureBar - nodeB.pressureBar;
        if (Math.abs(diff) <= 0.001f) {
            return;
        }
        float transfer = diff * PIPE_DIFFUSION_RATE * 0.5f;
        nodeA.pressureBar = Mth.clamp(nodeA.pressureBar - transfer, 0f, AirBrakeTuning.WORKING_PIPE_BAR);
        nodeB.pressureBar = Mth.clamp(nodeB.pressureBar + transfer, 0f, AirBrakeTuning.WORKING_PIPE_BAR);
    }

    public static void remove(BlockEntity source) {
        // Node lifetime is tied to ServerSubLevel / World map weak references.
    }
}
