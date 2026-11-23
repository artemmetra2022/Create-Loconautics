package com.lycoris.loconautics.content.overhead;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

public class PantographBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    private static final int CHECK_INTERVAL = 4;

    private record Contact(boolean found, int reach) {
        private static final Contact NONE = new Contact(false, 0);
    }

    public PantographBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public boolean hasContact() {
        return getBlockState().getValue(PantographBlock.CONTACT);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if (Math.floorMod(level.getGameTime() + worldPosition.asLong(), CHECK_INTERVAL) != 0) return;

        Contact contact = findContact();
        BlockState state = getBlockState();
        if (state.getValue(PantographBlock.CONTACT) == contact.found()
                && state.getValue(PantographBlock.REACH) == contact.reach()) return;

        BlockState updated = state.setValue(PantographBlock.CONTACT, contact.found())
                .setValue(PantographBlock.REACH, contact.reach());
        level.setBlock(worldPosition, updated, 3);
        level.updateNeighborsAt(worldPosition, updated.getBlock());
    }

    private Contact findContact() {
        SubLevel containing = Sable.HELPER.getContaining(this);
        if (!(containing instanceof ServerSubLevel sub) || sub.isRemoved()) return Contact.NONE;

        ServerLevel world = sub.getLevel();
        Vec3 projected = Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(worldPosition));
        if (projected == null) return Contact.NONE;

        BlockPos center = BlockPos.containing(projected.x, projected.y, projected.z);
        double bestScore = Double.MAX_VALUE;
        Contact best = Contact.NONE;

        for (int dy = 2; dy <= 6; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos wirePos = center.offset(dx, dy, dz);
                    if (!world.hasChunkAt(wirePos)) continue;
                    BlockState wire = world.getBlockState(wirePos);
                    if (!wire.is(OverheadBlocks.WIRE.get())) continue;

                    Direction.Axis axis = wire.getValue(RotatedPillarBlock.AXIS);
                    double along = axis == Direction.Axis.X
                            ? Math.abs(projected.x - (wirePos.getX() + 0.5))
                            : Math.abs(projected.z - (wirePos.getZ() + 0.5));
                    double across = axis == Direction.Axis.X
                            ? Math.abs(projected.z - (wirePos.getZ() + 0.5))
                            : Math.abs(projected.x - (wirePos.getX() + 0.5));
                    if (along > 0.62 || across > 0.42) continue;

                    // One wire cell sits above the train; the three available spring stages are 3/4/5 blocks.
                    double gap = wirePos.getY() + 0.94 - projected.y;
                    int blocks = (int) Math.round(gap);
                    if (blocks < 3 || blocks > 5 || Math.abs(gap - blocks) > 0.68) continue;

                    double score = Math.abs(gap - blocks) + along * 0.25 + across;
                    if (score < bestScore) {
                        bestScore = score;
                        best = new Contact(true, blocks - 3);
                    }
                }
            }
        }
        return best;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.translatable(hasContact()
                        ? "gui.goggles.pantograph_connected" : "gui.goggles.pantograph_disconnected")
                .withStyle(hasContact() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        return true;
    }
}
