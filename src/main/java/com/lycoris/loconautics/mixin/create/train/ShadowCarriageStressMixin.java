package com.lycoris.loconautics.mixin.create.train;

import com.lycoris.loconautics.trainphysics.state.CreateTrainShadow;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// our rails own the bogey spacing, so create must not squeeze or stretch a ghost car to a whole block
@Mixin(value = Carriage.class, remap = false)
public abstract class ShadowCarriageStressMixin {

    @Inject(method = "getAnchorDiff", at = @At("HEAD"), cancellable = true)
    private void loconautics$ghostCarKeepsItsShape(CallbackInfoReturnable<Double> cir) {
        Carriage carriage = (Carriage) (Object) this;
        if (carriage.train != null && CreateTrainShadow.isShadow(carriage.train)) {
            cir.setReturnValue((double) carriage.bogeySpacing);
        }
    }
}
