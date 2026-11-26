package com.lycoris.loconautics.content.controls.panel;

import com.lycoris.loconautics.content.combustion.SmallEngineBlock;
import com.lycoris.loconautics.content.controls.panel.target.RedstoneTargets;
import com.lycoris.loconautics.content.transmission.TransmissionBlock;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public abstract class LinkBoundBlockItem extends PanelBlockItem {

    protected LinkBoundBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    protected abstract DataComponentType<BlockPos> targetComponent();

    protected abstract String langPrefix();

    @Nullable
    public BlockPos boundLink(ItemStack stack) {
        return stack.get(targetComponent());
    }

    @Override
    protected void clearTarget(ItemStack stack) {
        stack.remove(targetComponent());
    }

    @Override
    protected String clearedKey() {
        return langPrefix() + ".cleared";
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);

        Player player = context.getPlayer();
        if (state.getBlock() instanceof SmallEngineBlock || state.getBlock() instanceof TransmissionBlock
                || RedstoneTargets.of(state) != null) {
            if (!level.isClientSide) {
                bind(context.getItemInHand(), player, state, pos);
            }
            return InteractionResult.SUCCESS;
        }

        if (!AllBlocks.REDSTONE_LINK.has(state)) return super.useOn(context);

        boolean receiver = state.getValue(RedstoneLinkBlock.RECEIVER);
        if (!level.isClientSide) {
            if (receiver) {
                bind(context.getItemInHand(), player, state, pos);
            } else if (player != null) {
                player.displayClientMessage(Component.translatable(langPrefix() + ".not_receiver"), true);
            }
        }
        return InteractionResult.SUCCESS;
    }

    private void bind(ItemStack stack, @Nullable Player player, BlockState state, BlockPos pos) {
        stack.set(targetComponent(), pos);
        announceBound(stack, player, state);
    }

    @Override
    @Nullable
    protected BlockPos boundTarget(ItemStack stack) {
        return boundLink(stack);
    }

    @Override
    public String getDescriptionId() {
        return getOrCreateDescriptionId();
    }

    @Override
    public void registerBlocks(Map<Block, Item> blockToItemMap, Item item) {
    }
}
