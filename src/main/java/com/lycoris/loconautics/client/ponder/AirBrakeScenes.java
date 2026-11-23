package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.content.airbrake.AirBrakeBlocks;
import com.lycoris.loconautics.content.airbrake.BrakeHoseConnectorBlock;
import com.lycoris.loconautics.content.airbrake.EmergencyBrakeBlock;
import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

public final class AirBrakeScenes {

    private AirBrakeScenes() {
    }

    public static void compressorSupply(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "air_brake_compressor", "Air Compressor & Reservoirs", 5);

        BlockPos shaft = new BlockPos(1, 2, 2);
        BlockPos compressor = new BlockPos(2, 2, 2);
        BlockPos res1 = new BlockPos(3, 2, 2);
        BlockPos res2 = new BlockPos(3, 2, 3);

        scene.world().showSection(util.select().fromTo(0, 1, 0, 4, 1, 4), Direction.DOWN);
        scene.world().showSection(util.select().position(compressor), Direction.DOWN);
        scene.world().showSection(util.select().position(shaft), Direction.EAST);
        scene.world().setKineticSpeed(util.select().fromTo(1, 2, 2, 2, 2, 2), 64f);
        scene.idle(15);

        scene.overlay().showText(75)
                .text("The Air Compressor pumps compressed air when driven by rotational force")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(compressor));
        scene.effects().rotationSpeedIndicator(compressor);
        scene.idle(85);

        scene.world().showSection(util.select().fromTo(3, 2, 2, 3, 2, 3), Direction.WEST);
        scene.idle(15);
        scene.overlay().showText(80)
                .text("Adjacent Air Reservoirs equalize pressure with each other and store up to 8.5 bar")
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .placeNearTarget()
                .pointAt(util.vector().topOf(res1));
        scene.idle(90);

        scene.overlay().showText(75)
                .text("Once reservoirs reach 8.5 bar, the compressor unloads to idle until pressure drops below 7.0 bar")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(res2));
        scene.idle(85);
    }

    public static void cabControls(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "air_brake_cab", "Cab Controls & Gauges", 5);

        BlockPos reservoir = new BlockPos(1, 2, 2);
        BlockPos valve = new BlockPos(3, 2, 2);
        BlockPos panel = new BlockPos(3, 2, 1);

        scene.world().showSection(util.select().fromTo(0, 1, 0, 4, 1, 4), Direction.DOWN);
        scene.world().showSection(util.select().fromTo(1, 2, 1, 3, 2, 3), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
                .text("On a locomotive, the Driver's Brake Valve automatically draws air from reservoirs anywhere on the same car")
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .placeNearTarget()
                .pointAt(util.vector().topOf(reservoir));
        scene.idle(90);

        scene.overlay().showControls(util.vector().topOf(valve), Pointing.DOWN, 35).rightClick();
        scene.idle(10);
        scene.overlay().showText(75)
                .text("Right click to step up braking (1-14 service, 15 emergency), or Shift-click to release back to 5.0 bar")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(valve));
        scene.idle(85);

        scene.overlay().showControls(util.vector().topOf(valve), Pointing.DOWN, 35)
                .rightClick()
                .withItem(new ItemStack(BoilerBlocks.VALVE_ITEM.get()));
        scene.idle(10);
        scene.overlay().showText(80)
                .text("Bind a Panel Valve or Locomotive Gauge to the Driver's Brake Valve to control and monitor both pressures from the cab")
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .pointAt(util.vector().topOf(panel));
        scene.idle(90);
    }

    public static void doubleCab(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "air_brake_double_cab", "Double-Cab Locomotives", 7);

        BlockPos frontValve = new BlockPos(1, 2, 2);
        BlockPos rearValve = new BlockPos(5, 2, 2);

        scene.world().showSection(util.select().fromTo(0, 1, 0, 6, 1, 4), Direction.DOWN);
        scene.world().showSection(util.select().fromTo(1, 2, 2, 5, 2, 2), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(75)
                .text("When a locomotive has two cabs or two engines are coupled together, active valves would fight each other")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(frontValve));
        scene.idle(85);

        scene.overlay().showControls(util.vector().topOf(rearValve), Pointing.DOWN, 35).whileSneaking().rightClick();
        scene.idle(10);
        scene.overlay().showText(80)
                .text("Shift-right click an inactive valve at notch 0 to isolate it (Cut-Out) so only the lead cab controls the brake pipe")
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .placeNearTarget()
                .pointAt(util.vector().topOf(rearValve));
        scene.idle(90);
    }

    public static void consistHoses(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "air_brake_consist", "Connecting Cars with Brake Hoses", 9);

        BlockPos adapterA = new BlockPos(3, 2, 2);
        BlockPos adapterB = new BlockPos(5, 2, 2);
        BlockPos tripleB = new BlockPos(7, 2, 2);

        scene.world().showSection(util.select().fromTo(0, 1, 0, 8, 1, 4), Direction.DOWN);
        scene.world().showSection(util.select().fromTo(0, 2, 1, 3, 2, 3), Direction.DOWN);
        scene.world().showSection(util.select().fromTo(5, 2, 1, 8, 2, 3), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(75)
                .text("Mount Brake Hose Adapters on the ends of adjacent cars")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(adapterA));
        scene.idle(85);

        scene.overlay().showControls(util.vector().topOf(adapterA), Pointing.DOWN, 30)
                .rightClick()
                .withItem(new ItemStack(AirBrakeBlocks.BRAKE_HOSE.get()));
        scene.idle(20);
        scene.overlay().showControls(util.vector().topOf(adapterB), Pointing.DOWN, 30)
                .rightClick()
                .withItem(new ItemStack(AirBrakeBlocks.BRAKE_HOSE.get()));
        scene.world().modifyBlocks(util.select().position(adapterA),
                s -> s.setValue(BrakeHoseConnectorBlock.OPEN, true), false);
        scene.world().modifyBlocks(util.select().position(adapterB),
                s -> s.setValue(BrakeHoseConnectorBlock.OPEN, true), false);
        scene.idle(15);

        scene.overlay().showText(80)
                .text("Connecting a Brake Hose automatically opens both adapters and equalizes pipe pressure along the consist")
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .pointAt(util.vector().centerOf(new BlockPos(4, 2, 2)));
        scene.idle(90);

        scene.overlay().showText(85)
                .text("Close adapters by clicking before uncoupling! A snapped hose with open adapters vents the pipe and triggers emergency braking")
                .attachKeyFrame()
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .pointAt(util.vector().topOf(tripleB));
        scene.idle(95);
    }

    public static void tripleValve(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "air_brake_triple_valve", "Triple Valve & Manual Release", 5);

        BlockPos triple = new BlockPos(2, 2, 2);

        scene.world().showSection(util.select().fromTo(0, 1, 0, 4, 1, 4), Direction.DOWN);
        scene.world().showSection(util.select().fromTo(1, 2, 1, 3, 2, 3), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
                .text("Every braked car needs a Triple Valve. It charges its auxiliary reservoir from the brake pipe")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(triple));
        scene.idle(90);

        scene.overlay().showText(80)
                .text("When brake pipe pressure drops, the Triple Valve applies proportional braking to that car")
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .placeNearTarget()
                .pointAt(util.vector().topOf(triple));
        scene.idle(90);

        scene.overlay().showControls(util.vector().topOf(triple), Pointing.DOWN, 35).whileSneaking().rightClick();
        scene.idle(10);
        scene.overlay().showText(75)
                .text("Shift-right click an uncoupled car's Triple Valve to bleed its air and release the brakes for shunting")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(triple));
        scene.idle(85);
    }

    public static void emergencyBrake(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "air_brake_emergency", "Passenger Emergency Brake", 5);

        BlockPos handle = new BlockPos(2, 2, 1);

        scene.world().showSection(util.select().fromTo(0, 1, 0, 4, 1, 4), Direction.DOWN);
        scene.world().showSection(util.select().fromTo(1, 2, 1, 3, 3, 3), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showControls(util.vector().centerOf(handle), Pointing.DOWN, 35).rightClick();
        scene.idle(10);
        scene.world().modifyBlocks(util.select().position(handle),
                s -> s.setValue(EmergencyBrakeBlock.POWERED, true), false);
        scene.effects().indicateRedstone(handle);

        scene.overlay().showText(80)
                .text("Pulling the Emergency Brake vents the brake pipe to 0 bar, stopping the entire coupled train")
                .attachKeyFrame()
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .pointAt(util.vector().centerOf(handle));
        scene.idle(90);

        scene.overlay().showControls(util.vector().centerOf(handle), Pointing.DOWN, 35).rightClick();
        scene.idle(10);
        scene.world().modifyBlocks(util.select().position(handle),
                s -> s.setValue(EmergencyBrakeBlock.POWERED, false), false);
        scene.overlay().showText(65)
                .text("Click it again to reset the handle so the locomotive can recharge the pipe")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(handle));
        scene.idle(75);
    }

    private static CreateSceneBuilder open(SceneBuilder builder, String id, String title, int width) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(id, title);
        scene.configureBasePlate(0, 0, width);
        scene.showBasePlate();
        scene.idle(5);
        return scene;
    }
}
