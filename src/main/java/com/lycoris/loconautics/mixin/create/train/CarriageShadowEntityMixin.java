package com.lycoris.loconautics.mixin.create.train;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lycoris.loconautics.trainphysics.state.CreateTrainShadow;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

// a shadow has no entity, create would mark it invalid
@Mixin(value = Carriage.class, remap = false)
public abstract class CarriageShadowEntityMixin {

    @Shadow
    public Train train;

    @WrapOperation(method = "manageEntities",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/trains/entity/Carriage$DimensionalCarriageEntity;createEntity(Lnet/minecraft/world/level/Level;Z)V"))
    private void loconautics$shadowHasNoEntity(Carriage.DimensionalCarriageEntity dimensional, Level level,
                                               boolean loadPassengers, Operation<Void> original) {
        if (CreateTrainShadow.isShadow(train)) {
            return;
        }
        original.call(dimensional, level, loadPassengers);
    }
}
