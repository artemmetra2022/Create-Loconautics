package com.lycoris.loconautics.content.camera;

import com.lycoris.loconautics.content.controls.panel.PanelBlock;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.paneldisplay.LocomotiveStatusPartRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the physical monitor housing. The live feed texture is supplied by
 * CameraFeedRenderer and will be composited onto the screen face here.
 */
public final class CameraMonitorPartRenderer {

    private CameraMonitorPartRenderer() {
    }

    public static void render(PoseStack pose, MultiBufferSource buffers, int packedLight,
                              BlockState panelState, CameraMonitorPart part, float partialTick) {
        // Keep the same compact, already-tested housing geometry as the locomotive display.
        LocomotiveStatusPartRenderer.render(pose, buffers, packedLight, panelState);
    }
}
