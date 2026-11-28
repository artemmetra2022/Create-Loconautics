package com.lycoris.loconautics.content.sand;

import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class SandStorageBlocks {

    public static final DeferredHolder<Block, SandStorageBlock> SAND_STORAGE =
            LoconauticsRegistries.BLOCKS.register("sand_storage", () -> new SandStorageBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.0f)
                            .sound(SoundType.METAL)
                            .noOcclusion()
                            .requiresCorrectToolForDrops()
            ));

    public static final DeferredHolder<Item, BlockItem> SAND_STORAGE_ITEM =
            LoconauticsRegistries.ITEMS.register("sand_storage", () ->
                    new BlockItem(SAND_STORAGE.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SandStorageBlockEntity>> SAND_STORAGE_BE =
            LoconauticsRegistries.BLOCK_ENTITIES.register("sand_storage", () ->
                    BlockEntityType.Builder.of(SandStorageBlocks::createBE, SAND_STORAGE.get()).build(null));

    private static SandStorageBlockEntity createBE(BlockPos pos, BlockState state) {
        return new SandStorageBlockEntity(SAND_STORAGE_BE.get(), pos, state);
    }

    private SandStorageBlocks() {
    }

    public static void init() {
    }
}
