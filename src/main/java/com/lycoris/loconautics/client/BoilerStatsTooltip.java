package com.lycoris.loconautics.client;

import com.lycoris.loconautics.content.boiler.BoilerContributions;
import com.lycoris.loconautics.content.boiler.BoilerTags;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

public final class BoilerStatsTooltip {

    private BoilerStatsTooltip() {
    }

    public static void onTooltip(ItemTooltipEvent event) {
        if (!(event.getItemStack().getItem() instanceof BlockItem blockItem)) return;
        Block block = blockItem.getBlock();
        boolean body = block.defaultBlockState().is(BoilerTags.BOILER_BODY);
        Float heat = BoilerContributions.statsOf(block).fireboxHeat();
        if (!body && heat == null) return;

        List<Component> tooltip = event.getToolTip();
        int at = idLine(tooltip, blockItem);
        boolean held = Screen.hasAltDown();
        tooltip.add(at++, LoconauticsLang.translate("tooltip.boiler_stats.hold",
                LoconauticsLang.translate("tooltip.boiler_stats.key")
                        .style(held ? ChatFormatting.WHITE : ChatFormatting.GRAY).component())
                .style(ChatFormatting.DARK_GRAY).component());
        if (!held) return;

        if (body) {
            int capacity = BoilerContributions.waterCapacityOf(block.defaultBlockState());
            tooltip.add(at++, LoconauticsLang.translate("tooltip.boiler_stats.capacity",
                            green(String.valueOf(capacity)), darkGreen("mB"))
                    .style(ChatFormatting.GRAY).component());
        }
        if (heat != null) {
            tooltip.add(at, LoconauticsLang.translate("tooltip.boiler_stats.heat",
                            green(LoconauticsLang.number(heat).string()))
                    .style(ChatFormatting.GRAY).component());
        }
    }

    private static int idLine(List<Component> tooltip, BlockItem item) {
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        for (int i = tooltip.size() - 1; i > 0; i--) {
            if (tooltip.get(i).getString().equals(id)) return i;
        }
        return tooltip.size();
    }

    private static Component green(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GREEN);
    }

    private static Component darkGreen(String text) {
        return Component.literal(text).withStyle(ChatFormatting.DARK_GREEN);
    }
}
