package com.lycoris.loconautics.content.driverhint;

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

/**
 * Orange, silent driver hint banner. Sits above the safety alarm strip when both are active.
 */
@OnlyIn(Dist.CLIENT)
public final class DriverHintHUD {

    public static final LayeredDraw.Layer OVERLAY = DriverHintHUD::render;

    private static final int KEEP_ALIVE_TICKS = 40;

    private static DriverHint current = DriverHint.NONE;
    private static int keepAlive;
    private static final LerpedFloat displayedWidth = LerpedFloat.linear();

    private DriverHintHUD() {
    }

    public static void onHint(DriverHint hint) {
        if (hint == null || hint == DriverHint.NONE) {
            current = DriverHint.NONE;
            keepAlive = 0;
        } else {
            current = hint;
            keepAlive = KEEP_ALIVE_TICKS;
        }
    }

    public static void clear() {
        current = DriverHint.NONE;
        keepAlive = 0;
        displayedWidth.setValue(0.0);
    }

    public static void tick() {
        if (keepAlive > 0) {
            keepAlive--;
        } else {
            current = DriverHint.NONE;
        }
        Minecraft mc = Minecraft.getInstance();
        Component text = messageFor(current);
        double target = text != null ? (double) (mc.font.width(text) + 20) : 0.0;
        displayedWidth.chase(target, 0.45, LerpedFloat.Chaser.EXP);
        displayedWidth.tickChaser();
    }

    private static Component messageFor(DriverHint hint) {
        if (hint == null || hint == DriverHint.NONE) {
            return null;
        }
        return Component.translatable(hint.langKey());
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

        Component text = messageFor(current);
        if (text == null) {
            return;
        }

        long time = mc.level != null ? mc.level.getGameTime() : 0L;
        boolean pulse = (time / 8) % 2 == 0;
        int borderColor = pulse ? 0xFFFF9800 : 0xFFC77700;
        int textColor = 0xFFFFCC80;

        int centerX = graphics.guiWidth() / 2;
        // sit just above the safety strip (safety is at height-74 / height-56)
        int baseY = AnalogControllerClientHandler.isControlling()
                ? graphics.guiHeight() - 94
                : graphics.guiHeight() - 76;

        int left = centerX - boxWidth / 2;
        int top = baseY;
        int height = 16;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        graphics.fill(left, top, left + boxWidth, top + height, 0xD8181208);
        graphics.renderOutline(left, top, boxWidth, height, borderColor);

        Font font = mc.font;
        if (font.width(text) <= boxWidth - 10) {
            graphics.drawCenteredString(font, text, centerX, top + 4, textColor);
        }
        pose.popPose();
    }
}
