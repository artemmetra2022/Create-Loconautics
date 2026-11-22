package com.lycoris.loconautics.content.safety;

import com.lycoris.loconautics.content.analogcontroller.AnalogControllerClientHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class SafetyAlertHUD {

    public static final LayeredDraw.Layer OVERLAY = SafetyAlertHUD::render;

    private static final int KEEP_ALIVE_TICKS = 35;

    private static int currentAlarm = SafetyBlockEntity.ALARM_NONE;
    private static int keepAlive;
    private static final LerpedFloat displayedWidth = LerpedFloat.linear();

    private SafetyAlertHUD() {
    }

    public static void onAlert(int alarm) {
        if (alarm == SafetyBlockEntity.ALARM_NONE) {
            currentAlarm = SafetyBlockEntity.ALARM_NONE;
            keepAlive = 0;
        } else {
            currentAlarm = alarm;
            keepAlive = KEEP_ALIVE_TICKS;
        }
    }

    public static void clear() {
        currentAlarm = SafetyBlockEntity.ALARM_NONE;
        keepAlive = 0;
        displayedWidth.setValue(0.0);
    }

    public static void tick() {
        if (keepAlive > 0) {
            keepAlive--;
        } else {
            currentAlarm = SafetyBlockEntity.ALARM_NONE;
        }
        Minecraft mc = Minecraft.getInstance();
        Component text = messageFor(currentAlarm);
        double target = text != null ? (double) (mc.font.width(text) + 20) : 0.0;
        displayedWidth.chase(target, 0.45, LerpedFloat.Chaser.EXP);
        displayedWidth.tickChaser();
    }

    private static Component messageFor(int alarm) {
        return switch (alarm) {
            case SafetyBlockEntity.ALARM_APPROACHING_LIMIT ->
                    Component.translatable("loconautics.safety.alert.approaching_limit");
            case SafetyBlockEntity.ALARM_ANTI_ROLLBACK ->
                    Component.translatable("loconautics.safety.alert.anti_rollback");
            case SafetyBlockEntity.ALARM_OVERSPEED ->
                    Component.translatable("loconautics.safety.alert.overspeed");
            case SafetyBlockEntity.ALARM_DERAILMENT ->
                    Component.translatable("loconautics.safety.alert.derailment");
            default -> null;
        };
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.gameMode == null
                || mc.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            return;
        }

        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(true);
        int boxWidth = (int) displayedWidth.getValue(partialTicks);
        if (boxWidth <= 4) {
            return;
        }

        Component text = messageFor(currentAlarm);
        boolean emergency = SafetyBlockEntity.isEmergencyAlarm(currentAlarm);

        long time = mc.level != null ? mc.level.getGameTime() : 0L;
        boolean pulse = (time / 6) % 2 == 0;
        int borderColor = emergency
                ? (pulse ? 0xFFFF3B30 : 0xFFAA1E18)
                : (pulse ? 0xFFFFB300 : 0xFFB87A00);
        int textColor = emergency ? 0xFFFF6B6B : 0xFFFFD166;

        int centerX = graphics.guiWidth() / 2;
        int baseY = AnalogControllerClientHandler.isControlling()
                ? graphics.guiHeight() - 74
                : graphics.guiHeight() - 56;

        int left = centerX - boxWidth / 2;
        int top = baseY;
        int height = 16;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        graphics.fill(left, top, left + boxWidth, top + height, 0xD8141418);
        graphics.renderOutline(left, top, boxWidth, height, borderColor);

        Font font = mc.font;
        if (text != null && font.width(text) <= boxWidth - 10) {
            graphics.drawCenteredString(font, text, centerX, top + 4, textColor);
        }
        pose.popPose();
    }
}
