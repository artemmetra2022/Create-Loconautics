package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.content.overhead.CatenaryMastBlock;
import com.lycoris.loconautics.content.overhead.OverheadBlocks;
import com.lycoris.loconautics.content.overhead.PantographBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class OverheadScenes {
    private static final BlockPos POLE = new BlockPos(1, 1, 2);
    private static final BlockPos SECOND_POLE = new BlockPos(4, 1, 2);
    private static final BlockPos PANTOGRAPH = new BlockPos(2, 1, 2);
    private static final BlockPos MOTOR = new BlockPos(2, 1, 2);

    private OverheadScenes() { }

    public static void poles(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "overhead_poles", "Building overhead line masts");
        clear(scene);
        placeMast(scene, POLE, 4, Direction.EAST);
        scene.world().showSection(util.select().fromTo(new BlockPos(1, 1, 2), new BlockPos(1, 4, 2)), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(70)
                .text("Choose a short, standard or tall pole kit to match the required clearance")
                .attachKeyFrame().placeNearTarget().pointAt(util.vector().topOf(new BlockPos(1, 3, 2)));
        scene.idle(80);
        scene.overlay().showText(70)
                .text("The kit places a base, repeated shaft sections and a cantilever head in one operation")
                .attachKeyFrame().placeNearTarget().pointAt(util.vector().topOf(new BlockPos(1, 4, 2)));
        scene.idle(80);
        scene.overlay().showText(70)
                .text("Stand on the track side when placing it so the cantilever points over the track")
                .attachKeyFrame().placeNearTarget().pointAt(util.vector().topOf(new BlockPos(1, 4, 2)));
        scene.idle(80);
    }

    public static void wire(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "overhead_wire", "Installing the contact wire");
        clear(scene);
        placeMast(scene, POLE, 4, Direction.EAST);
        placeMast(scene, SECOND_POLE, 4, Direction.WEST);
        for (int x = 2; x <= 3; x++) {
            scene.world().setBlock(new BlockPos(x, 4, 2),
                    OverheadBlocks.WIRE.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X), false);
        }
        scene.world().showSection(util.select().fromTo(new BlockPos(1, 1, 2), new BlockPos(4, 4, 2)), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(70)
                .text("Connect the contact wire between cantilever heads at a consistent height")
                .attachKeyFrame().placeNearTarget().pointAt(util.vector().topOf(new BlockPos(2, 4, 2)));
        scene.idle(80);
        scene.overlay().showText(70)
                .text("The wire is non-colliding and can be laid as straight horizontal segments")
                .attachKeyFrame().placeNearTarget().pointAt(util.vector().topOf(new BlockPos(3, 4, 2)));
        scene.idle(80);
    }

    public static void pantograph(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "overhead_pantograph", "Collecting power with a pantograph");
        clear(scene);
        scene.world().setBlock(new BlockPos(2, 1, 2), Blocks.IRON_BLOCK.defaultBlockState(), false);
        scene.world().setBlock(new BlockPos(2, 4, 2),
                OverheadBlocks.WIRE.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X), false);
        BlockState folded = OverheadBlocks.PANTOGRAPH.get().defaultBlockState();
        scene.world().setBlock(PANTOGRAPH, folded, false);
        scene.world().showSection(util.select().fromTo(new BlockPos(2, 1, 2), new BlockPos(2, 4, 2)), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(65)
                .text("Mount the pantograph on the carriage roof directly below the contact wire")
                .attachKeyFrame().placeNearTarget().pointAt(util.vector().topOf(PANTOGRAPH));
        scene.idle(75);
        scene.world().setBlock(PANTOGRAPH, folded.setValue(PantographBlock.CONTACT, true)
                .setValue(PantographBlock.REACH, 1), false);
        scene.effects().indicateRedstone(PANTOGRAPH);
        scene.idle(15);
        scene.overlay().showText(75)
                .text("When the train passes under the wire, the pantograph raises and detects contact")
                .attachKeyFrame().placeNearTarget().pointAt(util.vector().topOf(PANTOGRAPH));
        scene.idle(85);
        scene.overlay().showText(70)
                .text("Contact outputs a redstone signal; no contact means the output is off")
                .attachKeyFrame().placeNearTarget().pointAt(util.vector().topOf(PANTOGRAPH));
        scene.idle(80);
    }

    public static void motor(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "overhead_electric_motor", "Driving a train with the electric traction motor");
        clear(scene);
        scene.world().setBlock(MOTOR, OverheadBlocks.ELECTRIC_MOTOR.get().defaultBlockState(), false);
        scene.world().showSection(util.select().position(MOTOR), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(70)
                .text("Place the electric traction motor on the carriage sublevel")
                .attachKeyFrame().placeNearTarget().pointAt(util.vector().topOf(MOTOR));
        scene.idle(80);
        scene.world().setKineticSpeed(util.select().position(MOTOR), 64);
        scene.effects().rotationSpeedIndicator(MOTOR);
        scene.overlay().showText(75)
                .text("A redstone level from 1 to 15 sets the motor speed up to 256 RPM")
                .attachKeyFrame().placeNearTarget().pointAt(util.vector().topOf(MOTOR));
        scene.idle(85);
        scene.overlay().showText(75)
                .text("Connect its output shaft to the drivetrain, and gate the throttle with pantograph contact")
                .attachKeyFrame().placeNearTarget().pointAt(util.vector().topOf(MOTOR));
        scene.idle(85);
    }

    private static void placeMast(CreateSceneBuilder scene, BlockPos base, int height, Direction facing) {
        for (int y = 0; y < height; y++) {
            CatenaryMastBlock.Part part = y == 0 ? CatenaryMastBlock.Part.BASE
                    : y == height - 1 ? CatenaryMastBlock.Part.HEAD : CatenaryMastBlock.Part.SHAFT;
            scene.world().setBlock(base.above(y), OverheadBlocks.MAST.get().defaultBlockState()
                    .setValue(CatenaryMastBlock.PART, part).setValue(CatenaryMastBlock.FACING, facing), false);
        }
    }

    private static void clear(CreateSceneBuilder scene) {
        for (int x = 0; x <= 5; x++)
            for (int y = 1; y <= 5; y++)
                for (int z = 0; z <= 5; z++)
                    scene.world().setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), false);
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
