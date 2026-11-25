package com.lycoris.loconautics.common.sable;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.LinkHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import dev.ryanhcode.sable.util.SableNBTUtils;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class SableTeleportation {

    // a dimension change removes the body then adds it back, and watchers drop it in between
    private static final Set<UUID> IN_FLIGHT = new HashSet<>();

    private static final List<String> ABSOLUTE_POS_KEYS = List.of("Source", "Controller", "LastKnownPos", "EnginePos",
            "Breaking", "MirrorParent");

    private SableTeleportation() {
    }

    public record PlotSlot(int x, int z) {}

    public record Result(@Nullable ServerSubLevel moved, @Nullable String failure, boolean lost,
                         Vector3dc plotShift) {
        public Result(@Nullable ServerSubLevel moved, @Nullable String failure) {
            this(moved, failure, false, new Vector3d());
        }

        public boolean ok() {
            return failure == null;
        }
    }

    public record Preflight(@Nullable PlotSlot slot, @Nullable String refusal) {
        public boolean ok() {
            return refusal == null;
        }
    }

    public static boolean isInFlight(UUID subLevelId) {
        return IN_FLIGHT.contains(subLevelId);
    }

    // a pose write on its own never reaches the physics body
    public static void writePose(ServerLevel level, ServerSubLevel sub, Vector3dc position, Quaterniondc orient) {
        Pose3d pose = sub.logicalPose();
        pose.position().set(position);
        pose.orientation().set(orient);
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null) {
            PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
            pipeline.teleport(sub, pose.position(), pose.orientation());
            pipeline.resetVelocity(sub);
        }
        sub.updateLastPose();
    }

    @Nullable
    public static PlotSlot plotSlot(ServerSubLevel sub) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(sub.getLevel());
        if (container == null) {
            return null;
        }
        return new PlotSlot(sub.getPlot().plotPos.x - container.getOrigin().x,
                sub.getPlot().plotPos.z - container.getOrigin().y);
    }

    // a pose y is the center of mass, so a commanded y needs this added to keep the bottom of the body on it
    // a pose position is the bodys center of mass, so a commanded y buries the bottom half of it unless the
    public static double baseOffsetY(ServerSubLevel sub) {
        return sub.logicalPose().position().y() - sub.boundingBox().minY();
    }

    // fullyload takes the slot the tag names, so that slot has to be free
    // block entity tags hold absolute coordinates and load never rebases them
    public static boolean plotSlotFree(ServerLevel destLevel, PlotSlot slot) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(destLevel);
        return container != null && !occupied(container, slot.x(), slot.z());
    }

    // the occupancy bits cover unloaded bodies too, fullload throws when a live body took a parked ones plot
    private static boolean occupied(ServerSubLevelContainer container, int x, int z) {
        return container.getSubLevel(x, z) != null || container.getOccupancy().get(container.getIndex(x, z));
    }

    @Nullable
    public static PlotSlot firstFreeSlot(ServerLevel destLevel, Set<PlotSlot> claimed) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(destLevel);
        if (container == null) {
            return null;
        }
        int side = 1 << container.getLogSideLength();
        for (int x = 0; x < side; x++) {
            for (int z = 0; z < side; z++) {
                PlotSlot slot = new PlotSlot(x, z);
                if (!occupied(container, x, z) && !claimed.contains(slot)) {
                    return slot;
                }
            }
        }
        return null;
    }

    // block entity tags hold absolute coordinates and load never rebases them, so a moved slot shifts them
    @Nullable
    public static String repot(CompoundTag tag, ServerLevel srcLevel, ServerLevel destLevel, PlotSlot from,
                               PlotSlot to) {
        ServerSubLevelContainer srcContainer = SubLevelContainer.getContainer(srcLevel);
        ServerSubLevelContainer destContainer = SubLevelContainer.getContainer(destLevel);
        if (srcContainer == null || destContainer == null) {
            return "one of the two levels has no sub level container";
        }
        return repotPlot(tag, srcContainer, destContainer, from, to);
    }

    // the pose rotation point lives in plot space, so a body moved to another plot slot keeps pointing at the old slot and its world bounds land thousands of blocks away until sable next refreshes the center of mass
    public static Vector3d plotShift(ServerLevel srcLevel, ServerLevel destLevel, PlotSlot from, PlotSlot to) {
        ServerSubLevelContainer srcContainer = SubLevelContainer.getContainer(srcLevel);
        ServerSubLevelContainer destContainer = SubLevelContainer.getContainer(destLevel);
        if (srcContainer == null || destContainer == null) {
            return new Vector3d();
        }
        int logBlockSize = destContainer.getLogPlotSize() + 4;
        double dx = (double) (((long) (to.x() + destContainer.getOrigin().x) - (from.x() + srcContainer.getOrigin().x))
                << logBlockSize);
        double dz = (double) (((long) (to.z() + destContainer.getOrigin().y) - (from.z() + srcContainer.getOrigin().y))
                << logBlockSize);
        return new Vector3d(dx, 0.0, dz);
    }

    @Nullable
    private static String gridMismatch(ServerSubLevelContainer srcContainer, ServerSubLevelContainer destContainer) {
        return srcContainer.getLogPlotSize() == destContainer.getLogPlotSize() ? null
                : "the two dimensions lay their plot grids out at different sizes";
    }

    @Nullable
    private static String repotPlot(CompoundTag tag, ServerSubLevelContainer srcContainer,
                                    ServerSubLevelContainer destContainer, PlotSlot from, PlotSlot to) {
        String mismatch = gridMismatch(srcContainer, destContainer);
        if (mismatch != null) {
            return mismatch;
        }
        CompoundTag plot = tag.getCompound("plot");
        plot.putInt("plot_x", to.x());
        plot.putInt("plot_z", to.z());
        int fromX = from.x() + srcContainer.getOrigin().x;
        int fromZ = from.z() + srcContainer.getOrigin().y;
        int toX = to.x() + destContainer.getOrigin().x;
        int toZ = to.z() + destContainer.getOrigin().y;
        if (fromX == toX && fromZ == toZ) {
            return null;
        }
        int logBlockSize = destContainer.getLogPlotSize() + 4;
        int dx = (toX - fromX) << logBlockSize;
        int dz = (toZ - fromZ) << logBlockSize;
        CompoundTag chunks = plot.getCompound("chunks");
        for (String chunkKey : chunks.getAllKeys()) {
            ListTag blockEntities = chunks.getCompound(chunkKey).getList("block_entities", Tag.TAG_COMPOUND);
            for (int i = 0; i < blockEntities.size(); i++) {
                CompoundTag be = blockEntities.getCompound(i);
                be.putInt("x", be.getInt("x") + dx);
                be.putInt("z", be.getInt("z") + dz);
                shiftAbsolutePositions(be, dx, dz);
            }
        }
        return null;
    }

    // create saves the kinetic source, multiblock controllers, engines and pulley links as absolute positions
    private static void shiftAbsolutePositions(CompoundTag be, int dx, int dz) {
        for (String key : ABSOLUTE_POS_KEYS) {
            shiftPos(be, key, dx, dz);
        }
        ListTag mirrorChildren = be.getList("MirrorChildren", Tag.TAG_COMPOUND);
        for (int i = 0; i < mirrorChildren.size(); i++) {
            shiftPos(mirrorChildren.getCompound(i), "Pos", dx, dz);
        }
        if (be.contains("Network", Tag.TAG_COMPOUND)) {
            CompoundTag network = be.getCompound("Network");
            network.putLong("Id", BlockPos.of(network.getLong("Id")).offset(dx, 0, dz).asLong());
        }
    }

    private static void shiftPos(CompoundTag tag, String key, int dx, int dz) {
        if (!tag.contains(key, Tag.TAG_INT_ARRAY)) {
            return;
        }
        int[] pos = tag.getIntArray(key);
        if (pos.length == 3) {
            pos[0] += dx;
            pos[2] += dz;
            tag.putIntArray(key, pos);
        }
    }

    // every refusal teleport can give before it removes the body, so a caller can check a whole rake first
    public static Preflight preflight(ServerSubLevel sub, ServerLevel destLevel, @Nullable PlotSlot targetSlot) {
        ServerLevel srcLevel = sub.getLevel();
        if (sub.isRemoved()) {
            return new Preflight(null, "body " + sub.getUniqueId() + " is not live");
        }
        ServerSubLevelContainer srcContainer = SubLevelContainer.getContainer(srcLevel);
        ServerSubLevelContainer destContainer = SubLevelContainer.getContainer(destLevel);
        if (srcContainer == null || destContainer == null) {
            return new Preflight(null, "one of the two levels has no sub level container");
        }
        if (destLevel == srcLevel) {
            return new Preflight(null, null);
        }
        PlotSlot fromSlot = plotSlot(sub);
        if (fromSlot == null) {
            return new Preflight(null, "body " + sub.getUniqueId() + " has no plot slot");
        }
        if (targetSlot == null) {
            targetSlot = plotSlotFree(destLevel, fromSlot) ? fromSlot : firstFreeSlot(destLevel, Set.of());
        }
        if (targetSlot == null) {
            return new Preflight(null, "no free plot left in " + destLevel.dimension().location());
        }
        if (!plotSlotFree(destLevel, targetSlot)) {
            return new Preflight(null, "plot " + targetSlot.x() + ", " + targetSlot.z() + " is taken in "
                    + destLevel.dimension().location());
        }
        String mismatch = gridMismatch(srcContainer, destContainer);
        if (mismatch != null) {
            return new Preflight(null, mismatch);
        }
        int delta = srcLevel.getMinSection() - destLevel.getMinSection();
        if (delta != 0) {
            for (PlotChunkHolder holder : sub.getPlot().getLoadedChunks()) {
                LevelChunk chunk = holder.getChunk();
                for (int idx = 0; idx < chunk.getSectionsCount(); idx++) {
                    if (!chunk.getSection(idx).hasOnlyAir() && !fitsHeight(idx + delta, destLevel)) {
                        return new Preflight(null, tooTall(destLevel));
                    }
                }
            }
        }
        return new Preflight(targetSlot, null);
    }

    public static Result teleport(ServerSubLevel sub, ServerLevel destLevel, Vector3dc position,
                                  Quaterniondc orient) {
        return teleport(sub, destLevel, position, orient, null);
    }

    public static Result teleport(ServerSubLevel sub, ServerLevel destLevel, Vector3dc position,
                                  Quaterniondc orient, @Nullable PlotSlot targetSlot) {
        Preflight checked = preflight(sub, destLevel, targetSlot);
        if (!checked.ok()) {
            return new Result(null, checked.refusal());
        }
        ServerLevel srcLevel = sub.getLevel();
        ServerSubLevelContainer srcContainer = SubLevelContainer.getContainer(srcLevel);
        ServerSubLevelContainer destContainer = SubLevelContainer.getContainer(destLevel);
        PlotSlot fromSlot = plotSlot(sub);
        targetSlot = checked.slot();

        destLevel.getChunk(BlockPos.containing(position.x(), position.y(), position.z()));

        UUID subLevelId = sub.getUniqueId();
        boolean staffLocked = PhysicsStaffServerHandler.get(srcLevel).isLocked(sub);
        SubLevelPassengers.Snapshot riders = SubLevelPassengers.capture(srcLevel, sub);
        List<SubLevelGlue.Strip> glue = SubLevelGlue.take(sub);
        SubLevelData data = SubLevelSerializer.toData(sub, List.of());
        CompoundTag tag = data.fullTag();
        CompoundTag keepPose = tag.getCompound("pose").copy();
        CompoundTag keepPlot = tag.getCompound("plot").copy();

        String tooTall = rebaseSections(tag, srcLevel, destLevel);
        if (tooTall != null) {
            SubLevelGlue.put(sub, glue);
            return new Result(null, tooTall);
        }

        if (targetSlot != null) {
            String badPlot = repotPlot(tag, srcContainer, destContainer, fromSlot, targetSlot);
            if (badPlot != null) {
                SubLevelGlue.put(sub, glue);
                return new Result(null, badPlot);
            }
        }

        Pose3d target = new Pose3d(sub.logicalPose());
        target.position().set(position);
        target.orientation().set(orient);
        Vector3d plotDelta = targetSlot == null ? new Vector3d() : plotShift(srcLevel, destLevel, fromSlot, targetSlot);
        target.rotationPoint().add(plotDelta);
        tag.put("pose", SableNBTUtils.writePose3d(target));
        tag.remove("linear_velocity");
        tag.remove("angular_velocity");

        ServerSubLevel moved;
        IN_FLIGHT.add(subLevelId);
        try {
            List<BlockEntity> oldBlockEntities = SubLevelBlocks.blockEntities(sub);
            for (BlockEntity be : oldBlockEntities) {
                if (be instanceof LinkHolder holder) {
                    holder.releaseLinks();
                }
            }
            srcContainer.removeSubLevel(sub, SubLevelRemovalReason.REMOVED);
            // the reload reuses plot positions, and ccs tick scheduler throws on a stale unremoved monitor
            for (BlockEntity be : oldBlockEntities) {
                if (!be.isRemoved()) {
                    be.setRemoved();
                }
            }
            moved = SubLevelSerializer.fullyLoad(destLevel, data);
            if (moved == null) {
                // the blocks in the snapshot were never touched, so the body can still go back where it came from
                tag.put("pose", keepPose);
                tag.put("plot", keepPlot);
                moved = SubLevelSerializer.fullyLoad(srcLevel, data);
                if (moved == null) {
                    LoconauticsConstants.LOGGER.error("[teleport] lost body {}, it loaded into neither {} nor {}",
                            subLevelId, destLevel.dimension().location(), srcLevel.dimension().location());
                    return new Result(null, "body " + subLevelId + " would not load into "
                            + destLevel.dimension().location() + " and could not be put back", true, new Vector3d());
                }
                SubLevelGlue.put(moved, glue);
                SubLevelPassengers.restore(srcLevel, moved, riders);
                relockStaff(srcLevel, subLevelId, staffLocked);
                return new Result(null, "body " + subLevelId + " would not load into "
                        + destLevel.dimension().location() + ", it is back in "
                        + srcLevel.dimension().location());
            }
        } finally {
            IN_FLIGHT.remove(subLevelId);
        }

        LoconauticsConstants.LOGGER.debug("[twinload] teleport body={} slot={},{} to {},{} pose={},{} rotationPoint={},{} "
                        + "worldBounds x {}..{} z {}..{}", subLevelId, fromSlot == null ? -1 : fromSlot.x(),
                fromSlot == null ? -1 : fromSlot.z(), targetSlot == null ? -1 : targetSlot.x(),
                targetSlot == null ? -1 : targetSlot.z(), (int) moved.logicalPose().position().x(),
                (int) moved.logicalPose().position().z(), (int) moved.logicalPose().rotationPoint().x(),
                (int) moved.logicalPose().rotationPoint().z(), (int) moved.boundingBox().minX(),
                (int) moved.boundingBox().maxX(), (int) moved.boundingBox().minZ(), (int) moved.boundingBox().maxZ());
        SubLevelGlue.put(moved, glue);
        SubLevelPassengers.restore(destLevel, moved, riders);
        relockStaff(destLevel, subLevelId, staffLocked);
        return new Result(moved, null, false, plotDelta);
    }

    private static void relockStaff(ServerLevel level, UUID subLevelId, boolean staffLocked) {
        if (staffLocked) {
            PhysicsStaffServerHandler.get(level).toggleLock(subLevelId);
        }
    }

    // sections save under a level relative index, so a different min height drops every block 64 and strands the block entity tags
    @Nullable
    public static String rebaseSections(CompoundTag tag, ServerLevel srcLevel, ServerLevel destLevel) {
        int delta = srcLevel.getMinSection() - destLevel.getMinSection();
        if (delta == 0) {
            return null;
        }
        CompoundTag chunks = tag.getCompound("plot").getCompound("chunks");
        for (String chunkKey : chunks.getAllKeys()) {
            CompoundTag chunkTag = chunks.getCompound(chunkKey);
            CompoundTag sections = chunkTag.getCompound("sections");
            CompoundTag shifted = new CompoundTag();
            for (String sectionKey : sections.getAllKeys()) {
                int moved = Integer.parseInt(sectionKey) + delta;
                if (!fitsHeight(moved, destLevel)) {
                    return tooTall(destLevel);
                }
                shifted.put(String.valueOf(moved), sections.getCompound(sectionKey));
            }
            chunkTag.put("sections", shifted);
            chunkTag.remove("heightmaps");
        }
        return null;
    }

    private static boolean fitsHeight(int sectionIndex, ServerLevel destLevel) {
        return sectionIndex >= 0 && sectionIndex < destLevel.getSectionsCount();
    }

    private static String tooTall(ServerLevel destLevel) {
        return "the body does not fit the build height of " + destLevel.dimension().location();
    }
}
