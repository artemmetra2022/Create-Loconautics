package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class HosePonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return LoconauticsConstants.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(LoconauticsRegistries.HOSE.getId())
                .addStoryBoard("hose/winch", HoseScenes::connect)
                .addStoryBoard("hose/pump", HoseScenes::fluid)
                .addStoryBoard("hose/relay", HoseScenes::ropeConnector);

        helper.forComponents(LoconauticsRegistries.HOSE_WINCH.getId())
                .addStoryBoard("hose/winch", HoseScenes::connect)
                .addStoryBoard("hose/winch", HoseScenes::winch)
                .addStoryBoard("hose/winch", HoseScenes::freeSpin)
                .addStoryBoard("hose/pump", HoseScenes::fluid);

        helper.forComponents(LoconauticsRegistries.HOSE_NOZZLE.getId())
                .addStoryBoard("hose/dock", HoseScenes::nozzle)
                .addStoryBoard("hose/pump", HoseScenes::fluid)
                .addStoryBoard("hose/socket", HoseScenes::socket);

        helper.forComponents(LoconauticsRegistries.HOSE_CONNECTOR.getId())
                .addStoryBoard("hose/link", HoseScenes::connectorLink)
                .addStoryBoard("hose/connector", HoseScenes::connector)
                .addStoryBoard("hose/connector", HoseScenes::connectorSpeed)
                .addStoryBoard("hose/relay", HoseScenes::ropeConnector);

        helper.forComponents(LoconauticsConstants.id("fluid_hatch_gray"))
                .addStoryBoard("hose/socket", HoseScenes::socket)
                .addStoryBoard("hose/boiler", HoseScenes::boiler)
                .addStoryBoard("hose/dock", HoseScenes::nozzle);

        helper.forComponents(BoilerBlocks.EXTRA_BODIES.get("industrial").get("ribbed").getId())
                .addStoryBoard("hose/boiler", HoseScenes::boiler);

        helper.forComponents(ResourceLocation.fromNamespaceAndPath("simulated", "rope_connector"))
                .addStoryBoard("hose/relay", HoseScenes::ropeConnector);
    }
}
