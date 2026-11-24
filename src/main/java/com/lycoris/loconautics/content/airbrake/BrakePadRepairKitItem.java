package com.lycoris.loconautics.content.airbrake;

import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public final class BrakePadRepairKitItem extends Item {
    public BrakePadRepairKitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity blockEntity;
        SubLevel subLevel = Sable.HELPER.getContaining(level, Vec3.atCenterOf(pos));
        // Resolve the block entity through Sable's loaded plot chunks, not the parent Level lookup.
        if (subLevel != null) {
            blockEntity = SubLevelBlocks.findAll(subLevel, TripleValveBlockEntity.class).stream()
                    .filter(candidate -> candidate.getBlockPos().equals(pos)).findFirst().orElse(null);
            SableTrain train = SableTrainRegistry.bySubLevel(subLevel.getUniqueId());
            if (train != null && Math.abs(train.speed()) > 0.05) {
                if (player != null) {
                    player.displayClientMessage(Component.translatable("loconautics.brake_pads.repair_moving"), true);
                }
                return InteractionResult.CONSUME;
            }
        } else {
            blockEntity = level.getBlockEntity(pos);
        }

        if (!(blockEntity instanceof TripleValveBlockEntity valve)) {
            return InteractionResult.PASS;
        }
        valve.repairBrakePads();
        if (player != null && !player.isCreative()) {
            context.getItemInHand().shrink(1);
        }
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.65f, 1.15f);
        if (player != null) {
            player.displayClientMessage(Component.translatable("loconautics.brake_pads.repaired"), true);
        }
        return InteractionResult.SUCCESS;
    }
}
