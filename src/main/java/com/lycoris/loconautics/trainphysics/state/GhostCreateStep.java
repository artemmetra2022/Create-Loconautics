package com.lycoris.loconautics.trainphysics.state;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.boiler.BoilerGhost;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.debug.CartMoveTrace;
import com.lycoris.loconautics.trainphysics.debug.GhostConsistTrace;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import com.lycoris.loconautics.trainphysics.spacing.TrainSpacing;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.lycoris.loconautics.foundation.LogFormat.shortId;

// server only
public final class GhostCreateStep {

    public static final CartStateStore STORE = new Store();

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    // the gaps the cars last held while loaded, kept apart from state because every cart event drops state
    private static final Map<UUID, Rest> REST = new ConcurrentHashMap<>();

    // cars whose rail points still come from their live body, only those readings may teach a rest gap
    private static final Set<UUID> FROM_LIVE = ConcurrentHashMap.newKeySet();

    private static final Map<UUID, Long> SETTLED = new ConcurrentHashMap<>();

    private static final Map<UUID, Long> WARNED = new ConcurrentHashMap<>();

    private record Rest(List<UUID> carts, double[] gaps) {}

    private record Settled(double[] held, boolean moved) {}

    public record Outcome(List<SableTrain> cars, boolean held) {}

    private static final class State {
        long lastStep = Long.MIN_VALUE / 2;
        List<CreateTrainShadow.Member> layout = List.of();
        long portalHeldAt = Long.MIN_VALUE / 2;
        boolean held;
        double[] exactGaps = new double[0];
    }

    private record Pose(TravellingPoint point, TrackNode node1, TrackNode node2, TrackEdge edge, double position,
                        boolean upsideDown) {}

    private GhostCreateStep() {
    }

    public static void clear() {
        STATES.clear();
        REST.clear();
        FROM_LIVE.clear();
        SETTLED.clear();
        WARNED.clear();
    }

    public static Outcome step(SableTrain train, boolean split) {
        ServerLevel level = train.level();
        long now = level.getGameTime();
        Train shadow = CreateTrainShadow.of(train.id());
        List<SableTrain> cars = shadow == null ? List.of() : CreateTrainShadow.carsOf(shadow);
        if (cars.isEmpty() || shadow.graph == null || !cars.contains(train)) {
            stop(List.of(train));
            warn(train.id(), now, "no shadow to move train={} shadow={} graph={} cars={}", shortId(train.id()),
                    shadow != null, shadow != null && shadow.graph != null, cars.size());
            return new Outcome(List.of(train), false);
        }
        State state = STATES.computeIfAbsent(shadow.id, id -> new State());
        String reason = holdReason(cars, split);
        if (reason != null) {
            stop(cars);
            state.lastStep = Long.MIN_VALUE / 2;
            warn(shadow.id, now, "hold train={} cars={} reason={}", shortId(shadow.id), cars.size(), reason);
            return new Outcome(cars, false);
        }
        for (SableTrain car : cars) {
            if (!TrainRailAdvance.railsValid(car)) {
                stop(cars);
                state.lastStep = Long.MIN_VALUE / 2;
                warn(shadow.id, now, "hold train={} reason=a rail of {} no longer sits on its track",
                        shortId(shadow.id), shortId(car.id()));
                return new Outcome(cars, false);
            }
        }
        CreateTrainShadow.refreshFromCars(shadow, cars);

        List<CreateTrainShadow.Member> layout = CreateTrainShadow.membersOf(shadow);
        SableTrain leader = GhostConsist.leader(cars);
        BoilerGhost.step(leader, cars);
        leader.tickMotion();
        double[] sense = new double[cars.size()];
        for (int i = 0; i < sense.length; i++) {
            sense[i] = CreateTrainShadow.senseOf(cars.get(i), layout.get(i).reversed());
        }
        int lead = cars.indexOf(leader);
        double shadowTarget = leader.targetSpeed() * sense[lead];
        for (int i = 0; i < sense.length; i++) {
            if (i != lead) {
                cars.get(i).setTargetSpeed(shadowTarget * sense[i]);
                cars.get(i).setSpeed(leader.speed() * sense[lead] * sense[i]);
            }
        }
        double speedIn = leader.speed();
        double ds = TrainSpacing.ghostStep(leader);
        boolean portalWait = now - state.portalHeldAt < LoconauticsConstants.GHOSTCREATE_PORTAL_RETRY_TICKS;
        if (portalWait) {
            ds = 0.0;
        }
        double carried = leader.speed() * sense[lead];
        for (int i = 0; i < sense.length; i++) {
            if (i != lead) {
                cars.get(i).setSpeed(carried * sense[i]);
            }
        }
        if (ds != 0.0) {
            state.held = move(shadow, cars, layout, sense, lead, ds, shadowTarget, state, now);
        } else if (portalWait) {
            state.held = true;
        }
        for (int i = 0; i < cars.size(); i++) {
            double relative = sense[i] * sense[lead];
            GhostConsistTrace.noteStep(cars.get(i), speedIn * relative, ds * relative, split, state.held,
                    cars.size(), cars.get(i) == leader ? null : leader);
            CartMoveTrace.step(cars.get(i), ds * relative);
        }
        return new Outcome(cars, state.held);
    }

