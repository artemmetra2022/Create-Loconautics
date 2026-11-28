package com.lycoris.loconautics.content.hose.link;

import com.lycoris.loconautics.client.HoseDraw;
import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.content.steelcable.SteelCableHolderBehaviour;
import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class HoseConnectorRenderer implements BlockEntityRenderer<HoseConnectorBlockEntity> {

    public HoseConnectorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(HoseConnectorBlockEntity be, float partialTicks, PoseStack ps,
                       MultiBufferSource buffer, int light, int overlay) {
        if (be.previewStrand() != null && be.getLevel() != null) {
            com.lycoris.loconautics.client.HoseStrandDraw.draw(ps, buffer, be.getLevel(), be.getBlockPos(),
                    be.getBlockState(), be.previewStrand());
        }
        SteelCableHolderBehaviour holder = be.getBehaviour(SteelCableHolderBehaviour.TYPE);
        if (be.previewStrand() == null || holder == null || holder.attachedStrandIds().isEmpty()) {
            return;
        }
        Direction open = be.plateSide().getOpposite();
        SuperByteBuffer knot = CachedBuffers.partialFacing(
                LoconauticsPartialModels.HOSE_NOZZLE_KNOT, be.getBlockState(), Direction.NORTH);
        Vec3 attach = be.getAttachmentPoint().subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
        HoseDraw.connectorKnot(ps, buffer.getBuffer(RenderType.solid()), knot, attach,
                new Vec3(open.getStepX(), open.getStepY(), open.getStepZ()), light);
    }

    @Override
    public AABB getRenderBoundingBox(HoseConnectorBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1.0);
    }
}
