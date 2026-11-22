package com.lycoris.loconautics.content.brakes;

import com.lycoris.loconautics.content.frequency.BindFrequencyScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class BrakeControllerScreen
        extends BindFrequencyScreen<BrakeControllerBlockEntity, BrakeControllerMenu> {

    public BrakeControllerScreen(BrakeControllerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }
}
