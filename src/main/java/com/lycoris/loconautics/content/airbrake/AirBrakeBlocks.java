package com.lycoris.loconautics.content.airbrake;

import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class AirBrakeBlocks {

    public static final DeferredHolder<net.minecraft.world.level.block.Block, AirCompressorBlock> AIR_COMPRESSOR =
            LoconauticsRegistries.BLOCKS.register("air_compressor", () -> new AirCompressorBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5f)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .noOcclusion()
                            .requiresCorrectToolForDrops()
            ));

    public static final DeferredHolder<Item, BlockItem> AIR_COMPRESSOR_ITEM =
            LoconauticsRegistries.ITEMS.register("air_compressor", () ->
                    new BlockItem(AIR_COMPRESSOR.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AirCompressorBlockEntity>> AIR_COMPRESSOR_BE =
            LoconauticsRegistries.BLOCK_ENTITIES.register("air_compressor", () ->
                    BlockEntityType.Builder.of(
                            (pos, state) -> new AirCompressorBlockEntity(AirBrakeBlocks.AIR_COMPRESSOR_BE.get(), pos, state),
                            AIR_COMPRESSOR.get()
                    ).build(null));

    public static final DeferredHolder<net.minecraft.world.level.block.Block, AirReservoirBlock> AIR_RESERVOIR =
            LoconauticsRegistries.BLOCKS.register("air_reservoir", () -> new AirReservoirBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.0f)
                            .sound(SoundType.METAL)
                            .noOcclusion()
                            .requiresCorrectToolForDrops()
            ));

    public static final DeferredHolder<Item, BlockItem> AIR_RESERVOIR_ITEM =
            LoconauticsRegistries.ITEMS.register("air_reservoir", () ->
                    new BlockItem(AIR_RESERVOIR.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AirReservoirBlockEntity>> AIR_RESERVOIR_BE =
            LoconauticsRegistries.BLOCK_ENTITIES.register("air_reservoir", () ->
                    BlockEntityType.Builder.of(
                            (pos, state) -> new AirReservoirBlockEntity(AirBrakeBlocks.AIR_RESERVOIR_BE.get(), pos, state),
                            AIR_RESERVOIR.get()
                    ).build(null));

    public static final DeferredHolder<net.minecraft.world.level.block.Block, DriverBrakeValveBlock> DRIVER_BRAKE_VALVE =
            LoconauticsRegistries.BLOCKS.register("driver_brake_valve", () -> new DriverBrakeValveBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.0f)
                            .sound(SoundType.COPPER)
                            .noOcclusion()
                            .requiresCorrectToolForDrops()
            ));

    public static final DeferredHolder<Item, BlockItem> DRIVER_BRAKE_VALVE_ITEM =
            LoconauticsRegistries.ITEMS.register("driver_brake_valve", () ->
                    new BlockItem(DRIVER_BRAKE_VALVE.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DriverBrakeValveBlockEntity>> DRIVER_BRAKE_VALVE_BE =
            LoconauticsRegistries.BLOCK_ENTITIES.register("driver_brake_valve", () ->
                    BlockEntityType.Builder.of(
                            (pos, state) -> new DriverBrakeValveBlockEntity(AirBrakeBlocks.DRIVER_BRAKE_VALVE_BE.get(), pos, state),
                            DRIVER_BRAKE_VALVE.get()
                    ).build(null));

    public static final DeferredHolder<net.minecraft.world.level.block.Block, TripleValveBlock> TRIPLE_VALVE =
            LoconauticsRegistries.BLOCKS.register("triple_valve", () -> new TripleValveBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.0f)
                            .sound(SoundType.METAL)
                            .noOcclusion()
                            .requiresCorrectToolForDrops()
            ));

    public static final DeferredHolder<Item, BlockItem> TRIPLE_VALVE_ITEM =
            LoconauticsRegistries.ITEMS.register("triple_valve", () ->
                    new BlockItem(TRIPLE_VALVE.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TripleValveBlockEntity>> TRIPLE_VALVE_BE =
            LoconauticsRegistries.BLOCK_ENTITIES.register("triple_valve", () ->
                    BlockEntityType.Builder.of(
                            (pos, state) -> new TripleValveBlockEntity(AirBrakeBlocks.TRIPLE_VALVE_BE.get(), pos, state),
                            TRIPLE_VALVE.get()
                    ).build(null));

    public static final DeferredHolder<net.minecraft.world.level.block.Block, BrakeHoseConnectorBlock> BRAKE_HOSE_CONNECTOR =
            LoconauticsRegistries.BLOCKS.register("brake_hose_connector", () -> new BrakeHoseConnectorBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(2.5f)
                            .sound(SoundType.METAL)
                            .noOcclusion()
                            .requiresCorrectToolForDrops()
            ));

    public static final DeferredHolder<Item, BlockItem> BRAKE_HOSE_CONNECTOR_ITEM =
            LoconauticsRegistries.ITEMS.register("brake_hose_connector", () ->
                    new BlockItem(BRAKE_HOSE_CONNECTOR.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BrakeHoseConnectorBlockEntity>> BRAKE_HOSE_CONNECTOR_BE =
            LoconauticsRegistries.BLOCK_ENTITIES.register("brake_hose_connector", () ->
                    BlockEntityType.Builder.of(
                            (pos, state) -> new BrakeHoseConnectorBlockEntity(AirBrakeBlocks.BRAKE_HOSE_CONNECTOR_BE.get(), pos, state),
                            BRAKE_HOSE_CONNECTOR.get()
                    ).build(null));

    public static final DeferredHolder<Item, BrakeHoseItem> BRAKE_HOSE =
            LoconauticsRegistries.ITEMS.register("brake_hose", () ->
                    new BrakeHoseItem(new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<net.minecraft.world.level.block.Block, EmergencyBrakeBlock> EMERGENCY_BRAKE =
            LoconauticsRegistries.BLOCKS.register("emergency_brake", () -> new EmergencyBrakeBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_RED)
                            .strength(2.5f)
                            .sound(SoundType.METAL)
                            .noOcclusion()
                            .requiresCorrectToolForDrops()
            ));

    public static final DeferredHolder<Item, BlockItem> EMERGENCY_BRAKE_ITEM =
            LoconauticsRegistries.ITEMS.register("emergency_brake", () ->
                    new BlockItem(EMERGENCY_BRAKE.get(), new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EmergencyBrakeBlockEntity>> EMERGENCY_BRAKE_BE =
            LoconauticsRegistries.BLOCK_ENTITIES.register("emergency_brake", () ->
                    BlockEntityType.Builder.of(
                            (pos, state) -> new EmergencyBrakeBlockEntity(AirBrakeBlocks.EMERGENCY_BRAKE_BE.get(), pos, state),
                            EMERGENCY_BRAKE.get()
                    ).build(null));

    private AirBrakeBlocks() {
    }

    public static void init() {
    }
}
