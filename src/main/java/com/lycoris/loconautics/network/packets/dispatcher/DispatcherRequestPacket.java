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
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.world.phys.Vec3;

public record DispatcherRequestPacket(BlockPos pos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DispatcherRequestPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("dispatcher_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DispatcherRequestPacket> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, DispatcherRequestPacket::pos, DispatcherRequestPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DispatcherRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.serverLevel().hasChunkAt(packet.pos())
                    || !player.serverLevel().getBlockState(packet.pos()).is(LoconauticsRegistries.DISPATCHER.get())
                    || !player.position().closerThan(Vec3.atCenterOf(packet.pos()), 16.0)) return;
            DispatcherService.Snapshot snapshot = DispatcherService.snapshot(player.getServer());
            PacketDistributor.sendToPlayer(player, DispatcherSnapshotPacket.of(packet.pos(), snapshot));
        });
    }
}
