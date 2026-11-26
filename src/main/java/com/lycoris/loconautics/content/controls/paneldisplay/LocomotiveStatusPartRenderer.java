package com.lycoris.loconautics.content.controls.paneldisplay;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.state.BlockState;

public final class LocomotiveStatusPartRenderer {
    private LocomotiveStatusPartRenderer() { }
    public static void render(PoseStack ms, MultiBufferSource buffers, int light, BlockState state) {
        CachedBuffers.partial(LoconauticsPartialModels.STATUS_DISPLAY, state)
                .light(light).renderInto(ms, buffers.getBuffer(RenderType.cutout()));
    }
}
