package com.lycoris.loconautics.content.boiler;

import com.simibubi.create.foundation.placement.PoleHelper;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.function.Predicate;

public class BoilerPlacement extends PoleHelper<Direction> {
    private final Predicate<ItemStack> itemPredicate;

    public BoilerPlacement(Predicate<BlockState> matchesState, Predicate<ItemStack> itemPredicate,
                           Property<Direction> facing) {
        super(matchesState, state -> state.getValue(facing).getAxis(), facing);
        this.itemPredicate = itemPredicate;
    }

    @Override
    public Predicate<ItemStack> getItemPredicate() {
        return itemPredicate;
    }
}
