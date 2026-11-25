package com.lycoris.loconautics.content.camera;

import com.lycoris.loconautics.content.controls.paneldisplay.LocomotiveStatusPartRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.state.BlockState;

/** Renders the monitor housing and the live camera image on its screen. */
public final class CameraMonitorPartRenderer {

    private CameraMonitorPartRenderer() {
    }

    public static void render(PoseStack pose, MultiBufferSource buffers, int packedLight,
                              BlockState panelState, CameraMonitorPart part, float partialTick) {
        LocomotiveStatusPartRenderer.render(pose, buffers, packedLight, panelState);
        CameraFeedManager.request(part.camera());
        CameraFeedManager.renderMonitor(part.camera(), pose, buffers);
    }
}
