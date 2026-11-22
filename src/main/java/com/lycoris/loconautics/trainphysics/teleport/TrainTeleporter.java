package com.lycoris.loconautics.trainphysics.teleport;

import com.lycoris.loconautics.trainphysics.DerailReason;
import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.SableTeleportation;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.boiler.firebox.FireboxBlockEntity;
import com.lycoris.loconautics.content.knuckle.KnuckleBlockEntity;
import com.lycoris.loconautics.network.packets.train.SableTrainSyncPacket;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.sublevel.SubLevelContacts;
import com.lycoris.loconautics.trainphysics.state.CartEvent;
import com.lycoris.loconautics.trainphysics.state.CartLifecycle;
import com.lycoris.loconautics.trainphysics.portal.PortalFrame;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.spawn.PlacementClearance;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.lycoris.loconautics.trainphysics.spawn.SubLevelSplitWatcher;
import com.lycoris.loconautics.trainphysics.state.SableTrainPersistence;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class TrainTeleporter {

    private TrainTeleporter() {
    }

    public record Result(@Nullable String failure, int moved, List<SableTrain> cars) {
        public boolean ok() {
            return failure == null;
        }
    }

    private record Landing(SableTrain train, ServerSubLevel sub, Vector3d origin, Quaterniond orient,
                           @Nullable SableTrainSpawner.SeatPose seat, double railTravelSign) {}

    private record Home(ServerLevel level, Vector3d position, Quaterniond orient,
                         @Nullable SableTeleportation.PlotSlot slot, Map<BlockPos, UUID> drivers,
                         @Nullable Vector3d carriedVelocity) {}

    private enum Mode { RAIL, FREE, HANDOVER }

    private static final TeleportStrands.Shift NO_SHIFT = new TeleportStrands.Shift(0, 0);

    public static Result teleport(SableTrain train, ServerLevel destLevel, Vec3 position, double yawDeg,
                                  Consumer<String> trace) {
        List<SableTrain> cars = chain(train);
        String refusal = refuse(cars);
        if (refusal != null) {
            return new Result(refusal, 0, cars);
        }
        List<Landing> plan = freeDropPlan(cars, position, yawDeg);
        if (plan == null) {
            return new Result("one of the cars has no live body", 0, cars);
        }
        return run(plan, destLevel, Mode.FREE, trace);
    }

    public static Result teleportToRail(SableTrain train, ServerLevel destLevel, TrackGraphLocation loc,
                                        Vec3 upNormal, Vec3 look, Consumer<String> trace) {
        return teleportToRail(train, destLevel, loc, upNormal, look, look, trace);
    }

    public static Result teleportToRail(SableTrain train, ServerLevel destLevel, TrackGraphLocation loc,
                                        Vec3 upNormal, Vec3 look, @Nullable Vec3 facing, Consumer<String> trace) {
        List<SableTrain> cars = chain(train);
        String refusal = refuse(cars);
        if (refusal != null) {
            return new Result(refusal, 0, cars);
        }
        List<TrackGraphLocation> spots = SableTrainSpawner.chainSpots(cars, loc, upNormal, look);
        if (spots == null) {
            return new Result("not enough track past there for the whole train", 0, cars);
        }
        if (PlacementClearance.blocker(destLevel, cars, spots, upNormal) != null) {
            return new Result("another train is on the track there", 0, cars);
        }
        List<Landing> plan = new ArrayList<>();
        for (int i = 0; i < cars.size(); i++) {
            SableTrain car = cars.get(i);
            ServerSubLevel sub = SubLevelData.resolve(car.level(), car.car().subLevelId());
            if (sub == null) {
                return new Result("car " + shortId(car) + " has no live body", 0, cars);
            }
            Vec3 carFacing = facing != null ? facing : (car.speed() < 0.0 ? look.reverse() : look);
            SableTrainSpawner.SeatPose seat =
                    SableTrainSpawner.seatPoseAt(car.level(), car, spots.get(i), upNormal, carFacing, false);
            if (seat == null || seat.origin() == null) {
                return new Result("car " + shortId(car) + " has no usable rail pose there", 0, cars);
            }
            plan.add(new Landing(car, sub, seat.origin(), seat.orient(), seat,
                    carFacing.dot(look) < 0.0 ? -1.0 : 1.0));
        }
        return run(plan, destLevel, Mode.RAIL, trace);
    }

    @Nullable
    public static String handOverRefusal(SableTrain train) {
        return refuse(chain(train));
    }

    public static Result handOver(SableTrain train, PortalFrame frame, Consumer<String> trace) {
        List<SableTrain> cars = chain(train);
        String refusal = refuse(cars);
        if (refusal != null) {
            return new Result(refusal, 0, cars);
        }
        ServerLevel destLevel = train.level().getServer().getLevel(frame.farDim());
        if (destLevel == null) {
            return new Result("no level loaded for " + frame.farDim().location(), 0, cars);
        }
        List<Landing> plan = new ArrayList<>();
        for (SableTrain car : cars) {
            ServerSubLevel sub = SubLevelData.resolve(car.level(), car.car().subLevelId());
            if (sub == null || car.level() != train.level()) {
                return new Result("car " + shortId(car) + " has no live body on this side", 0, cars);
            }
            plan.add(new Landing(car, sub, frame.toFar(sub.logicalPose().position()),
                    frame.orientToFar(sub.logicalPose().orientation()), null, 1.0));
        }
        Result result = run(plan, destLevel, Mode.HANDOVER, trace);
        PortalFrame turned = frame.inverted();
        for (SableTrain car : result.cars()) {
            if (car.level() == destLevel) {
                for (RailCarriage rail : rails(car)) {
                    rail.setPortal(turned);
                }
            }
        }
        return result;
    }

    private static List<RailCarriage> rails(SableTrain train) {
        List<RailCarriage> rails = new ArrayList<>();
        if (train.car().carriage() != null) {
            rails.add(train.car().carriage());
        }
        for (SableTrain.Bogey bogey : train.car().bogeys()) {
            if (bogey.rail() != null) {
                rails.add(bogey.rail());
            }
        }
        return rails;
    }

    private static List<SableTrain> chain(SableTrain train) {
        List<SableTrain> cars = SableTrainSpawner.chainCars(train.level(), train.car().subLevelId());
        return cars.isEmpty() ? List.of(train) : cars;
    }

    @Nullable
    private static String refuse(List<SableTrain> cars) {
        Set<UUID> inChain = new HashSet<>();
        for (SableTrain car : cars) {
            if (car.car() != null && car.car().subLevelId() != null) {
                inChain.add(car.car().subLevelId());
            }
            if (car.level() != cars.get(0).level()) {
                return "car " + shortId(car) + " is not in the same level as the rest of the train";
            }
        }
        for (SableTrain car : cars) {
            if (car.car() == null || car.car().subLevelId() == null) {
                return "a car in the chain has no body";
            }
            if (car.isPhysics()) {
                return "car " + shortId(car) + " is a free physics body, not a pinned one";
            }
            if (SableTeleportation.isInFlight(car.car().subLevelId())) {
                return "car " + shortId(car) + " is already mid teleport";
            }
            for (UUID mate : TrainConsist.neighbours(car.level(), car.car().subLevelId())) {
                if (inChain.contains(mate)) {
                    continue;
                }
                if (SubLevelData.resolve(car.level(), mate) == null) {
                    return "car " + shortId(car) + " is coupled to a car that is not loaded";
                }
                if (SableTrainRegistry.bySubLevel(mate) != null) {
                    return "car " + shortId(car) + " is coupled to a car that is not moving with it";
                }
            }
        }
        return null;
    }

    @Nullable
    private static List<Landing> freeDropPlan(List<SableTrain> cars, Vec3 position, double yawDeg) {
        List<ServerSubLevel> subs = new ArrayList<>();
        for (SableTrain car : cars) {
            ServerSubLevel sub = SubLevelData.resolve(car.level(), car.car().subLevelId());
            if (sub == null) {
                return null;
            }
            subs.add(sub);
        }
        ServerSubLevel lead = subs.get(0);
        Quaterniond target = new Quaterniond().rotateY(Math.toRadians(yawDeg));
        Quaterniondc leadOrient = lead.logicalPose().orientation();
        Quaterniond turn = new Quaterniond(target).mul(new Quaterniond(leadOrient).invert());
        Vector3d leadOrigin = new Vector3d(lead.logicalPose().position());

        Vector3d landing = new Vector3d(position.x, position.y + SableTeleportation.baseOffsetY(lead), position.z);

        List<Landing> plan = new ArrayList<>();
        for (int i = 0; i < cars.size(); i++) {
            ServerSubLevel sub = subs.get(i);
            Vector3d offset = new Vector3d(sub.logicalPose().position()).sub(leadOrigin);
            turn.transform(offset);
            Vector3d origin = new Vector3d(landing).add(offset);
            Quaterniond orient = new Quaterniond(turn).mul(new Quaterniond(sub.logicalPose().orientation()));
            plan.add(new Landing(cars.get(i), sub, origin, orient, null, 1.0));
        }
        return plan;
    }

    private static Result run(List<Landing> plan, ServerLevel destLevel, Mode mode, Consumer<String> trace) {
        SubLevelSplitWatcher.ensureObserver(destLevel);
        Map<UUID, SableTeleportation.PlotSlot> seats = new HashMap<>();
        Set<SableTeleportation.PlotSlot> claimed = new HashSet<>();
        for (Landing landing : plan) {
            if (landing.sub().getLevel() == destLevel) {
                continue;
            }
            SableTeleportation.PlotSlot slot = SableTeleportation.plotSlot(landing.sub());
            if (slot == null) {
                return new Result("car " + shortId(landing.train()) + " has no plot slot", 0, cars(plan));
            }
            if (claimed.contains(slot) || !SableTeleportation.plotSlotFree(destLevel, slot)) {
                slot = SableTeleportation.firstFreeSlot(destLevel, claimed);
            }
            if (slot == null) {
                return new Result("no free plot left in " + destLevel.dimension().location()
                        + " for car " + shortId(landing.train()), 0, cars(plan));
            }
            claimed.add(slot);
            seats.put(landing.sub().getUniqueId(), slot);
            trace.accept("car " + shortId(landing.train()) + " takes plot " + slot.x() + ", " + slot.z() + " in "
                    + destLevel.dimension().location());
        }
        for (Landing landing : plan) {
            String refusal = SableTeleportation.preflight(landing.sub(), destLevel,
                    seats.get(landing.sub().getUniqueId())).refusal();
            if (refusal != null) {
                return new Result(refusal, 0, cars(plan));
            }
        }

        Set<UUID> moving = new HashSet<>();
        List<Home> homes = new ArrayList<>();
        for (Landing landing : plan) {
            moving.add(landing.sub().getUniqueId());
            homes.add(home(landing, mode));
        }

        ServerLevel sourceLevel = plan.get(0).sub().getLevel();
        TeleportStrands.Carry strands = TeleportStrands.capture(sourceLevel, moving);
        LoconauticsConstants.LOGGER.info("[issue281] {} cars={} strands carried={} cut={} from {} to {}", mode, plan.size(),
                strands.carried(), strands.cut(), sourceLevel.dimension().location(), destLevel.dimension().location());
        Map<UUID, TeleportStrands.Shift> shifts = new HashMap<>();
        Map<UUID, ServerLevel> where = new HashMap<>();
        List<ServerSubLevel> arrived = new ArrayList<>();

        for (int i = 0; i < plan.size(); i++) {
            Landing landing = plan.get(i);
            CartLifecycle.beforeTeleport(landing.train());
            releaseContacts(landing.sub());

            SableTeleportation.Result result = SableTeleportation.teleport(landing.sub(), destLevel,
                    landing.origin(), landing.orient(), seats.get(landing.sub().getUniqueId()));
            if (!result.ok()) {
                LoconauticsConstants.LOGGER.error("[teleport] train {} stopped: {}", landing.train().id(),
                        result.failure());
                if (result.lost()) {
                    SubLevelSplitWatcher.forgetRemoved(landing.sub().getUniqueId());
                }
                return rollBack(plan, homes, arrived, destLevel, strands, where, shifts, moving, result.failure(), mode,
                        trace);
            }
            arrived.add(result.moved());
            shifts.put(landing.sub().getUniqueId(), new TeleportStrands.Shift((int) result.plotShift().x(),
                    (int) result.plotShift().z()));
            where.put(landing.sub().getUniqueId(), destLevel);
        }

        int moved = 0;
        for (int i = 0; i < plan.size(); i++) {
            Landing landing = plan.get(i);
            SableTrain train = landing.train();
            ServerSubLevel body = arrived.get(i);
            arrive(landing, mode, destLevel);
            Vector3d carriedVelocity = homes.get(i).carriedVelocity();
            ServerSubLevelContainer destContainer = SubLevelContainer.getContainer(destLevel);
            if (carriedVelocity != null && destContainer != null) {
                destContainer.physicsSystem().getPipeline().addLinearAndAngularVelocity(body, carriedVelocity,
                        new Vector3d());
            }
            land(train, body, moving, shifts.get(landing.sub().getUniqueId()), homes.get(i).drivers(),
                    mode == Mode.HANDOVER ? CartEvent.HANDED_OVER : CartEvent.TELEPORTED, trace);
            moved++;
            LoconauticsConstants.LOGGER.info("[issue281] car={} landed {}/{} derailed={} speed={}", shortId(train), moved,
                    plan.size(), train.isDerailed(), train.speed());
            trace.accept("car " + shortId(train) + " landed at " + format(landing.origin()));
        }
        followPartnerPlots(plan, destLevel, shifts);
        holdCoupledContacts(plan, destLevel);
        TeleportStrands.restore(strands, where, sourceLevel, shifts);
        LoconauticsConstants.LOGGER.info("[issue281] all {} cars landed, strands restored", moved);
        if (strands.carried() > 0 || strands.cut() > 0) {
            trace.accept(strands.carried() + " strands respawned, " + strands.cut() + " cut loose");
        }
        return new Result(null, moved, cars(plan));
    }

    // cars before the failed one go back to the plot they left, so knuckle partner positions never changed
    private static Result rollBack(List<Landing> plan, List<Home> homes, List<ServerSubLevel> arrived,
                                   ServerLevel destLevel, TeleportStrands.Carry strands, Map<UUID, ServerLevel> where,
                                   Map<UUID, TeleportStrands.Shift> shifts, Set<UUID> moving, String failure, Mode mode,
                                   Consumer<String> trace) {
        int failedAt = arrived.size();
        for (int i = failedAt - 1; i >= 0; i--) {
            UUID id = plan.get(i).sub().getUniqueId();
            Home home = homes.get(i);
            SableTeleportation.Result back = SableTeleportation.teleport(arrived.get(i), home.level(), home.position(),
                    home.orient(), home.slot());
            if (!back.ok()) {
                LoconauticsConstants.LOGGER.error("[teleport] car {} could not go back home, it stays in {}: {}",
                        id, destLevel.dimension().location(), back.failure());
                continue;
            }
            arrived.set(i, back.moved());
            where.remove(id);
            shifts.remove(id);
        }
        ServerLevel sourceLevel = homes.get(0).level();
        for (int i = 0; i <= failedAt; i++) {
            Landing landing = plan.get(i);
            SableTrain train = landing.train();
            UUID id = landing.sub().getUniqueId();
            ServerSubLevel body = i < failedAt ? arrived.get(i) : SubLevelData.resolve(sourceLevel, id);
            if (body == null) {
                LoconauticsConstants.LOGGER.error("[teleport] car {} has no body after the failed move", id);
                continue;
            }
            if (where.containsKey(id)) {
                arrive(landing, mode, destLevel);
                land(train, body, moving, shifts.get(id), homes.get(i).drivers(),
                        mode == Mode.HANDOVER ? CartEvent.HANDED_OVER : CartEvent.TELEPORTED, trace);
                continue;
            }
            GhostWheelBodies.rebuild(train);
            land(train, body, moving, NO_SHIFT, homes.get(i).drivers(), CartEvent.RELOCATED, trace);
        }
        for (int i = failedAt + 1; i < plan.size(); i++) {
            settleKnuckles(plan.get(i).sub(), moving);
        }
        holdCoupledContacts(plan, sourceLevel);
        TeleportStrands.restore(strands, where, sourceLevel, shifts);
        return new Result(failure, where.size(), cars(plan));
    }

    private static Home home(Landing landing, Mode mode) {
        ServerSubLevel sub = landing.sub();
        return new Home(sub.getLevel(), new Vector3d(sub.logicalPose().position()),
                new Quaterniond(sub.logicalPose().orientation()), SableTeleportation.plotSlot(sub), drivers(sub),
                mode == Mode.HANDOVER ? carriedVelocity(landing) : null);
    }

    private static void arrive(Landing landing, Mode mode, ServerLevel destLevel) {
        SableTrain train = landing.train();
        train.setLevel(destLevel);
        if (mode == Mode.RAIL) {
            double speed = Math.abs(train.speed()) * landing.railTravelSign();
            train.relocate(landing.seat().carriage(), landing.seat().bogeys());
            train.setDerailed(false, null);
            train.setSpeed(speed);
        } else if (mode != Mode.HANDOVER) {
            train.setSpeed(0.0);
            train.setTargetSpeed(0.0);
            train.setDerailed(true, DerailReason.TELEPORT);
        }
    }

    private static void land(SableTrain train, ServerSubLevel body, Set<UUID> moving, TeleportStrands.Shift shift,
                             Map<BlockPos, UUID> drivers, CartEvent event, Consumer<String> trace) {
        settleKnuckles(body, moving);
        reseatDrivers(body, shift, drivers, trace);
        CartLifecycle.fire(train, event);
        SableTrainPersistence.persist(train);
        SableTrainSyncPacket.broadcast(train);
    }

    @Nullable
    // the body is rebuilt on landing and rapier starts it at rest, so the speed is turned with the car and handed back
    private static Vector3d carriedVelocity(Landing landing) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(landing.sub().getLevel());
        if (container == null) {
            return null;
        }
        Vector3d velocity = container.physicsSystem().getPipeline().getLinearVelocity(landing.sub(), new Vector3d());
        Quaterniond turn = new Quaterniond(landing.orient())
                .mul(new Quaterniond(landing.sub().logicalPose().orientation()).invert());
        return turn.transform(velocity);
    }

    // the contact filters are bound to the bodies that are about to be removed, drop them before the body goes
    private static void releaseContacts(ServerSubLevel sub) {
        for (UUID wheel : GhostWheelBodies.wheelBodiesOf(sub.getUniqueId())) {
            SubLevelContacts.releaseAll(wheel);
        }
        SubLevelContacts.releaseAll(sub.getUniqueId());
    }

    // the filters went with the old bodies, so coupled cars would touch for a tick or two after landing
    private static void holdCoupledContacts(List<Landing> plan, ServerLevel destLevel) {
        for (Landing landing : plan) {
            ServerSubLevel moved = SubLevelData.resolve(destLevel, landing.sub().getUniqueId());
            if (moved == null) {
                continue;
            }
            for (KnuckleBlockEntity knuckle : SubLevelBlocks.findAll(moved, KnuckleBlockEntity.class)) {
                UUID partnerId = knuckle.getPartnerSubLevelId();
                ServerSubLevel partner = partnerId == null ? null : SubLevelData.resolve(destLevel, partnerId);
                if (partner != null) {
                    SubLevelContacts.disableCoupled(moved, partner);
                }
            }
        }
    }

    // each car lands on its own plot, so the partner position a knuckle stored points at the old plot until it is shifted
    private static void followPartnerPlots(List<Landing> plan, ServerLevel destLevel,
                                           Map<UUID, TeleportStrands.Shift> shifts) {
        for (Landing landing : plan) {
            ServerSubLevel moved = SubLevelData.resolve(destLevel, landing.sub().getUniqueId());
            if (moved == null) {
                continue;
            }
            for (KnuckleBlockEntity knuckle : SubLevelBlocks.findAll(moved, KnuckleBlockEntity.class)) {
                TeleportStrands.Shift partnerShift = shifts.get(knuckle.getPartnerSubLevelId());
                BlockPos matePos = knuckle.getPartnerLocalPos();
                if (partnerShift != null && matePos != null) {
                    knuckle.movePartnerPos(partnerShift.apply(matePos));
                }
            }
            // a firebox keeps its controller and barrel positions in plot coordinates, the new plot is at another x and z
            TeleportStrands.Shift own = shifts.get(landing.sub().getUniqueId());
            if (own == null) {
                continue;
            }
            BlockPos plotShift = own.apply(BlockPos.ZERO);
            for (FireboxBlockEntity firebox : SubLevelBlocks.findAll(moved, FireboxBlockEntity.class)) {
                firebox.shiftReferences(plotShift);
            }
        }
    }

    private static void settleKnuckles(ServerSubLevel sub, Set<UUID> moving) {
        for (KnuckleBlockEntity knuckle : SubLevelBlocks.findAll(sub, KnuckleBlockEntity.class)) {
            UUID partner = knuckle.getPartnerSubLevelId();
            if (partner == null || moving.contains(partner)) {
                knuckle.releaseCoupling();
                continue;
            }
            ServerSubLevel partnerSub = SubLevelData.resolve(sub.getLevel(), partner);
            BlockPos matePos = knuckle.getPartnerLocalPos();
            if (partnerSub != null && matePos != null
                    && partnerSub.getLevel().getBlockEntity(matePos) instanceof KnuckleBlockEntity mate) {
                mate.clearConnection();
                mate.clearPartner();
            }
            knuckle.clearConnection();
            knuckle.clearPartner();
            LoconauticsConstants.LOGGER.info("[teleport] uncoupled {} from {}, it is not coming along",
                    sub.getUniqueId(), partner);
        }
    }

    private static Map<BlockPos, UUID> drivers(ServerSubLevel sub) {
        Map<BlockPos, UUID> drivers = new HashMap<>();
        for (AnalogControllerBlockEntity controller : SubLevelBlocks.findAll(sub, AnalogControllerBlockEntity.class)) {
            UUID user = controller.getCurrentUser();
            if (user != null) {
                drivers.put(controller.getBlockPos(), user);
            }
        }
        return drivers;
    }

    private static void reseatDrivers(ServerSubLevel moved, TeleportStrands.Shift shift, Map<BlockPos, UUID> drivers,
                                      Consumer<String> trace) {
        ServerLevel level = moved.getLevel();
        if (drivers.isEmpty()) {
            trace.accept("no driver on any controller of " + moved.getUniqueId().toString().substring(0, 8));
        }
        drivers.forEach((pos, user) -> {
            BlockPos landed = shift.apply(pos);
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(user);
            boolean found = level.getBlockEntity(landed) instanceof AnalogControllerBlockEntity;
            trace.accept("driver " + (player == null ? user : player.getScoreboardName()) + " at " + pos.toShortString()
                    + " to " + landed.toShortString() + " controller=" + found + " player in "
                    + (player == null ? "none" : player.level().dimension().location()));
            if (player != null && level.getBlockEntity(landed) instanceof AnalogControllerBlockEntity controller) {
                controller.resumeUser(player);
            }
        });
    }

    private static List<SableTrain> cars(List<Landing> plan) {
        List<SableTrain> out = new ArrayList<>();
        for (Landing landing : plan) {
            out.add(landing.train());
        }
        return out;
    }

    private static String format(Vector3d v) {
        return String.format("%.1f %.1f %.1f", v.x, v.y, v.z);
    }

    private static String shortId(SableTrain train) {
        return train.id().toString().substring(0, 8);
    }
}
