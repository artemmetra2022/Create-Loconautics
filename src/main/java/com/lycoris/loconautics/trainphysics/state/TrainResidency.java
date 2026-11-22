package com.lycoris.loconautics.trainphysics.state;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.SableTeleportation;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.content.schedule.ScheduleConductor;
import com.lycoris.loconautics.network.packets.train.SableTrainSyncPacket;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.SableTrain.Car;
import com.lycoris.loconautics.trainphysics.SableTrain.Residency;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.TrainLength;
import com.lycoris.loconautics.trainphysics.debug.GhostConsistTrace;
import com.lycoris.loconautics.trainphysics.debug.HostThrashTrace;
import com.lycoris.loconautics.trainphysics.drive.ConsistPower;
import com.lycoris.loconautics.trainphysics.drive.ExternalForceDerail;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.drive.observer.TrainObservers;
import com.lycoris.loconautics.trainphysics.portal.TrainPortalCrossing;
import com.lycoris.loconautics.trainphysics.rail.RailFrame;
import com.lycoris.loconautics.trainphysics.signal.TrainSignals;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.lycoris.loconautics.trainphysics.station.StationLanding;
import com.lycoris.loconautics.trainphysics.station.TrainStations;
import com.lycoris.loconautics.trainphysics.spacing.TrainSpacing;
import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.lycoris.loconautics.foundation.LogFormat.shortId;

public final class TrainResidency {

    public enum Phase { GHOST, LOADING, SETTLING, LIVE, PARKING, LOST }

    private static final long NONE = Long.MIN_VALUE;

    private static final Map<UUID, CarState> CARS = new ConcurrentHashMap<>();

    public static final CartStateStore STORE = new Store();

    private static final class CarState {
        Phase phase = Phase.LIVE;
        boolean reconcile;
        boolean railheadHeld;
        int snatchCooldown;
        boolean snatchMissLogged;
        int snatchMisses;
        long deferUntil = NONE;
        boolean deferLogged;
        boolean velocityCheck;
        int reseatFails;
        long ghostStepped = -1L;
        long embodiedAt = Long.MIN_VALUE / 2;
        long pullBlockedUntil = NONE;
    }

    private static CarState state(SableTrain train) {
        return CARS.computeIfAbsent(train.id(), id -> new CarState());
    }

    private static void setPhase(SableTrain train, Phase next, String why) {
        CarState state = state(train);
        if (state.phase == next) {
            return;
        }
        LoconauticsConstants.TRACE.warn("[residency] train={} phase {} to {} why={}", shortId(train.id()), state.phase, next, why);
        state.phase = next;
    }

    // a miss makes sable log an error per candidate chunk, so a body that cannot be found must not be hunted every tick
    private static final int SNATCH_RETRY_TICKS = 40;

    // 30 misses at 40 ticks each is a minute of looking, after that the body is checked on disk and in every level
    private static final int LOST_CHECK_MISSES = 30;

    private static final int LOST_SEARCH_RADIUS = 2;

    private static final int DEFER_RECHECK_TICKS = 20;

    private static final int RESEAT_RETRY_TICKS = 40;

    // every failed try tears the body down and loads it again, so a reseat that never works stops being retried
    private static final int MAX_RESEAT_FAILS = 5;

    // the chunks of a body that just came back stay pinned this long, so a station hold that ends on the embody tick cannot drop them
    public static final long EMBODY_GRACE_TICKS = 100L;

    // a body sable took away this soon after it came back flapped, nothing pulls it back for a while
    private static final long FLAP_TICKS = 20L;

    private static final long FLAP_HOLDOFF_TICKS = 100L;


    public static final int POSE_SETTLE_TICKS = 20;

    private static final double RESEAT_IN_PLACE = 1.0;

    private static final int EXTENT_REFRESH_MASK = 15;

    public static void clear() {
        CARS.clear();
        TrainSpacing.clear();
        GhostCreateStep.clear();
    }

    private TrainResidency() {
    }

    public static void onSubLevelAdded(SubLevel subLevel) {
        SableTrain train = SableTrainRegistry.bySubLevel(subLevel.getUniqueId());
        if (train == null || !train.isGhost()) {
            return;
        }
        state(train).reconcile = true;
        setPhase(train, Phase.LOADING, "body loaded by sable");
    }

    // a train restored off disk whose body was already loaded never went through onsubleveladded, nothing had it in the registry yet
    public static void queueReconcile(SableTrain train) {
        if (train != null) {
            state(train).reconcile = true;
            setPhase(train, Phase.LOADING, "restored from save");
        }
    }

