package com.lycoris.loconautics.content.sand;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public class SandStorageBlock extends Block implements IBE<SandStorageBlockEntity> {

    public static final IntegerProperty FILL_LEVEL = IntegerProperty.create("fill_level", 0, 4);

    public SandStorageBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FILL_LEVEL, 0));
    }

    public static int fillLevelFor(int amount) {
        return Mth.clamp(Mth.ceil(amount * 4f / SandStorageBlockEntity.CAPACITY), 0, 4);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILL_LEVEL);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.SAND) && !stack.is(Items.RED_SAND)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!(level.getBlockEntity(pos) instanceof SandStorageBlockEntity storage)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (storage.getSand() >= SandStorageBlockEntity.CAPACITY) {
            return ItemInteractionResult.FAIL;
        }

        if (!level.isClientSide) {
            storage.addSand(1);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            float pitch = 0.9f + level.random.nextFloat() * 0.2f;
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.SAND_PLACE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.55f, pitch);
            storage.notifyUpdate();
            level.updateNeighbourForOutputSignal(pos, this);
        }

        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public Class<SandStorageBlockEntity> getBlockEntityClass() {
        return SandStorageBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SandStorageBlockEntity> getBlockEntityType() {
        return SandStorageBlocks.SAND_STORAGE_BE.get();
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SandStorageBlockEntity storage) {
            return Mth.floor(storage.getSand() * 15f / SandStorageBlockEntity.CAPACITY);
        }
        return 0;
    }
}
