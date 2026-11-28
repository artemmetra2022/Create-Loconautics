package com.lycoris.loconautics.content.controls.paneldisplay;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panel.visual.PanelPartVisual;
import com.lycoris.loconautics.content.controls.panel.visual.ShiftedModels;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;

public final class LocomotiveStatusPartVisual extends PanelPartVisual {
    private final TransformedInstance display;
    public LocomotiveStatusPartVisual(VisualizationContext context) {
        super(context);
        display = create(ShiftedModels.plain(LoconauticsPartialModels.STATUS_DISPLAY));
    }
    @Override public boolean matches(PanelPart part) { return part instanceof LocomotiveStatusPart; }
    @Override
    public void update(PanelBlockEntity panel, PanelSlot slot, PoseStack ms, float partialTick, int light, boolean full) {
        if (full) place(display, ms, light);
    }
}
