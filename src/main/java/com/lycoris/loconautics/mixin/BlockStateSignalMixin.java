package com.lycoris.loconautics.mixin;

import com.lycoris.loconautics.content.controls.panel.target.PanelSignals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockBehaviour.BlockStateBase.class, remap = false)
public abstract class BlockStateSignalMixin {

    @Inject(method = "getSignal", at = @At("RETURN"), cancellable = true)
    private void loconautics$panelSignal(BlockGetter level, BlockPos pos, Direction direction,
                                         CallbackInfoReturnable<Integer> cir) {
        if (!(level instanceof ServerLevel server) || !PanelSignals.any()) return;
        int panel = PanelSignals.signalFrom(server, pos, direction);
        if (panel > cir.getReturnValue()) cir.setReturnValue(panel);
    }
}
