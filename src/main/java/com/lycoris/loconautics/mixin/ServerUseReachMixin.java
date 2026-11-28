package com.lycoris.loconautics.mixin;

import com.lycoris.loconautics.foundation.pick.OversizeShape;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerGamePacketListenerImpl.class, remap = false)
public abstract class ServerUseReachMixin {

    private static final double OVERSIZE_LIMIT = 2.5;

    @Shadow
    public ServerPlayer player;

    @Unique
    private boolean loconautics$oversizeTarget;

    @Inject(method = "handleUseItemOn", at = @At("HEAD"))
    private void loconautics$markOversize(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        BlockPos pos = packet.getHitResult().getBlockPos();
        loconautics$oversizeTarget = player.level().getBlockState(pos).getBlock() instanceof OversizeShape;
    }

    // vanilla drops clicks over one block from the center, oversize shapes reach past that
    @ModifyConstant(method = "handleUseItemOn", constant = @Constant(doubleValue = 1.0000001))
    private double loconautics$widenReach(double limit) {
        return loconautics$oversizeTarget ? OVERSIZE_LIMIT : limit;
    }
}
