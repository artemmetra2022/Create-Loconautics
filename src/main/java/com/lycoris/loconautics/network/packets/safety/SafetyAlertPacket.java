package com.lycoris.loconautics.network.packets.safety;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.safety.SafetyAlertHUD;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SafetyAlertPacket(int alarm) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SafetyAlertPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("safety_alert"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SafetyAlertPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SafetyAlertPacket::alarm,
                    SafetyAlertPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SafetyAlertPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> SafetyAlertHUD.onAlert(packet.alarm()));
    }
}
