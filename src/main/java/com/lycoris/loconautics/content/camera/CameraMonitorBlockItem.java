package com.lycoris.loconautics.content.camera;

import com.lycoris.loconautics.content.controls.panel.PanelBlockItem;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public final class CameraMonitorBlockItem extends PanelBlockItem {

    public CameraMonitorBlockItem(Block panelHost, Item.Properties properties) {
        super(panelHost, properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        InteractionResult binding = bindCamera(context);
        if (binding != InteractionResult.PASS) return binding;
        return super.onItemUseFirst(stack, context);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        InteractionResult binding = bindCamera(context);
        return binding != InteractionResult.PASS ? binding : super.useOn(context);
    }

    /**
     * Bind before the clicked block's normal interaction runs. Some block
     * interactions consume the click, which otherwise prevents BlockItem.useOn
     * from being reached on all client/server interaction paths.
     */
    private InteractionResult bindCamera(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof CameraBlockEntity)) return InteractionResult.PASS;

        if (!level.isClientSide) {
            GlobalPos camera = GlobalPos.of(level.dimension(), pos);
            ItemStack stack = context.getItemInHand();
            CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            CameraMonitorPart.writeToTag(data, camera);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            announceBound(stack, context.getPlayer(), level.getBlockState(pos));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @Nullable
    protected PanelPart partFor(ItemStack stack) {
        GlobalPos camera = CameraMonitorPart.fromTag(
                stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
        return camera == null ? null : new CameraMonitorPart(camera);
    }

    public static ItemStack stackFor(GlobalPos camera) {
        ItemStack stack = new ItemStack(CameraBlocks.CAMERA_MONITOR_ITEM.get());
        CompoundTag data = new CompoundTag();
        CameraMonitorPart.writeToTag(data, camera);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }

    @Override
    protected void clearTarget(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.remove("CameraDimension");
        data.remove("CameraPosition");
        if (data.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }

    @Override
    protected String clearedKey() {
        return "gui.loconautics.camera_monitor.unbound";
    }

    @Override
    @Nullable
    protected BlockPos boundTarget(ItemStack stack) {
        GlobalPos camera = CameraMonitorPart.fromTag(
                stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
        return camera == null ? null : camera.pos();
    }

    @Override
    protected void notifyCannotPlace(net.minecraft.world.entity.player.Player player) {
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "gui.loconautics.camera_monitor.bind_first"), true);
    }

    @Override
    public void registerBlocks(Map<Block, Item> blockToItemMap, Item item) {
    }

    @Override
    public String getDescriptionId() {
        return getOrCreateDescriptionId();
    }
}
