package com.lycoris.loconautics.content.retardertrack;

import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackMaterial;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;

/**
 * A Create track that, while it gets a redstone signal, brakes a train down to a chosen speed. It does not
 * stop the train: a train that is already slower than the limit is left alone. The limit is chosen with a
 * wrench: click the top of the rail for the next limit, click a side for the previous one.
 * <p>
 * It is a normal Create track block (same shapes, same graph, same bezier curves) that only differs by a block
 * state, so it can be swapped for a plain track with the variant picker. On curves only the end blocks of the
 * curve carry the block, the middle of a bezier is not a block, so {@link RetarderTracks} also brakes along the
 * whole connection that leaves a powered retarder block.
 */
public class RetarderTrackBlock extends TrackBlock {

    /** limits in km/h in the order the wrench cycles through them, the index is the LIMIT state */
    public static final int[] LIMITS = {5, 10, 15, 20, 25, 30, 35, 40, 50, 60, 70, 80, 100, 120, 140, 160};
    public static final int DEFAULT_INDEX = 5; // 30 km/h

    public static final IntegerProperty LIMIT = IntegerProperty.create("limit", 0, LIMITS.length - 1);

    public RetarderTrackBlock(Properties properties) {
        super(properties, TrackMaterial.ANDESITE);
        registerDefaultState(defaultBlockState().setValue(LIMIT, DEFAULT_INDEX));
    }

    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIMIT);
    }

    /** the speed this rail brakes down to, in blocks per second */
    public static double limitBlocksPerSecond(BlockState state) {
        return LIMITS[state.getValue(LIMIT)] / 3.6;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos();
        int step = context.getClickedFace() == Direction.UP ? 1 : -1;
        int next = Math.floorMod(state.getValue(LIMIT) + step, LIMITS.length);
        level.setBlock(pos, state.setValue(LIMIT, next), Block.UPDATE_CLIENTS);

        Player player = context.getPlayer();
        if (player != null) {
            player.displayClientMessage(
                    Component.translatable("loconautics.gui.retarder_track.limit", LIMITS[next]), true);
        }
        level.playSound(null, pos, SoundEvents.COPPER_PLACE, SoundSource.BLOCKS, 0.7f, 0.8f + next * 0.03f);
        return InteractionResult.SUCCESS;
    }

    /** Create's track block entity only accepts the blocks it was registered with, so ours has to be added */
    public static void onAddBlockEntityBlocks(BlockEntityTypeAddBlocksEvent event) {
        event.modify(AllBlockEntityTypes.TRACK.get(), LoconauticsRegistries.RETARDER_TRACK.get());
    }
}
