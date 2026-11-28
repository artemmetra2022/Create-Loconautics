package com.lycoris.loconautics.client;

import com.lycoris.loconautics.common.strand.StrandRenderer;
import com.lycoris.loconautics.content.hose.HoseNozzleKnot;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

public final class HoseDraw {

    private static final double CONNECTOR_PUSH = 1.0 / 16.0;

    private HoseDraw() {
    }

    // x, y and z are the point relative to whatever the pose stack is anchored on
    public static void segment(PoseStack ps, VertexConsumer vb, SuperByteBuffer middle,
                               @Nullable SuperByteBuffer knot, Quaternionf orient, double x, double y, double z,
                               double length, float girth, int light) {
        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(orient);
        if (knot != null) {
            ps.pushPose();
            ps.translate(-0.5, -0.5, -0.5);
            knot.light(light).renderInto(ps, vb);
            ps.popPose();
        }
        ps.scale(girth, (float) length, girth);
        ps.translate(-0.5, 0.0, -0.5);
        middle.light(light).renderInto(ps, vb);
        ps.popPose();
    }

    public static void knot(PoseStack ps, VertexConsumer vb, SuperByteBuffer knot, Quaternionf orient, double x,
                            double y, double z, float scale, int light) {
        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(orient);
        ps.scale(scale, scale, scale);
        ps.translate(-0.5, -0.5, -0.5);
        knot.light(light).renderInto(ps, vb);
        ps.popPose();
    }

    // a connector knot lies along the hose, away from the block, pushed out of the plate and inflated like the nozzle knot
    public static void connectorKnot(PoseStack ps, VertexConsumer vb, SuperByteBuffer knot, Vec3 attach, Vec3 outward,
                                     int light) {
        Vec3 dir = outward.normalize();
        Quaternionf along = dir.y < -0.9999 ? new Quaternionf().rotationX((float) Math.PI)
                : StrandRenderer.rotationTo(new Vector3d(0.0, 1.0, 0.0), new Vector3d(dir.x, dir.y, dir.z));
        Vec3 at = attach.add(dir.scale(CONNECTOR_PUSH));
        knot(ps, vb, knot, along, at.x, at.y, at.z, HoseNozzleKnot.INFLATE, light);
    }

    // the last segment points into the nozzle, so the knot is flipped to rise back toward the hose
    public static void nozzleKnotAtEnd(PoseStack ps, VertexConsumer vb, SuperByteBuffer knot, Quaternionf lastOrient,
                                       double x, double y, double z, int light) {
        Quaternionf flipped = HoseNozzleKnot.atNozzle(lastOrient);
        Vector3f back = HoseNozzleKnot.backOff(flipped);
        knot(ps, vb, knot, flipped, x + back.x, y + back.y, z + back.z, HoseNozzleKnot.INFLATE, light);
    }

    // the first segment already points away from the nozzle, so no flip
    public static void nozzleKnotAtStart(PoseStack ps, VertexConsumer vb, SuperByteBuffer knot,
                                         Quaternionf firstOrient, double x, double y, double z, int light) {
        Vector3f back = HoseNozzleKnot.backOff(firstOrient);
        knot(ps, vb, knot, firstOrient, x + back.x, y + back.y, z + back.z, HoseNozzleKnot.INFLATE, light);
    }
}
