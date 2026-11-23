package com.lycoris.loconautics.content.controls.paneldisplay;

import com.lycoris.loconautics.network.packets.controls.StatusDisplayOpenPacket;
import com.lycoris.loconautics.network.packets.controls.StatusDisplayRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.Locale;

public final class LocomotiveStatusScreen extends Screen {
    private static final int WIDTH = 380;
    private static final int HEIGHT = 232;
    private final BlockPos pos;
    private LocomotiveStatusData data;
    private int refreshTicks;

    private LocomotiveStatusScreen(StatusDisplayOpenPacket packet) {
        super(Component.translatable("gui.loconautics.status_display.title"));
        pos = packet.pos();
        data = packet.data();
    }
    public static void open(StatusDisplayOpenPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof LocomotiveStatusScreen screen && screen.pos.equals(packet.pos())) {
            screen.data = packet.data();
        } else minecraft.setScreen(new LocomotiveStatusScreen(packet));
    }
    @Override public void tick() {
        super.tick();
        if (++refreshTicks >= 20) {
            refreshTicks = 0;
            if (Minecraft.getInstance().getConnection() != null)
                PacketDistributor.sendToServer(new StatusDisplayRequestPacket(pos));
        }
    }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xF0111B24);
        graphics.fill(left, top, left + WIDTH, top + 3, 0xFF42B7C5);
        graphics.fill(left + 8, top + 8, left + WIDTH - 8, top + HEIGHT - 8, 0xFF1A2731);
        graphics.drawString(font, title, left + 14, top + 13, 0xFFEAF4F7, false);

        int severity = data.severity();
        int statusColor = severity == 2 ? 0xFFFF5555 : severity == 1 ? 0xFFFFC34A : 0xFF67D49A;
        String status = !data.trainPresent()
                ? Component.translatable("gui.loconautics.status_display.no_train").getString()
                : Component.translatable(severity == 2 ? "gui.loconautics.status_display.emergency"
                : severity == 1 ? "gui.loconautics.status_display.attention"
                : "gui.loconautics.status_display.normal").getString();
        graphics.fill(left + 12, top + 34, left + WIDTH - 12, top + 55, 0xFF293640);
        graphics.drawString(font, status, left + 20, top + 41, statusColor, false);

        int labelColor = 0xFF9FB5C2, valueColor = 0xFFEAF4F7, mutedColor = 0xFF81929D;
        graphics.drawString(font, Component.translatable("gui.loconautics.status_display.motion"),
                left + 16, top + 66, 0xFF56C8D4, false);
        drawRow(graphics, left + 16, top + 82, 164, "speed",
                data.trainPresent() ? String.format(Locale.ROOT, "%.1f km/h", data.speedKmh()) : "--", labelColor, valueColor);
        drawRow(graphics, left + 16, top + 101, 164, "acceleration",
                data.trainPresent() ? String.format(Locale.ROOT, "%+.2f m/s²", data.acceleration()) : "--", labelColor, valueColor);
        drawRow(graphics, left + 16, top + 120, 164, "mass",
                data.trainPresent() ? String.format(Locale.ROOT, "%,.0f kg", data.consistMass()) : "--", labelColor, valueColor);
        drawRow(graphics, left + 16, top + 139, 164, "grade",
                data.trainPresent() ? String.format(Locale.ROOT, "%+.1f %%", data.gradePercent()) : "--", labelColor, valueColor);
        drawRow(graphics, left + 16, top + 158, 164, "adhesion",
                data.trainPresent() ? String.format(Locale.ROOT, "%.2f m/s²", data.adhesionLimit()) : "--", labelColor, valueColor);

        graphics.drawString(font, Component.translatable("gui.loconautics.status_display.traction"),
                left + 202, top + 66, 0xFFFFC15A, false);
        drawRow(graphics, left + 202, top + 82, 162, "engine_rpm",
                data.enginePresent() ? String.format(Locale.ROOT, "%,.0f rpm", data.engineRpm()) : "--", labelColor, valueColor);
        drawRow(graphics, left + 202, top + 101, 162, "engine_power",
                data.enginePresent() ? String.format(Locale.ROOT, "%,.0f SU", data.engineOutputSu()) : "--", labelColor, valueColor);
        drawRow(graphics, left + 202, top + 120, 162, "fuel",
                data.enginePresent() ? String.format(Locale.ROOT, "%.0f %%", data.fuelPercent()) : "--", labelColor, valueColor);
        drawRow(graphics, left + 202, top + 139, 162, "heat",
                data.enginePresent() ? String.format(Locale.ROOT, "%.0f %%", data.engineHeatPercent()) : "--", labelColor, valueColor);

        graphics.drawString(font, Component.translatable("gui.loconautics.status_display.brakes"),
                left + 202, top + 160, 0xFF7CB7FF, false);
        drawRow(graphics, left + 202, top + 176, 162, "main_air",
                data.brakePresent() ? String.format(Locale.ROOT, "%.1f bar", data.mainAirBar()) : "--", labelColor, valueColor);
        drawRow(graphics, left + 202, top + 195, 162, "pipe_air",
                data.brakePresent() ? String.format(Locale.ROOT, "%.1f bar", data.brakePipeBar()) : "--", labelColor, valueColor);

        String safety = data.emergency() ? Component.translatable("gui.loconautics.status_display.emergency").getString()
                : data.safetyAlarm() > 0 ? Component.translatable("gui.loconautics.status_display.attention").getString()
                : Component.translatable("gui.loconautics.status_display.normal").getString();
        int safetyColor = data.emergency() || data.safetyAlarm() >= 2 ? 0xFFFF5555
                : data.severity() == 1 ? 0xFFFFC34A : 0xFF67D49A;
        Component safetyText = Component.translatable("gui.loconautics.status_display.safety")
                .copy().append(Component.literal(": " + safety));
        graphics.drawString(font, safetyText, left + 16, top + 185, safetyColor, false);

        String lower = Component.translatable("gui.loconautics.status_display.slip").getString()
                + ": " + (data.trainPresent() ? String.format(Locale.ROOT, "%.0f %%", data.wheelSlipPercent()) : "--")
                + "    " + Component.translatable("gui.loconautics.status_display.brake_demand").getString()
                + ": " + (data.trainPresent() ? String.format(Locale.ROOT, "%.0f %%", data.brakeDemandPercent()) : "--");
        graphics.drawString(font, lower, left + 16, top + 207, mutedColor, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    private void drawRow(GuiGraphics graphics, int x, int y, int rowWidth, String key, String value,
                         int labelColor, int valueColor) {
        Component label = Component.translatable("gui.loconautics.status_display." + key);
        graphics.drawString(font, label, x, y, labelColor, false);
        graphics.drawString(font, value, x + rowWidth - font.width(value), y, valueColor, false);
    }
}
