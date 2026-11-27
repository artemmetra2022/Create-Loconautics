package com.lycoris.loconautics.content.controls.panel.visual;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panellamp.SignalLampPart;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;

public final class SignalLampPartVisual extends PanelPartVisual {

    private final SignalLampPart identity;
    private final TransformedInstance shell;
    private final PanelBulbVisual bulb;

    public SignalLampPartVisual(VisualizationContext context, SignalLampPart lamp) {
        super(context);
        identity = lamp;
        shell = create(PanelModels.slimBody(lamp.body()));
        bulb = new PanelBulbVisual(this::create, LoconauticsPartialModels.SIGNAL_LAMP_BULB);
    }

    @Override
    public boolean matches(com.lycoris.loconautics.content.controls.panel.PanelPart part) {
        return part instanceof SignalLampPart lamp
                && lamp.target().equals(identity.target())
                && lamp.mode() == identity.mode()
                && lamp.body() == identity.body();
    }

    @Override
    public void update(PanelBlockEntity panel, PanelSlot slot, PoseStack ms, float partialTick,
                       int light, boolean full) {
        if (full) place(shell, ms, light);
        bulb.update(ms, light, panel.bulbGlow(slot, partialTick),
                ((SignalLampPart) panel.getPart(slot)).tint(panel), full);
    }
}
