package com.lycoris.loconautics.network.packets.dispatcher;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.dispatcher.DispatcherService;
import com.lycoris.loconautics.network.PacketTargets;
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

/** Hold/release is currently supported for Loconautics/Sable consists only. */
public record DispatcherCommandPacket(BlockPos pos, UUID trainId, boolean hold) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DispatcherCommandPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("dispatcher_command"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DispatcherCommandPacket> STREAM_CODEC =
            StreamCodec.of(DispatcherCommandPacket::write, DispatcherCommandPacket::read);

    private static void write(RegistryFriendlyByteBuf buf, DispatcherCommandPacket packet) {
        BlockPos.STREAM_CODEC.encode(buf, packet.pos);
        buf.writeUUID(packet.trainId);
        buf.writeBoolean(packet.hold);
    }

    private static DispatcherCommandPacket read(RegistryFriendlyByteBuf buf) {
        return new DispatcherCommandPacket(BlockPos.STREAM_CODEC.decode(buf), buf.readUUID(), buf.readBoolean());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DispatcherCommandPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.serverLevel().hasChunkAt(packet.pos())
                    || !player.serverLevel().getBlockState(packet.pos()).is(LoconauticsRegistries.DISPATCHER.get())
                    || !player.position().closerThan(Vec3.atCenterOf(packet.pos()), 16.0)) return;
            if (!DispatcherService.setLoconauticsHold(packet.trainId(), packet.hold())) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "gui.loconautics.dispatcher.command_failed"), true);
            }
            PacketDistributor.sendToPlayer(player, DispatcherSnapshotPacket.of(packet.pos(),
                    DispatcherService.snapshot(player.getServer())));
        });
    }
}
