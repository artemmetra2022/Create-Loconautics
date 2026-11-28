package com.lycoris.loconautics.content.controls.panellamp;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.client.gui.selector.PartModelViewer;
import com.lycoris.loconautics.client.gui.selector.PartSelectorScreen;
import com.lycoris.loconautics.client.gui.selector.SelectorPanel;
import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.controls.panel.PanelBodyRenderer;
import com.lycoris.loconautics.content.controls.panel.PanelFrequencyMenu;
import com.lycoris.loconautics.content.controls.panel.style.PanelBulbRenderer;
import com.lycoris.loconautics.content.combustion.SmallEngineBlockEntity;
import com.lycoris.loconautics.network.packets.controls.SignalLampSelectPacket;
import com.simibubi.create.foundation.gui.widget.Label;
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public final class SignalLampSelectorScreen extends PartSelectorScreen {

    private final List<SignalLampMode> choices;
    private final LampPreview viewer = new LampPreview();
    private SignalLampMode selectedMode;

    public SignalLampSelectorScreen(PanelFrequencyMenu menu, Inventory inventory, SignalLampPart lamp) {
        super(menu, inventory, "signal_lamp_selector");
        List<SignalLampMode> available = findChoices(lamp);
        choices = available.isEmpty() ? List.of(lamp.mode()) : List.copyOf(available);
        selectedMode = choices.contains(lamp.mode()) ? lamp.mode() : choices.get(0);
        viewer.setBody(lamp.body());
        viewer.setTint(selectedMode.color(null));
    }

    private static List<SignalLampMode> findChoices(SignalLampPart lamp) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.isLoaded(lamp.target())) {
            return List.of(lamp.mode());
        }
        BlockEntity source = minecraft.level.getBlockEntity(lamp.target());
        if (source == null) source = SmallEngineBlockEntity.resolve(minecraft.level, lamp.target());
        List<SignalLampMode> found = SignalLampMode.choicesFor(source);
        return found.isEmpty() ? List.of(lamp.mode()) : found;
    }

    @Override
    protected PartModelViewer viewer() {
        return viewer;
    }

    @Override
    protected List<ColumnSpec> columnSpecs() {
        return List.of();
    }

    @Override
    protected int initialSelection(int column) {
        return 0;
    }

    @Override
    protected boolean hasMetricBar() {
        return true;
    }

    @Override
    protected void addExtraControls() {
        int x = guiLeft + SelectorPanel.METRIC_X;
        int y = guiTop + SelectorPanel.METRIC_Y;
        Label label = new Label(x + 4, y + 5, Component.empty()).withShadow();
        List<Component> names = new ArrayList<>();
        for (SignalLampMode choice : choices) {
            names.add(Component.translatable("loconautics." + choice.langKey()));
        }

        SelectionScrollInput input = new SelectionScrollInput(
                x, y, SelectorPanel.METRIC_W, SelectorPanel.BUTTON_SIZE);
        input.forOptions(names)
                .titled(Component.translatable("gui.loconautics.signal_lamp_selector.mode"))
                .writingTo(label)
                .calling(index -> {
                    int safeIndex = Math.max(0, Math.min(choices.size() - 1, index));
                    selectedMode = choices.get(safeIndex);
                    viewer.setTint(selectedMode.color(null));
                })
                .setState(indexOf(selectedMode));
        addRenderableWidget(input);
        addRenderableWidget(label);
    }

    private int indexOf(SignalLampMode mode) {
        int index = choices.indexOf(mode);
        return index < 0 ? 0 : index;
    }

    @Override
    protected void pushToViewer() {
        viewer.setTint(selectedMode.color(null));
    }

    @Override
    protected void sendApply() {
        PacketDistributor.sendToServer(new SignalLampSelectPacket(panelPos, slot.ordinal(), selectedMode.id()));
    }

    @Override
    protected net.minecraft.world.level.block.state.BlockState state() {
        return BoilerBlocks.LOCOMOTIVE_GAUGE.get().defaultBlockState();
    }

    private static final class LampPreview extends PartModelViewer {
        private int body = SignalLampPart.DEFAULT_BODY;
        private int tint = 0xFFFFB52E;

        void setBody(int body) {
            this.body = body;
        }

        void setTint(int tint) {
            this.tint = tint;
        }

        @Override
        protected void draw(PoseStack pose, MultiBufferSource buffers) {
            PanelBodyRenderer.renderSlimBody(pose, buffers.getBuffer(RenderType.cutout()),
                    LightTexture.FULL_BRIGHT, state, body);
            PanelBulbRenderer.render(pose, buffers, LightTexture.FULL_BRIGHT, state, 1f,
                    LoconauticsPartialModels.SWITCH_BULB, tint);
        }
    }
}
