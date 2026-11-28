package com.lycoris.loconautics.content.boiler;

import com.lycoris.loconautics.foundation.LoconauticsLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

// the level bars of the boiler goggles, drawn like Create does for its own boiler
public final class BoilerGoggleBars {

    private final int low;
    private final int high;
    private final int total;

    public BoilerGoggleBars(int total, int... levels) {
        int lowest = Integer.MAX_VALUE;
        int highest = 0;
        for (int level : levels) {
            lowest = Math.min(lowest, level);
            highest = Math.max(highest, level);
        }
        this.total = Math.max(1, total);
        this.low = Math.min(this.total, lowest == Integer.MAX_VALUE ? 0 : lowest);
        this.high = Math.min(this.total, highest);
    }

    public MutableComponent row(String key, int level) {
        return label(key).append(bar(Math.min(total, level)));
    }

    // the light blue part is what the bogeys use, the gray rest is what the boiler could give
    public MutableComponent powerRow(String key, float used, float max) {
        int filled = max <= 0f ? 0 : Math.round(total * Math.min(1f, used / max));
        if (used > 0f && filled == 0) filled = 1;
        return label(key).append(bars(filled, ChatFormatting.AQUA)).append(bars(total - filled, ChatFormatting.DARK_GRAY));
    }

    private static MutableComponent label(String key) {
        return LoconauticsLang.translate("gui.goggles.boiler_bar_" + key).style(ChatFormatting.GRAY).component()
                .append(LoconauticsLang.translate("gui.goggles.boiler_bar_" + key + "_dots")
                        .style(ChatFormatting.DARK_GRAY).component());
    }

    private MutableComponent bar(int level) {
        return Component.empty()
                .append(bars(Math.max(0, low - 1), ChatFormatting.DARK_GREEN))
                .append(bars(low > 0 ? 1 : 0, ChatFormatting.GREEN))
                .append(bars(Math.max(0, level - low), ChatFormatting.DARK_GREEN))
                .append(bars(Math.max(0, high - level), ChatFormatting.DARK_RED))
                .append(bars(Math.max(0, total - Math.max(high, level)), ChatFormatting.DARK_GRAY));
    }

    private static MutableComponent bars(int count, ChatFormatting format) {
        return Component.literal("|".repeat(Math.max(0, count))).withStyle(format);
    }
}
