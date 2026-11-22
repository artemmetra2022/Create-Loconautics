package com.lycoris.loconautics.content.controls.panelvalve;

import com.lycoris.loconautics.content.boiler.safetyvalve.SafetyValveBlock;
import com.lycoris.loconautics.content.combustion.SmallEngineBlock;
import com.lycoris.loconautics.content.boiler.steamtakeoff.SteamTakeoffBlock;
import com.lycoris.loconautics.content.controls.panel.PanelBlockItem;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.core.BlockPos;
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

public class ValveBlockItem extends PanelBlockItem {

    public ValveBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Nullable
    public static BlockPos boundValve(ItemStack stack) {
        return stack.get(LoconauticsRegistries.VALVE_TARGET.get());
    }

    @Override
    protected void clearTarget(ItemStack stack) {
        stack.remove(LoconauticsRegistries.VALVE_TARGET.get());
    }

    @Override
    protected String clearedKey() {
        return "gui.loconautics.valve.cleared";
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof SafetyValveBlock || state.getBlock() instanceof SteamTakeoffBlock
                || state.getBlock() instanceof SmallEngineBlock) {
            if (!level.isClientSide) {
                context.getItemInHand().set(LoconauticsRegistries.VALVE_TARGET.get(), pos);
                announceBound(context.getItemInHand(), context.getPlayer(), state);
            }
            return InteractionResult.SUCCESS;
        }

        return super.useOn(context);
    }

    @Override
    @Nullable
    protected PanelPart partFor(ItemStack stack) {
        BlockPos valve = boundValve(stack);
        return valve == null ? null : new ValvePart(0f, valve, ValvePart.DEFAULT_WHEEL_COLOR);
    }

    @Override
    protected void notifyCannotPlace(Player player) {
        player.displayClientMessage(Component.translatable("gui.loconautics.valve.unbound"), true);
    }

    @Override
    @Nullable
    protected BlockPos boundTarget(ItemStack stack) {
        return boundValve(stack);
    }

    @Override
    public String getDescriptionId() {
        return getOrCreateDescriptionId();
    }

    @Override
    public void registerBlocks(Map<Block, Item> blockToItemMap, Item item) {
    }
}
