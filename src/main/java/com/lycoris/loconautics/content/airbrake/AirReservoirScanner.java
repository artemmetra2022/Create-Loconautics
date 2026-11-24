package com.lycoris.loconautics.content.airbrake;

import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Finds AirReservoirBlockEntity and AirCompressorBlockEntity on the same carriage (ServerSubLevel)
 * so engine-room reservoirs feed cab brake valves without requiring direct adjacency.
 * Falls back to 6-neighbor adjacency when placed in the world outside a sub-level.
 */
public final class AirReservoirScanner {

    private static final int SCAN_INTERVAL_TICKS = 10;

    private record Scan(long tick, List<BlockPos> reservoirs, List<BlockPos> compressors, List<BlockPos> distributors) {
    }

    private static final Map<ServerSubLevel, Scan> SCANS = new WeakHashMap<>();

    private AirReservoirScanner() {
    }


    public static boolean hasReservoir(BlockEntity origin) {
        if (!(origin.getLevel() instanceof ServerLevel level)) return false;
        SubLevel sub = Sable.HELPER.getContaining(origin);
        if (sub instanceof ServerSubLevel serverSub && !serverSub.isRemoved()) {
            return !scan(serverSub).reservoirs().isEmpty();
        }
        for (Direction dir : Direction.values()) {
            if (level.getBlockEntity(origin.getBlockPos().relative(dir)) instanceof AirReservoirBlockEntity) return true;
        }
        return false;
    }

    public static boolean hasDistributor(BlockEntity origin) {
        if (!(origin.getLevel() instanceof ServerLevel level)) return false;
        SubLevel sub = Sable.HELPER.getContaining(origin);
        if (sub instanceof ServerSubLevel serverSub && !serverSub.isRemoved()) {
            return !scan(serverSub).distributors().isEmpty();
        }
        for (Direction dir : Direction.values()) {
            if (level.getBlockEntity(origin.getBlockPos().relative(dir)) instanceof TripleValveBlockEntity) return true;
        }
        return false;
    }

    public static boolean hasRequiredCompressorTargets(BlockEntity origin) {
        return hasReservoir(origin) && hasDistributor(origin);
    }

    public static List<AirReservoirBlockEntity> findReservoirs(BlockEntity origin) {
        List<AirReservoirBlockEntity> out = new ArrayList<>();
        if (!(origin.getLevel() instanceof ServerLevel level)) {
            return out;
        }
        SubLevel sub = Sable.HELPER.getContaining(origin);
        if (sub instanceof ServerSubLevel serverSub && !serverSub.isRemoved()) {
            Scan scan = scan(serverSub);
            ServerLevel subLevel = serverSub.getLevel();
            for (BlockPos pos : scan.reservoirs()) {
                if (!pos.equals(origin.getBlockPos())
                        && subLevel.getBlockEntity(pos) instanceof AirReservoirBlockEntity reservoir) {
                    out.add(reservoir);
                }
            }
            return out;
        }
        BlockPos originPos = origin.getBlockPos();
        for (Direction dir : Direction.values()) {
            if (level.getBlockEntity(originPos.relative(dir)) instanceof AirReservoirBlockEntity reservoir) {
                out.add(reservoir);
            }
        }
        return out;
    }

    public static List<AirCompressorBlockEntity> findCompressors(BlockEntity origin) {
        List<AirCompressorBlockEntity> out = new ArrayList<>();
        if (!(origin.getLevel() instanceof ServerLevel level)) {
            return out;
        }
        SubLevel sub = Sable.HELPER.getContaining(origin);
        if (sub instanceof ServerSubLevel serverSub && !serverSub.isRemoved()) {
            Scan scan = scan(serverSub);
            ServerLevel subLevel = serverSub.getLevel();
            for (BlockPos pos : scan.compressors()) {
                if (!pos.equals(origin.getBlockPos())
                        && subLevel.getBlockEntity(pos) instanceof AirCompressorBlockEntity compressor) {
                    out.add(compressor);
                }
            }
            return out;
        }
        BlockPos originPos = origin.getBlockPos();
        for (Direction dir : Direction.values()) {
            if (level.getBlockEntity(originPos.relative(dir)) instanceof AirCompressorBlockEntity compressor) {
                out.add(compressor);
            }
        }
        return out;
    }

    private static Scan scan(ServerSubLevel sub) {
        ServerLevel subLevel = sub.getLevel();
        long now = subLevel.getGameTime();
        Scan cached = SCANS.get(sub);
        if (cached != null && now - cached.tick() < SCAN_INTERVAL_TICKS && now >= cached.tick()) {
            return cached;
        }
        List<BlockPos> reservoirs = new ArrayList<>();
        List<BlockPos> compressors = new ArrayList<>();
        List<BlockPos> distributors = new ArrayList<>();
        SubLevelBlocks.forEachBlock(sub, (pos, state) -> {
            if (state.getBlock() instanceof AirReservoirBlock) {
                reservoirs.add(pos.immutable());
            } else if (state.getBlock() instanceof AirCompressorBlock) {
                compressors.add(pos.immutable());
            } else if (state.getBlock() instanceof TripleValveBlock) {
                distributors.add(pos.immutable());
            }
            return true;
        });
        Scan fresh = new Scan(now, reservoirs, compressors, distributors);
        SCANS.put(sub, fresh);
        return fresh;
    }
}
