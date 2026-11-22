package com.lycoris.loconautics.content.smokestack;

import com.lycoris.loconautics.content.boiler.BoilerAttach;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Predicate;

public final class SmokestackPlacement implements IPlacementHelper {

    public static final int ID = PlacementHelpers.register(new SmokestackPlacement());

    private SmokestackPlacement() {
    }

    public static void init() {
    }

    @Override
    public Predicate<ItemStack> getItemPredicate() {
        return stack -> stack.getItem() instanceof BlockItem item && item.getBlock() instanceof SmokestackBlock;
    }

    @Override
    public Predicate<BlockState> getStatePredicate() {
        return state -> state.getBlock() instanceof SmokestackBlock;
    }

    @Override
    public PlacementOffset getOffset(Player player, Level level, BlockState state, BlockPos pos, BlockHitResult ray) {
        Direction dir = ray.getLocation().y - pos.getY() >= 0.5 ? Direction.UP : Direction.DOWN;
        BlockPos target = pos.relative(dir);
        while (level.getBlockState(target).getBlock() instanceof SmokestackBlock) {
            target = target.relative(dir);
        }
        if (!level.isInWorldBounds(target) || !level.getBlockState(target).canBeReplaced()) {
            return PlacementOffset.fail();
        }
        return PlacementOffset.success(target, BoilerAttach.settled(level, target));
    }
}
