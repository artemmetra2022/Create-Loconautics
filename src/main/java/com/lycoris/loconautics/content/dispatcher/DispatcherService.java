package com.lycoris.loconautics.content.dispatcher;

import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.signal.TrainSignals;
import com.lycoris.loconautics.trainphysics.state.CreateTrainShadow;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.signal.SignalEdgeGroup;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Collects a unified read-only view of native Create trains and Loconautics trains. */
public final class DispatcherService {

    public record Snapshot(List<DispatcherTrainEntry> trains, int occupiedSections,
                           int reservedSections, int conflicts, int totalSections) {
    }

    private DispatcherService() {
    }

    public static Snapshot snapshot(MinecraftServer server) {
        List<DispatcherTrainEntry> result = new ArrayList<>();
        Set<UUID> seenSableConsists = new HashSet<>();

        // Native Create trains are the authoritative source for ordinary Create trains.
        for (Train train : new ArrayList<>(Create.RAILWAYS.trains.values())) {
            if (train == null || train.invalid || CreateTrainShadow.isShadow(train)) continue;
            DispatcherTrainEntry entry = createEntry(train);
            if (entry != null) result.add(entry);
        }

        // Loconautics mirrors its consists into Create's railway map for compatibility. Those
        // mirrors are deliberately skipped above and represented here as one Sable consist.
        for (SableTrain train : SableTrainRegistry.all()) {
            Train shadow = CreateTrainShadow.of(train.id());
            if (shadow != null && CreateTrainShadow.isShadow(shadow)) {
                if (!seenSableConsists.add(shadow.id)) continue;
                List<SableTrain> members = CreateTrainShadow.carsOf(shadow);
                if (members.isEmpty()) members = List.of(train);
                DispatcherTrainEntry entry = sableConsistEntry(shadow, members);
                if (entry != null) result.add(entry);
            } else {
                if (!seenSableConsists.add(train.id())) continue;
                DispatcherTrainEntry entry = sableEntry(train, List.of(train));
                if (entry != null) result.add(entry);
            }
        }

        result.sort(Comparator.comparing(DispatcherTrainEntry::type)
                .thenComparing(DispatcherTrainEntry::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(e -> e.id().toString()));

        // Merge both engines by Create signal-group ID. A Loconautics carriage is represented
        // by its consist shadow ID so several wagons in one section do not look like a collision.
        Set<UUID> allSectionIds = new HashSet<>(Create.RAILWAYS.signalEdgeGroups.keySet());
        allSectionIds.addAll(TrainSignals.trackedGroups());
        int occupied = 0;
        int reserved = 0;
        int conflicts = 0;
        for (UUID sectionId : allSectionIds) {
            SignalEdgeGroup group = Create.RAILWAYS.signalEdgeGroups.get(sectionId);
            Set<UUID> occupants = new HashSet<>();
            if (group != null) {
                for (Train train : group.trains) {
                    if (!CreateTrainShadow.isShadow(train)) occupants.add(train.id);
                }
            }
            for (UUID sableTrainId : TrainSignals.occupantsOf(sectionId)) {
                Train shadow = CreateTrainShadow.of(sableTrainId);
                occupants.add(shadow != null && CreateTrainShadow.isShadow(shadow) ? shadow.id : sableTrainId);
            }
            if (!occupants.isEmpty()) occupied++;
            if (group != null && group.reserved != null || TrainSignals.reserved(sectionId)) reserved++;
            if (occupants.size() > 1) conflicts++;
        }
        return new Snapshot(List.copyOf(result), occupied, reserved, conflicts, allSectionIds.size());
    }

    private static DispatcherTrainEntry createEntry(Train train) {
        ResourceKey<Level> dimension = train.getPresentDimensions().stream().findFirst().orElse(null);
        if (dimension == null) return null;
        BlockPos pos = train.getPositionInDimension(dimension).orElse(null);
        if (pos == null) return null;

        int state = train.derailed ? DispatcherTrainEntry.DERAILED
                : train.navigation.waitingForSignal != null ? DispatcherTrainEntry.WAITING
                : Math.abs(train.speed) > 0.001 ? DispatcherTrainEntry.MOVING
                : DispatcherTrainEntry.STOPPED;
        String name = train.name == null ? "" : train.name.getString().trim();
        if (name.isEmpty()) name = "Train " + shortId(train.id);
        double speed = Double.isFinite(train.speed) ? train.speed * 20.0 : 0.0;
        return new DispatcherTrainEntry(train.id, "create", name, dimension.location().toString(),
                pos.getX(), pos.getY(), pos.getZ(), speed, state,
                train.occupiedSignalBlocks == null ? 0 : train.occupiedSignalBlocks.size(),
                train.reservedSignalBlocks == null ? 0 : train.reservedSignalBlocks.size(), false);
    }

    private static DispatcherTrainEntry sableConsistEntry(Train shadow, List<SableTrain> members) {
        SableTrain lead = members.get(0);
        ResourceKey<Level> dimension = lead.level() == null ? null : lead.level().dimension();
        if (dimension == null) return null;
        Vec3 position = positionOf(lead);
        if (position == null) return null;

        boolean derailed = members.stream().anyMatch(SableTrain::isDerailed);
        boolean held = members.stream().anyMatch(t -> DispatcherTrainControl.isHeld(t.id()));
        Set<UUID> occupiedGroups = new HashSet<>();
        Set<UUID> reservedGroups = new HashSet<>();
        for (SableTrain member : members) {
            occupiedGroups.addAll(TrainSignals.groupsOf(member.id()));
            reservedGroups.addAll(TrainSignals.reservedGroupsOf(member.id()));
        }
        int occupied = occupiedGroups.size();
        int reserved = reservedGroups.size();
        double speed = Double.isFinite(shadow.speed) ? shadow.speed * 20.0 : lead.speed() * 20.0;
        int state = derailed ? DispatcherTrainEntry.DERAILED : held ? DispatcherTrainEntry.DISPATCH_HELD
                : Math.abs(speed) > 0.02 ? DispatcherTrainEntry.MOVING : DispatcherTrainEntry.STOPPED;
        String name = shadow.name == null ? "" : shadow.name.getString().trim();
        if (name.isEmpty()) name = "Loconautics " + shortId(shadow.id);
        return new DispatcherTrainEntry(shadow.id, "loconautics", name, dimension.location().toString(),
                floor(position.x), floor(position.y), floor(position.z), speed, state, occupied, reserved, held);
    }

    private static DispatcherTrainEntry sableEntry(SableTrain train, List<SableTrain> members) {
        if (train.level() == null) return null;
        Vec3 position = positionOf(train);
        if (position == null) return null;
        boolean held = DispatcherTrainControl.isHeld(train.id());
        int state = train.isDerailed() ? DispatcherTrainEntry.DERAILED : held
                ? DispatcherTrainEntry.DISPATCH_HELD : Math.abs(train.speed()) > 0.02
                ? DispatcherTrainEntry.MOVING : DispatcherTrainEntry.STOPPED;
        String name = train.name() == null || train.name().isBlank()
                ? "Loconautics " + shortId(train.id()) : train.name();
        return new DispatcherTrainEntry(train.id(), "loconautics", name,
                train.level().dimension().location().toString(), floor(position.x), floor(position.y),
                floor(position.z), train.speed() * 20.0, state, TrainSignals.groupsOf(train.id()).size(),
                TrainSignals.reservedGroupsOf(train.id()).size(), held);
    }

    private static Vec3 positionOf(SableTrain train) {
        Vector3dc known = train.lastKnownPos();
        if (known != null && Double.isFinite(known.x()) && Double.isFinite(known.y()) && Double.isFinite(known.z())) {
            return new Vec3(known.x(), known.y(), known.z());
        }
        if (train.car() == null) return null;
        if (train.car().carriage() != null) {
            Vec3 position = train.car().carriage().moveCenter();
            if (position != null) return position;
        }
        for (SableTrain.Bogey bogey : train.car().bogeys()) {
            if (bogey.rail() != null) {
                Vec3 position = bogey.rail().moveCenter();
                if (position != null) return position;
            }
        }
        return null;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    /** Applies a dispatcher service-brake request to an entire Loconautics consist. */
    public static boolean setLoconauticsHold(UUID trainId, boolean held) {
        SableTrain train = SableTrainRegistry.get(trainId);
        if (train == null) return false;
        Train shadow = CreateTrainShadow.of(trainId);
        List<SableTrain> members = shadow != null && CreateTrainShadow.isShadow(shadow)
                ? CreateTrainShadow.carsOf(shadow) : List.of(train);
        if (members.isEmpty()) members = List.of(train);
        for (SableTrain member : members) DispatcherTrainControl.setHeld(member.id(), held);
        return true;
    }
}
