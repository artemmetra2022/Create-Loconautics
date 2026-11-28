package com.lycoris.loconautics.network.packets.boiler;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.boiler.BoilerContributions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.LinkedHashMap;
import java.util.Map;

public record BoilerContributionsSyncPacket(Map<ResourceLocation, BoilerContributions.Stats> stats)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BoilerContributionsSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("boiler_contributions_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BoilerContributionsSyncPacket> STREAM_CODEC =
            StreamCodec.of(BoilerContributionsSyncPacket::write, BoilerContributionsSyncPacket::read);

    private static void write(RegistryFriendlyByteBuf buf, BoilerContributionsSyncPacket packet) {
        buf.writeVarInt(packet.stats().size());
        packet.stats().forEach((id, stats) -> {
            buf.writeResourceLocation(id);
            writeFloat(buf, stats.fireboxHeat());
            writeFloat(buf, stats.boilerCapacity());
            buf.writeBoolean(stats.waterCapacity() != null);
            if (stats.waterCapacity() != null) buf.writeVarInt(stats.waterCapacity());
        });
    }

    private static void writeFloat(RegistryFriendlyByteBuf buf, Float value) {
        buf.writeBoolean(value != null);
        if (value != null) buf.writeFloat(value);
    }

    private static Float readFloat(RegistryFriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readFloat() : null;
    }

    private static BoilerContributionsSyncPacket read(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        Map<ResourceLocation, BoilerContributions.Stats> out = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            ResourceLocation id = buf.readResourceLocation();
            Float heat = readFloat(buf);
            Float capacity = readFloat(buf);
            Integer water = buf.readBoolean() ? buf.readVarInt() : null;
            out.put(id, new BoilerContributions.Stats(heat, capacity, water));
        }
        return new BoilerContributionsSyncPacket(out);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BoilerContributionsSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> BoilerContributions.replaceFromSync(packet.stats()));
    }
}
