package com.lycoris.loconautics.content.controls.panellamp;

import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelFrequencyMenu;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelPartType;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.combustion.SmallEngineBlockEntity;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record SignalLampPart(BlockPos target, SignalLampMode mode, int body, boolean active) implements PanelPart {

    public static final int DEFAULT_BODY = 3;

    public static SignalLampPart read(CompoundTag tag) {
        SignalLampMode mode = SignalLampMode.byId(tag.getString("Mode"));
        return mode == null ? null : new SignalLampPart(
                BlockPos.of(tag.getLong("Target")),
                mode,
                com.lycoris.loconautics.content.controls.panel.PanelPartNbt.readBody(tag, DEFAULT_BODY),
                tag.getBoolean("Active"));
    }

    @Override
    public PanelPart relocated(Level level, BlockPos shift, boolean copied) {
        BlockPos moved = PanelPart.follow(level, target, shift, copied);
        return moved == null ? this : new SignalLampPart(moved, mode, body, active);
    }

    @Override
    public PanelPartType type() {
        return PanelPartType.SIGNAL_LAMP;
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putLong("Target", target.asLong());
        tag.putString("Mode", mode.id());
        tag.putInt("Body", body);
        tag.putBoolean("Active", active);
    }

    @Override
    public ItemStack asStack() {
        return SignalLampBlockItem.stackFor(this);
    }

    @Override
    public InteractionResult use(PanelBlockEntity panel, PanelSlot slot, Player player) {
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult configure(PanelBlockEntity panel, PanelSlot slot, Player player) {
        PanelFrequencyMenu.open(player, panel.getBlockPos(), slot);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean consumesUse() {
        return false;
    }

    @Override
    public float animationTarget(PanelBlockEntity panel) {
        return active ? 1f : 0f;
    }

    @Override
    public float lightLevel(PanelBlockEntity panel, PanelSlot slot) {
        return panel.animation(slot).getValue();
    }

    @Override
    public void serverTick(PanelBlockEntity panel, PanelSlot slot) {
        Level level = panel.getLevel();
        if (level == null || level.isClientSide || !level.isLoaded(target)) return;
        BlockEntity source = sourceOf(level, target);
        boolean next = mode.isActive(source);
        if (next != active) {
            panel.replacePart(slot, new SignalLampPart(target, mode, body, next));
        }
    }

    @Override
    public void addToGoggleTooltip(PanelBlockEntity panel, List<Component> tooltip) {
        LoconauticsLang.translate("gui.goggles.signal_lamp_stats").forGoggles(tooltip);
        BlockEntity source = panel.getLevel() == null || !panel.getLevel().isLoaded(target)
                ? null : sourceOf(panel.getLevel(), target);
        if (!mode.supports(source)) {
            LoconauticsLang.translate("gui.goggles.signal_lamp.disconnected")
                    .style(ChatFormatting.RED).forGoggles(tooltip, 1);
            return;
        }
        LoconauticsLang.translate(mode.langKey()).style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        boolean lit = mode.isActive(source);
        LoconauticsLang.translate(mode.statusKey(source))
                .style(lit ? (mode.warning() ? ChatFormatting.GOLD : ChatFormatting.RED) : ChatFormatting.GREEN)
                .forGoggles(tooltip, 1);
    }

    public int tint(PanelBlockEntity panel) {
        Level level = panel.getLevel();
        if (level == null || !level.isLoaded(target)) return mode.color(null);
        return mode.color(sourceOf(level, target));
    }

    @Nullable
    private static BlockEntity sourceOf(Level level, BlockPos target) {
        BlockEntity source = level.getBlockEntity(target);
        if (source == null) source = SmallEngineBlockEntity.resolve(level, target);
        return source;
    }

    public static int body(CompoundTag tag) {
        return com.lycoris.loconautics.content.controls.panel.PanelPartNbt.readBody(tag, DEFAULT_BODY);
    }
}
