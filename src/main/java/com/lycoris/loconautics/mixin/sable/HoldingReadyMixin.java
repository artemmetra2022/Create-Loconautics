package com.lycoris.loconautics.mixin.sable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lycoris.loconautics.trainphysics.state.TrainResidency;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SubLevelHoldingChunk.class, remap = false)
public abstract class HoldingReadyMixin {

    // a ghost trains body comes back only when the train asks, sable alone would bring it back where it stopped
    @WrapOperation(method = "collectReadySubLevels",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/storage/holding/SubLevelHoldingChunk;canLoadSubLevel(Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelData;)Z"))
    private boolean loconautics$ghostOwned(ServerLevel level, SubLevelData data, Operation<Boolean> original) {
        return original.call(level, data) && !TrainResidency.ghostOwned(data.uuid());
    }
}
