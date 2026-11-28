package com.lycoris.loconautics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.List;

public final class HoseStrandDraw {

    private HoseStrandDraw() {
    }

    public static void draw(PoseStack ps, MultiBufferSource buffer, Level level, BlockPos owner, BlockState state,
                            List<Vec3> points) {
        VertexConsumer vb = buffer.getBuffer(RenderType.solid());
        SuperByteBuffer knot = CachedBuffers.partialFacing(LoconauticsPartialModels.HOSE_STRAND_KNOT, state, Direction.NORTH);
        SuperByteBuffer middle = CachedBuffers.partialFacing(LoconauticsPartialModels.HOSE_STRAND, state, Direction.NORTH);
        Quaternionf orient = new Quaternionf();
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 p0 = points.get(i);
            double length = RopeRendering.orientSegment(orient, p0, points.get(i + 1));
            if (length < 1.0e-5) {
                continue;
            }
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(p0.x, p0.y, p0.z));
            HoseDraw.segment(ps, vb, middle, i > 0 ? knot : null, orient, p0.x - owner.getX(),
                    p0.y - owner.getY(), p0.z - owner.getZ(), length, 1.0f, light);
        }
    }
}
