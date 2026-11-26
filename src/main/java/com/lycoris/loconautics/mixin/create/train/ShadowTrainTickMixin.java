package com.lycoris.loconautics.mixin.create.train;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.lycoris.loconautics.trainphysics.state.CreateTrainShadow;
import com.lycoris.loconautics.trainphysics.state.GhostCreateStep;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Navigation;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// create only moves a ghost shadow, our code owns its schedule and stops
@Mixin(value = Train.class, remap = false)
public abstract class ShadowTrainTickMixin {

    @WrapOperation(method = "tick(Lnet/minecraft/world/level/Level;)V",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/trains/schedule/ScheduleRuntime;tick(Lnet/minecraft/world/level/Level;)V"))
    private void loconautics$ghostSkipsRuntime(ScheduleRuntime runtime, Level level, Operation<Void> original) {
        if (!CreateTrainShadow.isShadow((Train) (Object) this)) {
            original.call(runtime, level);
        }
    }

    @WrapOperation(method = "tick(Lnet/minecraft/world/level/Level;)V",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/trains/entity/Navigation;tick(Lnet/minecraft/world/level/Level;)V"))
    private void loconautics$ghostSkipsNavigation(Navigation navigation, Level level, Operation<Void> original) {
        if (!CreateTrainShadow.isShadow((Train) (Object) this)) {
            original.call(navigation, level);
        }
    }

    @WrapOperation(method = "tick(Lnet/minecraft/world/level/Level;)V",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/trains/entity/Train;tickPassiveSlowdown()V"))
    private void loconautics$ghostSkipsSlowdown(Train train, Operation<Void> original) {
        if (!CreateTrainShadow.isShadow(train)) {
            original.call(train);
        }
    }

    @WrapOperation(method = "tick(Lnet/minecraft/world/level/Level;)V",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/trains/entity/Train;updateConductors()V"))
    private void loconautics$ghostSkipsConductors(Train train, Operation<Void> original) {
        if (!CreateTrainShadow.isShadow(train)) {
            original.call(train);
        }
    }

    @WrapOperation(method = "tick(Lnet/minecraft/world/level/Level;)V",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/trains/station/GlobalStation;runMailTransfer()V"))
    private void loconautics$ghostSkipsMail(GlobalStation station, Operation<Void> original) {
        if (!CreateTrainShadow.isShadow((Train) (Object) this)) {
            original.call(station);
        }
    }

    @WrapOperation(method = "tick(Lnet/minecraft/world/level/Level;)V",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/trains/entity/Train;collideWithOtherTrains(Lnet/minecraft/world/level/Level;Lcom/simibubi/create/content/trains/entity/Carriage;)V"))
    private void loconautics$ghostSkipsCollision(Train train, Level level, Carriage carriage, Operation<Void> original) {
        if (!CreateTrainShadow.isShadow(train)) {
            original.call(train, level, carriage);
        }
    }

    // the second store to actual is the measured anchor distance, shifting it makes the whole block target mean the real gap
    @ModifyVariable(method = "tick(Lnet/minecraft/world/level/Level;)V", at = @At(value = "STORE", ordinal = 1),
            name = "actual")
    private double loconautics$springAimsAtRealGap(double actual, @Local(name = "carriage") Carriage carriage) {
        Train train = (Train) (Object) this;
        return actual + GhostCreateStep.spacingOffset(train, train.carriages.indexOf(carriage) - 1);
    }

    @Inject(method = "updateNavigationTarget", at = @At("HEAD"), cancellable = true)
    private void loconautics$ghostKeepsItsRoute(Level level, double distance, CallbackInfo ci) {
        if (CreateTrainShadow.isShadow((Train) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "arriveAt", at = @At("HEAD"), cancellable = true)
    private void loconautics$ghostStopsAreOurs(GlobalStation station, CallbackInfo ci) {
        if (CreateTrainShadow.isShadow((Train) (Object) this)) {
            GhostCreateStep.noteCreateArrival((Train) (Object) this, station);
            ci.cancel();
        }
    }
}
