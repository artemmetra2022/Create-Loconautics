package com.lycoris.loconautics.network.packets.dispatcher;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.dispatcher.DispatcherService;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Close or open every signal that protects a given block section. */
public record DispatcherSignalPacket(BlockPos pos, UUID sectionId, boolean hold) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DispatcherSignalPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("dispatcher_signal"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DispatcherSignalPacket> STREAM_CODEC =
            StreamCodec.of(DispatcherSignalPacket::write, DispatcherSignalPacket::read);

    private static void write(RegistryFriendlyByteBuf buf, DispatcherSignalPacket packet) {
        BlockPos.STREAM_CODEC.encode(buf, packet.pos);
        buf.writeUUID(packet.sectionId);
        buf.writeBoolean(packet.hold);
    }

    private static DispatcherSignalPacket read(RegistryFriendlyByteBuf buf) {
        return new DispatcherSignalPacket(BlockPos.STREAM_CODEC.decode(buf), buf.readUUID(), buf.readBoolean());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DispatcherSignalPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.serverLevel().hasChunkAt(packet.pos())
                    || !player.serverLevel().getBlockState(packet.pos()).is(LoconauticsRegistries.DISPATCHER.get())
                    || !player.position().closerThan(Vec3.atCenterOf(packet.pos()), 16.0)) return;
            if (!DispatcherService.setSectionHold(packet.sectionId(), packet.hold)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "gui.loconautics.dispatcher.signal_failed"), true);
            }
            PacketDistributor.sendToPlayer(player, DispatcherSnapshotPacket.of(packet.pos(),
                    DispatcherService.snapshot(player.getServer())));
        });
    }
}