    public static void onSubLevelUnloaded(SubLevel subLevel) {
        GhostConsistTrace.noteAnyUnload(subLevel);
        SableTrain train = SableTrainRegistry.bySubLevel(subLevel.getUniqueId());
        if (train == null) {
            return;
        }
        if (train.isGhost()) {
            if (subLevel instanceof ServerSubLevel parked) {
                train.setBodyChunk(chunkOf(parked.logicalPose().position()));
            }
            state(train).reconcile = false;
            setPhase(train, Phase.GHOST, "parked body went to disk");
            return;
        }
        if (subLevel instanceof ServerSubLevel sub) {
            GlobalSavedSubLevelPointer pointer = sub.getLastSerializationPointer();
            if (pointer != null) {
                train.setBodyPointer(pointer);
            }
            train.setBodyChunk(chunkOf(sub.logicalPose().position()));
            train.setLastKnownPos(sub.logicalPose().position());
            logUnload(train, sub);
            GhostConsistTrace.noteUnload(train, sub);
        }
        train.setResidency(Residency.GHOST);
        setPhase(train, Phase.GHOST, "body unloaded");
        long unloadedAt = train.level().getGameTime();
        CarState state = state(train);
        if (unloadedAt - state.embodiedAt < FLAP_TICKS) {
            state.pullBlockedUntil = unloadedAt + FLAP_HOLDOFF_TICKS;
        }
        state.reconcile = false;
        CartLifecycle.fire(train, CartEvent.GHOSTED);
        GhostCreateStep.noteFromLive(train);
        if (train.level().getServer() != null && train.level().getServer().isRunning()) {
            SableTrainPersistence.persist(train);
        }
    }

    public static void cacheEmbodiedState(SableTrain train, ServerSubLevel sub) {
        GlobalSavedSubLevelPointer pointer = sub.getLastSerializationPointer();
        if (pointer != null) {
            train.setBodyPointer(pointer);
        }
        train.setBodyChunk(chunkOf(sub.logicalPose().position()));
        train.setLastKnownPos(sub.logicalPose().position());
        train.setConsistCache(TrainConsist.connectedThisTick(train.level(), train.car().subLevelId()));
        if (train.extent() == null || (train.level().getGameTime() & EXTENT_REFRESH_MASK) == (train.id().hashCode() & EXTENT_REFRESH_MASK)) {
            refreshExtent(train);
        }
        var controller = SableTrainDriver.controllerIn(train);
        train.setScheduleDriven(controller != null && controller.isScheduleControlled());
        // the full power top, the ghost scales it by the schedule notch limit the way the lever would
        double top = ConsistPower.peakTopBpt(train);
        if (top > 1.0e-4) {
            train.setGhostCruise(top);
        }
    }

    private static void refreshExtent(SableTrain train) {
        var measured = TrainLength.measure(train.level(), train);
        if (measured != null) {
            train.setExtent(measured);
        }
    }

    public static void tick(SableTrain train) {
        if (state(train).reconcile) {
            if (TrainSpacing.mayEmbody(train) && matesLoaded(train)) {
                state(train).reconcile = false;
                if (embody(train) && !train.isGhost()) {
                    embodyMates(train);
                    return;
                }
            }
            train.setResidency(Residency.GHOST);
        }
        if (train.isGhost()) {
            tickGhost(train);
            return;
        }
        ServerSubLevel sub = resolve(train);
        if (sub == null) {
            train.setResidency(Residency.GHOST);
            setPhase(train, Phase.GHOST, "body gone while live");
            return;
        }
        CarState state = state(train);
        boolean velocityCheck = state.velocityCheck;
        state.velocityCheck = false;
        if (velocityCheck) {
            logEmbodiedVelocity(train, sub);
        }
        cacheEmbodiedState(train, sub);
        if (state.phase != Phase.LIVE && train.level().getGameTime() - state.embodiedAt >= POSE_SETTLE_TICKS) {
            setPhase(train, Phase.LIVE, "settled");
        }
    }

    private static void tickGhost(SableTrain train) {
        Car car = train.car();
        if (car == null || car.subLevelId() == null) {
            return;
        }
        Vector3d cursor = railCursor(car);
        if (train.isDerailed() || train.isPhysics()) {
            CarState state = state(train);
            boolean held = state.railheadHeld;
            state.railheadHeld = false;
            if (held) {
                StationChunkTickets.release(train.id(), "ghost derailed");
            }
            ChunkPos fell = train.bodyChunk();
            if (fell != null && footprintLoaded(train.level(), fell)) {
                snatchBody(train, cursor);
            }
            return;
        }
        boolean split = hasEmbodiedMate(train);
        if (staffLockedGhost(train)) {
            train.setTargetSpeed(0.0);
            train.setSpeed(0.0);
        } else {
            SableScheduleRuntime.tick(train, null);
            TrainStations.tickStation(train, null);
            TrainSignals.tickSignals(train);
            stepGhostConsist(train, split);
            TrainObservers.tick(train);
        }

        cursor = railCursor(car);
        boolean loaded = activeChunk(train.level(), cursor) && !car.carriage().railHostMissing();
        StationChunkTickets.update(train);
        if (loaded) {
            snatchBody(train, cursor);
        } else {
            TrainSpacing.release(train);
        }
    }

    private static boolean staffLockedGhost(SableTrain train) {
        Set<UUID> ids = new HashSet<>(train.consistCache());
        ids.add(train.car().subLevelId());
        return ExternalForceDerail.staffLockedById(train.level(), ids);
    }

    // one speed and one step for the whole consist
    private static void stepGhostConsist(SableTrain train, boolean split) {
        long now = train.level().getGameTime();
        if (state(train).ghostStepped == now) {
            return;
        }
        GhostCreateStep.Outcome outcome = GhostCreateStep.step(train, split);
        for (SableTrain member : outcome.cars()) {
            state(member).railheadHeld = outcome.held();
            state(member).ghostStepped = now;
        }
    }

