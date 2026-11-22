package com.lycoris.loconautics.content.controls.panel;

import com.lycoris.loconautics.content.combustion.SmallEngineBlockEntity;
import com.lycoris.loconautics.content.controls.panel.target.PanelSignals;
import com.lycoris.loconautics.content.controls.panel.target.RedstoneTarget;
import com.lycoris.loconautics.content.controls.panel.target.RedstoneTargets;
import com.lycoris.loconautics.content.transmission.TransmissionBlockEntity;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public interface RedstoneLinkedPart extends PanelPart {

    int MAX_SIGNAL = 15;

    @Nullable
    BlockPos link();

    int signal(Level level);

    @Override
    default float lightLevel(PanelBlockEntity panel, PanelSlot slot) {
        return panel.outputSignal(slot, signal(panel.getLevel())) / (float) MAX_SIGNAL;
    }

    default boolean connected(PanelBlockEntity panel) {
        return link() != null || panel.hasLinkFrequency(this);
    }

    default int engineSignal(int signal) {
        return signal;
    }

    default void driveTransmission(TransmissionBlockEntity transmission, int signal) {
        transmission.driveSpeedFromPanel(signal);
    }

    default void driveLink(PanelBlockEntity panel, PanelSlot slot, Level level, BlockPos link,
                           PanelPart unlinked, int signal) {
        SmallEngineBlockEntity engine = SmallEngineBlockEntity.resolve(level, link);
        if (engine != null) {
            engine.driveFromPanel(panel.outputSignal(slot, engineSignal(signal)));
            return;
        }
        if (level.isLoaded(link) && level.getBlockEntity(link) instanceof TransmissionBlockEntity transmission) {
            driveTransmission(transmission, panel.outputSignal(slot, signal));
            return;
        }
        RedstoneTarget target = level.isLoaded(link) ? RedstoneTargets.of(level.getBlockState(link)) : null;
        if (target != null && level instanceof ServerLevel server) {
            int driven = Math.round(PanelDriveBus.report(level, link, panel.outputSignal(slot, signal)));
            PanelSignals.set(server, link, target.level(driven));
            return;
        }
        if (!PanelLink.stillThere(level, link)) {
            PanelDriveBus.release(level, link);
            PanelSignals.release(level, link);
            panel.replacePart(slot, unlinked);
            return;
        }
        float driven = PanelDriveBus.report(level, link, panel.outputSignal(slot, signal));
        if (level.getBlockEntity(link) instanceof RedstoneLinkBlockEntity receiver) {
            receiver.setSignal(Math.round(driven));
        }
        PanelLink.set(level, link, true);
    }

    @Override
    default void onRemoved(PanelBlockEntity panel) {
        Level level = panel.getLevel();
        BlockPos link = link();
        if (link == null || level == null) return;
        PanelDriveBus.release(level, link);
        PanelSignals.release(level, link);
        if (level.getBlockEntity(link) instanceof RedstoneLinkBlockEntity receiver) receiver.setSignal(0);
        PanelLink.set(level, link, false);
    }
}
