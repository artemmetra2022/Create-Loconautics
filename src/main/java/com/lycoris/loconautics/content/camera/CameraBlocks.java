package com.lycoris.loconautics.content.camera;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.foundation.LoconauticsBlockProperties;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Registration for the camera hardware. The monitor is a panel component
 * and is registered alongside the other panel instruments.
 */
public final class CameraBlocks {

    public static final DeferredHolder<Block, CameraBlock> CAMERA =
            LoconauticsRegistries.BLOCKS.register("security_camera", () ->
                    new CameraBlock(Block.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(2.0f)
                            .sound(SoundType.METAL)
                            .noOcclusion()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CameraBlockEntity>> CAMERA_BE =
            LoconauticsRegistries.BLOCK_ENTITIES.register("security_camera", () ->
                    BlockEntityType.Builder.of(CameraBlockEntity::new, CAMERA.get()).build(null));

    private CameraBlocks() {
    }

    public static void init() {
        // Referencing this class from the mod constructor forces deferred registration.
    }
}
