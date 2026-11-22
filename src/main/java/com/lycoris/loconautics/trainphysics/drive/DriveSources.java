package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleValidity;
import com.lycoris.loconautics.content.boiler.bogey.BoilerBogeyBlockEntity;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

// server only, the one place that lists and picks the drive sources of a car
public final class DriveSources {

    private DriveSources() {
    }

    public static void clearCache() {
        BearingAxleValidity.clearCache();
    }

    public static List<TrainDriveSource> valid(ServerSubLevel sub) {
        List<TrainDriveSource> sources = new ArrayList<>(BearingAxleValidity.valid(sub));
        sources.addAll(SubLevelBlocks.findAll(sub, BoilerBogeyBlockEntity.class));
        return sources;
    }

    @Nullable
    public static TrainDriveSource resolve(ServerSubLevel sub) {
        return pick(valid(sub));
    }

    @Nullable
    public static <T extends TrainDriveSource> T pick(List<T> valid) {
        T best = null;
        double bestStrength = -1.0;
        for (T source : valid) {
            double strength = Math.abs(source.rawRpm());
            if (best == null || strength > bestStrength
                    || (strength == bestStrength && olderThan(source, best))) {
                best = source;
                bestStrength = strength;
            }
        }
        return best;
    }

    // the front of the car in the world, snapped to a horizontal direction
    @Nullable
    public static Direction front(@Nullable TrainDriveSource source, ServerSubLevel sub) {
        if (source == null) {
            return null;
        }
        BlockState state = source.getBlockState();
        if (state.isAir()) {
            return null;
        }
        Direction local = source.frontLocal();
        Vector3d dir = new Vector3d(local.getStepX(), 0.0, local.getStepZ());
        sub.logicalPose().transformNormal(dir, dir);
        if (dir.x * dir.x + dir.z * dir.z < 1.0e-9) {
            return null;
        }
        return Direction.getNearest(dir.x, 0.0, dir.z);
    }

    private static boolean olderThan(TrainDriveSource a, TrainDriveSource b) {
        if (a.getPlacedTime() != b.getPlacedTime()) {
            return a.getPlacedTime() < b.getPlacedTime();
        }
        return a.getBlockPos().asLong() < b.getBlockPos().asLong();
    }
}
