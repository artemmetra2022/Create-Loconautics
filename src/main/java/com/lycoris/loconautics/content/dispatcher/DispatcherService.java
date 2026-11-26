package com.lycoris.loconautics.content.dispatcher;

import com.lycoris.loconautics.common.railway.SignalOverrides;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.signal.TrainSignals;
import com.lycoris.loconautics.trainphysics.state.CreateTrainShadow;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.signal.SignalEdgeGroup;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Collects a unified view of Create trains, Loconautics trains and block sections. */
public final class DispatcherService {

    public record Snapshot(List<DispatcherTrainEntry> trains, List<DispatcherSectionEntry> sections,
                           List<DispatcherEventEntry> events, int occupiedSections,
                           int reservedSections, int conflicts, int totalSections) {
    }

    private record Anchor(String dimension, int x, int y, int z, boolean held) {
    }

    private DispatcherService() {
    }

    public static Snapshot snapshot(MinecraftServer server) {
        List<DispatcherTrainEntry> result = new ArrayList<>();
        Set<UUID> seenSableConsists = new HashSet<>();
        Map<UUID, String> trainNames = new HashMap<>();

        for (Train train : new ArrayList<>(Create.RAILWAYS.trains.values())) {
            if (train == null || train.invalid || CreateTrainShadow.isShadow(train)) continue;
            DispatcherTrainEntry entry = createEntry(train);
            if (entry != null) {
                result.add(entry);
                trainNames.put(entry.id(), entry.name());
            }
        }

        for (SableTrain train : SableTrainRegistry.all()) {
            Train shadow = CreateTrainShadow.of(train.id());
            if (shadow != null && CreateTrainShadow.isShadow(shadow)) {
                if (!seenSableConsists.add(shadow.id)) continue;
                List<SableTrain> members = CreateTrainShadow.carsOf(shadow);
                if (members.isEmpty()) members = List.of(train);
                DispatcherTrainEntry entry = sableConsistEntry(shadow, members);
                if (entry != null) {
                    result.add(entry);
                    trainNames.put(entry.id(), entry.name());
                }
            } else {
                if (!seenSableConsists.add(train.id())) continue;
                DispatcherTrainEntry entry = sableEntry(train, List.of(train));
                if (entry != null) {
                    result.add(entry);
                    trainNames.put(entry.id(), entry.name());
                }
            }
        }

        result.sort(Comparator.comparing(DispatcherTrainEntry::type)
                .thenComparing(DispatcherTrainEntry::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(e -> e.id().toString()));

        Map<UUID, Anchor> anchors = collectAnchors();
        Set<UUID> allSectionIds = new HashSet<>(Create.RAILWAYS.signalEdgeGroups.keySet());
        allSectionIds.addAll(TrainSignals.trackedGroups());
        allSectionIds.addAll(anchors.keySet());

        List<DispatcherSectionEntry> sections = new ArrayList<>();
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
            boolean isReserved = (group != null && group.reserved != null) || TrainSignals.reserved(sectionId);
            if (!occupants.isEmpty()) occupied++;
            if (isReserved) reserved++;
            if (occupants.size() > 1) conflicts++;

            int state = occupants.size() > 1 ? DispatcherSectionEntry.CONFLICT
                    : !occupants.isEmpty() ? DispatcherSectionEntry.OCCUPIED
                    : isReserved ? DispatcherSectionEntry.RESERVED
                    : DispatcherSectionEntry.CLEAR;

            StringBuilder trainsLabel = new StringBuilder();
            for (UUID id : occupants) {
                if (trainsLabel.length() > 0) trainsLabel.append(", ");
                trainsLabel.append(trainNames.getOrDefault(id, shortId(id)));
                if (trainsLabel.length() > 48) {
                    trainsLabel.setLength(45);
                    trainsLabel.append("\u2026");
                    break;
                }
            }
            if (isReserved && occupants.isEmpty()) {
                UUID reservedBy = TrainSignals.reservedBy(sectionId);
                if (reservedBy != null) {
                    trainsLabel.append(trainNames.getOrDefault(reservedBy, shortId(reservedBy)));
                }
            }

            Anchor anchor = anchors.get(sectionId);
            String dimension = anchor == null ? "" : anchor.dimension();
            int x = anchor == null ? 0 : anchor.x();
            int y = anchor == null ? 0 : anchor.y();
            int z = anchor == null ? 0 : anchor.z();
            boolean held = anchor != null && anchor.held();
            sections.add(new DispatcherSectionEntry(sectionId, state, trainsLabel.toString(),
                    dimension, x, y, z, held));
        }

        sections.sort(Comparator.comparingInt((DispatcherSectionEntry s) -> -s.state())
                .thenComparing(s -> s.id().toString()));
        if (sections.size() > 48) {
            sections = new ArrayList<>(sections.subList(0, 48));
        }

        DispatcherJournal.observe(server, result, sections);
        return new Snapshot(List.copyOf(result), List.copyOf(sections),
                DispatcherJournal.recent(server), occupied, reserved, conflicts, allSectionIds.size());
    }

