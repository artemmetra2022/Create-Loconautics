package com.lycoris.loconautics.content.hose.flow;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

// pipes have no fluid handler, so we fill the open ends of the network
public final class HosePipeSink implements IFluidHandler {

    private record Step(BlockPos pos, int pipes) {
    }

    private record Entry(@Nullable BlockPos from, Direction enterSide) {
    }

    private record PipeSide(BlockPos pos, Direction side, boolean inbound) {
    }

    private record End(IFluidHandler handler, List<PipeSide> path) {

        void drive(Level level, FluidStack fluid, boolean reversed) {
            for (PipeSide s : path) {
                HosePipeFlow.drive(level, s.pos(), s.side(), s.inbound() != reversed, fluid);
            }
        }
    }

    private final Level level;
    private final BlockPos endpoint;
    private final BlockPos firstPipe;
    private final Direction plateSide;

    public HosePipeSink(Level level, BlockPos endpoint, Direction plateSide) {
        this.level = level;
        this.endpoint = endpoint;
        this.firstPipe = endpoint.relative(plateSide);
        this.plateSide = plateSide;
    }

    public static boolean isPipe(Level level, BlockPos pos) {
        return FluidPropagator.getPipe(level, pos) != null;
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return true;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return 0;
        }
        int left = resource.getAmount();
        for (End end : openEnds()) {
            if (left <= 0) {
                break;
            }
            int moved = end.handler().fill(resource.copyWithAmount(left), action);
            if (moved > 0 && action.execute()) {
                end.drive(level, resource, false);
            }
            left -= moved;
        }
        return resource.getAmount() - left;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return take(resource, resource.getAmount(), action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return take(null, maxDrain, action);
    }

    private FluidStack take(@Nullable FluidStack wanted, int amount, FluidAction action) {
        if (amount <= 0) {
            return FluidStack.EMPTY;
        }
        for (End end : openEnds()) {
            FluidStack out = wanted == null ? end.handler().drain(amount, action)
                    : end.handler().drain(wanted.copyWithAmount(amount), action);
            if (!out.isEmpty()) {
                if (action.execute()) {
                    end.drive(level, out, true);
                }
                return out;
            }
        }
        return FluidStack.EMPTY;
    }

    private List<End> openEnds() {
        List<End> ends = new ArrayList<>();
        Queue<Step> queue = new ArrayDeque<>();
        Map<BlockPos, Entry> entries = new HashMap<>();
        int limit = FluidPropagator.getPumpRange();
        queue.add(new Step(firstPipe, 1));
        entries.put(firstPipe, new Entry(null, plateSide.getOpposite()));
        while (!queue.isEmpty()) {
            Step step = queue.poll();
            FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, step.pos());
            if (pipe == null) {
                continue;
            }
            for (Direction d : FluidPropagator.getPipeConnections(level.getBlockState(step.pos()), pipe)) {
                BlockPos next = step.pos().relative(d);
                if (next.equals(endpoint) || !level.isLoaded(next)) {
                    continue;
                }
                if (FluidPropagator.getPipe(level, next) != null) {
                    if (step.pipes() < limit && !entries.containsKey(next)) {
                        entries.put(next, new Entry(step.pos(), d.getOpposite()));
                        queue.add(new Step(next, step.pipes() + 1));
                    }
                    continue;
                }
                IFluidHandler handler = handlerAt(next, d.getOpposite());
                if (handler == null && FluidPropagator.isOpenEnd(level, step.pos(), d)) {
                    handler = HoseOpenEnds.handler(level, step.pos(), d);
                }
                if (handler != null) {
                    ends.add(new End(handler, pathTo(entries, step.pos(), d)));
                }
            }
        }
        return ends;
    }

    private static List<PipeSide> pathTo(Map<BlockPos, Entry> entries, BlockPos last, Direction exit) {
        List<PipeSide> path = new ArrayList<>();
        path.add(new PipeSide(last, exit, false));
        BlockPos at = last;
        while (at != null) {
            Entry entry = entries.get(at);
            path.add(new PipeSide(at, entry.enterSide(), true));
            at = entry.from();
            if (at != null) {
                path.add(new PipeSide(at, entry.enterSide().getOpposite(), false));
            }
        }
        return path;
    }

    @Nullable
    private IFluidHandler handlerAt(BlockPos pos, Direction side) {
        return level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
    }
}
