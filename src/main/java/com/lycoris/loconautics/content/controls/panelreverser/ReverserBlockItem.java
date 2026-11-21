package com.lycoris.loconautics.content.controls.panelreverser;

import com.lycoris.loconautics.content.controls.panel.LinkBoundBlockItem;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class ReverserBlockItem extends LinkBoundBlockItem {

    public ReverserBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected DataComponentType<BlockPos> targetComponent() {
        return LoconauticsRegistries.REVERSER_TARGET.get();
    }

    @Override
    protected String langPrefix() {
        return "gui.loconautics.gauge_reverser";
    }

    @Override
    protected PanelPart partFor(ItemStack stack) {
        return new ReverserPart(ReverserPart.NEUTRAL, boundLink(stack), ReverserPart.DEFAULT_BODY,
                ReverserPart.DEFAULT_KNOB_COLOR, ReverserPart.DEFAULT_STYLE, ReverserPart.DEFAULT_OPTIONS);
    }
}
