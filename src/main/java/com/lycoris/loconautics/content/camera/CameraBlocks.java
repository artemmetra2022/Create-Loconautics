package com.lycoris.loconautics.content.camera;

import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
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

    public static final DeferredHolder<net.minecraft.world.item.Item, BlockItem> CAMERA_ITEM =
            LoconauticsRegistries.ITEMS.register("security_camera", () ->
                    new BlockItem(CAMERA.get(), new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<net.minecraft.world.item.Item, CameraMonitorBlockItem> CAMERA_MONITOR_ITEM =
            LoconauticsRegistries.ITEMS.register("camera_monitor", () ->
                    new CameraMonitorBlockItem(com.lycoris.loconautics.content.boiler.BoilerBlocks.LOCOMOTIVE_GAUGE.get(),
                            new net.minecraft.world.item.Item.Properties().stacksTo(16)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CameraBlockEntity>> CAMERA_BE =
            LoconauticsRegistries.BLOCK_ENTITIES.register("security_camera", () ->
                    BlockEntityType.Builder.of((pos, state) -> new CameraBlockEntity(CAMERA_BE.get(), pos, state), CAMERA.get()).build(null));

    private CameraBlocks() {
    }

    public static void init() {
        // Referencing this class from the mod constructor forces deferred registration.
    }
}
