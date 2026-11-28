package com.lycoris.loconautics.content.controls.panelcabsignal;

import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelPartNbt;
import com.lycoris.loconautics.content.controls.panel.PanelPartType;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.railsignal.RailwaySignalBlock;
import com.lycoris.loconautics.content.railsignal.SignalAspects;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.signal.TrainSignalAccessor;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Cab signal repeater: a panel lamp that duplicates the aspect of the signal ahead of the train
 * the panel is mounted on. {@code aspect} is the ordinal of {@link RailwaySignalBlock.Aspect},
 * or {@link #NONE} when no signal is in range.
 */
public record CabSignalPart(int body, int aspect) implements PanelPart {

    public static final int DEFAULT_BODY = 3;
    public static final int NONE = -1;

    private static final int RED = 0xFFFF3434;
    private static final int YELLOW = 0xFFFFC21E;
    private static final int YELLOW_GREEN = 0xFFBDE63C;
    private static final int GREEN = 0xFF34E07A;
    private static final int IDLE = 0xFFB4B8BE;

    public static CabSignalPart read(CompoundTag tag) {
        int aspect = tag.contains("Aspect") ? Mth.clamp(tag.getInt("Aspect"), NONE,
                RailwaySignalBlock.Aspect.values().length - 1) : NONE;
        return new CabSignalPart(PanelPartNbt.readBody(tag, DEFAULT_BODY), aspect);
    }

    public RailwaySignalBlock.Aspect shown() {
        return aspect < 0 ? null : RailwaySignalBlock.Aspect.values()[aspect];
    }

    /** Colour of the lamp glass for the current aspect. */
    public int tint() {
        return tintFor(aspect);
    }

    public static int tintFor(int aspect) {
        if (aspect < 0 || aspect >= RailwaySignalBlock.Aspect.values().length) return IDLE;
        return switch (RailwaySignalBlock.Aspect.values()[aspect]) {
            case RED -> RED;
            case YELLOW -> YELLOW;
            case YELLOW_GREEN -> YELLOW_GREEN;
            case GREEN -> GREEN;
        };
    }

    @Override
    public PanelPartType type() {
        return PanelPartType.CAB_SIGNAL;
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putInt("Body", body);
        tag.putInt("Aspect", aspect);
    }

    @Override
    public ItemStack asStack() {
        return new ItemStack(BoilerBlocks.CAB_SIGNAL_ITEM.get());
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
        return aspect >= 0 ? 1f : 0f;
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
        int next = sample(panel, level).aspect();
        if (next != aspect) {
            panel.replacePart(slot, new CabSignalPart(body, next));
        }
    }

    /** What the train carrying a panel sees ahead: aspect ordinal and distance in blocks. */
    public record Sample(int aspect, int distance) {
        public static final Sample NONE_AHEAD = new Sample(NONE, -1);
    }

    /** The nearest signal ahead of the train carrying this panel (distance rounded to 10 blocks). */
    public static Sample sample(PanelBlockEntity panel, Level level) {
        SubLevel found = Sable.HELPER.getContaining(panel);
        if (!(found instanceof ServerSubLevel sub) || sub.isRemoved()) return Sample.NONE_AHEAD;
        SableTrain train = SableTrainRegistry.bySubLevel(sub.getUniqueId());
        if (train == null || train.isGhost()) return Sample.NONE_AHEAD;
        TrainSignalAccessor.SignalAhead ahead = TrainSignalAccessor.nextSignal(train);
        if (ahead == null || ahead.signal() == null) return Sample.NONE_AHEAD;
        if (!SignalAspects.hasHead(ahead.signal(), ahead.primary())) return Sample.NONE_AHEAD;
        double gap = Math.max(0.0, ahead.distance());
        int meters = (int) (Math.round(gap / 10.0) * 10L);
        return new Sample(SignalAspects.displayed(level, ahead.signal(), ahead.primary()).ordinal(), meters);
    }

    @Override
    public void addToGoggleTooltip(PanelBlockEntity panel, List<Component> tooltip) {
        LoconauticsLang.translate("gui.goggles.cab_signal").forGoggles(tooltip);
        RailwaySignalBlock.Aspect shown = shown();
        if (shown == null) {
            LoconauticsLang.translate("gui.goggles.cab_signal.none")
                    .style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
            return;
        }
        ChatFormatting color = switch (shown) {
            case RED -> ChatFormatting.RED;
            case YELLOW, YELLOW_GREEN -> ChatFormatting.GOLD;
            case GREEN -> ChatFormatting.GREEN;
        };
        LoconauticsLang.translate("gui.goggles.cab_signal." + shown.name().toLowerCase(java.util.Locale.ROOT))
                .style(color).forGoggles(tooltip, 1);
    }

    @Override
    public PanelPart relocated(Level level, BlockPos shift, boolean copied) {
        return this;
    }
}
