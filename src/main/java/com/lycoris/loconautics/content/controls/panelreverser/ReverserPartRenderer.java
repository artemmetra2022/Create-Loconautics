package com.lycoris.loconautics.content.controls.panelreverser;

import com.lycoris.loconautics.content.controls.panelswitch.SwitchPartRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.state.BlockState;

public class ReverserPartRenderer {

    public static void render(float fraction, PoseStack ms, MultiBufferSource buffers,
                              int light, BlockState state, int body, int knobColor, int style, int options, float bulbGlow) {
        SwitchPartRenderer.render(fraction, ms, buffers, light, state, body, knobColor, style, options, bulbGlow);
    }
}
