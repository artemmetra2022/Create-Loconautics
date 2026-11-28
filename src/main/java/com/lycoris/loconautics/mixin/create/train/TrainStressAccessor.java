package com.lycoris.loconautics.mixin.create.train;

import com.simibubi.create.content.trains.entity.Train;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Train.class, remap = false)
public interface TrainStressAccessor {

    @Accessor("stress")
    void loconautics$setStress(double[] stress);
}
