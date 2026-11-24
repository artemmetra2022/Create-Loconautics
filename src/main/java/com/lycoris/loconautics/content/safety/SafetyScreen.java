package com.lycoris.loconautics.content.safety;

import com.lycoris.loconautics.network.packets.safety.SafetyConfigurePacket;
import com.lycoris.loconautics.network.packets.safety.SafetyOpenPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The list of safety systems of a Safety Unit, opened with a wrench. Per system: a switch, and a button that binds
 * a toggle frequency from the items in the hands (main hand and off hand, like a Redstone Link). A bound system
 * runs only while its frequency has a signal. Changes are sent as they are made.
 */
public class SafetyScreen extends Screen {

    private static final int WIDTH = 300;
    private static final int ROW = 24;
    private static final int HEADER = 30;
    private static final int LABEL_X = 10;
    private static final int SWITCH_X = 150;
    private static final int SWITCH_W = 44;
    private static final int LINK_X = 198;
    private static final int LINK_W = 68;
    private static final int CLEAR_X = 270;
    private static final int CLEAR_W = 20;

    private final BlockPos pos;
    private int enabledMask;
    private int speedLimit;
    private boolean invert;
    private int frequencyMask;
    private int activeMask;
    private int problemMask;

    private int left;
    private int top;
    private int panelHeight;

    private SafetyScreen(SafetyOpenPacket packet) {
        super(Component.translatable("gui.loconautics.safety"));
        this.pos = packet.pos();
        copy(packet);
    }

    private void copy(SafetyOpenPacket packet) {
        enabledMask = packet.enabledMask();
        speedLimit = packet.speedLimit();
        invert = packet.invert();
        frequencyMask = packet.frequencyMask();
        activeMask = packet.activeMask();
        problemMask = packet.problemMask();
    }

    public static void open(SafetyOpenPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SafetyScreen screen && screen.pos.equals(packet.pos())) {
            screen.copy(packet);
            screen.rebuildWidgets();
        } else {
            minecraft.setScreen(new SafetyScreen(packet));
        }
    }

    @Override
    protected void init() {
        // the device rows, then the limit row and the direction row
        panelHeight = HEADER + SafetyDevice.COUNT * ROW + 2 * ROW + 12;
        left = (width - WIDTH) / 2;
        top = Math.max(0, (height - panelHeight) / 2);

        for (SafetyDevice device : SafetyDevice.values()) {
            int i = device.ordinal();
            int y = top + HEADER + i * ROW;
            boolean on = (enabledMask & (1 << i)) != 0;
            boolean bound = (frequencyMask & (1 << i)) != 0;

            Button toggle = addRenderableWidget(Button.builder(onOff(on), button -> {
                enabledMask ^= 1 << i;
                send(-1, SafetyConfigurePacket.ACTION_NONE);
                rebuildWidgets();
            }).bounds(left + SWITCH_X, y, SWITCH_W, 20).build());
            toggle.active = device.implemented();

            Button link = addRenderableWidget(Button.builder(
                    Component.translatable(bound ? "gui.loconautics.safety.rebind" : "gui.loconautics.safety.bind"),
                    button -> send(i, SafetyConfigurePacket.ACTION_BIND))
                    .bounds(left + LINK_X, y, LINK_W, 20)
                    .tooltip(Tooltip.create(Component.translatable("gui.loconautics.safety.bind.tooltip")))
                    .build());
            // Placeholder safety modes remain visible for clarity, but cannot be enabled or bound.
            link.active = device.implemented();

            Button clear = Button.builder(Component.literal("x"),
                    button -> send(i, SafetyConfigurePacket.ACTION_CLEAR))
                    .bounds(left + CLEAR_X, y, CLEAR_W, 20)
                    .tooltip(Tooltip.create(Component.translatable("gui.loconautics.safety.clear.tooltip")))
                    .build();
            clear.active = bound && device.implemented();
            addRenderableWidget(clear);
        }

        // speed limit of the overspeed system
        int limitY = top + HEADER + SafetyDevice.COUNT * ROW;
        addRenderableWidget(Button.builder(Component.literal("-"), button -> {
            speedLimit = Mth.clamp(speedLimit - step(), SafetyBlockEntity.MIN_SPEED_LIMIT,
                    SafetyBlockEntity.MAX_SPEED_LIMIT);
            send(-1, SafetyConfigurePacket.ACTION_NONE);
        }).bounds(left + SWITCH_X, limitY, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> {
            speedLimit = Mth.clamp(speedLimit + step(), SafetyBlockEntity.MIN_SPEED_LIMIT,
                    SafetyBlockEntity.MAX_SPEED_LIMIT);
            send(-1, SafetyConfigurePacket.ACTION_NONE);
        }).bounds(left + SWITCH_X + 94, limitY, 20, 20).build());

        // flips what counts as forward for the anti rollback system
        int invertY = limitY + ROW;
        addRenderableWidget(Button.builder(onOff(invert), button -> {
            invert = !invert;
            send(-1, SafetyConfigurePacket.ACTION_NONE);
            rebuildWidgets();
        }).bounds(left + SWITCH_X, invertY, SWITCH_W, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.loconautics.safety.invert.tooltip")))
                .build());
    }

    private static int step() {
        return hasShiftDown() ? 50 : 10;
    }

    private static Component onOff(boolean on) {
        return Component.translatable(on ? "gui.loconautics.safety.on" : "gui.loconautics.safety.off");
    }

    private void send(int device, int action) {
        PacketDistributor.sendToServer(new SafetyConfigurePacket(pos, enabledMask, speedLimit, invert, device, action));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(left, top, left + WIDTH, top + panelHeight, 0xE0101010);
        graphics.renderOutline(left, top, WIDTH, panelHeight, 0xFF6C6C6C);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, top + 10, 0xFFFFFF);

        for (SafetyDevice device : SafetyDevice.values()) {
            int i = device.ordinal();
            int y = top + HEADER + i * ROW + 6;
            boolean running = (activeMask & (1 << i)) != 0;
            // a green dot while the system is armed
            graphics.fill(left + LABEL_X, y + 2, left + LABEL_X + 4, y + 6, running ? 0xFF55FF55 : 0xFF555555);
            Component name = Component.translatable(device.langKey());
            if (!device.implemented()) {
                name = name.copy().append(" ").append(Component.translatable("gui.loconautics.safety.stub"));
            }
            graphics.drawString(font, name, left + LABEL_X + 8, y, device.implemented() ? 0xFFFFFF : 0x808080, false);
        }

        int limitY = top + HEADER + SafetyDevice.COUNT * ROW;
        graphics.drawString(font, Component.translatable("gui.loconautics.safety.limit"),
                left + LABEL_X + 8, limitY + 6, 0xFFFFFF, false);
        graphics.drawCenteredString(font, Component.literal(speedLimit + " km/h"),
                left + SWITCH_X + 57, limitY + 6, 0x55FFFF);

        graphics.drawString(font, Component.translatable("gui.loconautics.safety.invert"),
                left + LABEL_X + 8, limitY + ROW + 6, 0xFFFFFF, false);

        if ((problemMask & SafetyBlockEntity.PROBLEM_NO_SPEEDOMETER) != 0) {
            graphics.drawString(font, Component.translatable("loconautics.safety.problem.no_speedometer"),
                    left + LABEL_X, top + panelHeight - 12, 0xFF5555, false);
        } else if ((problemMask & SafetyBlockEntity.PROBLEM_NO_TRANSMISSION) != 0) {
            graphics.drawString(font, Component.translatable("loconautics.safety.problem.no_transmission"),
                    left + LABEL_X, top + panelHeight - 12, 0xFF5555, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
