package com.lycoris.loconautics.content.controls.paneldisplay;

import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelPartType;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.network.packets.controls.StatusDisplayOpenPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.List;

public record LocomotiveStatusPart() implements PanelPart {
    public static LocomotiveStatusPart read(CompoundTag tag) {
        return new LocomotiveStatusPart();
    }
    @Override public PanelPart relocated(Level level, BlockPos shift, boolean copied) { return this; }
    @Override public PanelPartType type() { return PanelPartType.STATUS_DISPLAY; }
    @Override public void write(CompoundTag tag) { }
    @Override public ItemStack asStack() { return new ItemStack(BoilerBlocks.LOCOMOTIVE_STATUS_DISPLAY_ITEM.get()); }
    @Override
    public InteractionResult use(PanelBlockEntity panel, PanelSlot slot, Player player) {
        if (player instanceof ServerPlayer serverPlayer && panel.getLevel() != null && !panel.getLevel().isClientSide) {
            PacketDistributor.sendToPlayer(serverPlayer, StatusDisplayOpenPacket.of(panel, LocomotiveStatusData.sample(panel)));
        }
        return InteractionResult.CONSUME;
    }
    @Override public boolean consumesUse() { return true; }
    @Override public float animationTarget(PanelBlockEntity panel) { return 0f; }
    @Override
    public void addToGoggleTooltip(PanelBlockEntity panel, List<Component> tooltip) {
        tooltip.add(Component.translatable("gui.goggles.status_display"));
        tooltip.add(Component.translatable("gui.loconautics.status_display.live"));
    }
}
