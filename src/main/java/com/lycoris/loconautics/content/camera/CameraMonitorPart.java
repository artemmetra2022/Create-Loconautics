package com.lycoris.loconautics.content.camera;

import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelPartType;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A live-camera monitor mounted in a locomotive control panel. */
public record CameraMonitorPart(GlobalPos camera) implements PanelPart {

    private static final String DIMENSION_TAG = "CameraDimension";
    private static final String POSITION_TAG = "CameraPosition";

    public static CameraMonitorPart read(CompoundTag tag) {
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString(DIMENSION_TAG));
        if (dimension == null || !tag.contains(POSITION_TAG)) return null;
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimension);
        return new CameraMonitorPart(GlobalPos.of(key, BlockPos.of(tag.getLong(POSITION_TAG))));
    }

    @Override
    public PanelPartType type() {
        return PanelPartType.CAMERA_MONITOR;
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putString(DIMENSION_TAG, camera.dimension().location().toString());
        tag.putLong(POSITION_TAG, camera.pos().asLong());
    }

    @Override
    public ItemStack asStack() {
        return CameraMonitorBlockItem.stackFor(camera);
    }

    @Override
    public InteractionResult use(PanelBlockEntity panel, PanelSlot slot, Player player) {
        return InteractionResult.CONSUME;
    }

    @Override
    public float animationTarget(PanelBlockEntity panel) {
        return 0;
    }

    @Override
    public PanelPart relocated(Level level, BlockPos shift, boolean copied) {
        return this;
    }

    @Override
    public void addToGoggleTooltip(PanelBlockEntity panel, List<Component> tooltip) {
        tooltip.add(Component.translatable("gui.loconautics.camera_monitor.linked")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(camera.dimension().location() + " " + camera.pos().toShortString())
                .withStyle(ChatFormatting.AQUA));
    }

    @Nullable
    public static GlobalPos fromTag(@Nullable CompoundTag tag) {
        if (tag == null) return null;
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString(DIMENSION_TAG));
        if (dimension == null || !tag.contains(POSITION_TAG)) return null;
        return GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dimension),
                BlockPos.of(tag.getLong(POSITION_TAG)));
    }

    public static void writeToTag(CompoundTag tag, GlobalPos camera) {
        tag.putString(DIMENSION_TAG, camera.dimension().location().toString());
        tag.putLong(POSITION_TAG, camera.pos().asLong());
    }
}
