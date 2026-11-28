package com.lycoris.loconautics.content.controls.panel.target;

import com.lycoris.loconautics.LoconauticsConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.jetbrains.annotations.Nullable;

public final class RedstoneTargets {

    public static final DataMapType<net.minecraft.world.level.block.Block, RedstoneTarget> TYPE = DataMapType
            .builder(LoconauticsConstants.id("redstone_target"), Registries.BLOCK, RedstoneTarget.CODEC)
            .synced(RedstoneTarget.CODEC, false)
            .build();

    private RedstoneTargets() {
    }

    public static void register(RegisterDataMapTypesEvent event) {
        event.register(TYPE);
    }

    @Nullable
    public static RedstoneTarget of(BlockState state) {
        return state.getBlockHolder().getData(TYPE);
    }
}
