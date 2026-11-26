package com.lycoris.loconautics.content.controls.panelreverser;

import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.controls.panel.PanelBlock;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelGlass;
import com.lycoris.loconautics.content.controls.panel.PanelHost;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelPartNbt;
import com.lycoris.loconautics.content.controls.panel.PanelPartType;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panel.RedstoneLinkedPart;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.lycoris.loconautics.content.transmission.TransmissionBlockEntity;
import com.lycoris.loconautics.trainphysics.drive.DriveSources;
import com.lycoris.loconautics.trainphysics.drive.TrainDriveSource;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.lycoris.loconautics.registry.LoconauticsSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record ReverserPart(int position, @Nullable BlockPos link, int body, int knobColor, int style, int options) implements RedstoneLinkedPart {

    public static final int REVERSE = 0;

    public static final int NEUTRAL = 1;

    public static final int FORWARD = 2;

    public static final int DEFAULT_BODY = 3;

    public static final int DEFAULT_KNOB_COLOR = 0;

    public static final int DEFAULT_STYLE = 0;

    public static final int DEFAULT_OPTIONS = PanelStyles.SWITCH.defaultOptions();

    private static final int[] SIGNALS = {0, 7, 15};

    private static final int REVERSE_MAX_SIGNAL = 3;

    private static final int FORWARD_MIN_SIGNAL = 11;

    private static final double PART_CENTER_Z = 4;

    public static int clamp(int position) {
        return Mth.clamp(position, REVERSE, FORWARD);
    }

    public static ReverserPart read(CompoundTag tag) {
        int style = PanelPartNbt.readStyle(tag, PanelStyles.SWITCH, DEFAULT_STYLE);
        return new ReverserPart(tag.contains("Position") ? clamp(tag.getInt("Position")) : NEUTRAL,
                PanelPartNbt.readLink(tag),
                PanelPartNbt.readBody(tag, DEFAULT_BODY),
                PanelPartNbt.readColor(tag, "KnobColor", DEFAULT_KNOB_COLOR),
                style,
                PanelPartNbt.readOptions(tag, PanelStyles.SWITCH, DEFAULT_OPTIONS));
    }

    public float fraction() {
        return position / 2f;
    }

    public int shownOptions() {
        return PanelStyles.SWITCH.effectiveOptions(options, style);
    }

    private ReverserPart withPosition(int next) {
        return new ReverserPart(next, link, body, knobColor, style, options);
    }

    @Override
    public PanelGlass glass() {
        return PanelStyles.SWITCH.isOn(shownOptions(), PanelStyles.SWITCH_GLASS) ? PanelGlass.SLIM : null;
    }

    @Override
    public PanelPart relocated(Level level, BlockPos shift, boolean copied) {
        return new ReverserPart(position, PanelPart.follow(level, link, shift, copied), body, knobColor, style, options);
    }

    @Override
    public PanelPartType type() {
        return PanelPartType.REVERSER;
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putInt("Position", position);
        PanelPartNbt.writeAppearance(tag, link, body, "KnobColor", knobColor, style, options);
    }

    @Override
    public ItemStack asStack() {
        ItemStack stack = new ItemStack(BoilerBlocks.GAUGE_REVERSER_ITEM.get());
        if (link != null) stack.set(LoconauticsRegistries.REVERSER_TARGET.get(), link);
        return stack;
    }

    @Override
    public void serverTick(PanelBlockEntity panel, PanelSlot slot) {
        Level level = panel.getLevel();
        if (link == null || level == null) return;

        driveLink(panel, slot, level, link, new ReverserPart(position, null, body, knobColor, style, options), signal(level));
    }

    @Override
    public void driveTransmission(TransmissionBlockEntity transmission, int signal) {
        transmission.driveDirectionFromPanel(signal <= REVERSE_MAX_SIGNAL);
        transmission.driveNeutralFromPanel(signal > REVERSE_MAX_SIGNAL && signal < FORWARD_MIN_SIGNAL);
    }

    @Override
    public void driveTransmission(PanelBlockEntity panel, PanelSlot slot,
                                  TransmissionBlockEntity transmission, int signal) {
        int orientedSignal = signal;
        if (facesOppositeDriveEnd(panel)) {
            if (signal <= REVERSE_MAX_SIGNAL) orientedSignal = 15;
            else if (signal >= FORWARD_MIN_SIGNAL) orientedSignal = 0;
        }
        boolean reversed = orientedSignal <= REVERSE_MAX_SIGNAL;
        boolean neutral = orientedSignal > REVERSE_MAX_SIGNAL && orientedSignal < FORWARD_MIN_SIGNAL;
        transmission.driveDirectionFromPanel(panel.getBlockPos(), slot.ordinal(), reversed, neutral);
    }

    private static boolean facesOppositeDriveEnd(PanelBlockEntity panel) {
        if (!(Sable.HELPER.getContaining(panel) instanceof ServerSubLevel sub) || sub.isRemoved()) return false;
        TrainDriveSource axle = DriveSources.resolve(sub);
        if (axle == null) return false;
        Direction cabForward = panel.getBlockState().getValue(HorizontalDirectionalBlock.FACING).getOpposite();
        return cabForward == axle.frontLocal().getOpposite();
    }

    private void selectDirectionSource(PanelBlockEntity panel, PanelSlot slot) {
        Level level = panel.getLevel();
        if (link != null && level != null && level.isLoaded(link)
                && level.getBlockEntity(link) instanceof TransmissionBlockEntity transmission) {
            transmission.selectPanelDirectionSource(panel.getBlockPos(), slot.ordinal());
        }
    }

    @Override
    public void onRemoved(PanelBlockEntity panel) {
        RedstoneLinkedPart.super.onRemoved(panel);
        Level level = panel.getLevel();
        if (link != null && level != null && level.isLoaded(link)
                && level.getBlockEntity(link) instanceof TransmissionBlockEntity transmission) {
            transmission.releasePanelDirectionSource(panel.getBlockPos());
        }
    }

    @Override
    public int engineSignal(int signal) {
        return 0;
    }

    @Override
    public int signal(Level level) {
        return SIGNALS[clamp(position)];
    }

    @Override
    public InteractionResult use(PanelBlockEntity panel, PanelSlot slot, Player player) {
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult use(PanelBlockEntity panel, PanelSlot slot, Player player, BlockHitResult hit) {
        Level level = panel.getLevel();
        if (level == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;

        int next = clamp(position + (upperHalf(panel, slot, hit) ? 1 : -1));
        selectDirectionSource(panel, slot);
        if (next == position) return InteractionResult.CONSUME;

        panel.replacePart(slot, withPosition(next));
        level.playSound(null, panel.getBlockPos(), LoconauticsSounds.SWITCH_TOGGLE.get(), SoundSource.BLOCKS,
                0.6f, 0.85f + 0.15f * next);
        return InteractionResult.CONSUME;
    }

    private static boolean upperHalf(PanelBlockEntity panel, PanelSlot slot, BlockHitResult hit) {
        BlockState state = panel.getBlockState();
        if (!(state.getBlock() instanceof PanelHost host)) return true;
        Vec3 model = host.partSpace(panel.getBlockPos(), state, hit.getLocation());
        if (model == null) return true;
        double offset = panel.isCentered() ? PanelBlock.CENTER_OFFSET : slot.modelZ;
        return model.z * 16 - offset >= PART_CENTER_Z;
    }

    @Override
    public float animationTarget(PanelBlockEntity panel) {
        return fraction();
    }

    @Override
    public double chaseSpeed() {
        return 0.4;
    }

    @Override
    public void addToGoggleTooltip(PanelBlockEntity panel, List<Component> tooltip) {
        LoconauticsLang.translate("gui.goggles.reverser_stats").forGoggles(tooltip);
        String key = switch (position) {
            case FORWARD -> "gui.goggles.reverser_forward";
            case REVERSE -> "gui.goggles.reverser_reverse";
            default -> "gui.goggles.reverser_neutral";
        };
        ChatFormatting color = switch (position) {
            case FORWARD -> ChatFormatting.GREEN;
            case REVERSE -> ChatFormatting.GOLD;
            default -> ChatFormatting.GRAY;
        };
        LoconauticsLang.translate(key).style(color).forGoggles(tooltip, 1);
        if (!connected(panel)) PanelPart.addDisconnected(tooltip);
    }
}
