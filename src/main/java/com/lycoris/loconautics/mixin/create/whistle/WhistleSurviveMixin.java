package com.lycoris.loconautics.mixin.create.whistle;

import com.lycoris.loconautics.content.boiler.BoilerAttach;
import com.simibubi.create.content.decoration.steamWhistle.WhistleBlock;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WhistleBlock.class)
public abstract class WhistleSurviveMixin {

    // create only lets a whistle sit on its own fluid tank, boilers and firebox bodies count too
    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void loconautics$surviveOnBoiler(BlockState state, LevelReader level, BlockPos pos,
                                             CallbackInfoReturnable<Boolean> cir) {
        BlockPos base = pos.relative(WhistleBlock.getAttachedDirection(state));
        if (BoilerAttach.isHostAt(level, base)) cir.setReturnValue(true);
    }
}
