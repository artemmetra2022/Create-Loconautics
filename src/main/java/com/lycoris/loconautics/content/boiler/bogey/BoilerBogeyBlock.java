package com.lycoris.loconautics.content.boiler.bogey;

import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.frequency.FrequencyBindUtil;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import com.simibubi.create.content.trains.track.TrackMaterial;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class BoilerBogeyBlock extends AbstractBogeyBlock<BoilerBogeyBlockEntity> {

    public BoilerBogeyBlock(Properties properties) {
        super(properties, BogeySizes.SMALL);
        registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false));
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (!level.isClientSide) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof BoilerBogeyBlockEntity bogey) bogey.clientTick();
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState placed = defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getAxis());
        return withWater(placed, context);
    }

    @Override
    public BlockState getRotatedBlockState(BlockState state, Direction targetedFace) {
        return state.cycle(AXIS);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                            BlockHitResult hit) {
        if (player.isShiftKeyDown() || !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
            return super.useWithoutItem(state, level, pos, player, hit);
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof BoilerBogeyBlockEntity bogey) {
            player.openMenu(bogey, bogey::sendToMenu);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(AllItems.WRENCH.get()) && player.isShiftKeyDown()) {
            // with no wheelset left the click goes on to the normal wrench, which takes the bogey out
            if (!(level.getBlockEntity(pos) instanceof BoilerBogeyBlockEntity bogey) || bogey.wheelPairs() == 0) {
                return super.useItemOn(stack, state, level, pos, player, hand, hit);
            }
            if (level.isClientSide) return ItemInteractionResult.SUCCESS;
            if (bogey.removeWheelPair()) {
                if (!player.isCreative()) {
                    player.getInventory().placeItemBackInInventory(new ItemStack(LoconauticsRegistries.TRAIN_WHEEL.get()));
                }
                level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.5f, 0.7f);
            }
            return ItemInteractionResult.CONSUME;
        }
        if (stack.is(AllItems.WRENCH.get()) && hit.getDirection().getAxis().isHorizontal()) {
            if (level.isClientSide) return ItemInteractionResult.SUCCESS;
            if (level.getBlockEntity(pos) instanceof BoilerBogeyBlockEntity bogey) {
                bogey.flipPistons();
                level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.4f, 0.8f);
            }
            return ItemInteractionResult.CONSUME;
        }
        if (!stack.is(LoconauticsRegistries.TRAIN_WHEEL.get())) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof BoilerBogeyBlockEntity bogey)) return ItemInteractionResult.FAIL;
        if (!bogey.addWheelPair()) {
            player.displayClientMessage(Component.translatable("loconautics.boiler_bogey.wheels_full"), true);
            return ItemInteractionResult.CONSUME;
        }
        if (!player.isCreative()) stack.shrink(1);
        level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.5f, 1.3f);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.isCreative()
                && level.getBlockEntity(pos) instanceof BoilerBogeyBlockEntity bogey) {
            int extra = bogey.wheelPairs();
            if (extra > 0) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        new ItemStack(LoconauticsRegistries.TRAIN_WHEEL.get(), extra));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !FrequencyBindUtil.isBound(stack)
                || !(level.getBlockEntity(pos) instanceof BoilerBogeyBlockEntity bogey)) {
            return;
        }
        var registries = level.registryAccess();
        ItemStack[] speed = FrequencyBindUtil.read(stack, registries, "Speed");
        if (speed != null) bogey.setSpeedFrequency(speed[0], speed[1]);
        ItemStack[] direction = FrequencyBindUtil.read(stack, registries, "Direction");
        if (direction != null) bogey.setDirectionFrequency(direction[0], direction[1]);
        ItemStack[] left = FrequencyBindUtil.read(stack, registries, "Left");
        if (left != null) bogey.setLeftFrequency(left[0], left[1]);
        ItemStack[] right = FrequencyBindUtil.read(stack, registries, "Right");
        if (right != null) bogey.setRightFrequency(right[0], right[1]);
    }

    @Override
    public TrackMaterial.TrackType getTrackType(BogeyStyle style) {
        return TrackMaterial.TrackType.STANDARD;
    }

    @Override
    public double getWheelPointSpacing() {
        return 2;
    }

    @Override
    public double getWheelRadius() {
        // the flat of the wheel rim is 14 pixels from the axle, the spin angle is distance over this
        return 14 / 16d;
    }

    @Override
    public Vec3 getConnectorAnchorOffset() {
        return new Vec3(0, 7 / 32f, 1);
    }

    @Override
    public BogeyStyle getDefaultStyle() {
        return BoilerBogeyStyles.BOILER;
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos,
                                       Player player) {
        return new ItemStack(BoilerBlocks.BOILER_BOGEY_ITEM.get());
    }

    @Override
    public Class<BoilerBogeyBlockEntity> getBlockEntityClass() {
        return BoilerBogeyBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BoilerBogeyBlockEntity> getBlockEntityType() {
        return BoilerBlocks.BOILER_BOGEY_BE.get();
    }
}
