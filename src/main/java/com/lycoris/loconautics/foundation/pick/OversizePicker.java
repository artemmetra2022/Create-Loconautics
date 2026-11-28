package com.lycoris.loconautics.foundation.pick;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public final class OversizePicker {

    private static final double SAMPLE_STEP = 0.2;
    private static final double MAX_LENGTH = 16.0;

    private OversizePicker() {
    }

    private static final class Best {
        BlockHitResult hit;
        double dist;
    }

    // a ray only tests the cells it crosses, so shapes that leave their cell are tested from the neighbors
    public static BlockHitResult extend(BlockGetter level, ClipContext context, BlockHitResult vanilla) {
        Vec3 from = context.getFrom();
        Vec3 to = context.getTo();
        double length = to.subtract(from).length();
        if (length < 1.0e-6 || length > MAX_LENGTH) return vanilla;

        Best best = new Best();
        best.hit = vanilla;
        best.dist = vanilla.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : distanceOf(level, vanilla, from);

        scan(level, context, from, to, best);
        if (level instanceof Level world) {
            BoundingBox3d bounds = new BoundingBox3d(
                    Math.min(from.x, to.x), Math.min(from.y, to.y), Math.min(from.z, to.z),
                    Math.max(from.x, to.x), Math.max(from.y, to.y), Math.max(from.z, to.z));
            for (SubLevel sub : Sable.HELPER.getAllIntersecting(world, bounds)) {
                Pose3dc pose = poseOf(world, sub);
                scan(sub.getLevel(), context, toPlot(pose, from), toPlot(pose, to), best);
            }
        }
        return best.hit;
    }

    private static double distanceOf(BlockGetter level, BlockHitResult hit, Vec3 from) {
        if (level instanceof Level world) {
            SubLevel sub = Sable.HELPER.getContaining(world, hit.getBlockPos());
            if (sub != null) return toPlot(poseOf(world, sub), from).distanceToSqr(hit.getLocation());
        }
        return from.distanceToSqr(hit.getLocation());
    }

    private static Pose3dc poseOf(Level world, SubLevel sub) {
        return world instanceof LevelPoseProviderExtension provider ? provider.sable$getPose(sub) : sub.logicalPose();
    }

    private static Vec3 toPlot(Pose3dc pose, Vec3 world) {
        Vector3d out = pose.transformPositionInverse(new Vector3d(world.x, world.y, world.z), new Vector3d());
        return new Vec3(out.x, out.y, out.z);
    }

    private static void scan(BlockGetter space, ClipContext context, Vec3 from, Vec3 to, Best best) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        LongOpenHashSet cells = new LongOpenHashSet();
        LongOpenHashSet owners = new LongOpenHashSet();
        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos near = new BlockPos.MutableBlockPos();
        int steps = (int) Math.ceil(length / SAMPLE_STEP);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            cell.set(from.x + delta.x * t, from.y + delta.y * t, from.z + delta.z * t);
            if (!cells.add(cell.asLong())) continue;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        near.set(cell.getX() + dx, cell.getY() + dy, cell.getZ() + dz);
                        if (!owners.add(near.asLong())) continue;
                        BlockState state = space.getBlockState(near);
                        if (!(state.getBlock() instanceof OversizeShape)) continue;
                        BlockHitResult hit = clipOwner(space, context, near.immutable(), state, from, to);
                        if (hit == null) continue;
                        double dist = from.distanceToSqr(hit.getLocation());
                        if (dist < best.dist) {
                            best.hit = hit;
                            best.dist = dist;
                        }
                    }
                }
            }
        }
    }

    @Nullable
    private static BlockHitResult clipOwner(BlockGetter level, ClipContext context, BlockPos owner,
                                            BlockState state, Vec3 from, Vec3 to) {
        VoxelShape shape = context.getBlockShape(state, level, owner);
        if (shape.isEmpty()) return null;
        return level.clipWithInteractionOverride(from, to, owner, shape, state);
    }
}
