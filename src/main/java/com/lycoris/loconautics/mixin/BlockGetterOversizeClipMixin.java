package com.lycoris.loconautics.mixin;

import com.lycoris.loconautics.foundation.pick.OversizePicker;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockGetter.class, remap = false)
public interface BlockGetterOversizeClipMixin {

    @Inject(method = "clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;",
            at = @At("RETURN"), cancellable = true)
    private void loconautics$pickOversize(ClipContext context, CallbackInfoReturnable<BlockHitResult> cir) {
        if (((ClipContextAccessor) context).loconautics$getBlock() != ClipContext.Block.OUTLINE) return;
        cir.setReturnValue(OversizePicker.extend((BlockGetter) this, context, cir.getReturnValue()));
    }
}
