package com.lycoris.loconautics.content.overhead;

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

public final class OverheadBlocks {

    private OverheadBlocks() { }

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.0f)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .noOcclusion();
    }

    public static final DeferredHolder<Block, CatenaryMastBlock> MAST =
            LoconauticsRegistries.BLOCKS.register("catenary_mast",
                    () -> new CatenaryMastBlock(metal()));

    public static final DeferredHolder<Item, CatenaryPoleItem> POLE_SHORT =
            LoconauticsRegistries.ITEMS.register("catenary_pole_short",
                    () -> new CatenaryPoleItem(6, new Item.Properties().stacksTo(16)));
    public static final DeferredHolder<Item, CatenaryPoleItem> POLE_STANDARD =
            LoconauticsRegistries.ITEMS.register("catenary_pole_standard",
                    () -> new CatenaryPoleItem(7, new Item.Properties().stacksTo(16)));
    public static final DeferredHolder<Item, CatenaryPoleItem> POLE_TALL =
            LoconauticsRegistries.ITEMS.register("catenary_pole_tall",
                    () -> new CatenaryPoleItem(8, new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<Block, OverheadWireBlock> WIRE =
            LoconauticsRegistries.BLOCKS.register("overhead_contact_wire",
                    () -> new OverheadWireBlock(metal().strength(0.8f).sound(SoundType.CHAIN)));

    public static final DeferredHolder<Item, BlockItem> WIRE_ITEM =
            LoconauticsRegistries.ITEMS.register("overhead_contact_wire",
                    () -> new BlockItem(WIRE.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<Block, PantographBlock> PANTOGRAPH =
            LoconauticsRegistries.BLOCKS.register("pantograph",
                    () -> new PantographBlock(metal().strength(2.5f)));

    public static final DeferredHolder<Item, BlockItem> PANTOGRAPH_ITEM =
            LoconauticsRegistries.ITEMS.register("pantograph",
                    () -> new BlockItem(PANTOGRAPH.get(), new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<Block, ElectricTractionMotorBlock> ELECTRIC_MOTOR =
            LoconauticsRegistries.BLOCKS.register("electric_traction_motor",
                    () -> new ElectricTractionMotorBlock(metal().strength(3.5f)));

    public static final DeferredHolder<Item, BlockItem> ELECTRIC_MOTOR_ITEM =
            LoconauticsRegistries.ITEMS.register("electric_traction_motor",
                    () -> new BlockItem(ELECTRIC_MOTOR.get(), new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectricTractionMotorBlockEntity>>
            ELECTRIC_MOTOR_BE = LoconauticsRegistries.BLOCK_ENTITIES.register("electric_traction_motor", () ->
            BlockEntityType.Builder.of(OverheadBlocks::createElectricMotorBE, ELECTRIC_MOTOR.get()).build(null));

    private static ElectricTractionMotorBlockEntity createElectricMotorBE(BlockPos pos, BlockState state) {
        return new ElectricTractionMotorBlockEntity(ELECTRIC_MOTOR_BE.get(), pos, state);
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PantographBlockEntity>>
            PANTOGRAPH_BE = LoconauticsRegistries.BLOCK_ENTITIES.register("pantograph", () ->
            BlockEntityType.Builder.of(OverheadBlocks::createPantographBE, PANTOGRAPH.get()).build(null));

    private static PantographBlockEntity createPantographBE(BlockPos pos, BlockState state) {
        return new PantographBlockEntity(PANTOGRAPH_BE.get(), pos, state);
    }

    public static void init() { }
}