    public static boolean ghostOwned(UUID subId) {
        SableTrain train = SableTrainRegistry.bySubLevel(subId);
        return train != null && train.isGhost();
    }

    // creates carriageentityhandler.isactivechunk, a carriage exists where its anchor is entity ticking
    private static boolean activeChunk(ServerLevel level, Vector3d pos) {
        return level != null && pos != null && !inPlotGrid(level, pos)
                && level.isPositionEntityTicking(BlockPos.containing(pos.x, pos.y, pos.z));
    }

    // a body that has been searched for a minute and not found, the hold for it gets the dwell cap
    public static boolean bodyLost(SableTrain train) {
        return state(train).phase == Phase.LOST;
    }

    private static void snatchBody(SableTrain train, Vector3d cursor) {
        CarState state = state(train);
        if (state.snatchCooldown > 0) {
            if (state.snatchCooldown > 1) {
                state.snatchCooldown--;
                return;
            }
            state.snatchCooldown = 0;
        }
        String notReady = matesNotReady(train);
        if (notReady != null) {
            GhostConsistTrace.noteSnatch(train, "a mate is not ready, " + notReady);
            return;
        }
        if (!TrainSpacing.mayEmbody(train)) {
            GhostConsistTrace.noteSnatch(train, "embody gate closed");
            return;
        }
        if (resolve(train) != null) {
            if (!matesLoaded(train)) {
                GhostConsistTrace.noteSnatch(train, "a mate body is not loaded yet");
                return;
            }
            state.reconcile = false;
            embody(train);
            embodyMates(train);
            return;
        }
        if (pullBlocked(train)) {
            GhostConsistTrace.noteSnatch(train, "sable took the body back right after it came back");
            return;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null) {
            return;
        }
        UUID subId = train.car().subLevelId();
        HostThrashTrace.snatch(train, !train.isTrackless() && train.car().carriage().railHostMissing(), cursor == null ? "none" : chunkOf(cursor).toString());
        SubLevelHoldingChunkMap map = container.getHoldingChunkMap();
        HoldingSubLevel holding = map.getHoldingSubLevel(subId);
        if (holding != null && cursor != null && !targetLoaded(train.level(), holding.data().bounds(),
                holding.data().pose().position(), cursor)) {
            GhostConsistTrace.noteSnatch(train, "the chunks where the body would land are not loaded yet");
            return;
        }
        GlobalSavedSubLevelPointer pointer = holding != null ? holding.pointer() : train.bodyPointer();
        List<ChunkPos> candidates = candidateChunks(train, holding);
        if (candidates.isEmpty()) {
            GhostConsistTrace.noteSnatch(train, "no candidate chunks, a body with no pointer and no chunk can never be found");
            return;
        }
        short storageIndex = pointer == null ? 0 : pointer.storageIndex();
        short subLevelIndex = pointer == null ? 0 : pointer.subLevelIndex();
        for (ChunkPos candidate : candidates) {
            map.snatchAndLoad(new GlobalSavedSubLevelPointer(candidate, storageIndex, subLevelIndex), subId);
            if (resolve(train) == null) {
                continue;
            }
            train.setBodyChunk(candidate);
            state.reconcile = false;
            embody(train);
            embodyMates(train);
            return;
        }
        state.snatchCooldown = SNATCH_RETRY_TICKS;
        int misses = ++state.snatchMisses;
        if (misses == LOST_CHECK_MISSES) {
            markLost(train);
        }
        if (!state.snatchMissLogged) {
            state.snatchMissLogged = true;
            LoconauticsConstants.LOGGER.warn("[residency] body {} of train {} was not found in any of {} candidate "
                    + "holding chunks, pointer {}, bodyChunk {}, holdingKnown={}, misses={}", shortId(subId),
                    shortId(train.id()), candidates.size(), pointer, train.bodyChunk(), holding != null, misses);
            LoconauticsConstants.TRACE.warn("[ghostedge] miss train={} candidates={} storedBounds={} grownBounds={}",
                    shortId(train.id()), candidates,
                    holding == null ? "none" : boundsText(holding.data().bounds(), 0.0),
                    holding == null ? "none" : boundsText(holding.data().bounds(), 1.0));
        }
        if (misses == LOST_CHECK_MISSES || (misses > LOST_CHECK_MISSES && misses % (LOST_CHECK_MISSES * 10) == 0)) {
            LoconauticsConstants.LOGGER.warn("[residency] body {} of train {} is still missing after {} misses, {}",
                    shortId(subId), shortId(train.id()), misses, lostReport(train, container, subId, pointer));
        }
    }

    private static void markLost(SableTrain train) {
        setPhase(train, Phase.LOST, "body not found after " + LOST_CHECK_MISSES + " searches, kept and listed in /loconautics lost");
        state(train).railheadHeld = false;
        StationChunkTickets.release(train.id(), "body lost");
        TrainSpacing.release(train);
    }

