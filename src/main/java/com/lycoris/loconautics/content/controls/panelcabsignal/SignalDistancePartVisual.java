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

public final class SignalDistancePartVisual extends PanelPartVisual {

    /** Lamp centres in 1/16 block, relative to the slot centre (a 2x2 grid). */
    public static final float[] OFFSET_X = {-1.4f, 1.4f, -1.4f, 1.4f};
    public static final float[] OFFSET_Z = {-1.4f, -1.4f, 1.4f, 1.4f};
    public static final float LAMP_SCALE = 0.42f;

    private final SignalDistancePart identity;
    private final TransformedInstance shell;
    private final PanelBulbVisual[] lamps = new PanelBulbVisual[SignalDistancePart.LAMPS];

    public SignalDistancePartVisual(VisualizationContext context, SignalDistancePart part) {
        super(context);
        identity = part;
        shell = create(PanelModels.slimBody(part.body()));
        for (int i = 0; i < lamps.length; i++) {
            lamps[i] = new PanelBulbVisual(this::create, LoconauticsPartialModels.SIGNAL_LAMP_BULB);
        }
    }

    @Override
    public boolean matches(PanelPart part) {
        return part instanceof SignalDistancePart distance && distance.body() == identity.body();
    }

    @Override
    public void update(PanelBlockEntity panel, PanelSlot slot, PoseStack ms, float partialTick,
                       int light, boolean full) {
        if (full) place(shell, ms, light);
        SignalDistancePart part = (SignalDistancePart) panel.getPart(slot);
        float glow = panel.bulbGlow(slot, partialTick);
        for (int i = 0; i < lamps.length; i++) {
            ms.pushPose();
            ms.translate((4 + OFFSET_X[i]) / 16.0, 0, (4 + OFFSET_Z[i]) / 16.0);
            ms.scale(LAMP_SCALE, 1f, LAMP_SCALE);
            ms.translate(-4 / 16.0, 0, -4 / 16.0);
            lamps[i].update(ms, light, i < part.lit() ? glow : 0f, part.tint(), full);
            ms.popPose();
        }
    }
}
