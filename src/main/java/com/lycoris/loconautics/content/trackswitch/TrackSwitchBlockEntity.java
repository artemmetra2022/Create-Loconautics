package com.lycoris.loconautics.content.trackswitch;

import com.lycoris.loconautics.common.railway.PointOverrides;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds one junction and which of its branches trains are sent down. The choice lives in {@link PointOverrides}
 * (that map is only in memory), so it is written again every time the stand loads.
 */
public class TrackSwitchBlockEntity extends BlockEntity {

    /** which stand holds each junction, so that two stands never fight over the same switch */
    private static final Map<String, GlobalPos> STANDS = new ConcurrentHashMap<>();

    private String junction = "";
    /** node id of the branch trains are sent to, empty while the stand lets Create choose */
    private String toward = "";
    private int branchNumber;
    private int branchCount;
    private boolean unloading;

    public int branchNumber() { return branchNumber; }
    public int branchCount() { return branchCount; }

    public TrackSwitchBlockEntity(BlockPos pos, BlockState state) {
        super(LoconauticsRegistries.TRACK_SWITCH_BE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            if (!junction.isEmpty() && !claim(junction)) {
                junction = "";
                toward = "";
                branchNumber = 0;
                branchCount = 0;
                setChanged();
            }
            apply();
            if (junction.isEmpty() || toward.isEmpty()) {
                // no block changes while the chunk is still loading, do it on the next server tick
                server.getServer().execute(() -> {
                    if (!isRemoved() && level != null && level.getBlockEntity(worldPosition) == this) {
                        show(null, null);
                    }
                });
            }
        }
    }

    @Override
    public void onChunkUnloaded() {
        // an unloading chunk keeps its stand locks, only a real removal lets go of the junction
        unloading = true;
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel && !unloading) {
            release();
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Junction", junction);
        tag.putString("Toward", toward);
        tag.putInt("BranchNumber", branchNumber);
        tag.putInt("BranchCount", branchCount);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        junction = tag.getString("Junction");
        toward = tag.getString("Toward");
        branchNumber = tag.getInt("BranchNumber");
        branchCount = tag.getInt("BranchCount");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    public boolean isBound() { return !junction.isEmpty(); }
    public boolean isForced() { return !toward.isEmpty(); }

    /** looks for the nearest junction and binds to it, dropping the old one */
    public void rebind(Player player) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        TrackSwitchJunctions.Junction found = TrackSwitchJunctions.nearest(server, worldPosition);
        if (found == null) {
            tell(player, "loconautics.track_switch.no_junction");
            return;
        }
        String previous = junction;
        if (!claim(found.id())) {
            tell(player, "loconautics.track_switch.occupied");
            return;
        }
        if (previous.equals(found.id())) {
            PointOverrides.clear(previous);
            PointOverrides.unmarkAuto(previous);
        } else {
            release();
        }
        junction = found.id();
        branchCount = found.branches().size();
        branchNumber = 0;
        toward = "";
        setChanged();
        apply();
        show(null, null);
        tell(player, "loconautics.track_switch.bound");
    }

    /** auto, then each branch in turn as a lock, then auto again */
    public void cycle(Player player) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (junction.isEmpty()) {
            rebind(player);
            return;
        }
        TrackSwitchJunctions.Junction current = TrackSwitchJunctions.find(junction);
        if (current == null) {
            release();
            junction = "";
            toward = "";
            setChanged();
            show(null, null);
            tell(player, "loconautics.track_switch.lost");
            return;
        }
        List<TrackSwitchJunctions.Branch> branches = current.branches();
        int index = -1;
        for (int i = 0; i < branches.size(); i++) {
            if (branches.get(i).id().equals(toward)) {
                index = i;
            }
        }
        index++;
        branchCount = branches.size();
        branchNumber = index >= branches.size() ? 0 : index + 1;
        if (index >= branches.size()) {
            PointOverrides.clear(junction);
            toward = "";
            setChanged();
            apply();
            show(null, null);
            tell(player, "loconautics.track_switch.free");
            return;
        }
        TrackSwitchJunctions.Branch branch = branches.get(index);
        toward = branch.id();
        setChanged();
        apply();
        show(current.at(), branch.at());
        if (player != null) {
            player.displayClientMessage(Component.translatable("loconautics.track_switch.set",
                    index + 1, branches.size(),
                    Math.round(branch.at().x), Math.round(branch.at().y), Math.round(branch.at().z)), true);
        }
    }

    /** arrow toward the held branch, or the auto fork when there is none */
    private void show(Vec3 junctionAt, Vec3 branchAt) {
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof TrackSwitchBlock)) {
            return;
        }
        BlockState shown;
        if (junctionAt == null || branchAt == null) {
            shown = state.setValue(TrackSwitchBlock.MODE, junction.isEmpty()
                    ? TrackSwitchBlock.Mode.UNBOUND : TrackSwitchBlock.Mode.AUTO);
        } else {
            double dx = branchAt.x - junctionAt.x;
            double dz = branchAt.z - junctionAt.z;
            Direction toward = Math.abs(dx) > Math.abs(dz)
                    ? (dx > 0 ? Direction.EAST : Direction.WEST)
                    : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
            shown = state.setValue(TrackSwitchBlock.MODE, TrackSwitchBlock.Mode.FORCED)
                    .setValue(TrackSwitchBlock.FACING, toward);
        }
        if (shown != state) {
            level.setBlock(worldPosition, shown, Block.UPDATE_CLIENTS);
        }
        if (shown == state) {
            level.sendBlockUpdated(worldPosition, state, shown, Block.UPDATE_CLIENTS);
        }
    }

    private void apply() {
        if (junction.isEmpty()) {
            return;
        }
        if (toward.isEmpty()) {
            PointOverrides.markAuto(junction);
        } else {
            PointOverrides.unmarkAuto(junction);
            PointOverrides.set(junction, toward);
        }
    }

    /** takes the junction for this stand unless another live stand already holds it */
    private boolean claim(String id) {
        if (!(level instanceof ServerLevel server)) {
            return true;
        }
        GlobalPos me = GlobalPos.of(server.dimension(), worldPosition);
        GlobalPos owner = STANDS.putIfAbsent(id, me);
        if (owner == null || owner.equals(me)) {
            return true;
        }
        ServerLevel ownerLevel = server.getServer().getLevel(owner.dimension());
        if (ownerLevel != null) {
            if (!ownerLevel.isLoaded(owner.pos())) {
                return false;
            }
            if (ownerLevel.getBlockEntity(owner.pos()) instanceof TrackSwitchBlockEntity other
                    && !other.isRemoved() && id.equals(other.junction)) {
                return false;
            }
        }
        STANDS.put(id, me);
        return true;
    }

    private void release() {
        if (!junction.isEmpty() && level != null) {
            STANDS.remove(junction, GlobalPos.of(level.dimension(), worldPosition));
        }
        if (!junction.isEmpty()) {
            PointOverrides.clear(junction);
            PointOverrides.unmarkAuto(junction);
        }
    }

    private static void tell(Player player, String key) {
        if (player != null) {
            player.displayClientMessage(Component.translatable(key), true);
        }
    }
}
