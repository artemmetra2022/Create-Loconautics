package com.lycoris.loconautics.content.controls.panelcabsignal;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panel.visual.PanelBulbVisual;
import com.lycoris.loconautics.content.controls.panel.visual.PanelModels;
import com.lycoris.loconautics.content.controls.panel.visual.PanelPartVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;

public final class CabSignalPartVisual extends PanelPartVisual {

    private final CabSignalPart identity;
    private final TransformedInstance shell;
    private final PanelBulbVisual bulb;

    public CabSignalPartVisual(VisualizationContext context, CabSignalPart part) {
        super(context);
        identity = part;
        shell = create(PanelModels.slimBody(part.body()));
        bulb = new PanelBulbVisual(this::create, LoconauticsPartialModels.SIGNAL_LAMP_BULB);
    }

    @Override
    public boolean matches(PanelPart part) {
        return part instanceof CabSignalPart cab && cab.body() == identity.body();
    }

    @Override
    public void update(PanelBlockEntity panel, PanelSlot slot, PoseStack ms, float partialTick,
                       int light, boolean full) {
        if (full) place(shell, ms, light);
        bulb.update(ms, light, panel.bulbGlow(slot, partialTick),
                ((CabSignalPart) panel.getPart(slot)).tint(), full);
    }
}
