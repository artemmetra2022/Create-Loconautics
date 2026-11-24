package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.overhead.OverheadBlocks;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public final class OverheadPonderPlugin implements PonderPlugin {
    @Override
    public String getModId() {
        return LoconauticsConstants.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(OverheadBlocks.POLE_SHORT.getId(), OverheadBlocks.POLE_STANDARD.getId(),
                        OverheadBlocks.POLE_TALL.getId())
                .addStoryBoard("small_engine", OverheadScenes::poles);
        helper.forComponents(OverheadBlocks.WIRE_ITEM.getId())
                .addStoryBoard("small_engine", OverheadScenes::wire);
        helper.forComponents(OverheadBlocks.PANTOGRAPH_ITEM.getId())
                .addStoryBoard("small_engine", OverheadScenes::pantograph);
        helper.forComponents(OverheadBlocks.ELECTRIC_MOTOR_ITEM.getId())
                .addStoryBoard("small_engine", OverheadScenes::motor);
    }
}
