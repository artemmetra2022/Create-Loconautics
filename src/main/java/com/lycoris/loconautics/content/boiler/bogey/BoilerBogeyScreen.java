package com.lycoris.loconautics.content.boiler.bogey;

import com.lycoris.loconautics.content.frequency.BindFrequencyScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class BoilerBogeyScreen extends BindFrequencyScreen<BoilerBogeyBlockEntity, BoilerBogeyMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "loconautics", "textures/gui/frequency/frequency_bind_4.png");

    public BoilerBogeyScreen(BoilerBogeyMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, TEXTURE, 214, 153, 214, 153, 19, 158, 119, 157, 179);
    }
}
