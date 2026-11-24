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
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class AirBrakeScenes {

    private AirBrakeScenes() {
    }

    public static void compressorSupply(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "air_brake_compressor", "loconautics.ponder.air_brake.compressor.header", 5);

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
                .text(tr("loconautics.ponder.air_brake.compressor.text_1"))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(compressor));
        scene.effects().rotationSpeedIndicator(compressor);
        scene.idle(85);

        scene.world().showSection(util.select().fromTo(3, 2, 2, 3, 2, 3), Direction.WEST);
        scene.idle(15);
        scene.overlay().showText(80)
                .text(tr("loconautics.ponder.air_brake.compressor.text_2"))
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .placeNearTarget()
                .pointAt(util.vector().topOf(res1));
        scene.idle(90);

        scene.overlay().showText(75)
                .text(tr("loconautics.ponder.air_brake.compressor.text_3"))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(res2));
        scene.idle(85);
    }

    public static void cabControls(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "air_brake_cab", "loconautics.ponder.air_brake.cab.header", 5);

        BlockPos reservoir = new BlockPos(1, 2, 2);
        BlockPos valve = new BlockPos(3, 2, 2);
        BlockPos panel = new BlockPos(3, 2, 1);

        scene.world().showSection(util.select().fromTo(0, 1, 0, 4, 1, 4), Direction.DOWN);
        scene.world().showSection(util.select().fromTo(1, 2, 1, 3, 2, 3), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
                .text(tr("loconautics.ponder.air_brake.cab.text_1"))
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .placeNearTarget()
                .pointAt(util.vector().topOf(reservoir));
        scene.idle(90);

        scene.overlay().showControls(util.vector().topOf(valve), Pointing.DOWN, 35).rightClick();
        scene.idle(10);
        scene.overlay().showText(75)
                .text(tr("loconautics.ponder.air_brake.cab.text_2"))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(valve));
        scene.idle(85);

        scene.overlay().showControls(util.vector().topOf(valve), Pointing.DOWN, 35)
                .rightClick()
                .withItem(new ItemStack(BoilerBlocks.VALVE_ITEM.get()));
        scene.idle(10);
        scene.overlay().showText(80)
                .text(tr("loconautics.ponder.air_brake.cab.text_3"))
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .pointAt(util.vector().topOf(panel));
        scene.idle(90);
    }

    public static void doubleCab(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "air_brake_double_cab", "loconautics.ponder.air_brake.double_cab.header", 7);

        BlockPos frontValve = new BlockPos(1, 2, 2);
        BlockPos rearValve = new BlockPos(5, 2, 2);

        scene.world().showSection(util.select().fromTo(0, 1, 0, 6, 1, 4), Direction.DOWN);
        scene.world().showSection(util.select().fromTo(1, 2, 2, 5, 2, 2), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(75)
                .text(tr("loconautics.ponder.air_brake.double_cab.text_1"))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(frontValve));
        scene.idle(85);

        scene.overlay().showControls(util.vector().topOf(rearValve), Pointing.DOWN, 35).whileSneaking().rightClick();
        scene.idle(10);
        scene.overlay().showText(80)
                .text(tr("loconautics.ponder.air_brake.double_cab.text_2"))
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .placeNearTarget()
                .pointAt(util.vector().topOf(rearValve));
        scene.idle(90);
    }

    public static void consistHoses(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "air_brake_consist", "loconautics.ponder.air_brake.consist.header", 9);

        BlockPos adapterA = new BlockPos(3, 2, 2);
        BlockPos adapterB = new BlockPos(5, 2, 2);
        BlockPos tripleB = new BlockPos(7, 2, 2);

        scene.world().showSection(util.select().fromTo(0, 1, 0, 8, 1, 4), Direction.DOWN);
        scene.world().showSection(util.select().fromTo(0, 2, 1, 3, 2, 3), Direction.DOWN);
        scene.world().showSection(util.select().fromTo(5, 2, 1, 8, 2, 3), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(75)
                .text(tr("loconautics.ponder.air_brake.consist.text_1"))
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
                .text(tr("loconautics.ponder.air_brake.consist.text_2"))
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .pointAt(util.vector().centerOf(new BlockPos(4, 2, 2)));
        scene.idle(90);

        scene.overlay().showText(85)
                .text(tr("loconautics.ponder.air_brake.consist.text_3"))
                .attachKeyFrame()
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .pointAt(util.vector().topOf(tripleB));
        scene.idle(95);
    }

    public static void tripleValve(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "air_brake_triple_valve", "loconautics.ponder.air_brake.triple_valve.header", 5);

        BlockPos triple = new BlockPos(2, 2, 2);

        scene.world().showSection(util.select().fromTo(0, 1, 0, 4, 1, 4), Direction.DOWN);
        scene.world().showSection(util.select().fromTo(1, 2, 1, 3, 2, 3), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
                .text(tr("loconautics.ponder.air_brake.triple_valve.text_1"))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(triple));
        scene.idle(90);

        scene.overlay().showText(80)
                .text(tr("loconautics.ponder.air_brake.triple_valve.text_2"))
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .placeNearTarget()
                .pointAt(util.vector().topOf(triple));
        scene.idle(90);

        scene.overlay().showControls(util.vector().topOf(triple), Pointing.DOWN, 35).whileSneaking().rightClick();
        scene.idle(10);
        scene.overlay().showText(75)
                .text(tr("loconautics.ponder.air_brake.triple_valve.text_3"))
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(triple));
        scene.idle(85);
    }

    public static void emergencyBrake(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "air_brake_emergency", "loconautics.ponder.air_brake.emergency.header", 5);

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
                .text(tr("loconautics.ponder.air_brake.emergency.text_1"))
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
                .text(tr("loconautics.ponder.air_brake.emergency.text_2"))
                .placeNearTarget()
                .pointAt(util.vector().centerOf(handle));
        scene.idle(75);
    }

    public static void preDepartureCheck(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "air_brake_pre_departure_check",
                "loconautics.ponder.air_brake.pre_departure.header", 9);

        BlockPos valve = new BlockPos(2, 2, 2);
        BlockPos firstTriple = new BlockPos(4, 2, 2);
        BlockPos middleTriple = new BlockPos(6, 2, 2);
        BlockPos rearTriple = new BlockPos(8, 2, 2);

        scene.world().setBlock(firstTriple, AirBrakeBlocks.TRIPLE_VALVE.get().defaultBlockState(), false);
        scene.world().setBlock(middleTriple, AirBrakeBlocks.TRIPLE_VALVE.get().defaultBlockState(), false);
        scene.world().setBlock(rearTriple, AirBrakeBlocks.TRIPLE_VALVE.get().defaultBlockState(), false);
        scene.world().showSection(util.select().fromTo(0, 1, 0, 8, 2, 4), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(85)
                .text(tr("loconautics.ponder.air_brake.pre_departure.text_1"))
                .attachKeyFrame().placeNearTarget().pointAt(util.vector().topOf(valve));
        scene.idle(95);

        scene.overlay().showText(85)
                .text(tr("loconautics.ponder.air_brake.pre_departure.text_2"))
                .attachKeyFrame().colored(PonderPalette.BLUE).placeNearTarget()
                .pointAt(util.vector().topOf(firstTriple));
        scene.idle(95);

        scene.overlay().showText(85)
                .text(tr("loconautics.ponder.air_brake.pre_departure.text_3"))
                .attachKeyFrame().placeNearTarget().pointAt(util.vector().topOf(middleTriple));
        scene.idle(95);

        scene.overlay().showControls(util.vector().topOf(valve), Pointing.DOWN, 35).rightClick();
        scene.idle(10);
        scene.overlay().showText(85)
                .text(tr("loconautics.ponder.air_brake.pre_departure.text_4"))
                .attachKeyFrame().placeNearTarget().pointAt(util.vector().topOf(valve));
        scene.idle(95);

        scene.overlay().showText(85)
                .text(tr("loconautics.ponder.air_brake.pre_departure.text_5"))
                .attachKeyFrame().placeNearTarget().pointAt(util.vector().topOf(rearTriple));
        scene.idle(95);

        scene.overlay().showText(85)
                .text(tr("loconautics.ponder.air_brake.pre_departure.text_6"))
                .attachKeyFrame().colored(PonderPalette.GREEN).placeNearTarget()
                .pointAt(util.vector().topOf(valve));
        scene.idle(95);

        scene.overlay().showText(85)
                .text(tr("loconautics.ponder.air_brake.pre_departure.text_7"))
                .attachKeyFrame().colored(PonderPalette.RED).placeNearTarget()
                .pointAt(util.vector().topOf(rearTriple));
        scene.idle(95);
    }

    public static void faultFinding(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "air_brake_fault_finding",
                "loconautics.ponder.air_brake.fault_finding.header", 9);

        BlockPos valve = new BlockPos(2, 2, 2);
        BlockPos frontTriple = new BlockPos(4, 2, 2);
        BlockPos rearTriple = new BlockPos(8, 2, 2);
        scene.world().setBlock(frontTriple, AirBrakeBlocks.TRIPLE_VALVE.get().defaultBlockState(), false);
        scene.world().setBlock(rearTriple, AirBrakeBlocks.TRIPLE_VALVE.get().defaultBlockState(), false);
        scene.world().showSection(util.select().fromTo(0, 1, 0, 8, 2, 4), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(85)
                .text(tr("loconautics.ponder.air_brake.fault_finding.text_1"))
                .attachKeyFrame().placeNearTarget().pointAt(util.vector().topOf(valve));
        scene.idle(95);

        scene.overlay().showText(85)
                .text(tr("loconautics.ponder.air_brake.fault_finding.text_2"))
                .attachKeyFrame().placeNearTarget().pointAt(util.vector().topOf(frontTriple));
        scene.idle(95);

        scene.overlay().showText(85)
                .text(tr("loconautics.ponder.air_brake.fault_finding.text_3"))
                .attachKeyFrame().colored(PonderPalette.BLUE).placeNearTarget()
                .pointAt(util.vector().topOf(rearTriple));
        scene.idle(95);

        scene.overlay().showText(85)
                .text(tr("loconautics.ponder.air_brake.fault_finding.text_4"))
                .attachKeyFrame().placeNearTarget().pointAt(util.vector().topOf(rearTriple));
        scene.idle(95);

        scene.overlay().showText(85)
                .text(tr("loconautics.ponder.air_brake.fault_finding.text_5"))
                .attachKeyFrame().colored(PonderPalette.RED).placeNearTarget()
                .pointAt(util.vector().topOf(valve));
        scene.idle(95);

        scene.overlay().showText(85)
                .text(tr("loconautics.ponder.air_brake.fault_finding.text_6"))
                .attachKeyFrame().placeNearTarget().pointAt(util.vector().topOf(frontTriple));
        scene.idle(95);

        scene.overlay().showText(85)
                .text(tr("loconautics.ponder.air_brake.fault_finding.text_7"))
                .attachKeyFrame().colored(PonderPalette.GREEN).placeNearTarget()
                .pointAt(util.vector().topOf(valve));
        scene.idle(95);
    }

    public static void brakingPractice(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "air_brake_braking_practice",
                "loconautics.ponder.air_brake.braking_practice.header", 7);

        BlockPos valve = new BlockPos(3, 2, 2);
        BlockPos leadingTriple = new BlockPos(4, 2, 2);
        BlockPos rearTriple = new BlockPos(6, 2, 2);

        // A simple two-car diagram makes the delay along the brake pipe visible.
        scene.world().setBlock(leadingTriple,
                AirBrakeBlocks.TRIPLE_VALVE.get().defaultBlockState(), false);
        scene.world().setBlock(rearTriple,
                AirBrakeBlocks.TRIPLE_VALVE.get().defaultBlockState(), false);
        scene.world().showSection(util.select().fromTo(0, 1, 0, 6, 2, 4), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(85)
                .text(tr("loconautics.ponder.air_brake.braking_practice.text_1"))
                .attachKeyFrame().placeNearTarget().pointAt(util.vector().topOf(valve));
        scene.idle(95);

        scene.overlay().showText(85)
                .text(tr("loconautics.ponder.air_brake.braking_practice.text_2"))
                .attachKeyFrame().placeNearTarget().pointAt(util.vector().topOf(valve));
        scene.idle(95);

        scene.overlay().showControls(util.vector().topOf(valve), Pointing.DOWN, 35).rightClick();
        scene.effects().indicateRedstone(valve);
        scene.overlay().showText(85)
                .text(tr("loconautics.ponder.air_brake.braking_practice.text_3"))
                .attachKeyFrame().colored(PonderPalette.BLUE).placeNearTarget()
                .pointAt(util.vector().topOf(valve));
        scene.idle(95);

        scene.overlay().showText(85)
                .text(tr("loconautics.ponder.air_brake.braking_practice.text_4"))
                .attachKeyFrame().placeNearTarget().pointAt(util.vector().topOf(leadingTriple));
        scene.idle(95);

        scene.overlay().showText(85)
                .text(tr("loconautics.ponder.air_brake.braking_practice.text_5"))
                .attachKeyFrame().placeNearTarget().pointAt(util.vector().topOf(rearTriple));
        scene.idle(95);

        scene.overlay().showText(95)
                .text(tr("loconautics.ponder.air_brake.braking_practice.text_6"))
                .attachKeyFrame().colored(PonderPalette.BLUE).placeNearTarget()
                .pointAt(util.vector().topOf(rearTriple));
        scene.idle(105);

        scene.overlay().showText(95)
                .text(tr("loconautics.ponder.air_brake.braking_practice.text_7"))
                .attachKeyFrame().colored(PonderPalette.GREEN).placeNearTarget()
                .pointAt(util.vector().topOf(valve));
        scene.idle(105);

        scene.overlay().showText(95)
                .text(tr("loconautics.ponder.air_brake.braking_practice.text_8"))
                .attachKeyFrame().colored(PonderPalette.RED).placeNearTarget()
                .pointAt(util.vector().topOf(valve));
        scene.idle(105);

        scene.overlay().showControls(util.vector().topOf(valve), Pointing.DOWN, 35)
                .whileSneaking().rightClick();
        scene.overlay().showText(90)
                .text(tr("loconautics.ponder.air_brake.braking_practice.text_9"))
                .attachKeyFrame().placeNearTarget().pointAt(util.vector().topOf(valve));
        scene.idle(100);

        scene.overlay().showText(90)
                .text(tr("loconautics.ponder.air_brake.braking_practice.text_10"))
                .attachKeyFrame().placeNearTarget().pointAt(util.vector().topOf(leadingTriple));
        scene.idle(100);
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static CreateSceneBuilder open(SceneBuilder builder, String id, String titleKey, int width) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(id, tr(titleKey));
        scene.configureBasePlate(0, 0, width);
        scene.showBasePlate();
        scene.idle(5);
        return scene;
    }
}
