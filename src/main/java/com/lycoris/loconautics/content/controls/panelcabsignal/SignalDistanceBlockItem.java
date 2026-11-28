package com.lycoris.loconautics.content.controls.panelcabsignal;

import com.lycoris.loconautics.content.controls.panel.PanelBlockItem;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class SignalDistanceBlockItem extends PanelBlockItem {

    public SignalDistanceBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    @Nullable
    protected PanelPart partFor(ItemStack stack) {
        return new SignalDistancePart(SignalDistancePart.DEFAULT_BODY, CabSignalPart.NONE, -1);
    }

    @Override
    protected void clearTarget(ItemStack stack) {
    }

    @Override
    protected String clearedKey() {
        return "gui.loconautics.signal_distance.live";
    }

    @Override
    public void registerBlocks(Map<Block, Item> blockToItemMap, Item item) {
    }

    @Override
    public String getDescriptionId() {
        return getOrCreateDescriptionId();
    }
}
