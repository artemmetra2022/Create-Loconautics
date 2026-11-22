package com.lycoris.loconautics.content.retardertrack;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class RetarderTracks {

    /** how hard a retarder rail brakes at most, blocks per second squared. a heavy train needs a longer rail. */
    public static final double RETARDER_DECEL = 20.0;

    // the rail point sits on the track block, but not exactly in a known cell, so look a block around it
    private static final int[] PROBE_DY = {0, -1, 1};

    private RetarderTracks() {
    }

    /**
     * The speed limit, in blocks per second, of a powered retarder rail under the given rail point, or NaN when
     * there is none. A host is the sub level the track sits on, null for a track in the world.
     */
    public static double limitAt(ServerLevel level, ServerSubLevel host, Vector3dc railPoint) {
        if (level == null || railPoint == null) {
            return Double.NaN;
        }
        Vector3d at = host == null
                ? new Vector3d(railPoint)
                : host.logicalPose().transformPositionInverse(new Vector3d(railPoint), new Vector3d());
        BlockPos base = BlockPos.containing(at.x, at.y, at.z);
        for (int dy : PROBE_DY) {
            BlockPos pos = base.above(dy);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof RetarderTrackBlock && level.hasNeighborSignal(pos)) {
                return RetarderTrackBlock.limitBlocksPerSecond(state);
            }
        }
        return Double.NaN;
    }
}
