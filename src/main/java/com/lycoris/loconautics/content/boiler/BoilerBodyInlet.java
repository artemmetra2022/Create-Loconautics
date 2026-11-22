package com.lycoris.loconautics.content.boiler;

import com.lycoris.loconautics.content.boiler.firebox.FireboxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public final class BoilerBodyInlet implements IFluidHandler {

    private static final int CACHE_TICKS = 20;

    private final Level level;
    private final BlockPos pos;

    @Nullable private BlockPos cachedController;
    private long cachedAt = Long.MIN_VALUE;

    public BoilerBodyInlet(Level level, BlockPos pos) {
        this.level = level;
        this.pos = pos.immutable();
    }

    @Nullable
    private FireboxBlockEntity controller() {
        if (level.isClientSide) {
            return null;
        }
        long now = level.getGameTime();
        if (cachedController == null || now - cachedAt >= CACHE_TICKS
                || !(level.getBlockEntity(cachedController) instanceof FireboxBlockEntity)) {
            cachedController = BoilerLookup.controllerPos(level, pos);
            cachedAt = now;
        }
        return cachedController != null && level.getBlockEntity(cachedController) instanceof FireboxBlockEntity be
                && be.getWaterTank().getCapacity() > 0 ? be : null;
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        FireboxBlockEntity firebox = controller();
        return firebox == null ? FluidStack.EMPTY : firebox.getWaterTank().getFluid();
    }

    @Override
    public int getTankCapacity(int tank) {
        FireboxBlockEntity firebox = controller();
        return firebox == null ? 0 : firebox.getWaterTank().getCapacity();
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return stack.getFluid() == Fluids.WATER && controller() != null;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        FireboxBlockEntity firebox = controller();
        return firebox == null ? 0 : firebox.fillFromHose(resource, action);
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return FluidStack.EMPTY;
    }
}
