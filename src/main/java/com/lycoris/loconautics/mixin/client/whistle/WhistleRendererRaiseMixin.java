package com.lycoris.loconautics.mixin.client.whistle;

import com.lycoris.loconautics.content.boiler.BoilerAttach;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.decoration.steamWhistle.WhistleBlock;
import com.simibubi.create.content.decoration.steamWhistle.WhistleBlockEntity;
import com.simibubi.create.content.decoration.steamWhistle.WhistleRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WhistleRenderer.class, remap = false)
public abstract class WhistleRendererRaiseMixin {

    // the mouth is drawn by the renderer, so it has to move out with the raised block
    @Inject(method = "renderSafe", at = @At("HEAD"))
    private void loconautics$liftStart(WhistleBlockEntity be, float partialTicks, PoseStack ms,
                                       MultiBufferSource buffer, int light, int overlay, CallbackInfo ci) {
        ms.pushPose();
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof WhistleBlock) || !state.getValue(BoilerAttach.RAISED)) return;
        Direction out = WhistleBlock.getAttachedDirection(state).getOpposite();
        ms.translate(out.getStepX() * BoilerAttach.BULGE, out.getStepY() * BoilerAttach.BULGE,
                out.getStepZ() * BoilerAttach.BULGE);
    }

    @Inject(method = "renderSafe", at = @At("RETURN"))
    private void loconautics$liftEnd(WhistleBlockEntity be, float partialTicks, PoseStack ms,
                                     MultiBufferSource buffer, int light, int overlay, CallbackInfo ci) {
        ms.popPose();
    }
}
