package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.common.PreviewStrandHolder;
import com.lycoris.loconautics.content.steelcable.SteelCableHolderBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

public final class HosePreviewStrands {

    public static final UUID ID = new UUID(0x486F7365L, 0x5072657669657750L);

    private HosePreviewStrands() {
    }

    public static boolean isPreview(UUID id) {
        return ID.equals(id);
    }

    public static void attach(CreateSceneBuilder scene, BlockPos holder) {
        scene.world().modifyBlockEntity(holder, SmartBlockEntity.class, be -> {
            SteelCableHolderBehaviour behaviour = be.getBehaviour(SteelCableHolderBehaviour.TYPE);
            if (behaviour != null) {
                behaviour.attach(ID);
            }
        });
    }

    public static void draw(CreateSceneBuilder scene, BlockPos connector, List<Vec3> points) {
        scene.world().modifyBlockEntity(connector, BlockEntity.class, be -> {
            if (be instanceof PreviewStrandHolder holder) {
                holder.setPreviewStrand(points);
            }
        });
    }
}
