package com.lycoris.loconautics.content.boiler;

import com.lycoris.loconautics.foundation.math.TiltedShape;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public final class BoilerShapes {

    private BoilerShapes() {
    }

    private static final double[] SMALL = {-8.0, 24.0, 1.373, 14.629};
    private static final double[] BIG = {-16.0, 32.0, -0.532, 16.532};
    private static final int CORNER_STEPS = 3;

    private static final double SQRT_HALF = Math.sqrt(0.5);

    private static final VoxelShape[][] VISUAL_SHAPES = buildAll(BoilerShapes::buildVisualBarrel);
    private static final VoxelShape[][] COLLISION_SHAPES = buildAll(BoilerShapes::buildSteppedBarrel);
    private static final VoxelShape[] FIREBOX_VISUAL = buildFirebox(true);
    private static final VoxelShape[] FIREBOX_COLLISION = buildFirebox(false);

    private static VoxelShape[] buildFirebox(boolean visual) {
        VoxelShape[] out = new VoxelShape[3];
        for (Direction.Axis axis : Direction.Axis.values()) {
            out[axis.ordinal()] = axis == Direction.Axis.Y
                    ? (visual ? buildVisualBarrel(axis, SMALL) : buildSteppedBarrel(axis, SMALL))
                    : fireboxShape(axis, visual);
        }
        return out;
    }

    private static VoxelShape fireboxShape(Direction.Axis lengthAxis, boolean visual) {
        double outLo = SMALL[0], flatLo = SMALL[2];
        VoxelShape lower = lengthAxis == Direction.Axis.X
                ? boxForAxis(lengthAxis, outLo, flatLo, outLo, SMALL[1])
                : boxForAxis(lengthAxis, outLo, SMALL[1], outLo, flatLo);
        if (!visual) {
            return Shapes.or(buildSteppedBarrel(lengthAxis, SMALL), lower).optimize();
        }
        double half = (SMALL[1] - SMALL[0]) / 32.0;
        double flat = (SMALL[3] - SMALL[2]) / 32.0;
        double along = (half + flat) * SQRT_HALF;
        double across = (half - flat) * SQRT_HALF;
        List<AABB> turned = List.of(turnedBox(lengthAxis, -along, along, -across, across),
                turnedBox(lengthAxis, -across, across, -along, along));
        VoxelShape straight = Shapes.or(boxForAxis(lengthAxis, outLo, SMALL[1], flatLo, SMALL[3]),
                boxForAxis(lengthAxis, flatLo, SMALL[3], outLo, SMALL[1]), lower).optimize();
        return TiltedShape.of(straight, turned,
                point -> turnedToBlock(lengthAxis, point), point -> blockToTurned(lengthAxis, point),
                fireboxOutline(lengthAxis, half, flat));
    }

    private static List<Vec3[]> fireboxOutline(Direction.Axis lengthAxis, double half, double flat) {
        double[][] rim = {{half, -half}, {half, flat}, {flat, half}, {-flat, half}, {-half, flat}, {-half, -half}};
        List<Vec3[]> edges = new ArrayList<>();
        for (int i = 0; i < rim.length; i++) {
            double[] here = rim[i];
            double[] next = rim[(i + 1) % rim.length];
            Vec3 lo = sidewaysPoint(lengthAxis, here, 0.0);
            Vec3 hi = sidewaysPoint(lengthAxis, here, 1.0);
            edges.add(new Vec3[]{lo, hi});
            edges.add(new Vec3[]{lo, sidewaysPoint(lengthAxis, next, 0.0)});
            edges.add(new Vec3[]{hi, sidewaysPoint(lengthAxis, next, 1.0)});
        }
        return edges;
    }

    private static Vec3 sidewaysPoint(Direction.Axis lengthAxis, double[] cross, double along) {
        double side = cross[0] + 0.5;
        double up = cross[1] + 0.5;
        return lengthAxis == Direction.Axis.X ? new Vec3(along, up, side) : new Vec3(side, up, along);
    }

    private static VoxelShape[][] buildAll(BiFunction<Direction.Axis, double[], VoxelShape> build) {
        VoxelShape[][] out = new VoxelShape[2][3];
        for (int b = 0; b < 2; b++) {
            double[] oct = b == 1 ? BIG : SMALL;
            for (Direction.Axis axis : Direction.Axis.values()) {
                out[b][axis.ordinal()] = build.apply(axis, oct);
            }
        }
        return out;
    }

    private static VoxelShape buildVisualBarrel(Direction.Axis lengthAxis, double[] oct) {
        double outLo = oct[0], outHi = oct[1], flatLo = oct[2], flatHi = oct[3];
        VoxelShape straight = Shapes.or(boxForAxis(lengthAxis, outLo, outHi, flatLo, flatHi),
                boxForAxis(lengthAxis, flatLo, flatHi, outLo, outHi)).optimize();

        double half = (outHi - outLo) / 32.0;
        double flat = (flatHi - flatLo) / 32.0;
        double along = (half + flat) * SQRT_HALF;
        double across = (half - flat) * SQRT_HALF;
        List<AABB> turned = List.of(turnedBox(lengthAxis, -along, along, -across, across),
                turnedBox(lengthAxis, -across, across, -along, along));

        return TiltedShape.of(straight, turned,
                point -> turnedToBlock(lengthAxis, point), point -> blockToTurned(lengthAxis, point),
                octagonOutline(lengthAxis, half, flat));
    }

    private static List<Vec3[]> octagonOutline(Direction.Axis lengthAxis, double half, double flat) {
        double[][] rim = {{half, flat}, {flat, half}, {-flat, half}, {-half, flat},
                {-half, -flat}, {-flat, -half}, {flat, -half}, {half, -flat}};
        List<Vec3[]> edges = new ArrayList<>();
        for (int i = 0; i < rim.length; i++) {
            double[] here = rim[i];
            double[] next = rim[(i + 1) % rim.length];
            Vec3 lo = rimPoint(lengthAxis, here, 0.0);
            Vec3 hi = rimPoint(lengthAxis, here, 1.0);
            edges.add(new Vec3[]{lo, hi});
            edges.add(new Vec3[]{lo, rimPoint(lengthAxis, next, 0.0)});
            edges.add(new Vec3[]{hi, rimPoint(lengthAxis, next, 1.0)});
        }
        return edges;
    }

    private static Vec3 rimPoint(Direction.Axis lengthAxis, double[] cross, double along) {
        double u = cross[0] + 0.5;
        double v = cross[1] + 0.5;
        return switch (lengthAxis) {
            case X -> new Vec3(along, u, v);
            case Y -> new Vec3(u, along, v);
            case Z -> new Vec3(u, v, along);
        };
    }

    private static AABB turnedBox(Direction.Axis lengthAxis, double u0, double u1, double v0, double v1) {
        return switch (lengthAxis) {
            case X -> new AABB(0.0, u0, v0, 1.0, u1, v1);
            case Y -> new AABB(u0, 0.0, v0, u1, 1.0, v1);
            case Z -> new AABB(u0, v0, 0.0, u1, v1, 1.0);
        };
    }

    private static Vec3 turnedToBlock(Direction.Axis lengthAxis, Vec3 turned) {
        double s = crossU(lengthAxis, turned);
        double t = crossV(lengthAxis, turned);
        return withCross(lengthAxis, turned, (s - t) * SQRT_HALF + 0.5, (s + t) * SQRT_HALF + 0.5);
    }

    private static Vec3 blockToTurned(Direction.Axis lengthAxis, Vec3 local) {
        double u = crossU(lengthAxis, local) - 0.5;
        double v = crossV(lengthAxis, local) - 0.5;
        return withCross(lengthAxis, local, (u + v) * SQRT_HALF, (v - u) * SQRT_HALF);
    }

    private static double crossU(Direction.Axis lengthAxis, Vec3 point) {
        return lengthAxis == Direction.Axis.X ? point.y : point.x;
    }

    private static double crossV(Direction.Axis lengthAxis, Vec3 point) {
        return lengthAxis == Direction.Axis.Z ? point.y : point.z;
    }

    private static Vec3 withCross(Direction.Axis lengthAxis, Vec3 point, double u, double v) {
        return switch (lengthAxis) {
            case X -> new Vec3(point.x, u, v);
            case Y -> new Vec3(u, point.y, v);
            case Z -> new Vec3(u, v, point.z);
        };
    }

    private static VoxelShape buildSteppedBarrel(Direction.Axis lengthAxis, double[] oct) {
        double outLo = oct[0], outHi = oct[1], flatLo = oct[2], flatHi = oct[3];
        double step = (flatLo - outLo) / CORNER_STEPS;
        VoxelShape shape = Shapes.or(boxForAxis(lengthAxis, outLo, outHi, flatLo, flatHi),
                boxForAxis(lengthAxis, flatLo, flatHi, outLo, outHi));
        for (int k = 0; k < CORNER_STEPS; k++) {
            double inset = (k + 1) * step;
            double u0 = outLo + inset;
            double u1 = outHi - inset;
            shape = Shapes.or(shape,
                    boxForAxis(lengthAxis, u0, u1, flatHi + k * step, flatHi + (k + 1) * step));
            shape = Shapes.or(shape,
                    boxForAxis(lengthAxis, u0, u1, flatLo - (k + 1) * step, flatLo - k * step));
        }
        return shape.optimize();
    }

    private static VoxelShape boxForAxis(Direction.Axis lengthAxis, double u0, double u1, double v0, double v1) {
        return switch (lengthAxis) {
            case X -> Block.box(0.0, u0, v0, 16.0, u1, v1);
            case Y -> Block.box(u0, 0.0, v0, u1, 16.0, v1);
            case Z -> Block.box(u0, v0, 0.0, u1, v1, 16.0);
        };
    }

    public static VoxelShape visual(Direction.Axis axis, boolean big) {
        return VISUAL_SHAPES[big ? 1 : 0][axis.ordinal()];
    }

    public static VoxelShape fireboxVisual(Direction.Axis axis) {
        return FIREBOX_VISUAL[axis.ordinal()];
    }

    public static VoxelShape fireboxCollision(Direction.Axis axis) {
        return FIREBOX_COLLISION[axis.ordinal()];
    }

    public static VoxelShape collision(Direction.Axis axis, boolean big) {
        return COLLISION_SHAPES[big ? 1 : 0][axis.ordinal()];
    }
}
