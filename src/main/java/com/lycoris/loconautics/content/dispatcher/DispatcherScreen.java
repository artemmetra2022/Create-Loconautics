package com.lycoris.loconautics.content.dispatcher;

import com.lycoris.loconautics.foundation.ui.NoBlurScreen;
import com.lycoris.loconautics.network.packets.dispatcher.DispatcherCommandPacket;
import com.lycoris.loconautics.network.packets.dispatcher.DispatcherRequestPacket;
import com.lycoris.loconautics.network.packets.dispatcher.DispatcherSnapshotPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Live dispatcher console. All train state and commands are validated on the server. */
public final class DispatcherScreen extends NoBlurScreen {

    private static final int WIDTH = 520;
    private static final int HEIGHT = 292;
    private static final int PAGE_TRAINS = 0;
    private static final int PAGE_SECTIONS = 1;
    private static final int PAGE_EVENTS = 2;
    private static DispatcherScreen active;

    private final BlockPos pos;
    private List<DispatcherTrainEntry> trains;
    private int occupiedSections;
    private int reservedSections;
    private int conflicts;
    private int totalSections;
    private int page = PAGE_TRAINS;
    private int scroll;
    private UUID selectedId;
    private int refreshTicks;

    private DispatcherScreen(DispatcherSnapshotPacket packet) {
        super(Component.translatable("gui.loconautics.dispatcher.title"));
        pos = packet.pos();
        accept(packet);
    }

