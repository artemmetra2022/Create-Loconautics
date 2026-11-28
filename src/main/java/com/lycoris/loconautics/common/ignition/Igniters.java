package com.lycoris.loconautics.common.ignition;

import com.lycoris.loconautics.LoconauticsConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class Igniters {
    public static final TagKey<Item> TAG = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(LoconauticsConstants.MOD_ID, "igniters"));

    private Igniters() {
    }

    public static boolean is(ItemStack stack) {
        return stack.is(TAG);
    }
}
