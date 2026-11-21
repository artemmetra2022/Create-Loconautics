package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.content.brakes.BrakeControllerBlock;
import com.lycoris.loconautics.content.brakes.BrakeControllerBlockEntity;
import com.lycoris.loconautics.content.brakes.BrakeControllerMenu;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The structure runs along Z: output shaft, Transmission, Brake Controller, then the shaft coming from the engine.
 * The engine itself stays out of the scene, a plain shaft stands in for its output.
 */
public class BrakeControllerScenes {

    private static final float INPUT_RPM = 64f;

    private static final BlockPos BRAKE = new BlockPos(2, 1, 3);
    private static final BlockPos TRANSMISSION = new BlockPos(2, 1, 2);
    private static final BlockPos ENGINE_SHAFT = new BlockPos(2, 1, 5);
    private static final BlockPos OUTPUT_SHAFT = new BlockPos(2, 1, 0);

    public static void single(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "brake_controller_single", "Braking the drive shaft");

        scene.world().showSection(util.select().position(BRAKE), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(70)
                .text("The Brake Controller passes rotation straight through, from one end to the other")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(BRAKE));
        scene.idle(80);

        scene.world().showSection(transmission(util), Direction.SOUTH);
        scene.world().showSection(engine(util), Direction.NORTH);
        scene.world().setKineticSpeed(engine(util).add(util.select().position(BRAKE)), INPUT_RPM);
        scene.world().setKineticSpeed(transmission(util), INPUT_RPM);
        scene.idle(15);
        scene.overlay().showText(80)
                .text("It only fits between an engine and a Transmission, all on the same shaft line")
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .placeNearTarget()
                .pointAt(util.vector().topOf(ENGINE_SHAFT));
        scene.idle(90);

        scene.overlay().showText(60)
                .text("With the brake released the shaft keeps its full speed")
                .placeNearTarget()
                .pointAt(util.vector().topOf(OUTPUT_SHAFT));
        scene.effects().rotationSpeedIndicator(OUTPUT_SHAFT);
        scene.idle(70);

        brake(scene, util, 8);
        scene.effects().rotationSpeedIndicator(OUTPUT_SHAFT);
        scene.overlay().showText(70)
                .text("Pulling the brake slows the output side, the harder the slower")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(OUTPUT_SHAFT));
        scene.idle(80);

        brake(scene, util, 15);
        scene.overlay().showText(70)
                .text("At full brake the output stops while the engine keeps turning")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(BRAKE));
        scene.idle(80);
    }

    public static void frequency(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "brake_controller_frequency", "Binding the Brake Controller");

        scene.world().showSection(util.select().position(BRAKE), Direction.DOWN);
        scene.world().showSection(transmission(util), Direction.SOUTH);
        scene.world().showSection(engine(util), Direction.NORTH);
        scene.world().setKineticSpeed(engine(util).add(util.select().position(BRAKE)), INPUT_RPM);
        scene.world().setKineticSpeed(transmission(util), INPUT_RPM);
        scene.idle(20);

        GuiPonder.Panel panel = GuiPonder.open(scene, GuiPonder.TWO_ROWS,
                Component.translatable(BrakeControllerMenu.BRAKE_KEY));

        scene.overlay().showControls(util.vector().topOf(BRAKE), Pointing.DOWN, 30).rightClick();
        scene.idle(10);
        scene.overlay().showText(50)
                .text("Right click it with empty hands to open its Interface")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(BRAKE));
        scene.idle(55);

        panel.show(scene);
        scene.idle(20);
        panel.mark(scene, 0);
        scene.overlay().showText(80)
                .text("The Brake row is the frequency that sets the brake, the lever position is the strength")
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(20);
        panel.put(scene, 0, new ItemStack(Items.GOLD_INGOT));
        scene.idle(20);
        panel.put(scene, 1, new ItemStack(Items.REDSTONE));
        scene.idle(70);

        panel.mark(scene, -1);
        scene.overlay().showText(80)
                .text("Sneak and right click an Analog Controller with the item in hand to copy its frequency")
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .placeNearTarget();
        scene.idle(90);
        panel.hide(scene);
        scene.idle(15);
    }

    private static Selection transmission(SceneBuildingUtil util) {
        return util.select().fromTo(2, 1, 0, 2, 1, 2);
    }

    private static Selection engine(SceneBuildingUtil util) {
        return util.select().fromTo(2, 1, 4, 2, 1, 5);
    }

    private static void brake(CreateSceneBuilder scene, SceneBuildingUtil util, int signal) {
        scene.overlay().showControls(util.vector().topOf(BRAKE), Pointing.DOWN, 30).rightClick();
        scene.idle(7);
        scene.world().modifyBlockEntityNBT(util.select().position(BRAKE), BrakeControllerBlockEntity.class,
                nbt -> nbt.putInt("BrakeSignal", signal));
        scene.world().modifyBlocks(util.select().position(BRAKE),
                state -> state.setValue(BrakeControllerBlock.POWERED, signal > 0), false);
        scene.effects().indicateRedstone(BRAKE);
        float coefficient = 1f - signal / (float) BrakeControllerBlockEntity.MAX_SIGNAL;
        scene.world().setKineticSpeed(transmission(util), INPUT_RPM * coefficient);
    }

    private static CreateSceneBuilder open(SceneBuilder builder, String id, String title) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(id, title);
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);
        return scene;
    }
}
