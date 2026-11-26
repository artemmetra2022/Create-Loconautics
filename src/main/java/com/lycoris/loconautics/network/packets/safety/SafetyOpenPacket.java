package com.lycoris.loconautics.network.packets.safety;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.safety.SafetyScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** opens the Safety Unit screen, or refreshes it when it is already open for the same block */
public record SafetyOpenPacket(BlockPos pos, int enabledMask, int speedLimit, boolean invert, int frequencyMask,
                               int activeMask, int problemMask) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SafetyOpenPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("safety_open"));

    // composite() only goes up to 6 fields and this has 7, so it is written by hand
    public static final StreamCodec<RegistryFriendlyByteBuf, SafetyOpenPacket> STREAM_CODEC =
            StreamCodec.of(SafetyOpenPacket::write, SafetyOpenPacket::read);

    private static void write(RegistryFriendlyByteBuf buf, SafetyOpenPacket p) {
        BlockPos.STREAM_CODEC.encode(buf, p.pos);
        buf.writeVarInt(p.enabledMask);
        buf.writeVarInt(p.speedLimit);
        buf.writeBoolean(p.invert);
        buf.writeVarInt(p.frequencyMask);
        buf.writeVarInt(p.activeMask);
        buf.writeVarInt(p.problemMask);
    }

    private static SafetyOpenPacket read(RegistryFriendlyByteBuf buf) {
        return new SafetyOpenPacket(
                BlockPos.STREAM_CODEC.decode(buf),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SafetyOpenPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> SafetyScreen.open(packet));
    }
}
