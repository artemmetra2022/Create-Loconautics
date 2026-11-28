package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.content.railsignal.RailwaySignalBlock;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.content.trains.signal.SignalBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Ponder instructions for the custom Loconautics signal and its Create block sections. */
public final class RailSignalScenes {

    private static final BlockPos SIGNAL_A = new BlockPos(1, 1, 3);
    private static final BlockPos SIGNAL_B = new BlockPos(5, 1, 3);
    private static final BlockPos TRACK_A = new BlockPos(1, 1, 4);

    private RailSignalScenes() {
    }

    public static void blockSections(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("rail_signal_blocks", tr("loconautics.ponder.rail_signal.header"));
        scene.configureBasePlate(1, 0, 9);
        scene.scaleSceneView(.75f);
        scene.setSceneOffsetY(-1);
        scene.showBasePlate();
        scene.idle(5);

        scene.world().setBlock(SIGNAL_A,
                LoconauticsRegistries.RAIL_SIGNAL.get().defaultBlockState()
                        .setValue(RailwaySignalBlock.FACING, Direction.WEST)
                        .setValue(RailwaySignalBlock.ASPECT, RailwaySignalBlock.Aspect.GREEN), false);
        scene.world().setBlock(SIGNAL_B,
                LoconauticsRegistries.RAIL_SIGNAL.get().defaultBlockState()
                        .setValue(RailwaySignalBlock.FACING, Direction.WEST)
                        .setValue(RailwaySignalBlock.ASPECT, RailwaySignalBlock.Aspect.RED), false);
        scene.world().showSection(util.select().fromTo(0, 1, 4, 6, 1, 4), Direction.DOWN);
        scene.idle(12);

        scene.overlay().showText(85)
                .text(tr("loconautics.ponder.rail_signal.text_1"))
                .attachKeyFrame().placeNearTarget()
                .pointAt(util.vector().topOf(TRACK_A));
        scene.idle(95);

        scene.overlay().showControls(util.vector().topOf(TRACK_A), Pointing.DOWN, 45)
                .rightClick().withItem(new ItemStack(LoconauticsRegistries.RAIL_SIGNAL_ITEM.get()));
        scene.idle(12);
        scene.overlay().showText(90)
                .text(tr("loconautics.ponder.rail_signal.text_2"))
                .attachKeyFrame().placeNearTarget()
                .pointAt(util.vector().topOf(TRACK_A));
        scene.idle(100);

        scene.world().showSection(util.select().position(SIGNAL_A), Direction.DOWN);
        scene.world().showSection(util.select().position(SIGNAL_B), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showControls(util.vector().topOf(SIGNAL_B), Pointing.DOWN, 45)
                .rightClick().withItem(new ItemStack(LoconauticsRegistries.RAIL_SIGNAL_ITEM.get()));
        scene.idle(12);
        scene.overlay().showText(95)
                .text(tr("loconautics.ponder.rail_signal.text_3"))
                .attachKeyFrame().placeNearTarget()
                .pointAt(util.vector().topOf(SIGNAL_B));
        scene.idle(105);

        scene.overlay().showText(90)
                .text(tr("loconautics.ponder.rail_signal.text_4"))
                .attachKeyFrame().placeNearTarget()
                .pointAt(util.vector().topOf(SIGNAL_A));
        scene.idle(100);

        scene.world().modifyBlocks(util.select().position(SIGNAL_B),
                state -> state.setValue(RailwaySignalBlock.ASPECT, RailwaySignalBlock.Aspect.YELLOW), false);
        scene.overlay().showText(85)
                .text(tr("loconautics.ponder.rail_signal.text_5"))
                .attachKeyFrame().placeNearTarget()
                .pointAt(util.vector().topOf(SIGNAL_B));
        scene.idle(95);

        scene.world().modifyBlocks(util.select().position(SIGNAL_B),
                state -> state.setValue(RailwaySignalBlock.ASPECT, RailwaySignalBlock.Aspect.RED)
                        .setValue(SignalBlock.POWERED, true), false);
        scene.effects().indicateRedstone(SIGNAL_B);
        scene.overlay().showText(85)
                .text(tr("loconautics.ponder.rail_signal.text_6"))
                .attachKeyFrame().colored(net.createmod.ponder.api.PonderPalette.RED)
                .placeNearTarget().pointAt(util.vector().topOf(SIGNAL_B));
        scene.idle(95);

        scene.overlay().showText(90)
                .text(tr("loconautics.ponder.rail_signal.text_7"))
                .attachKeyFrame().placeNearTarget()
                .pointAt(util.vector().topOf(SIGNAL_A));
        scene.idle(100);
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }
}
