package com.lycoris.loconautics.content.controls.panelcabsignal;

import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelPartNbt;
import com.lycoris.loconautics.content.controls.panel.PanelPartType;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Panel indicator for the distance to the signal ahead: four small lamps that fill up as the train
 * closes in (1 lamp beyond 120 blocks, 2 within 120, 3 within 60, 4 within 20), coloured by the
 * aspect shown. Goggles show the distance in blocks.
 */
public record SignalDistancePart(int body, int aspect, int distance) implements PanelPart {

    public static final int DEFAULT_BODY = 3;
    public static final int LAMPS = 4;

    public static SignalDistancePart read(CompoundTag tag) {
        return new SignalDistancePart(PanelPartNbt.readBody(tag, DEFAULT_BODY),
                tag.contains("Aspect") ? tag.getInt("Aspect") : CabSignalPart.NONE,
                tag.contains("Distance") ? tag.getInt("Distance") : -1);
    }

    /** How many of the four lamps are lit. */
    public int lit() {
        if (distance < 0) return 0;
        if (distance <= 20) return 4;
        if (distance <= 60) return 3;
        if (distance <= 120) return 2;
        return 1;
    }

    public int tint() {
        return CabSignalPart.tintFor(aspect);
    }

    @Override
    public PanelPartType type() {
        return PanelPartType.SIGNAL_DISTANCE;
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putInt("Body", body);
        tag.putInt("Aspect", aspect);
        tag.putInt("Distance", distance);
    }

    @Override
    public ItemStack asStack() {
        return new ItemStack(BoilerBlocks.SIGNAL_DISTANCE_ITEM.get());
    }

    @Override
    public InteractionResult use(PanelBlockEntity panel, PanelSlot slot, Player player) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean consumesUse() {
        return false;
    }

    @Override
    public float animationTarget(PanelBlockEntity panel) {
        return distance >= 0 ? 1f : 0f;
    }

    @Override
    public float lightLevel(PanelBlockEntity panel, PanelSlot slot) {
        return panel.animation(slot).getValue();
    }

    @Override
    public void serverTick(PanelBlockEntity panel, PanelSlot slot) {
        Level level = panel.getLevel();
        if (level == null || level.isClientSide) return;
        if ((level.getGameTime() + slot.ordinal()) % 5L != 0L) return;
        CabSignalPart.Sample sample = CabSignalPart.sample(panel, level);
        if (sample.aspect() != aspect || sample.distance() != distance) {
            panel.replacePart(slot, new SignalDistancePart(body, sample.aspect(), sample.distance()));
        }
    }

    @Override
    public void addToGoggleTooltip(PanelBlockEntity panel, List<Component> tooltip) {
        LoconauticsLang.translate("gui.goggles.signal_distance").forGoggles(tooltip);
        if (distance < 0) {
            LoconauticsLang.translate("gui.goggles.cab_signal.none")
                    .style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
            return;
        }
        LoconauticsLang.translate("gui.goggles.signal_distance.value", distance)
                .style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
    }

    @Override
    public PanelPart relocated(Level level, BlockPos shift, boolean copied) {
        return this;
    }
}
