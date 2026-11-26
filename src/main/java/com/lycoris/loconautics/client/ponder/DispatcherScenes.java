package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/** Ponder introduction to the dispatcher console. */
public final class DispatcherScenes {
    private static final BlockPos CONSOLE = new BlockPos(2, 1, 2);

    private DispatcherScenes() {
    }

    public static void overview(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("dispatcher", tr("loconautics.ponder.dispatcher.header"));
        scene.configureBasePlate(0, 0, 5);
        scene.scaleSceneView(0.85f);
        scene.showBasePlate();
        scene.idle(5);
        scene.world().setBlock(CONSOLE, LoconauticsRegistries.DISPATCHER.get().defaultBlockState(), false);
        scene.world().showSection(util.select().position(CONSOLE), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(95)
                .text(tr("loconautics.ponder.dispatcher.text_1"))
                .attachKeyFrame().placeNearTarget()
                .pointAt(util.vector().topOf(CONSOLE));
        scene.idle(105);
        scene.overlay().showText(100)
                .text(tr("loconautics.ponder.dispatcher.text_2"))
                .attachKeyFrame().placeNearTarget()
                .pointAt(util.vector().topOf(CONSOLE));
        scene.idle(110);
        scene.overlay().showText(100)
                .text(tr("loconautics.ponder.dispatcher.text_3"))
                .attachKeyFrame().placeNearTarget()
                .pointAt(util.vector().topOf(CONSOLE));
        scene.idle(110);
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }
}
