package com.lycoris.loconautics.content.boiler.bogey;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.client.BoilerBogeyClient;
import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.casingpicker.BogeyChoice;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class BoilerBogeyStyles {

    public static final ResourceLocation CYCLE_GROUP = LoconauticsConstants.id("boiler");

    // its own group, so the wrench never turns a steam bogey into a standard one
    public static final BogeyStyle BOILER = new BogeyStyle.Builder(LoconauticsConstants.id("boiler"), CYCLE_GROUP)
            .displayName(Component.translatable("loconautics.bogey.style.boiler"))
            .size(BogeySizes.SMALL, BoilerBlocks.BOILER_BOGEY::get, () -> BoilerBogeyClient::smallRenderer)
            .build();

    public static final BogeyChoice CHOICE = new BogeyChoice(BOILER, BogeySizes.SMALL);

    private BoilerBogeyStyles() {
    }

    public static void init() {
    }
}
