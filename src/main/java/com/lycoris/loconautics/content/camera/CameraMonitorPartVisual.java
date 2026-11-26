package com.lycoris.loconautics.content.camera;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panel.visual.PanelPartVisual;
import com.lycoris.loconautics.content.controls.panel.visual.ShiftedModels;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;

/**
 * Flywheel counterpart for the camera monitor housing.
 *
 * Without a visual here the panel renderer skipped this part entirely,
 * making a mounted camera monitor appear transparent/invisible.
 */
public final class CameraMonitorPartVisual extends PanelPartVisual {

    private final CameraMonitorPart identity;
    private final TransformedInstance housing;

    public CameraMonitorPartVisual(VisualizationContext context, CameraMonitorPart part) {
        super(context);
        identity = part;
        housing = create(ShiftedModels.plain(LoconauticsPartialModels.STATUS_DISPLAY));
    }

    @Override
    public boolean matches(PanelPart part) {
        return part instanceof CameraMonitorPart monitor && monitor.camera().equals(identity.camera());
    }

    @Override
    public void update(PanelBlockEntity panel, PanelSlot slot, PoseStack pose, float partialTick,
                       int light, boolean full) {
        if (full) place(housing, pose, light);
    }
}
