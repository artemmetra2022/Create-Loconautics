package com.lycoris.loconautics.content.controls.panellamp;

import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.controls.panel.PanelBlockItem;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class SignalLampBlockItem extends PanelBlockItem {

    public SignalLampBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockEntity source = level.getBlockEntity(pos);
        if (source == null) source = com.lycoris.loconautics.content.combustion.SmallEngineBlockEntity.resolve(level, pos);
        SignalLampMode mode = SignalLampMode.forSource(source);
        if (mode == null) return super.useOn(context);

        if (!level.isClientSide) {
            ItemStack stack = context.getItemInHand();
            stack.set(LoconauticsRegistries.SIGNAL_LAMP_TARGET.get(), pos);
            stack.set(LoconauticsRegistries.SIGNAL_LAMP_MODE.get(), mode.id());
            announceBound(stack, context.getPlayer(), level.getBlockState(pos));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public String getDescriptionId() {
        return getOrCreateDescriptionId();
    }

    @Override
    public void registerBlocks(Map<Block, Item> blockToItemMap, Item item) {
    }

    @Override
    @Nullable
    protected PanelPart partFor(ItemStack stack) {
        BlockPos target = boundTarget(stack);
        SignalLampMode mode = SignalLampMode.byId(stack.get(LoconauticsRegistries.SIGNAL_LAMP_MODE.get()));
        return target == null || mode == null
                ? null : new SignalLampPart(target, mode, SignalLampPart.DEFAULT_BODY, false);
    }

    public static ItemStack stackFor(SignalLampPart lamp) {
        ItemStack stack = new ItemStack(BoilerBlocks.SIGNAL_LAMP_ITEM.get());
        stack.set(LoconauticsRegistries.SIGNAL_LAMP_TARGET.get(), lamp.target());
        stack.set(LoconauticsRegistries.SIGNAL_LAMP_MODE.get(), lamp.mode().id());
        return stack;
    }

    @Override
    protected void clearTarget(ItemStack stack) {
        stack.remove(LoconauticsRegistries.SIGNAL_LAMP_TARGET.get());
        stack.remove(LoconauticsRegistries.SIGNAL_LAMP_MODE.get());
    }

    @Override
    protected String clearedKey() {
        return "gui.loconautics.signal_lamp.cleared";
    }

    @Override
    @Nullable
    protected BlockPos boundTarget(ItemStack stack) {
        return stack.get(LoconauticsRegistries.SIGNAL_LAMP_TARGET.get());
    }

    @Override
    protected void notifyCannotPlace(Player player) {
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "gui.loconautics.signal_lamp.bind_first"), true);
    }
}
