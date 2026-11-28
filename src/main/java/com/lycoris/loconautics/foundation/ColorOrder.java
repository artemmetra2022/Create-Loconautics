package com.lycoris.loconautics.foundation;

import net.minecraft.world.item.DyeColor;

import java.util.List;

public final class ColorOrder {

    public static final List<DyeColor> ORDER = List.of(
            DyeColor.WHITE, DyeColor.LIGHT_GRAY, DyeColor.GRAY, DyeColor.BLACK,
            DyeColor.BROWN, DyeColor.RED, DyeColor.ORANGE, DyeColor.YELLOW,
            DyeColor.LIME, DyeColor.GREEN, DyeColor.CYAN, DyeColor.LIGHT_BLUE,
            DyeColor.BLUE, DyeColor.PURPLE, DyeColor.MAGENTA, DyeColor.PINK);

    public static final List<String> EXTRA = List.of("industrial", "andesite", "brass", "copper", "zinc");

    public static final int EXTRA_START = 16;

    public static final int COUNT = EXTRA_START + EXTRA.size();

    private ColorOrder() {
    }

    public static int indexOf(DyeColor color) {
        return ORDER.indexOf(color);
    }

    public static int extraIndex(String name) {
        int at = EXTRA.indexOf(name);
        return at < 0 ? -1 : EXTRA_START + at;
    }

    public static DyeColor at(int index) {
        return ORDER.get(index);
    }
}
