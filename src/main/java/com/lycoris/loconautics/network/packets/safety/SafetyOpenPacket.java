package com.lycoris.loconautics.network.packets.safety;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.safety.SafetyScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** opens the Safety Unit screen, or refreshes it when it is already open for the same block */
public record SafetyOpenPacket(BlockPos pos, int enabledMask, int speedLimit, boolean invert, int frequencyMask,
                               int activeMask, int problemMask) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SafetyOpenPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("safety_open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SafetyOpenPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SafetyOpenPacket::pos,
                    ByteBufCodecs.VAR_INT, SafetyOpenPacket::enabledMask,
                    ByteBufCodecs.VAR_INT, SafetyOpenPacket::speedLimit,
                    ByteBufCodecs.BOOL, SafetyOpenPacket::invert,
                    ByteBufCodecs.VAR_INT, SafetyOpenPacket::frequencyMask,
                    ByteBufCodecs.VAR_INT, SafetyOpenPacket::activeMask,
                    ByteBufCodecs.VAR_INT, SafetyOpenPacket::problemMask,
                    SafetyOpenPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SafetyOpenPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> SafetyScreen.open(packet));
    }
}
