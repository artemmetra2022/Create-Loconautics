package com.lycoris.loconautics.content.retardertrack;

import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public final class RetarderTracks {

    /** how hard a retarder rail brakes at most, blocks per second squared. a heavy train needs a longer rail. */
    public static final double RETARDER_DECEL = 20.0;

    // the rail point sits on the track block, but not exactly in a known cell, so look a block around it
    private static final int[] PROBE_DY = {0, -1, 1};

    /** how far a connection can reach from the retarder block that carries it, plus some slack */
    private static final double SEARCH_RADIUS = 40.0;
    /** the retarder blocks of a chunk are looked up again after this many ticks */
    private static final long CACHE_TICKS = 10L;
    /** above this many cached chunks per level the stale entries are dropped */
    private static final int MAX_CACHED_CHUNKS = 512;
    /** how far the rail point may be from the line of a connection and still count as on it */
    private static final double ON_CURVE_HORIZONTAL = 0.8;
    private static final double ON_CURVE_VERTICAL = 1.5;

    private static final Map<ServerLevel, Map<Long, Cell>> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    /** a track connection (straight or curved) that starts at a retarder block */
    private record Span(BlockPos from, BlockPos to, BezierConnection curve, AABB box) {
    }

    private record Cell(long scannedAt, List<Span> spans) {
    }

    private RetarderTracks() {
    }

    /**
     * The speed limit, in blocks per second, of a powered retarder rail under the given rail point, or NaN when
     * there is none. A host is the sub level the track sits on, null for a track in the world.
     * <p>
     * A retarder block brakes the cell it stands in. A track connection that leaves a retarder block (the long
     * straight or curved piece laid between two clicks) brakes along its whole length, because only its ends are
     * blocks. The connection is powered when either of its end blocks is a powered retarder.
     */
    public static double limitAt(ServerLevel level, ServerSubLevel host, Vector3dc railPoint) {
        if (level == null || railPoint == null) {
            return Double.NaN;
        }
        Vector3d at = host == null
                ? new Vector3d(railPoint)
                : host.logicalPose().transformPositionInverse(new Vector3d(railPoint), new Vector3d());
        double best = Double.NaN;

        BlockPos base = BlockPos.containing(at.x, at.y, at.z);
        for (int dy : PROBE_DY) {
            BlockPos pos = base.above(dy);
            double limit = poweredLimit(level, pos);
            best = lower(best, limit);
        }

        int minX = Mth.floor(at.x - SEARCH_RADIUS) >> 4;
        int maxX = Mth.floor(at.x + SEARCH_RADIUS) >> 4;
        int minZ = Mth.floor(at.z - SEARCH_RADIUS) >> 4;
        int maxZ = Mth.floor(at.z + SEARCH_RADIUS) >> 4;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                for (Span span : spansOf(level, cx, cz)) {
                    if (!span.box().contains(at.x, at.y, at.z)) {
                        continue;
                    }
                    double limit = lower(poweredLimit(level, span.from()), poweredLimit(level, span.to()));
                    if (Double.isNaN(limit) || !isOnCurve(span.curve(), at)) {
                        continue;
                    }
                    best = lower(best, limit);
                }
            }
        }
        return best;
    }

    /** the limit of a powered retarder block at the position, NaN for anything else */
    private static double poweredLimit(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof RetarderTrackBlock && level.hasNeighborSignal(pos)) {
            return RetarderTrackBlock.limitBlocksPerSecond(state);
        }
        return Double.NaN;
    }

    private static double lower(double a, double b) {
        if (Double.isNaN(a)) {
            return b;
        }
        if (Double.isNaN(b)) {
            return a;
        }
        return Math.min(a, b);
    }

    private static List<Span> spansOf(ServerLevel level, int chunkX, int chunkZ) {
        Map<Long, Cell> cells = CACHE.computeIfAbsent(level, l -> new ConcurrentHashMap<>());
        long key = (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
        long now = level.getGameTime();
        Cell cell = cells.get(key);
        if (cell != null && now - cell.scannedAt() < CACHE_TICKS && now >= cell.scannedAt()) {
            return cell.spans();
        }
        if (cells.size() > MAX_CACHED_CHUNKS) {
            cells.entrySet().removeIf(e -> now - e.getValue().scannedAt() >= CACHE_TICKS || now < e.getValue().scannedAt());
        }
        List<Span> spans = scan(level, chunkX, chunkZ);
        cells.put(key, new Cell(now, spans));
        return spans;
    }

    private static List<Span> scan(ServerLevel level, int chunkX, int chunkZ) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            return List.of();
        }
        List<Span> out = new ArrayList<>();
        try {
            for (BlockPos pos : new ArrayList<>(chunk.getBlockEntities().keySet())) {
                if (!(chunk.getBlockState(pos).getBlock() instanceof RetarderTrackBlock)
                        || !(chunk.getBlockEntity(pos) instanceof TrackBlockEntity track)) {
                    continue;
                }
                for (Map.Entry<BlockPos, BezierConnection> entry : track.getConnections().entrySet()) {
                    BezierConnection curve = entry.getValue();
                    if (curve != null) {
                        out.add(new Span(pos.immutable(), entry.getKey(), curve, curve.getBounds().inflate(1.5)));
                    }
                }
            }
        } catch (RuntimeException e) {
            // the chunk changed under us, the next scan will get it
            return List.of();
        }
        return out;
    }

    /** whether the point lies on the line of the connection, flat distance with a looser height check */
    private static boolean isOnCurve(BezierConnection curve, Vector3d at) {
        int count = Math.max(1, curve.getSegmentCount());
        Vec3 previous = curve.getPosition(0.0);
        for (int i = 1; i <= count; i++) {
            Vec3 next = curve.getPosition((double) i / count);
            if (isNearSegment(previous, next, at)) {
                return true;
            }
            previous = next;
        }
        return false;
    }

    private static boolean isNearSegment(Vec3 a, Vec3 b, Vector3d p) {
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        double lengthSq = dx * dx + dz * dz;
        double t = lengthSq < 1.0e-9 ? 0.0 : Mth.clamp(((p.x - a.x) * dx + (p.z - a.z) * dz) / lengthSq, 0.0, 1.0);
        double nearX = a.x + dx * t;
        double nearZ = a.z + dz * t;
        double nearY = a.y + (b.y - a.y) * t;
        double offX = p.x - nearX;
        double offZ = p.z - nearZ;
        return offX * offX + offZ * offZ <= ON_CURVE_HORIZONTAL * ON_CURVE_HORIZONTAL
                && Math.abs(p.y - nearY) <= ON_CURVE_VERTICAL;
    }
}
