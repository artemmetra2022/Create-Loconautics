package com.lycoris.loconautics.network.packets.safety;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.safety.SafetyBlockEntity;
import com.lycoris.loconautics.content.safety.SafetyDevice;
import com.lycoris.loconautics.network.PacketTargets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * What the Safety Unit screen sends: the switches and the limit, and optionally one frequency action. The toggle
 * frequency of a system is taken from the player's hands: the main hand item is the first frequency, the off hand
 * item the second one, like the two slots of a Redstone Link.
 */
public record SafetyConfigurePacket(BlockPos pos, int enabledMask, int speedLimit, boolean invert,
                                    int device, int action) implements CustomPacketPayload {

    public static final int ACTION_NONE = 0;
    public static final int ACTION_BIND = 1;
    public static final int ACTION_CLEAR = 2;

    public static final CustomPacketPayload.Type<SafetyConfigurePacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("safety_configure"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SafetyConfigurePacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SafetyConfigurePacket::pos,
                    ByteBufCodecs.VAR_INT, SafetyConfigurePacket::enabledMask,
                    ByteBufCodecs.VAR_INT, SafetyConfigurePacket::speedLimit,
                    ByteBufCodecs.BOOL, SafetyConfigurePacket::invert,
                    ByteBufCodecs.VAR_INT, SafetyConfigurePacket::device,
                    ByteBufCodecs.VAR_INT, SafetyConfigurePacket::action,
                    SafetyConfigurePacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SafetyConfigurePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (player.isSpectator()) return;
            SafetyBlockEntity unit = PacketTargets.blockEntity(player.level(), packet.pos(), SafetyBlockEntity.class);
            if (unit == null || !PacketTargets.withinReach(player, unit)) return;

            unit.applyConfiguration(packet.enabledMask(), packet.speedLimit(), packet.invert());

            SafetyDevice device = SafetyDevice.byIndex(packet.device());
            if (device != null) {
                if (packet.action() == ACTION_CLEAR) {
                    unit.setFrequency(device.ordinal(), ItemStack.EMPTY, ItemStack.EMPTY);
                } else if (packet.action() == ACTION_BIND) {
                    ItemStack first = player.getItemInHand(InteractionHand.MAIN_HAND).copyWithCount(1);
                    ItemStack second = player.getItemInHand(InteractionHand.OFF_HAND).copyWithCount(1);
                    if (first.isEmpty() && second.isEmpty()) {
                        player.displayClientMessage(Component.translatable("loconautics.safety.bind_empty"), true);
                    } else {
                        unit.setFrequency(device.ordinal(), first, second);
                        player.displayClientMessage(Component.translatable("loconautics.safety.bound"), true);
                    }
                }
            }
            // the screen shows the new state, the frequency mask changed
            PacketDistributor.sendToPlayer(player, unit.openPacket());
        });
    }
}
