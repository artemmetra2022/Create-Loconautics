package com.lycoris.loconautics.mixin.create.fluid;

import com.lycoris.loconautics.content.hose.flow.HosePipeFlow;
import com.simibubi.create.content.fluids.PipeConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.Predicate;

@Mixin(value = PipeConnection.class, remap = false)
public abstract class PipeConnectionHoseFlowMixin {

    @Shadow
    public Direction side;

    @Shadow
    Optional<PipeConnection.Flow> flow;

    @Shadow
    private boolean tryStartingNewFlow(boolean inbound, FluidStack providedFluid) {
        throw new AssertionError();
    }

    @Inject(method = "manageFlows", at = @At("HEAD"), cancellable = true)
    private void loconautics$hoseDriven(Level world, BlockPos pos, FluidStack internalFluid,
                                        Predicate<FluidStack> extractionPredicate,
                                        CallbackInfoReturnable<Boolean> cir) {
        HosePipeFlow.Driven driven = HosePipeFlow.get(world, pos, side);
        if (driven == null) {
            return;
        }
        if (flow.isPresent() && flow.get().inbound == driven.inbound()
                && FluidStack.isSameFluidSameComponents(flow.get().fluid, driven.fluid())) {
            cir.setReturnValue(false);
            return;
        }
        flow = Optional.empty();
        cir.setReturnValue(tryStartingNewFlow(driven.inbound(), driven.fluid()));
    }
}
