package com.lycoris.loconautics.content.boiler;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.config.LoconauticsConfig;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

public final class FuelGrades {

    public static final TagKey<Item> POOR_FUEL = TagKey.create(Registries.ITEM, LoconauticsConstants.id("boiler_fuel/poor"));
    public static final TagKey<Item> PREMIUM_FUEL = TagKey.create(Registries.ITEM, LoconauticsConstants.id("boiler_fuel/premium"));

    public static final DataMapType<Item, FuelGradeEntry> DATA_MAP = DataMapType
            .builder(LoconauticsConstants.id("fuel_grade"), Registries.ITEM, FuelGradeEntry.CODEC)
            .build();

    private FuelGrades() {
    }

    public static void register(RegisterDataMapTypesEvent event) {
        event.register(DATA_MAP);
    }

    // tags win, anything else is graded by how long one item burns
    public static FuelGrade of(ItemStack stack) {
        FuelGradeEntry entry = stack.getItemHolder().getData(DATA_MAP);
        if (entry != null) return entry.grade();
        if (stack.is(PREMIUM_FUEL)) return FuelGrade.PREMIUM;
        if (stack.is(POOR_FUEL)) return FuelGrade.POOR;
        int burn = stack.getBurnTime(RecipeType.SMELTING);
        if (burn <= LoconauticsConfig.POOR_FUEL_BURN_LIMIT.get()) return FuelGrade.POOR;
        if (burn > LoconauticsConfig.PREMIUM_FUEL_BURN_LIMIT.get()) return FuelGrade.PREMIUM;
        return FuelGrade.NORMAL;
    }
}
