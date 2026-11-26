package com.lycoris.loconautics.content.dispatcher;

import com.lycoris.loconautics.network.packets.dispatcher.DispatcherSnapshotPacket;
import com.lycoris.loconautics.network.packets.dispatcher.DispatcherRequestPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import com.mojang.serialization.MapCodec;

/** A central, server-authoritative interface for monitoring the railway network. */
public class DispatcherBlock extends Block {

    public static final MapCodec<DispatcherBlock> CODEC = simpleCodec(DispatcherBlock::new);

    public DispatcherBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, DispatcherSnapshotPacket.of(pos,
                    DispatcherService.snapshot(serverPlayer.getServer())));
        }
        return InteractionResult.CONSUME;
    }
}
