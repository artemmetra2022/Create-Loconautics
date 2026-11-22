package com.lycoris.loconautics.mixin.create.train;

import com.lycoris.loconautics.trainphysics.state.CreateTrainShadow;
import com.simibubi.create.content.trains.entity.Navigation;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// junctions follow our schedule route
@Mixin(value = Navigation.class, remap = false)
public abstract class NavigationShadowMixin {

    @Shadow
    public Train train;

    @Inject(method = "control", at = @At("HEAD"), cancellable = true)
    private void loconautics$ghostRoute(TravellingPoint point,
                                        CallbackInfoReturnable<TravellingPoint.ITrackSelector> cir) {
        if (CreateTrainShadow.isShadow(train)) {
            cir.setReturnValue(CreateTrainShadow.ghostSelector(train, point));
        }
    }
}