    public static String findLost(SableTrain train) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null || train.car() == null || train.car().subLevelId() == null) {
            return shortId(train.id()) + " has no level or body id to search for";
        }
        if (resolve(train) != null) {
            state(train).reconcile = true;
            setPhase(train, Phase.LOADING, "lost body already live");
            return shortId(train.id()) + " body is already loaded, it will be put back on its rail";
        }
        UUID subId = train.car().subLevelId();
        GlobalSavedSubLevelPointer pointer = train.bodyPointer();
        short storageIndex = pointer == null ? 0 : pointer.storageIndex();
        short subLevelIndex = pointer == null ? 0 : pointer.subLevelIndex();
        List<ChunkPos> centers = new ArrayList<>(3);
        if (pointer != null) {
            centers.add(pointer.chunkPos());
        }
        if (train.bodyChunk() != null) {
            centers.add(train.bodyChunk());
        }
        Vector3d cursor = railCursor(train.car());
        if (cursor != null && !inPlotGrid(train.level(), cursor)) {
            centers.add(chunkOf(cursor));
        }
        List<ChunkPos> search = new ArrayList<>();
        for (ChunkPos center : centers) {
            addSpan(search, center.x - LOST_SEARCH_RADIUS, center.x + LOST_SEARCH_RADIUS,
                    center.z - LOST_SEARCH_RADIUS, center.z + LOST_SEARCH_RADIUS);
        }
        SubLevelHoldingChunkMap map = container.getHoldingChunkMap();
        for (ChunkPos chunk : search) {
            map.snatchAndLoad(new GlobalSavedSubLevelPointer(chunk, storageIndex, subLevelIndex), subId);
            if (resolve(train) == null) {
                continue;
            }
            CarState state = state(train);
            train.setBodyChunk(chunk);
            state.snatchMisses = 0;
            state.snatchCooldown = 0;
            state.snatchMissLogged = false;
            state.reconcile = true;
            setPhase(train, Phase.LOADING, "found by /loconautics lost find in chunk " + chunk);
            return shortId(train.id()) + " body found in chunk " + chunk.x + " " + chunk.z + ", it will be put back on its rail";
        }
        return shortId(train.id()) + " body was not in any of " + search.size() + " chunks around its last known spots";
    }

    // sable loads a body with its chain, an unready car would pull them all back down
    private static String matesNotReady(SableTrain train) {
        for (SableTrain mate : GhostConsist.ghosts(train, true)) {
            if (mate == train || bodyLost(mate)) {
                continue;
            }
            Vector3d at = railCursor(mate.car());
            if (at == null || !activeChunk(mate.level(), at)) {
                return shortId(mate.id()) + (at == null ? " has no rail point" : " has unloaded chunks at " + v3(at));
            }
        }
        return null;
    }

    // a consist comes back all or nothing, so one car never goes live while a mate body is still on disk
    private static boolean matesLoaded(SableTrain train) {
        for (SableTrain mate : GhostConsist.ghosts(train, true)) {
            if (mate != train && !bodyLost(mate) && resolve(mate) == null) {
                return false;
            }
        }
        return true;
    }

    private static void embodyMates(SableTrain train) {
        if (train.isGhost()) {
            return;
        }
        List<SableTrain> back = new ArrayList<>();
        back.add(train);
        for (SableTrain mate : GhostConsist.ghosts(train, false)) {
            if (mate != train && resolve(mate) != null) {
                state(mate).reconcile = false;
                embody(mate);
                if (!mate.isGhost()) {
                    back.add(mate);
                }
            }
        }
        for (SableTrain car : back) {
            ServerSubLevel sub = resolve(car);
            if (sub != null) {
                GhostKnuckles.reengage(car, sub);
            }
        }
    }

    private static String lostReport(SableTrain train, ServerSubLevelContainer container, UUID subId,
                                     GlobalSavedSubLevelPointer pointer) {
        StringBuilder out = new StringBuilder();
        MinecraftServer server = train.level().getServer();
        boolean live = false;
        if (server != null) {
            for (ServerLevel other : server.getAllLevels()) {
                ServerSubLevelContainer otherContainer = SubLevelContainer.getContainer(other);
                if (otherContainer != null && otherContainer.getSubLevel(subId) != null) {
                    out.append("live in ").append(other.dimension().location()).append(", ");
                    live = true;
                }
            }
        }
        if (!live) {
            out.append("not live in any level, ");
        }
        List<GlobalSavedSubLevelPointer> pointers = new ArrayList<>(2);
        if (pointer != null) {
            pointers.add(pointer);
        }
        if (train.bodyPointer() != null && !train.bodyPointer().equals(pointer)) {
            pointers.add(train.bodyPointer());
        }
        if (pointers.isEmpty()) {
            out.append("no saved pointer to check");
        }
        for (GlobalSavedSubLevelPointer saved : pointers) {
            SubLevelData data = container.getHoldingChunkMap().getStorage()
                    .attemptLoadSubLevel(saved.chunkPos(), saved.local());
            out.append("disk ").append(saved).append(data == null ? " has nothing"
                    : data.uuid().equals(subId) ? " holds this body" : " holds body " + shortId(data.uuid()))
                    .append(", ");
        }
        return out.toString();
    }

    // the body was filed under one of the world chunks it overlapped, the pose chunk first since that is nearly always it
    private static List<ChunkPos> candidateChunks(SableTrain train, HoldingSubLevel holding) {
        List<ChunkPos> out = new ArrayList<>();
        ChunkPos primary = holding != null ? chunkOf(new Vector3d(holding.data().pose().position()))
                : train.bodyChunk();
        if (primary != null) {
            out.add(primary);
        }
        if (holding != null) {
            // sable files a body under a chunk of its bounds grown by one block, the same margin its ready test uses
            BoundingBox3d bounds = holding.data().bounds();
            addSpan(out, Mth.floor(bounds.minX() - 1.0) >> 4, Mth.floor(bounds.maxX() + 1.0) >> 4,
                    Mth.floor(bounds.minZ() - 1.0) >> 4, Mth.floor(bounds.maxZ() + 1.0) >> 4);
        } else if (primary != null) {
            addSpan(out, primary.x - 1, primary.x + 1, primary.z - 1, primary.z + 1);
        }
        if (train.bodyChunk() != null && !out.contains(train.bodyChunk())) {
            out.add(train.bodyChunk());
        }
        if (train.bodyPointer() != null && !out.contains(train.bodyPointer().chunkPos())) {
            out.add(train.bodyPointer().chunkPos());
        }
        return out;
    }

    private static void addSpan(List<ChunkPos> out, int minX, int maxX, int minZ, int maxZ) {
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                ChunkPos pos = new ChunkPos(cx, cz);
                if (!out.contains(pos)) {
                    out.add(pos);
                }
            }
        }
    }

    private static ChunkPos chunkOf(Vector3dc pos) {
        return new ChunkPos((int) Math.floor(pos.x()) >> 4, (int) Math.floor(pos.z()) >> 4);
    }

    // true when nothing is left to retry, false when the body stays live and the next tick asks again
    private static boolean embody(SableTrain train) {
        ServerSubLevel sub = resolve(train);
        if (sub == null) {
            return true;
        }
        GhostCreateStep.settle(train);
        HostThrashTrace.embody(train, !train.isTrackless() && train.car().carriage().railHostMissing(),
                footprintLoaded(train.level(), railCursor(train.car())));
        Vector3d origin = new Vector3d();
        Quaterniond orient = SableTrainDriver.seatPose(sub, train, origin);
        boolean reseat = !train.isDerailed() && !train.isPhysics() && orient != null;
        if (reseat) {
            Target target = guardTarget(train, sub, origin, orient);
            if (target == Target.DEFERRED) {
                state(train).reconcile = true;
                setPhase(train, Phase.LOADING, "embody deferred");
                return false;
            }
            if (target == Target.PARKED) {
                return true;
            }
        }
        CartLifecycle.beforeTeleport(train);
        boolean seated = false;
        if (reseat) {
            Vector3d before = new Vector3d(sub.logicalPose().position());
            if (before.distance(origin) <= RESEAT_IN_PLACE) {
                SableTeleportation.writePose(train.level(), sub, origin, orient);
                seated = true;
            } else {
                SableTeleportation.Result placed = SableTeleportation.teleport(sub, train.level(), origin, orient);
                if (placed.ok()) {
                    sub = placed.moved();
                    seated = true;
                } else {
                    if (retryReseat(train, placed.failure())) {
                        return false;
                    }
                    // the failed teleport may have put the body back as a new object, so the old handle is stale
                    ServerSubLevel back = resolve(train);
                    if (back == null) {
                        return true;
                    }
                    sub = back;
                }
            }
        }
        if (seated) {
            // sable gives back 0.9 of the saved speed or none, the rail speed is what the train really has
            SableTrainDriver.seedRailVelocity(sub, train.car(), train);
            state(train).velocityCheck = true;
        }
        zeroLeverOfStoppedGhost(train, sub);
        GhostConsistTrace.noteEmbodied(train);
        train.setResidency(Residency.EMBODIED);
        setPhase(train, Phase.SETTLING, "embodied");
        CarState state = state(train);
        state.embodiedAt = train.level().getGameTime();
        state.snatchCooldown = 0;
        state.snatchMisses = 0;
        state.snatchMissLogged = false;
        state.deferUntil = NONE;
        state.deferLogged = false;
        state.reseatFails = 0;
        state.railheadHeld = false;
        StationChunkTickets.clearCap(train.id());
        CartLifecycle.fire(train, CartEvent.EMBODIED);
        cacheEmbodiedState(train, sub);
        SableTrainPersistence.persist(train);
        SableTrainSyncPacket.broadcast(train);
        ScheduleConductor.broadcastBlazeHats(sub);
        return true;
    }

    // the lever is saved with the body and would drive a parked ghost off the moment it loads
    private static void zeroLeverOfStoppedGhost(SableTrain train, ServerSubLevel sub) {
        if (Math.abs(train.targetSpeed()) > 1.0e-6 && Math.abs(train.speed()) > StationLanding.PARK_SPEED) {
            return;
        }
        for (AnalogControllerBlockEntity controller : SubLevelBlocks.findAll(sub, AnalogControllerBlockEntity.class)) {
            if (controller.getCurrentPower() != 0 && !controller.hasUser()) {
                GhostConsistTrace.noteLever(train, controller.getCurrentPower());
                controller.setCurrentPower(0);
            }
        }
    }

    // true when the train stays a ghost and the reseat is tried again after a pause, false once the tries ran out
    private static boolean retryReseat(SableTrain train, String failure) {
        UUID id = train.id();
        CarState state = state(train);
        int fails = ++state.reseatFails;
        LoconauticsConstants.LOGGER.warn("[ghost] {} could not be reseated off disk: {} [ghostedge] attempt {} of {}",
                shortId(id), failure, fails, MAX_RESEAT_FAILS);
        if (fails >= MAX_RESEAT_FAILS) {
            state.reseatFails = 0;
            return false;
        }
        train.beginPoseSettle(0);
        state.deferUntil = train.level().getGameTime() + RESEAT_RETRY_TICKS;
        state.reconcile = true;
        setPhase(train, Phase.LOADING, "reseat retry");
        return true;
    }

    private enum Target { PROCEED, PARKED, DEFERRED }

    // a body sable brought back at its old place is never reseated onto a cursor in chunks nobody has loaded
    private static Target guardTarget(SableTrain train, ServerSubLevel sub, Vector3d origin, Quaterniond orient) {
        if (sub.logicalPose().position().distance(origin) <= RESEAT_IN_PLACE) {
            return Target.PROCEED;
        }
        ServerLevel level = train.level();
        UUID id = train.id();
        long now = level.getGameTime();
        CarState state = state(train);
        if (state.deferUntil != NONE && now < state.deferUntil) {
            return Target.DEFERRED;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return Target.PROCEED;
        }
        boolean blind = train.car() != null && !train.isTrackless() && train.car().carriage().railHostMissing();
        if (!blind && targetLoaded(level, sub.boundingBox(), sub.logicalPose().position(), origin)) {
            state.deferUntil = NONE;
            state.deferLogged = false;
            return Target.PROCEED;
        }
        boolean aboard = !blind && playerAboard(level, sub);
        if (blind || aboard) {
            state.deferUntil = now + DEFER_RECHECK_TICKS;
            if (!state.deferLogged) {
                state.deferLogged = true;
                LoconauticsConstants.LOGGER.info("[ghostedge] embody deferred train={} body={} reason={} target={} "
                                + "bodyAt={}", shortId(train.id()), shortId(sub),
                        blind ? "the body its rail sits on is not loaded"
                                : "a player is aboard and the cursor chunks are not loaded",
                        v3(origin), v3(sub.logicalPose().position()));
            }
            return Target.DEFERRED;
        }
        state.deferUntil = NONE;
        state.deferLogged = false;
        if (hasEmbodiedMate(train)) {
            LoconauticsConstants.LOGGER.info("[ghostedge] park skipped train={} body={} reason=a mate is embodied",
                    shortId(train.id()), shortId(sub));
            return Target.PROCEED;
        }
        int chain = SubLevelHelper.getLoadingDependencyChain(sub).size();
        if (chain > 1) {
            LoconauticsConstants.LOGGER.info("[ghostedge] park skipped train={} body={} reason=loading chain of {}",
                    shortId(train.id()), shortId(sub), chain);
            return Target.PROCEED;
        }
        // like a create carriage outside ticking chunks, the body goes back to disk until its spot is active
        LoconauticsConstants.TRACE.warn("[ghostedge] embody refused train={} body={} target={} reason=target chunks "
                + "not loaded, parked where it is", shortId(train.id()), shortId(sub), v3(origin));
        state.snatchCooldown = SNATCH_RETRY_TICKS;
        setPhase(train, Phase.PARKING, "embody refused, target not loaded");
        container.getHoldingChunkMap().moveToUnloaded(sub, chunkOf(sub.logicalPose().position()));
        return Target.PARKED;
    }

    private static boolean targetLoaded(ServerLevel level, BoundingBox3dc box, Vector3dc at, Vector3dc origin) {
        double dx = origin.x() - at.x();
        double dz = origin.z() - at.z();
        int minX = Mth.floor(box.minX() + dx - 1.0) >> 4;
        int maxX = Mth.floor(box.maxX() + dx + 1.0) >> 4;
        int minZ = Mth.floor(box.minZ() + dz - 1.0) >> 4;
        int maxZ = Mth.floor(box.maxZ() + dz + 1.0) >> 4;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (!PhysicsChunkTicketManager.isChunkLoadedEnough(level, cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    // sable keeps a body with a player inside its box, so the check is the same box grown by one block
    private static boolean playerAboard(ServerLevel level, ServerSubLevel sub) {
        BoundingBox3dc box = sub.boundingBox();
        return !level.getPlayers(player -> {
            Vec3 c = player.getBoundingBox().getCenter();
            return c.x >= box.minX() - 1.0 && c.x <= box.maxX() + 1.0
                    && c.y >= box.minY() - 1.0 && c.y <= box.maxY() + 1.0
                    && c.z >= box.minZ() - 1.0 && c.z <= box.maxZ() + 1.0;
        }).isEmpty();
    }

    private static void logEmbodiedVelocity(SableTrain train, ServerSubLevel sub) {
        RigidBodyHandle handle = RigidBodyHandle.of(sub);
        RailFrame frame = RailFrame.of(sub, train);
        double raw = handle == null ? Double.NaN : handle.getLinearVelocity(new Vector3d()).length();
        LoconauticsConstants.LOGGER.info("[ghostedge] velocity train={} mode={} bodySpeed={} alongRail={} railSpeed={} "
                        + "blocks per second", shortId(train.id()), frame.mode(), f3(raw), f3(frame.speed()),
                f3(train.speed() * 20.0));
    }

    private static void logUnload(SableTrain train, ServerSubLevel sub) {
        MinecraftServer server = train.level().getServer();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (server == null || !server.isRunning() || container == null) {
            return;
        }
        HoldingSubLevel holding = container.getHoldingChunkMap().getHoldingSubLevel(sub.getUniqueId());
        LoconauticsConstants.LOGGER.info("[ghostedge] unload train={} body={} bodyChunk={} pointer={} filed={} "
                        + "storedBounds={} candidates={}", shortId(train.id()), shortId(sub), train.bodyChunk(),
                train.bodyPointer(), holding != null,
                holding == null ? "none" : boundsText(holding.data().bounds(), 0.0),
                candidateChunks(train, holding));
    }

    private static String boundsText(BoundingBox3dc box, double grow) {
        return String.format(Locale.ROOT, "x %.1f to %.1f, z %.1f to %.1f", box.minX() - grow, box.maxX() + grow,
                box.minZ() - grow, box.maxZ() + grow);
    }

    private static String v3(Vector3dc v) {
        return String.format(Locale.ROOT, "(%.1f,%.1f,%.1f)", v.x(), v.y(), v.z());
    }

    private static String f3(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static boolean hasEmbodiedMate(SableTrain train) {
        Set<UUID> consist = train.consistCache();
        if (consist.size() <= 1) {
            return false;
        }
        for (UUID id : consist) {
            SableTrain other = SableTrainRegistry.bySubLevel(id);
            if (other != null && other != train && !other.isGhost()) {
                return true;
            }
        }
        return false;
    }

    public static boolean embodiedWithin(SableTrain train, long ticks) {
        return !train.isGhost()
                && train.level().getGameTime() - state(train).embodiedAt < ticks;
    }

    // nothing may pull a ghost back chunk holds, snatches right after sable took its body away on a flap
    public static boolean pullBlocked(SableTrain train) {
        return train.level().getGameTime() < state(train).pullBlockedUntil;
    }

    // the chunks sable tests for every body that loads and unloads with this car, bounds grown by one block like its own test
    public static Set<ChunkPos> chainChunks(SableTrain train) {
        Set<ChunkPos> out = new HashSet<>();
        ServerSubLevel sub = resolve(train);
        if (sub == null) {
            return out;
        }
        for (ServerSubLevel body : SubLevelHelper.getLoadingDependencyChain(sub)) {
            BoundingBox3dc box = body.boundingBox();
            int minX = Mth.floor(box.minX() - 1.0) >> 4;
            int maxX = Mth.floor(box.maxX() + 1.0) >> 4;
            int minZ = Mth.floor(box.minZ() - 1.0) >> 4;
            int maxZ = Mth.floor(box.maxZ() + 1.0) >> 4;
            for (int cx = minX; cx <= maxX; cx++) {
                for (int cz = minZ; cz <= maxZ; cz++) {
                    out.add(new ChunkPos(cx, cz));
                }
            }
        }
        return out;
    }

    public static boolean holdForGhostedConsist(SableTrain train) {
        if (train.isGhost()) {
            return false;
        }
        TrainSpacing.limitLoaded(train);
        Set<UUID> consist = train.consistCache();
        if (consist.size() <= 1) {
            return false;
        }
        SableTrain gone = null;
        boolean waiting = false;
        for (UUID id : consist) {
            SableTrain other = SableTrainRegistry.bySubLevel(id);
            if (other == null || other == train || !other.isGhost() || bodyLost(other)) {
                continue;
            }
            if (other.level() == train.level() && resolve(other) == null) {
                gone = other;
                break;
            }
            waiting = true;
        }
        if (gone == null) {
            if (waiting) {
                train.setTargetSpeed(0.0);
            }
            return waiting;
        }
        // create makes a train with a player rider wait for its chunks, so a ridden rake holds instead of parking
        if (riderAboard(train) || crossingPortal(train)) {
            train.setTargetSpeed(0.0);
            return true;
        }
        parkRake(train, gone);
        return true;
    }

    private static boolean crossingPortal(SableTrain train) {
        for (UUID id : train.consistCache()) {
            if (TrainPortalCrossing.straddle(id) != null || TrainPortalCrossing.isCrossing(id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean riderAboard(SableTrain train) {
        for (UUID id : train.consistCache()) {
            SableTrain car = SableTrainRegistry.bySubLevel(id);
            ServerSubLevel sub = car == null || car.isGhost() ? null : resolve(car);
            if (sub != null && playerAboard(train.level(), sub)) {
                return true;
            }
        }
        return false;
    }

    // a rake is live or a ghost as a whole, a live car cannot be pulled by a mate that has no body
    private static void parkRake(SableTrain train, SableTrain gone) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null) {
            return;
        }
        List<SableTrain> live = new ArrayList<>();
        for (UUID id : train.consistCache()) {
            SableTrain car = SableTrainRegistry.bySubLevel(id);
            if (car != null && !car.isGhost() && car.level() == train.level() && resolve(car) != null) {
                live.add(car);
            }
        }
        LoconauticsConstants.LOGGER.warn("[residency] park rake train={} cars={} reason={} has no body", shortId(train.id()),
                live.size(), shortId(gone.id()));
        for (SableTrain car : live) {
            ServerSubLevel sub = resolve(car);
            if (sub == null) {
                continue;
            }
            if (!car.isDerailed() && !car.isPhysics()) {
                SableTrainSpawner.reseatOnRail(car, sub);
            }
            cacheEmbodiedState(car, sub);
            CartLifecycle.beforeTeleport(car);
            setPhase(car, Phase.PARKING, "rake parks with its ghost mate " + shortId(gone.id()));
            container.getHoldingChunkMap().moveToUnloaded(sub, chunkOf(sub.logicalPose().position()));
        }
    }

    public static String forceGhost(SableTrain train) {
        if (train.isGhost()) {
            return shortId(train.id()) + " is already a ghost";
        }
        ServerSubLevel sub = resolve(train);
        if (sub == null) {
            return shortId(train.id()) + " has no body to unload";
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null) {
            return "no sub level container in " + train.level().dimension().location();
        }
        cacheEmbodiedState(train, sub);
        setPhase(train, Phase.PARKING, "forced by command");
        container.getHoldingChunkMap().moveToUnloaded(sub, chunkOf(sub.logicalPose().position()));
        return shortId(train.id()) + " body pushed to the holding chunk, it should be a ghost now";
    }

    public static BlockPos ghostHoldAnchor(SableTrain train) {
        if (!train.isGhost() || train.car() == null || bodyLost(train)
                || !state(train).railheadHeld) {
            return null;
        }
        Vector3d cursor = railCursor(train.car());
        if (cursor == null || inPlotGrid(train.level(), cursor)) {
            return null;
        }
        return BlockPos.containing(cursor.x, cursor.y, cursor.z);
    }

    // a rail whose host body is not loaded reads in plot space, nothing there can be pinned or counted as loaded
    public static boolean inPlotGrid(ServerLevel level, Vector3d pos) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        return container != null && container.inBounds(pos);
    }

    public static Vector3d railCursor(Car car) {
        Vector3d center = new Vector3d();
        return SableTrainDriver.bodyPose(car, center) == null ? null : center;
    }

    public static boolean footprintLoaded(ServerLevel level, Vector3d pos) {
        if (level == null || pos == null) {
            return false;
        }
        return footprintLoaded(level, chunkOf(pos));
    }

    private static boolean footprintLoaded(ServerLevel level, ChunkPos center) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null && container.inBounds(center)) {
            return false;
        }
        for (int cx = center.x - 1; cx <= center.x + 1; cx++) {
            for (int cz = center.z - 1; cz <= center.z + 1; cz++) {
                if (!PhysicsChunkTicketManager.isChunkLoadedEnough(level, cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void forget(Set<UUID> live) {
        CARS.keySet().retainAll(live);
        TrainSpacing.forget(live);
    }

    public static String describe(SableTrain train) {
        return shortId(train.id()) + " " + train.residency()
                + " pointer=" + train.bodyPointer()
                + " bodyChunk=" + train.bodyChunk()
                + " consist=" + train.consistCache().size()
                + " scheduleDriven=" + train.isScheduleDriven()
                + " cruise=" + String.format("%.3f", train.ghostCruise())
                + " phase=" + state(train).phase;
    }

    public static String traceFlags(SableTrain train) {
        CarState state = state(train);
        return String.format(Locale.ROOT, "phase=%s railheadHeld=%s reconcile=%s snatchMisses=%d lost=%s "
                        + "deferTicksLeft=%d reseatFails=%d", state.phase, state.railheadHeld, state.reconcile,
                state.snatchMisses, bodyLost(train),
                state.deferUntil == NONE ? 0L : Math.max(0L, state.deferUntil - train.level().getGameTime()),
                state.reseatFails);
    }

    private static ServerSubLevel resolve(SableTrain train) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null) {
            return null;
        }
        UUID subId = train.car() == null ? null : train.car().subLevelId();
        if (subId == null || !(container.getSubLevel(subId) instanceof ServerSubLevel sub) || sub.isRemoved()) {
            return null;
        }
        return sub;
    }

    private static final class Store implements CartStateStore {

        @Override
        public boolean clears(CartEvent event) {
            return event == CartEvent.REMOVED;
        }

        @Override
        public void forget(SableTrain train, UUID subId, CartEvent event) {
            if (event == CartEvent.REMOVED) {
                CARS.remove(train.id());
            }
        }

        @Override
        public void retain(Set<UUID> liveTrains, Set<UUID> liveSubs) {
            CARS.keySet().retainAll(liveTrains);
        }

        @Override
        public String describe(UUID trainId, UUID subId) {
            CarState state = CARS.get(trainId);
            return state == null ? "" : "phase=" + state.phase + " reconcile=" + state.reconcile
                    + " railheadHeld=" + state.railheadHeld + " snatchMisses=" + state.snatchMisses;
        }
    }
}
