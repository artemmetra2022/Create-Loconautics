package com.lycoris.loconautics.client.copycat;

import com.simibubi.create.foundation.model.BakedModelHelper;
import com.simibubi.create.foundation.model.BakedQuadHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.List;

public final class CopycatQuads {

    public static final int ALL_AXES = -1;
    private static final AABB CUBE = new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

    private CopycatQuads() {}

    public static BlockState materialOf(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem item) return item.getBlock().defaultBlockState();
        return Blocks.COAL_BLOCK.defaultBlockState();
    }

    public static BakedModel donorModel(BlockState material) {
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(material);
    }

    public static void crop(List<BakedQuad> out, BlockState material, RandomSource rand,
                            RenderType renderType, AABB box) {
        BakedModel donor = donorModel(material);
        cropQuads(out, donor.getQuads(material, null, rand, ModelData.EMPTY, renderType), box, ALL_AXES, CUBE);
        for (Direction side : Direction.values()) {
            cropQuads(out, donor.getQuads(material, side, rand, ModelData.EMPTY, renderType), box, ALL_AXES, CUBE);
        }
    }

    public static void cropQuads(List<BakedQuad> out, List<BakedQuad> template, AABB box, int depthAxis, AABB shape) {
        for (Piece piece : split(box, depthAxis, shape)) {
            AABB source = corner(piece);
            AABB at = piece.box();
            Vec3 move = new Vec3(at.minX - source.minX, at.minY - source.minY, at.minZ - source.minZ);
            for (BakedQuad quad : template) {
                out.add(BakedQuadHelper.cloneWithCustomGeometry(quad,
                        BakedModelHelper.cropAndMove(quad.getVertices(), quad.getSprite(), source, move)));
            }
        }
    }

    private record Piece(AABB box, boolean[] high) {}

    private static AABB corner(Piece piece) {
        AABB at = piece.box();
        double[] min = {at.minX, at.minY, at.minZ};
        double[] max = {at.maxX, at.maxY, at.maxZ};
        for (int axis = 0; axis < 3; axis++) {
            double length = max[axis] - min[axis];
            min[axis] = piece.high()[axis] ? 1.0 - length : 0.0;
            max[axis] = piece.high()[axis] ? 1.0 : length;
        }
        return new AABB(min[0], min[1], min[2], max[0], max[1], max[2]);
    }

    private static List<Piece> split(AABB box, int depthAxis, AABB shape) {
        double[] min = {box.minX, box.minY, box.minZ};
        double[] max = {box.maxX, box.maxY, box.maxZ};
        double[] shapeMin = {shape.minX, shape.minY, shape.minZ};
        double[] shapeMax = {shape.maxX, shape.maxY, shape.maxZ};
        List<List<double[]>> spans = new ArrayList<>(3);
        for (int axis = 0; axis < 3; axis++) {
            spans.add(axis == depthAxis
                    ? depthSpans(min[axis], max[axis])
                    : spans(min[axis], max[axis], (shapeMin[axis] + shapeMax[axis]) / 2.0));
        }
        List<Piece> pieces = new ArrayList<>(8);
        for (double[] x : spans.get(0)) {
            for (double[] y : spans.get(1)) {
                for (double[] z : spans.get(2)) {
                    pieces.add(new Piece(new AABB(x[0], y[0], z[0], x[1], y[1], z[1]),
                            new boolean[] {x[2] > 0.0, y[2] > 0.0, z[2] > 0.0}));
                }
            }
        }
        return pieces;
    }

    // the shape is cut at its own middle, the low half takes the low corner of the donor and the high half the high corner
    private static List<double[]> spans(double min, double max, double mid) {
        if (min < mid && max > mid && (min > 0.0 || max < 1.0)) {
            return List.of(new double[] {min, mid, 0.0}, new double[] {mid, max, 1.0});
        }
        return List.of(new double[] {min, max, min + max >= 2.0 * mid ? 1.0 : 0.0});
    }

    private static List<double[]> depthSpans(double min, double max) {
        List<double[]> raw = min < 0.5 && max > 0.5 && (min > 0.0 || max < 1.0)
                ? List.of(new double[] {min, 0.5}, new double[] {0.5, max})
                : List.of(new double[] {min, max});
        List<double[]> out = new ArrayList<>(raw.size());
        for (double[] span : raw) {
            boolean inner = span[0] > 0.0 && span[1] < 1.0;
            boolean high = inner ? span[0] + span[1] < 1.0 : span[0] + span[1] >= 1.0;
            out.add(new double[] {span[0], span[1], high ? 1.0 : 0.0});
        }
        return out;
    }
}
