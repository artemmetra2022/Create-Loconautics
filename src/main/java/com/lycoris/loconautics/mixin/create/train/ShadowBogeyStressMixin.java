package com.lycoris.loconautics.mixin.create.train;

import com.lycoris.loconautics.trainphysics.state.CreateTrainShadow;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.Train;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// our rails own the wheel spacing, so create must not pull a ghost bogey toward its own
@Mixin(value = CarriageBogey.class, remap = false)
public abstract class ShadowBogeyStressMixin {

    @Inject(method = "getStress", at = @At("HEAD"), cancellable = true)
    private void loconautics$ghostBogeyKeepsItsShape(CallbackInfoReturnable<Double> cir) {
        Train train = ((CarriageBogey) (Object) this).carriage.train;
        if (train != null && CreateTrainShadow.isShadow(train)) {
            cir.setReturnValue(0.0);
        }
    }
}