    public static void open(DispatcherSnapshotPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DispatcherScreen screen && screen.pos.equals(packet.pos())) {
            screen.accept(packet);
        } else {
            active = new DispatcherScreen(packet);
            minecraft.setScreen(active);
        }
    }

    private void accept(DispatcherSnapshotPacket packet) {
        trains = new ArrayList<>(packet.trains());
        occupiedSections = packet.occupiedSections();
        reservedSections = packet.reservedSections();
        conflicts = packet.conflicts();
        totalSections = packet.totalSections();
        if (selectedId != null && trains.stream().noneMatch(t -> t.id().equals(selectedId))) {
            selectedId = null;
        }
        if (selectedId == null && !trains.isEmpty()) selectedId = trains.get(0).id();
        scroll = Math.min(scroll, Math.max(0, visibleTrains().size() - 8));
    }

    @Override
    protected void init() {
        active = this;
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.loconautics.dispatcher.tab_trains"),
                        button -> page = PAGE_TRAINS)
                .bounds(left + 14, top + 39, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.loconautics.dispatcher.tab_sections"),
                        button -> page = PAGE_SECTIONS)
                .bounds(left + 116, top + 39, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.loconautics.dispatcher.tab_events"),
                        button -> page = PAGE_EVENTS)
                .bounds(left + 218, top + 39, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.loconautics.dispatcher.refresh"),
                        button -> requestRefresh())
                .bounds(left + WIDTH - 116, top + 39, 102, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.loconautics.dispatcher.hold"),
                        button -> sendHold(true))
                .bounds(left + 14, top + HEIGHT - 31, 108, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.loconautics.dispatcher.release"),
                        button -> sendHold(false))
                .bounds(left + 128, top + HEIGHT - 31, 108, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.loconautics.dispatcher.close"),
                        button -> onClose())
                .bounds(left + WIDTH - 94, top + HEIGHT - 31, 80, 20).build());
    }

    private void requestRefresh() {
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new DispatcherRequestPacket(pos));
        }
    }

    private void sendHold(boolean hold) {
        DispatcherTrainEntry selected = selectedTrain();
        if (selected == null) return;
        if (!"loconautics".equals(selected.type())) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.translatable("gui.loconautics.dispatcher.create_hold_unsupported"), true);
            return;
        }
        PacketDistributor.sendToServer(new DispatcherCommandPacket(pos, selected.id(), hold));
    }

    private DispatcherTrainEntry selectedTrain() {
        if (selectedId == null) return null;
        return trains.stream().filter(t -> t.id().equals(selectedId)).findFirst().orElse(null);
    }

    private List<DispatcherTrainEntry> visibleTrains() {
        return trains == null ? List.of() : trains;
    }

    @Override
    public void tick() {
        super.tick();
        if (++refreshTicks >= 20) {
            refreshTicks = 0;
            requestRefresh();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        graphics.fill(0, 0, width, height, 0xB9000000);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xF0101921);
        graphics.fill(left, top, left + WIDTH, top + 4, 0xFF48B8C6);
        graphics.fill(left + 8, top + 8, left + WIDTH - 8, top + HEIGHT - 8, 0xFF182631);
        graphics.drawString(font, title, left + 15, top + 15, 0xFFF0F7FA, false);
        graphics.drawString(font, Component.translatable("gui.loconautics.dispatcher.live"),
                left + WIDTH - 124, top + 17, 0xFF75D5A4, false);

        renderTabContent(graphics, left, top, mouseX, mouseY);
        renderSelectedFooter(graphics, left, top);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderTabContent(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        if (page == PAGE_TRAINS) renderTrains(graphics, left, top, mouseX, mouseY);
        else if (page == PAGE_SECTIONS) renderSections(graphics, left, top);
        else renderEvents(graphics, left, top);
    }

    private void renderTrains(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        int x = left + 14;
        int y = top + 67;
        graphics.fill(x, y, left + WIDTH - 14, y + 18, 0xFF253744);
        graphics.drawString(font, Component.translatable("gui.loconautics.dispatcher.col_name"), x + 7, y + 5, 0xFF8FAAB9, false);
        graphics.drawString(font, Component.translatable("gui.loconautics.dispatcher.col_speed"), left + 270, y + 5, 0xFF8FAAB9, false);
        graphics.drawString(font, Component.translatable("gui.loconautics.dispatcher.col_position"), left + 338, y + 5, 0xFF8FAAB9, false);
        graphics.drawString(font, Component.translatable("gui.loconautics.dispatcher.col_state"), left + 422, y + 5, 0xFF8FAAB9, false);

        List<DispatcherTrainEntry> rows = visibleTrains();
        int visible = Math.min(8, Math.max(0, rows.size() - scroll));
        for (int i = 0; i < visible; i++) {
            DispatcherTrainEntry train = rows.get(scroll + i);
            int rowY = y + 20 + i * 19;
            boolean selected = train.id().equals(selectedId);
            int bg = selected ? 0xFF344E5E : (i % 2 == 0 ? 0xFF1D2B35 : 0xFF202F3A);
            graphics.fill(x, rowY, left + WIDTH - 14, rowY + 18, bg);
            int typeColor = "create".equals(train.type()) ? 0xFFFFC56A : 0xFF70CED4;
            String tag = "create".equals(train.type()) ? "CREATE" : "LOCO";
            graphics.drawString(font, tag, x + 5, rowY + 5, typeColor, false);
            graphics.drawString(font, truncate(train.name(), 21), x + 54, rowY + 5, 0xFFEAF2F6, false);
            String speed = String.format(Locale.ROOT, "%.1f km/h", Math.abs(train.speed()) * 3.6);
            graphics.drawString(font, speed, left + 270, rowY + 5, 0xFFD6E6EC, false);
            String location = train.x() + ", " + train.z();
            graphics.drawString(font, location, left + 338, rowY + 5, 0xFFB8CBD4, false);
            graphics.drawString(font, stateLabel(train.state()), left + 422, rowY + 5,
                    stateColor(train.state()), false);
        }
        if (rows.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.loconautics.dispatcher.no_trains"),
                    left + WIDTH / 2, y + 92, 0xFF9DB0BA);
        }
        String pageCount = rows.isEmpty() ? "0" : (scroll + 1) + "–" + Math.min(rows.size(), scroll + 8) + " / " + rows.size();
        graphics.drawString(font, pageCount, left + WIDTH - 82, top + 242, 0xFF8FAAB9, false);
        graphics.drawString(font, Component.translatable("gui.loconautics.dispatcher.scroll_hint"),
                left + 15, top + 242, 0xFF78909D, false);
    }

    private void renderSections(GuiGraphics graphics, int left, int top) {
        int x = left + 16;
        int y = top + 78;
        graphics.drawString(font, Component.translatable("gui.loconautics.dispatcher.sections_total", totalSections),
                x, y, 0xFFEAF2F6, false);
        graphics.drawString(font, Component.translatable("gui.loconautics.dispatcher.sections_occupied", occupiedSections),
                x, y + 26, 0xFFFFC66D, false);
        graphics.drawString(font, Component.translatable("gui.loconautics.dispatcher.sections_reserved", reservedSections),
                x, y + 52, 0xFF79C8E5, false);
        int conflictColor = conflicts > 0 ? 0xFFFF6464 : 0xFF69D49A;
        graphics.drawString(font, Component.translatable("gui.loconautics.dispatcher.sections_conflicts", conflicts),
                x, y + 78, conflictColor, false);
        graphics.fill(x, y + 112, left + WIDTH - 16, y + 113, 0xFF3A4D59);
        graphics.drawString(font, Component.translatable("gui.loconautics.dispatcher.sections_info_1"),
                x, y + 126, 0xFFB8C9D1, false);
        graphics.drawString(font, Component.translatable("gui.loconautics.dispatcher.sections_info_2"),
                x, y + 142, 0xFFB8C9D1, false);
        graphics.drawString(font, Component.translatable("gui.loconautics.dispatcher.sections_info_3"),
                x, y + 158, 0xFFB8C9D1, false);
    }

    private void renderEvents(GuiGraphics graphics, int left, int top) {
        int x = left + 16;
        int y = top + 76;
        int count = 0;
        if (conflicts > 0) {
            drawEvent(graphics, x, y + count++ * 23, 0xFFFF6060,
                    Component.translatable("gui.loconautics.dispatcher.event_conflicts", conflicts));
        }
        for (DispatcherTrainEntry train : visibleTrains()) {
            if (count >= 7) break;
            if (train.state() == DispatcherTrainEntry.DERAILED) {
                drawEvent(graphics, x, y + count++ * 23, 0xFFFF6060,
                        Component.translatable("gui.loconautics.dispatcher.event_derailed", train.name()));
            } else if (train.state() == DispatcherTrainEntry.WAITING) {
                drawEvent(graphics, x, y + count++ * 23, 0xFFFFC66D,
                        Component.translatable("gui.loconautics.dispatcher.event_waiting", train.name()));
            } else if (train.dispatcherHeld()) {
                drawEvent(graphics, x, y + count++ * 23, 0xFF75CDE0,
                        Component.translatable("gui.loconautics.dispatcher.event_held", train.name()));
            }
        }
        if (count == 0) {
            graphics.drawString(font, Component.translatable("gui.loconautics.dispatcher.no_events"),
                    x, y, 0xFF75D5A4, false);
        }
        graphics.drawString(font, Component.translatable("gui.loconautics.dispatcher.events_hint"),
                x, top + 244, 0xFF78909D, false);
    }

    private void drawEvent(GuiGraphics graphics, int x, int y, int color, Component message) {
        graphics.fill(x, y, x + 4, y + 15, color);
        graphics.drawString(font, message, x + 12, y + 3, 0xFFE7F0F4, false);
    }

    private void renderSelectedFooter(GuiGraphics graphics, int left, int top) {
        DispatcherTrainEntry selected = selectedTrain();
        if (selected == null || page != PAGE_TRAINS) {
            graphics.drawString(font, Component.translatable("gui.loconautics.dispatcher.command_scope"),
                    left + 250, top + HEIGHT - 24, 0xFF78909D, false);
            return;
        }
        String dimension = selected.dimension();
        String details = String.format(Locale.ROOT, "%s  %s  Y:%d  |  %d %s / %d %s",
                selected.name(), dimension, selected.y(), selected.occupiedSections(),
                Component.translatable("gui.loconautics.dispatcher.occupied_short").getString(),
                selected.reservedSections(),
                Component.translatable("gui.loconautics.dispatcher.reserved_short").getString());
        graphics.drawString(font, truncate(details, 48), left + 250, top + HEIGHT - 24, 0xFF9EB4C0, false);
    }

    private String stateLabel(int state) {
        return Component.translatable(switch (state) {
            case DispatcherTrainEntry.MOVING -> "gui.loconautics.dispatcher.state_moving";
            case DispatcherTrainEntry.WAITING -> "gui.loconautics.dispatcher.state_waiting";
            case DispatcherTrainEntry.DERAILED -> "gui.loconautics.dispatcher.state_derailed";
            case DispatcherTrainEntry.DISPATCH_HELD -> "gui.loconautics.dispatcher.state_held";
            default -> "gui.loconautics.dispatcher.state_stopped";
        }).getString();
    }

    private int stateColor(int state) {
        return switch (state) {
            case DispatcherTrainEntry.MOVING -> 0xFF82D7A8;
            case DispatcherTrainEntry.WAITING -> 0xFFFFC66D;
            case DispatcherTrainEntry.DERAILED -> 0xFFFF6464;
            case DispatcherTrainEntry.DISPATCH_HELD -> 0xFF76CDE1;
            default -> 0xFF9FB2BC;
        };
    }

    private String truncate(String value, int max) {
        if (value == null) return "";
        if (value.length() <= max) return value;
        return value.substring(0, Math.max(1, max - 1)) + "…";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && page == PAGE_TRAINS) {
            int left = (width - WIDTH) / 2;
            int top = (height - HEIGHT) / 2;
            int firstRowY = top + 87;
            if (mouseX >= left + 14 && mouseX <= left + WIDTH - 14
                    && mouseY >= firstRowY && mouseY < firstRowY + 8 * 19) {
                int row = (int) ((mouseY - firstRowY) / 19.0);
                int index = scroll + row;
                if (index >= 0 && index < visibleTrains().size()) {
                    selectedId = visibleTrains().get(index).id();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (page == PAGE_TRAINS) {
            scroll = Math.max(0, Math.min(Math.max(0, visibleTrains().size() - 8), scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if (active == this) active = null;
        super.onClose();
    }
}
