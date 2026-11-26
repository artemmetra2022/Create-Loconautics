package com.lycoris.loconautics.network.packets.driverhint;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.driverhint.DriverHint;
import com.lycoris.loconautics.content.driverhint.DriverHintHUD;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server → client: current driver hint id (0 = none). */
public record DriverHintPacket(int hint) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DriverHintPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("driver_hint"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DriverHintPacket> STREAM_CODEC =
            StreamCodec.of(DriverHintPacket::write, DriverHintPacket::read);

    private static void write(RegistryFriendlyByteBuf buf, DriverHintPacket packet) {
        buf.writeVarInt(packet.hint);
    }

    private static DriverHintPacket read(RegistryFriendlyByteBuf buf) {
        return new DriverHintPacket(buf.readVarInt());
    }

    public static DriverHintPacket of(DriverHint hint) {
        return new DriverHintPacket(hint == null ? 0 : hint.ordinal());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DriverHintPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> DriverHintHUD.onHint(DriverHint.byId(packet.hint())));
    }
}