    // only a body that unloaded while live, a refiled body went to disk from a ghost and its reading is the ghosts
    public static void noteFromLive(SableTrain train) {
        FROM_LIVE.add(train.id());
    }

    // creates spacing is whole blocks, so this is the real gap its spring has to hold
    public static double exactGap(Train shadow, int pair) {
        State state = STATES.get(shadow.id);
        return state == null || pair < 0 || pair >= state.exactGaps.length ? Double.NaN : state.exactGaps[pair];
    }

    public static void writeRest(UUID trainId, CompoundTag tag) {
        Rest rest = REST.get(trainId);
        if (rest == null) {
            return;
        }
        ListTag carts = new ListTag();
        for (UUID cart : rest.carts()) {
            carts.add(NbtUtils.createUUID(cart));
        }
        ListTag gaps = new ListTag();
        for (double gap : rest.gaps()) {
            gaps.add(DoubleTag.valueOf(gap));
        }
        tag.put("RestCarts", carts);
        tag.put("RestGaps", gaps);
    }

    // the gaps a rake held while loaded survive a restart, so the first ghost step after a reload trusts them
    public static void readRest(UUID trainId, CompoundTag tag) {
        ListTag carts = tag.getList("RestCarts", Tag.TAG_INT_ARRAY);
        ListTag gaps = tag.getList("RestGaps", Tag.TAG_DOUBLE);
        if (carts.isEmpty() || gaps.size() != carts.size() - 1) {
            return;
        }
        List<UUID> ids = new ArrayList<>(carts.size());
        for (Tag cart : carts) {
            ids.add(NbtUtils.loadUUID(cart));
        }
        double[] values = new double[gaps.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = gaps.getDouble(i);
        }
        REST.put(trainId, new Rest(ids, values));
    }

    // a consist comes back from one shadow state, with every pair of cars on its rest gap
    public static void settle(SableTrain train) {
        Train shadow = CreateTrainShadow.of(train.id());
        List<SableTrain> cars = shadow == null || shadow.graph == null ? List.of() : CreateTrainShadow.carsOf(shadow);
        if (cars.size() < 2 || !cars.contains(train)) {
            return;
        }
        ServerLevel level = train.level();
        if (SETTLED.getOrDefault(shadow.id, -1L) == level.getGameTime()) {
            return;
        }
        for (SableTrain car : cars) {
            if (!car.isGhost() || car.isDerailed() || car.isPhysics() || car.level() != level
                    || !TrainRailAdvance.railsValid(car)) {
                return;
            }
        }
        SETTLED.put(shadow.id, level.getGameTime());
        CreateTrainShadow.refreshFromCars(shadow, cars);
        Settled settled = settleGaps(shadow, cars, CreateTrainShadow.membersOf(shadow), level, false);
        if (settled.moved()) {
            CreateTrainShadow.copyBack(shadow, cars);
        }
    }

    // only a gap shorter than the rest gap is distrusted, it puts the cars inside each other, a longer one is kept as it is
    private static Settled settleGaps(Train shadow, List<SableTrain> cars, List<CreateTrainShadow.Member> layout,
                                      ServerLevel level, boolean live) {
        List<UUID> carts = layout.stream().map(CreateTrainShadow.Member::cart).toList();
        Rest known = REST.get(shadow.id);
        boolean remembered = known != null && known.carts().equals(carts);
        double tolerance = remembered ? LoconauticsConstants.GHOSTCREATE_GAP_TOLERANCE_BLOCKS
                : LoconauticsConstants.GHOSTCREATE_NOMINAL_SLACK_BLOCKS;
        double[] held = new double[cars.size() - 1];
        double[] kept = new double[held.length];
        boolean moved = false;
        for (int pair = 0; pair < held.length; pair++) {
            double measured = CreateTrainShadow.gap(shadow, pair + 1);
            double rest = remembered ? known.gaps()[pair] : CreateTrainShadow.nominalGap(cars, layout, pair);
            held[pair] = measured;
            kept[pair] = Double.isNaN(measured) && remembered ? rest : measured;
            if (Double.isNaN(measured) || Double.isNaN(rest) || measured >= rest - tolerance) {
                continue;
            }
            boolean placed = spread(shadow, level, pair + 1, rest);
            held[pair] = CreateTrainShadow.gap(shadow, pair + 1);
            kept[pair] = placed ? held[pair] : rest;
            moved |= placed;
            warn(shadow.id, level.getGameTime(), "handoff gap rejected train={} pair={}to{} measured={} rest={} remembered={} placed={} now={}",
                    shortId(shadow.id), pair, pair + 1, fmt(measured), fmt(rest), remembered, placed, fmt(held[pair]));
        }
        CreateTrainShadow.holdSpacing(shadow, held);
        if (live) {
            REST.put(shadow.id, new Rest(carts, kept));
        }
        return new Settled(held, moved);
    }

