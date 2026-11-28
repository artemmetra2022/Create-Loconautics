package com.lycoris.loconautics.network.packets.controls;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.paneldisplay.LocomotiveStatusData;
import com.lycoris.loconautics.content.controls.paneldisplay.LocomotiveStatusScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record StatusDisplayOpenPacket(BlockPos pos, LocomotiveStatusData data) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<StatusDisplayOpenPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("status_display_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StatusDisplayOpenPacket> STREAM_CODEC =
            StreamCodec.of(StatusDisplayOpenPacket::write, StatusDisplayOpenPacket::read);
    private static void write(RegistryFriendlyByteBuf buf, StatusDisplayOpenPacket packet) {
        BlockPos.STREAM_CODEC.encode(buf, packet.pos);
        packet.data.write(buf);
    }
    private static StatusDisplayOpenPacket read(RegistryFriendlyByteBuf buf) {
        return new StatusDisplayOpenPacket(BlockPos.STREAM_CODEC.decode(buf), LocomotiveStatusData.read(buf));
    }
    public static StatusDisplayOpenPacket of(PanelBlockEntity panel, LocomotiveStatusData data) {
        return new StatusDisplayOpenPacket(panel.getBlockPos(), data);
    }
    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(StatusDisplayOpenPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> LocomotiveStatusScreen.open(packet));
    }
}
