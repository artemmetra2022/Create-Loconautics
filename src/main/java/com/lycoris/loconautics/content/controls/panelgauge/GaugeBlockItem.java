package com.lycoris.loconautics.content.controls.panelgauge;

import com.lycoris.loconautics.content.controls.panel.PanelFrequencyMenu;
import com.lycoris.loconautics.content.controls.panel.PanelBlockItem;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

public class GaugeBlockItem extends PanelBlockItem {

    public GaugeBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Nullable
    public static BlockPos boundFirebox(ItemStack stack) {
        return stack.get(LoconauticsRegistries.LOCOMOTIVE_GAUGE_TARGET.get());
    }

    public static GaugeMetric boundMetric(ItemStack stack) {
        GaugeMetric metric = stack.get(LoconauticsRegistries.LOCOMOTIVE_GAUGE_METRIC.get());
        return metric == null ? GaugeMetric.TEMPERATURE : metric;
    }

    public static ItemStack stackFor(GaugeBlockEntity.Gauge gauge) {
        ItemStack stack = new ItemStack(
                com.lycoris.loconautics.content.boiler.BoilerBlocks.LOCOMOTIVE_GAUGE_ITEM.get());
        if (gauge != null && gauge.bound()) {
            stack.set(LoconauticsRegistries.LOCOMOTIVE_GAUGE_TARGET.get(), gauge.firebox());
            stack.set(LoconauticsRegistries.LOCOMOTIVE_GAUGE_METRIC.get(), gauge.metric());
        }
        return stack;
    }

    @Override
    protected void clearTarget(ItemStack stack) {
        stack.remove(LoconauticsRegistries.LOCOMOTIVE_GAUGE_TARGET.get());
    }

    @Override
    protected String clearedKey() {
        return "gui.loconautics.locomotive_gauge.cleared";
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        GaugeMetric.Group group = GaugeMetric.Group.of(level.getBlockEntity(pos));
        if (group != null) {
            if (!level.isClientSide) {
                ItemStack stack = context.getItemInHand();
                stack.set(LoconauticsRegistries.LOCOMOTIVE_GAUGE_TARGET.get(), pos);
                stack.set(LoconauticsRegistries.LOCOMOTIVE_GAUGE_METRIC.get(),
                        GaugeMetric.defaultFor(group));
                announceBound(stack, context.getPlayer(), level.getBlockState(pos));
            }
            return InteractionResult.SUCCESS;
        }

        return super.useOn(context);
    }

    @Override
    @Nullable
    protected PanelPart partFor(ItemStack stack) {
        BlockPos firebox = boundFirebox(stack);
        GaugeMetric metric = boundMetric(stack);
        int background = firebox == null ? GaugeBlockEntity.DEFAULT_BACKGROUND : metric.defaultBackground();
        return new GaugeBlockEntity.Gauge(firebox == null ? GaugeBlockEntity.Gauge.UNBOUND : firebox, metric, background,
                GaugeBlockEntity.DEFAULT_FRONT, GaugeBlockEntity.DEFAULT_NEEDLE_COLOR,
                GaugeBlockEntity.DEFAULT_OPTIONS);
    }

    @Override
    protected void onPartPlaced(Player player, BlockPos pos, PanelSlot slot, ItemStack stack) {
        PanelFrequencyMenu.open(player, pos, slot);
    }

    @Override
    @Nullable
    protected BlockPos boundTarget(ItemStack stack) {
        return boundFirebox(stack);
    }
}