    // moves the cars from this index back along the track until the gap in front of the first of them is the rest gap
    private static boolean spread(Train shadow, ServerLevel level, int index, double rest) {
        List<Pose> before = poses(shadow);
        boolean placed = false;
        for (int step = 0; step <= LoconauticsConstants.GHOSTCREATE_REPAIR_STEPS; step++) {
            double error = rest - CreateTrainShadow.gap(shadow, index);
            if (Double.isNaN(error)) {
                break;
            }
            if (Math.abs(error) < LoconauticsConstants.GHOSTCREATE_REPAIR_EPSILON_BLOCKS) {
                placed = true;
                break;
            }
            if (step == LoconauticsConstants.GHOSTCREATE_REPAIR_STEPS || !shift(shadow, level, index, -error)) {
                break;
            }
        }
        if (!placed) {
            restore(shadow, before, level);
        }
        return placed;
    }

    private static boolean shift(Train shadow, ServerLevel level, int from, double distance) {
        int count = shadow.carriages.size();
        boolean blocked = false;
        for (int n = 0; n < count - from; n++) {
            int index = distance < 0.0 ? count - 1 - n : from + n;
            TravellingPoint ahead = shadow.carriages.get(index - 1).getTrailingPoint();
            TravellingPoint behind = index == count - 1 ? null : shadow.carriages.get(index + 1).getLeadingPoint();
            Carriage carriage = shadow.carriages.get(index);
            carriage.travel(level, shadow.graph, distance, ahead, behind, LoconauticsConstants.CREATE_CARRIAGE_MIDDLE);
            blocked |= carriage.blocked;
        }
        for (TravellingPoint point : pointsOf(shadow)) {
            point.blocked = false;
        }
        return !blocked;
    }

    public static double spacingOffset(Train shadow, int pair) {
        double exact = exactGap(shadow, pair);
        return Double.isNaN(exact) || pair >= shadow.carriageSpacing.size() ? 0.0 : shadow.carriageSpacing.get(pair) - exact;
    }

    public static void noteCreateArrival(Train shadow, GlobalStation station) {
        ServerLevel level = CreateTrainShadow.carsOf(shadow).stream().findFirst().map(SableTrain::level).orElse(null);
        if (level != null) {
            warn(shadow.id, level.getGameTime(), "arriveAt cancelled on the shadow train={} station={}",
                    shortId(shadow.id), station.name);
        }
    }

    private static boolean move(Train shadow, List<SableTrain> cars, List<CreateTrainShadow.Member> layout,
                                double[] sense, int lead, double ds, double shadowTarget, State state, long now) {
        ServerLevel level = cars.get(0).level();
        boolean cold = now - state.lastStep > 1 || !layout.equals(state.layout);
        if (cold) {
            boolean live = cars.stream().anyMatch(car -> FROM_LIVE.contains(car.id()));
            Settled settled = settleGaps(shadow, cars, layout, level, live);
            cars.forEach(car -> FROM_LIVE.remove(car.id()));
            state.exactGaps = settled.held();
            if (settled.moved()) {
                CreateTrainShadow.copyBack(shadow, cars);
            }
        }
        List<Pose> before = poses(shadow);
        double speedIn = ds * sense[lead];
        shadow.speed = speedIn;
        shadow.targetSpeed = shadowTarget;
        shadow.derailed = false;
        shadow.invalid = false;
        shadow.carriageWaitingForChunks = -1;
        shadow.speedBeforeStall = null;
        GlobalStation destination = shadow.navigation.destination;
        shadow.navigation.destination = null;
        try {
            shadow.tick(level);
        } finally {
            shadow.navigation.destination = destination;
        }
        double after = shadow.speed;
        boolean stressed = shadow.derailed;
        boolean invalid = shadow.invalid;
        boolean blocked = shadow.carriages.stream().anyMatch(carriage -> carriage.blocked);
        boolean crossed = crossesPortal(shadow, level.dimension());
        shadow.derailed = false;
        shadow.invalid = false;
        if (invalid) {
            warn(shadow.id, now, "shadow was marked invalid and reset train={}", shortId(shadow.id));
        }
        if (crossed || stressed) {
            restore(shadow, before, level);
            if (crossed) {
                state.portalHeldAt = now;
                warn(shadow.id, now, "portal hold train={} the points reached another dimension, held on this side",
                        shortId(shadow.id));
                return true;
            }
            warn(shadow.id, now, "stress hold train={} coupling stress went over the limit, points put back gaps={} spacing={}",
                    shortId(shadow.id), gaps(shadow), shadow.carriageSpacing);
            stop(cars);
            return false;
        }
        CreateTrainShadow.copyBack(shadow, cars);
        state.lastStep = now;
        state.layout = layout;
        if (after != speedIn) {
            for (int i = 0; i < cars.size(); i++) {
                cars.get(i).setSpeed(after * sense[i]);
                if (after == 0.0) {
                    cars.get(i).setTargetSpeed(0.0);
                }
            }
        }
        if (blocked) {
            warn(shadow.id, now, "dead end train={} the points ran out of track, speed zeroed", shortId(shadow.id));
        }
        return blocked;
    }

