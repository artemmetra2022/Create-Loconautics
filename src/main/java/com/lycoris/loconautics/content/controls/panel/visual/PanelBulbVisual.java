package com.lycoris.loconautics.content.controls.panel.visual;

import com.lycoris.loconautics.content.controls.panel.style.PanelBulbRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.material.Transparency;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;

import java.util.function.Function;

public final class PanelBulbVisual {

    private final TransformedInstance glass;
    private final TransformedInstance glow;
    private boolean lastLit;
    private int lastLevel = -1;
    private int lastTint = -1;

    public PanelBulbVisual(Function<Model, TransformedInstance> factory, PartialModel bulb) {
        glass = factory.apply(ShiftedModels.plain(bulb));
        glow = factory.apply(ShiftedModels.withTransparency(bulb, Transparency.ADDITIVE));
    }

    public void update(PoseStack ms, int light, float bulbGlow, boolean full) {
        update(ms, light, bulbGlow, 0xFFFFFF, full);
    }

    public void update(PoseStack ms, int light, float bulbGlow, int tint, boolean full) {
        float amount = PanelBulbRenderer.glowFor(bulbGlow);
        boolean lit = PanelBulbRenderer.isLit(amount);
        int level = lit ? Mth.clamp(PanelBulbRenderer.additiveLevel(amount), 0, 255) : -1;
        if (!full && lit == lastLit && level == lastLevel && tint == lastTint) return;
        lastLit = lit;
        lastLevel = level;
        lastTint = tint;

        int red = tint >> 16 & 0xFF;
        int green = tint >> 8 & 0xFF;
        int blue = tint & 0xFF;
        PanelPartVisual.place(glass, ms, lit ? LightTexture.FULL_BRIGHT : light);
        glass.color(red, green, blue, 255);
        if (lit) {
            glow.setTransform(ms);
            glow.light(LightTexture.FULL_BRIGHT);
            glow.color(red * level / 255, green * level / 255, blue * level / 255, 255);
        } else {
            glow.setZeroTransform();
        }
        glow.setChanged();
        glass.setChanged();
    }
}