    private static Map<UUID, Anchor> collectAnchors() {
        Map<UUID, Anchor> anchors = new HashMap<>();
        if (Create.RAILWAYS == null) return anchors;
        for (TrackGraph graph : new ArrayList<>(Create.RAILWAYS.trackNetworks.values())) {
            for (SignalBoundary signal : graph.getPoints(EdgePointType.SIGNAL)) {
                for (boolean primary : new boolean[] { true, false }) {
                    UUID group = signal.groups.get(primary);
                    if (group == null || signal.blockEntities.get(primary).isEmpty()) continue;
                    boolean held = SignalOverrides.held(signal.getId(), primary);
                    BlockPos pos = signal.blockEntities.get(primary).keySet().iterator().next();
                    String dimension = "";
                    try {
                        dimension = signal.edgeLocation.getFirst().getDimension().location().toString();
                    } catch (Exception ignored) {
                    }
                    Anchor existing = anchors.get(group);
                    if (existing == null) {
                        anchors.put(group, new Anchor(dimension, pos.getX(), pos.getY(), pos.getZ(), held));
                    } else if (held && !existing.held()) {
                        anchors.put(group, new Anchor(existing.dimension(), existing.x(), existing.y(),
                                existing.z(), true));
                    }
                }
            }
        }
        return anchors;
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
        boolean signalHeld = members.stream().anyMatch(t -> TrainSignals.isHeld(t.id()));
        Set<UUID> occupiedGroups = new HashSet<>();
        Set<UUID> reservedGroups = new HashSet<>();
        for (SableTrain member : members) {
            occupiedGroups.addAll(TrainSignals.groupsOf(member.id()));
            reservedGroups.addAll(TrainSignals.reservedGroupsOf(member.id()));
        }
        double speed = Double.isFinite(shadow.speed) ? shadow.speed * 20.0 : lead.speed() * 20.0;
        int state = derailed ? DispatcherTrainEntry.DERAILED : held ? DispatcherTrainEntry.DISPATCH_HELD
                : signalHeld ? DispatcherTrainEntry.WAITING
                : Math.abs(speed) > 0.02 ? DispatcherTrainEntry.MOVING : DispatcherTrainEntry.STOPPED;
        String name = shadow.name == null ? "" : shadow.name.getString().trim();
        if (name.isEmpty()) name = "Loconautics " + shortId(shadow.id);
        return new DispatcherTrainEntry(shadow.id, "loconautics", name, dimension.location().toString(),
                floor(position.x), floor(position.y), floor(position.z), speed, state,
                occupiedGroups.size(), reservedGroups.size(), held);
    }

    private static DispatcherTrainEntry sableEntry(SableTrain train, List<SableTrain> members) {
        if (train.level() == null) return null;
        Vec3 position = positionOf(train);
        if (position == null) return null;
        boolean held = DispatcherTrainControl.isHeld(train.id());
        boolean signalHeld = TrainSignals.isHeld(train.id());
        int state = train.isDerailed() ? DispatcherTrainEntry.DERAILED : held
                ? DispatcherTrainEntry.DISPATCH_HELD : signalHeld ? DispatcherTrainEntry.WAITING
                : Math.abs(train.speed()) > 0.02
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

    public static boolean setLoconauticsHold(UUID trainId, boolean held) {
        SableTrain train = SableTrainRegistry.get(trainId);
        if (train == null) return false;
        Train shadow = CreateTrainShadow.of(trainId);
        List<SableTrain> members = shadow != null && CreateTrainShadow.isShadow(shadow)
                ? CreateTrainShadow.carsOf(shadow) : List.of(train);
        if (members.isEmpty()) members = List.of(train);
        boolean was = members.stream().anyMatch(t -> DispatcherTrainControl.isHeld(t.id()));
        for (SableTrain member : members) DispatcherTrainControl.setHeld(member.id(), held);
        if (was != held) {
            String name = train.name() == null || train.name().isBlank()
                    ? shortId(train.id()) : train.name();
            DispatcherJournal.record(held ? DispatcherJournal.HELD : DispatcherJournal.RELEASED, name);
        }
        return true;
    }

    /** Force every signal side that protects this section to red, or release those holds. */
    public static boolean setSectionHold(UUID sectionId, boolean hold) {
        if (sectionId == null || Create.RAILWAYS == null) return false;
        int changed = 0;
        for (TrackGraph graph : new ArrayList<>(Create.RAILWAYS.trackNetworks.values())) {
            for (SignalBoundary signal : graph.getPoints(EdgePointType.SIGNAL)) {
                for (boolean primary : new boolean[] { true, false }) {
                    if (!sectionId.equals(signal.groups.get(primary))) continue;
                    if (signal.blockEntities.get(primary).isEmpty()) continue;
                    if (hold) SignalOverrides.hold(signal.getId(), primary);
                    else SignalOverrides.release(signal.getId(), primary);
                    changed++;
                }
            }
        }
        if (changed == 0) return false;
        DispatcherJournal.record(hold ? DispatcherJournal.SIGNAL_CLOSED : DispatcherJournal.SIGNAL_OPENED,
                shortId(sectionId));
        return true;
    }
}
