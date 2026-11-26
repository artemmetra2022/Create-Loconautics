package com.lycoris.loconautics.content.dispatcher;

import com.lycoris.loconautics.foundation.ui.NoBlurScreen;
import com.lycoris.loconautics.network.packets.dispatcher.DispatcherCommandPacket;
import com.lycoris.loconautics.network.packets.dispatcher.DispatcherRequestPacket;
import com.lycoris.loconautics.network.packets.dispatcher.DispatcherSignalPacket;
import com.lycoris.loconautics.network.packets.dispatcher.DispatcherSnapshotPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Live dispatcher console. Trains, block sections, signal hold and event journal. */
public final class DispatcherScreen extends NoBlurScreen {

    private static final int WIDTH = 520;
    private static final int HEIGHT = 292;
    private static final int PAGE_TRAINS = 0;
    private static final int PAGE_SECTIONS = 1;
    private static final int PAGE_EVENTS = 2;
    private static DispatcherScreen active;

    private final BlockPos pos;
    private List<DispatcherTrainEntry> trains = List.of();
    private List<DispatcherSectionEntry> sections = List.of();
    private List<DispatcherEventEntry> events = List.of();
    private int occupiedSections;
    private int reservedSections;
    private int conflicts;
    private int totalSections;
    private int page = PAGE_TRAINS;
    private int scroll;
    private UUID selectedId;
    private UUID selectedSectionId;
    private int refreshTicks;
    private Button holdButton;
    private Button releaseButton;

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
        sections = new ArrayList<>(packet.sections());
        events = new ArrayList<>(packet.events());
        occupiedSections = packet.occupiedSections();
        reservedSections = packet.reservedSections();
        conflicts = packet.conflicts();
        totalSections = packet.totalSections();
        if (selectedId != null && trains.stream().noneMatch(t -> t.id().equals(selectedId))) {
            selectedId = null;
        }
        if (selectedSectionId != null && sections.stream().noneMatch(s -> s.id().equals(selectedSectionId))) {
            selectedSectionId = null;
        }
        if (selectedId == null && !trains.isEmpty()) selectedId = trains.get(0).id();
        if (selectedSectionId == null && !sections.isEmpty()) selectedSectionId = sections.get(0).id();
        scroll = Math.min(scroll, page == PAGE_SECTIONS
                ? Math.max(0, sections.size() - 7)
                : Math.max(0, trains.size() - 8));
    }

    @Override
    protected void init() {
        active = this;
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.loconautics.dispatcher.tab_trains"),
                        button -> { page = PAGE_TRAINS; scroll = 0; })
                .bounds(left + 14, top + 39, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.loconautics.dispatcher.tab_sections"),
                        button -> { page = PAGE_SECTIONS; scroll = 0; })
                .bounds(left + 116, top + 39, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.loconautics.dispatcher.tab_events"),
                        button -> { page = PAGE_EVENTS; scroll = 0; })
                .bounds(left + 218, top + 39, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.loconautics.dispatcher.refresh"),
                        button -> requestRefresh())
                .bounds(left + WIDTH - 116, top + 39, 102, 20).build());
        holdButton = addRenderableWidget(Button.builder(Component.translatable("gui.loconautics.dispatcher.hold"),
                        button -> sendCommand(true))
                .bounds(left + 14, top + HEIGHT - 31, 108, 20).build());
        releaseButton = addRenderableWidget(Button.builder(Component.translatable("gui.loconautics.dispatcher.release"),
                        button -> sendCommand(false))
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

    private void sendCommand(boolean hold) {
        if (page == PAGE_SECTIONS) {
            DispatcherSectionEntry selected = selectedSection();
            if (selected == null) return;
            PacketDistributor.sendToServer(new DispatcherSignalPacket(pos, selected.id(), hold));
            return;
        }
        DispatcherTrainEntry selected = selectedTrain();
        if (selected == null) return;
        if (!"loconautics".equals(selected.type())) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.translatable("gui.loconautics.dispatcher.create_hold_unsupported"), true);
            return;
        }
        PacketDistributor.sendToServer(new DispatcherCommandPacket(pos, selected.id(), hold));
    }

    private DispatcherSectionEntry selectedSection() {
        if (selectedSectionId == null) return null;
        return sections.stream().filter(s -> s.id().equals(selectedSectionId)).findFirst().orElse(null);
    }

    private DispatcherTrainEntry selectedTrain() {
        if (selectedId == null) return null;
        return trains.stream().filter(t -> t.id().equals(selectedId)).findFirst().orElse(null);
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

        if (page == PAGE_TRAINS) renderTrains(graphics, left, top);
        else if (page == PAGE_SECTIONS) renderSections(graphics, left, top);
        else renderEvents(graphics, left, top);
        renderSelectedFooter(graphics, left, top);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderTrains(GuiGraphics graphics, int left, int top) {
        int x = left + 14;
        int y = top + 67;
        graphics.fill(x, y, left + WIDTH - 14, y + 18, 0xFF253744);
        graphics.drawString(font, Component.translatable("gui.loconautics.dispatcher.col_name"), x + 7, y + 5, 0xFF8FAAB9, false);
        graphics.drawString(font, Component.translatable("gui.loconautics.dispatcher.col_speed"), left + 270, y + 5, 0xFF8FAAB9, false);
        graphics.drawString(font, Component.translatable("gui.loconautics.dispatcher.col_position"), left + 338, y + 5, 0xFF8FAAB9, false);
        graphics.drawString(font, Component.translatable("gui.loconautics.dispatcher.col_state"), left + 422, y + 5, 0xFF8FAAB9, false);

        List<DispatcherTrainEntry> rows = trains;
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
            graphics.drawString(font, train.x() + ", " + train.z(), left + 338, rowY + 5, 0xFFB8CBD4, false);
            graphics.drawString(font, stateLabel(train.state()), left + 422, rowY + 5, stateColor(train.state()), false);
        }
        if (rows.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.loconautics.dispatcher.no_trains"),
                    left + WIDTH / 2, y + 92, 0xFF9DB0BA);
        }
    }

    private void renderSections(GuiGraphics graphics, int left, int top) {
        int x = left + 14;
        int y = top + 67;
        graphics.fill(x, y, left + WIDTH - 14, y + 18, 0xFF253744);
        graphics.drawString(font, Component.translatable("gui.loconautics.dispatcher.col_section"), x + 7, y + 5, 0xFF8FAAB9, false);
        graphics.drawString(font, Component.translatable("gui.loconautics.dispatcher.col_state"), left + 200, y + 5, 0xFF8FAAB9, false);
        graphics.drawString(font, Component.translatable("gui.loconautics.dispatcher.col_trains"), left + 300, y + 5, 0xFF8FAAB9, false);

        int visible = Math.min(7, Math.max(0, sections.size() - scroll));
        for (int i = 0; i < visible; i++) {
            DispatcherSectionEntry section = sections.get(scroll + i);
            int rowY = y + 20 + i * 19;
            boolean selected = section.id().equals(selectedSectionId);
            int bg = selected ? 0xFF344E5E : (i % 2 == 0 ? 0xFF1D2B35 : 0xFF202F3A);
            graphics.fill(x, rowY, left + WIDTH - 14, rowY + 18, bg);
            String id = section.id().toString().substring(0, 8);
            graphics.drawString(font, id, x + 5, rowY + 5, 0xFFEAF2F6, false);
            graphics.drawString(font, sectionStateLabel(section.state()), left + 200, rowY + 5,
                    sectionStateColor(section.state()), false);
            graphics.drawString(font, truncate(section.trains(), 24), left + 300, rowY + 5, 0xFFB8CBD4, false);
        }
        if (sections.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.loconautics.dispatcher.no_sections"),
                    left + WIDTH / 2, y + 92, 0xFF9DB0BA);
        }
        graphics.drawString(font, Component.translatable("gui.loconautics.dispatcher.sections_total", totalSections),
                left + 14, top + 242, 0xFF8FAAB9, false);
    }

    private void renderEvents(GuiGraphics graphics, int left, int top) {
        int x = left + 16;
        int y = top + 76;
        int count = 0;
        for (DispatcherEventEntry event : events) {
            if (count >= 8) break;
            int color = switch (event.kind()) {
                case 1, 3 -> 0xFFFF6060;
                case 2 -> 0xFFFFC66D;
                default -> 0xFF75CDE0;
            };
            drawEvent(graphics, x, y + count++ * 23, color,
                    Component.literal(event.subject() + " (" + (event.ageTicks() / 20) + "s)"));
        }
        if (count == 0) {
            graphics.drawString(font, Component.translatable("gui.loconautics.dispatcher.no_events"),
                    x, y, 0xFF75D5A4, false);
        }
    }

    private void drawEvent(GuiGraphics graphics, int x, int y, int color, Component message) {
        graphics.fill(x, y, x + 4, y + 15, color);
        graphics.drawString(font, message, x + 12, y + 3, 0xFFE7F0F4, false);
    }

    private void renderSelectedFooter(GuiGraphics graphics, int left, int top) {
        if (page == PAGE_SECTIONS) {
            DispatcherSectionEntry selected = selectedSection();
            if (selected != null) {
                String details = selected.id().toString().substring(0, 8) + "  " + sectionStateLabel(selected.state());
                if (selected.held()) details += "  HELD";
                graphics.drawString(font, details, left + 250, top + HEIGHT - 24, 0xFF9EB4C0, false);
            }
            return;
        }
        DispatcherTrainEntry selected = selectedTrain();
        if (selected == null || page != PAGE_TRAINS) {
            graphics.drawString(font, Component.translatable("gui.loconautics.dispatcher.command_scope"),
                    left + 250, top + HEIGHT - 24, 0xFF78909D, false);
            return;
        }
        String details = String.format(Locale.ROOT, "%s  %s  Y:%d",
                selected.name(), selected.dimension(), selected.y());
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

    private String sectionStateLabel(int state) {
        return Component.translatable(switch (state) {
            case DispatcherSectionEntry.OCCUPIED -> "gui.loconautics.dispatcher.section_occupied";
            case DispatcherSectionEntry.RESERVED -> "gui.loconautics.dispatcher.section_reserved";
            case DispatcherSectionEntry.CONFLICT -> "gui.loconautics.dispatcher.section_conflict";
            default -> "gui.loconautics.dispatcher.section_clear";
        }).getString();
    }

    private int sectionStateColor(int state) {
        return switch (state) {
            case DispatcherSectionEntry.OCCUPIED -> 0xFFFFC66D;
            case DispatcherSectionEntry.RESERVED -> 0xFF79C8E5;
            case DispatcherSectionEntry.CONFLICT -> 0xFFFF6464;
            default -> 0xFF82D7A8;
        };
    }

    private String truncate(String value, int max) {
        if (value == null) return "";
        if (value.length() <= max) return value;
        return value.substring(0, Math.max(1, max - 1)) + "\u2026";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int left = (width - WIDTH) / 2;
            int top = (height - HEIGHT) / 2;
            int firstRowY = top + 87;
            if (mouseX >= left + 14 && mouseX <= left + WIDTH - 14
                    && mouseY >= firstRowY && mouseY < firstRowY + 8 * 19) {
                int row = (int) ((mouseY - firstRowY) / 19.0);
                if (page == PAGE_TRAINS) {
                    int index = scroll + row;
                    if (index >= 0 && index < trains.size()) {
                        selectedId = trains.get(index).id();
                        return true;
                    }
                } else if (page == PAGE_SECTIONS) {
                    int index = scroll + row;
                    if (index >= 0 && index < sections.size()) {
                        selectedSectionId = sections.get(index).id();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (page == PAGE_TRAINS) {
            scroll = Math.max(0, Math.min(Math.max(0, trains.size() - 8), scroll - (int) Math.signum(scrollY)));
            return true;
        }
        if (page == PAGE_SECTIONS) {
            scroll = Math.max(0, Math.min(Math.max(0, sections.size() - 7), scroll - (int) Math.signum(scrollY)));
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
