package com.lycoris.loconautics.content.controls.panelcabsignal;

import com.lycoris.loconautics.content.controls.panel.PanelBlockItem;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class CabSignalBlockItem extends PanelBlockItem {

    public CabSignalBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    @Nullable
    protected PanelPart partFor(ItemStack stack) {
        return new CabSignalPart(CabSignalPart.DEFAULT_BODY, CabSignalPart.NONE);
    }

    @Override
    protected void clearTarget(ItemStack stack) {
    }

    @Override
    protected String clearedKey() {
        return "gui.loconautics.cab_signal.live";
    }

    @Override
    public void registerBlocks(Map<Block, Item> blockToItemMap, Item item) {
    }

    @Override
    public String getDescriptionId() {
        return getOrCreateDescriptionId();
    }
}
