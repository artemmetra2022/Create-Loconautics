package com.lycoris.loconautics.network.packets.controls;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.combustion.SmallEngineBlockEntity;
import com.lycoris.loconautics.content.controls.panellamp.SignalLampMode;
import com.lycoris.loconautics.content.controls.panellamp.SignalLampPart;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SignalLampSelectPacket(BlockPos panelPos, int slot, String mode) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SignalLampSelectPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("signal_lamp_select"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SignalLampSelectPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SignalLampSelectPacket::panelPos,
                    ByteBufCodecs.VAR_INT, SignalLampSelectPacket::slot,
                    ByteBufCodecs.STRING_UTF8, SignalLampSelectPacket::mode,
                    SignalLampSelectPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SignalLampSelectPacket packet, IPayloadContext context) {
        SignalLampMode selected = SignalLampMode.byId(packet.mode());
        if (selected == null) return;

        PanelPartEdit.run(context, packet.panelPos(), packet.slot(), SignalLampPart.class,
                (player, panel, slot, lamp) -> {
                    if (!player.level().isLoaded(lamp.target())) return;
                    BlockEntity source = player.level().getBlockEntity(lamp.target());
                    if (source == null) {
                        source = SmallEngineBlockEntity.resolve(player.level(), lamp.target());
                    }
                    if (!selected.supports(source)) return;
                    panel.replacePart(slot, new SignalLampPart(
                            lamp.target(), selected, lamp.body(), selected.isActive(source)));
                });
    }
}
