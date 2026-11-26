package com.lycoris.loconautics.content.overhead;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

public class CatenaryPoleItem extends Item {

    private final int height;

    public CatenaryPoleItem(int height, Properties properties) {
        super(properties);
        if (height < 3 || height > 8) throw new IllegalArgumentException("Mast height must be 3..8 blocks");
        this.height = height;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPlaceContext placement = new BlockPlaceContext(context);
        BlockPos base = placement.getClickedPos();
        if (level.isClientSide) return InteractionResult.SUCCESS;

        for (int y = 0; y < height; y++) {
            BlockPos at = base.above(y);
            if (!level.hasChunkAt(at) || !level.getBlockState(at).canBeReplaced()) {
                return InteractionResult.FAIL;
            }
        }

        // Face the cantilever toward the player, who should be standing on the track side of the mast.
        Direction facing = context.getPlayer() == null
                ? Direction.NORTH : context.getPlayer().getDirection().getOpposite();
        for (int y = 0; y < height; y++) {
            CatenaryMastBlock.Part part = y == 0 ? CatenaryMastBlock.Part.BASE
                    : y == height - 1 ? CatenaryMastBlock.Part.HEAD : CatenaryMastBlock.Part.SHAFT;
            BlockState state = OverheadBlocks.MAST.get().defaultBlockState()
                    .setValue(CatenaryMastBlock.PART, part)
                    .setValue(CatenaryMastBlock.FACING, facing);
            if (!level.setBlock(base.above(y), state, 3)) {
                for (int rollback = 0; rollback < y; rollback++) {
                    level.removeBlock(base.above(rollback), false);
                }
                return InteractionResult.FAIL;
            }
        }

        SoundType sound = OverheadBlocks.MAST.get().defaultBlockState().getSoundType();
        level.playSound(null, base, sound.getPlaceSound(), SoundSource.BLOCKS,
                (sound.getVolume() + 1f) / 2f, sound.getPitch() * 0.9f);
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    public int height() { return height; }
}
