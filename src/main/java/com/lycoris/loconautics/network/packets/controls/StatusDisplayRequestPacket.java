package com.lycoris.loconautics.network.packets.controls;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.paneldisplay.LocomotiveStatusData;
import com.lycoris.loconautics.content.controls.paneldisplay.LocomotiveStatusPart;
import com.lycoris.loconautics.network.PacketTargets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.Arrays;

public record StatusDisplayRequestPacket(BlockPos pos) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<StatusDisplayRequestPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("status_display_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StatusDisplayRequestPacket> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, StatusDisplayRequestPacket::pos,
                    StatusDisplayRequestPacket::new);
    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(StatusDisplayRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            PanelBlockEntity panel = PacketTargets.blockEntity(player.serverLevel(), packet.pos(), PanelBlockEntity.class);
            if (panel == null || !PacketTargets.withinReach(player, panel)) return;
            boolean installed = Arrays.stream(panel.slots())
                    .anyMatch(slot -> panel.getPart(slot) instanceof LocomotiveStatusPart);
            if (!installed) return;
            PacketDistributor.sendToPlayer(player,
                    StatusDisplayOpenPacket.of(panel, LocomotiveStatusData.sample(panel)));
        });
    }
}