    private static String holdReason(List<SableTrain> cars, boolean split) {
        if (split) {
            return "a car of the consist is loaded";
        }
        ServerLevel level = cars.get(0).level();
        for (SableTrain car : cars) {
            if (!car.isGhost()) {
                return shortId(car.id()) + " is loaded";
            }
            if (car.isDerailed() || car.isPhysics()) {
                return shortId(car.id()) + " is derailed";
            }
            if (car.level() != level || car.car() == null) {
                return shortId(car.id()) + " is in another level";
            }
        }
        return null;
    }

    private static void stop(List<SableTrain> cars) {
        for (SableTrain car : cars) {
            car.setTargetSpeed(0.0);
            car.setSpeed(0.0);
        }
    }

    private static boolean crossesPortal(Train shadow, ResourceKey<Level> home) {
        for (Pose pose : poses(shadow)) {
            TravellingPoint point = pose.point();
            if (point.node1 != null && (!point.node1.getLocation().dimension.equals(home)
                    || point.edge != null && point.edge.isInterDimensional())) {
                return true;
            }
        }
        return false;
    }

    private static List<TravellingPoint> pointsOf(Train shadow) {
        List<TravellingPoint> points = new ArrayList<>(shadow.carriages.size() * 4);
        for (Carriage carriage : shadow.carriages) {
            points.add(carriage.leadingBogey().leading());
            points.add(carriage.leadingBogey().trailing());
            if (carriage.isOnTwoBogeys()) {
                points.add(carriage.trailingBogey().leading());
                points.add(carriage.trailingBogey().trailing());
            }
        }
        return points;
    }

    private static List<Pose> poses(Train shadow) {
        List<Pose> poses = new ArrayList<>();
        for (TravellingPoint point : pointsOf(shadow)) {
            poses.add(new Pose(point, point.node1, point.node2, point.edge, point.position, point.upsideDown));
        }
        return poses;
    }

    private static void restore(Train shadow, List<Pose> poses, ServerLevel level) {
        for (Pose pose : poses) {
            TravellingPoint point = pose.point();
            point.node1 = pose.node1();
            point.node2 = pose.node2();
            point.edge = pose.edge();
            point.position = pose.position();
            point.upsideDown = pose.upsideDown();
            point.blocked = false;
        }
        for (Carriage carriage : shadow.carriages) {
            carriage.updateContraptionAnchors();
            carriage.manageEntities(level);
        }
    }

    private static void warn(UUID id, long now, String message, Object... args) {
        Long last = WARNED.get(id);
        if (last != null && now - last < LoconauticsConstants.GHOSTCREATE_WARN_TICKS) {
            return;
        }
        WARNED.put(id, now);
        LoconauticsConstants.TRACE.warn(LoconauticsConstants.GHOSTCREATE_TAG + " " + message, args);
    }

    private static String gaps(Train shadow) {
        StringBuilder text = new StringBuilder();
        for (int i = 1; i < shadow.carriages.size(); i++) {
            text.append(i - 1).append("to").append(i).append('=').append(fmt(CreateTrainShadow.gap(shadow, i)))
                    .append(' ');
        }
        return text.toString().trim();
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static final class Store implements CartStateStore {

        @Override
        public void forget(SableTrain train, UUID subId, CartEvent event) {
            FROM_LIVE.remove(train.id());
            Train shadow = CreateTrainShadow.of(train.id());
            if (shadow != null) {
                STATES.remove(shadow.id);
            }
        }

        @Override
        public void retain(Set<UUID> liveTrains, Set<UUID> liveSubs) {
            STATES.keySet().retainAll(liveTrains);
            REST.keySet().retainAll(liveTrains);
            FROM_LIVE.retainAll(liveTrains);
            SETTLED.keySet().retainAll(liveTrains);
            WARNED.keySet().retainAll(liveTrains);
        }

        @Override
        public String describe(UUID trainId, UUID subId) {
            State state = STATES.get(trainId);
            return state == null ? "" : "ghostcreate held=" + state.held;
        }
    }
}
