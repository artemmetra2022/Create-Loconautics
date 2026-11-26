package com.lycoris.loconautics.content.railsignal;

import com.lycoris.loconautics.trainphysics.rail.RailFollower;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.entity.TravellingPoint.SteerDirection;
import com.simibubi.create.content.trains.graph.TrackGraphHelper;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.signal.SignalBlockEntity.SignalState;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.track.BezierTrackPointLocation;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Copies the next Create signal aspect without owning a SignalBoundary. */
public class SignalRepeaterBlockEntity extends SmartBlockEntity {

    private BlockPos targetTrack = BlockPos.ZERO;
    private boolean targetPositive = true;
    private boolean hasTarget;
    private boolean hasBezier;
    private BlockPos bezierKey = BlockPos.ZERO;
    private int bezierSegment;

    public SignalRepeaterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // no TrackTargetingBehaviour — no edge point on the graph
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if ((level.getGameTime() & 3L) != 0L) return;
        refreshAspect();
    }

    private void refreshAspect() {
        SignalRepeaterBlock.Aspect aspect = SignalRepeaterBlock.Aspect.RED;
        Direction facing = getBlockState().getValue(SignalRepeaterBlock.FACING);
        if (hasTarget) {
            BlockPos trackPos = worldPosition.offset(targetTrack);
            BlockState trackState = level.getBlockState(trackPos);
            AxisDirection dir = targetPositive ? AxisDirection.POSITIVE : AxisDirection.NEGATIVE;
            TrackGraphLocation loc = null;
            Vec3 axis = Vec3.ZERO;
            if (hasBezier) {
                BezierTrackPointLocation bezier = new BezierTrackPointLocation(
                        bezierKey.offset(worldPosition), bezierSegment);
                loc = TrackGraphHelper.getBezierGraphLocationAt(level, trackPos, dir, bezier);
            } else if (trackState.getBlock() instanceof ITrackBlock track) {
                List<Vec3> axes = track.getTrackAxes(level, trackPos, trackState);
                if (!axes.isEmpty()) {
                    axis = axes.get(0);
                    loc = TrackGraphHelper.getGraphLocationAt(level, trackPos, dir, axis);
                }
            }
            if (loc != null && loc.graph != null) {
                TravellingPoint scout = RailFollower.pointAt(loc);
                if (scout != null) {
                    SignalBoundary[] found = { null };
                    boolean[] primary = { true };
                    scout.travel(loc.graph, 256.0,
                            scout.steer(SteerDirection.NONE, new Vec3(0, 1, 0)),
                            (distance, pair) -> {
                                if (!(pair.getFirst() instanceof SignalBoundary signal)) return false;
                                if (Math.abs(distance) < 1.0) return false;
                                found[0] = signal;
                                primary[0] = signal.isPrimary(pair.getSecond().getSecond());
                                return true;
                            },
                            scout.ignoreTurns(), scout.ignorePortals());
                    if (found[0] != null) {
                        if (found[0].isForcedRed(primary[0])) {
                            aspect = SignalRepeaterBlock.Aspect.RED;
                        } else {
                            SignalState state = found[0].cachedStates.get(primary[0]);
                            aspect = switch (state) {
                                case GREEN -> SignalRepeaterBlock.Aspect.GREEN;
                                case YELLOW -> SignalRepeaterBlock.Aspect.YELLOW;
                                default -> SignalRepeaterBlock.Aspect.RED;
                            };
                        }
                    }
                    if (axis.lengthSqr() > 1.0e-6) {
                        double approachSign = targetPositive ? -1.0 : 1.0;
                        facing = Direction.getNearest(axis.x * approachSign, 0, axis.z * approachSign);
                    }
                }
            }
        }

        BlockState current = getBlockState();
        BlockState updated = current
                .setValue(SignalRepeaterBlock.ASPECT, aspect)
                .setValue(SignalRepeaterBlock.FACING, facing);
        if (updated != current) {
            level.setBlock(worldPosition, updated, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (hasTarget) {
            tag.put("TargetTrack", NbtUtils.writeBlockPos(targetTrack));
            tag.putBoolean("TargetDirection", targetPositive);
            if (hasBezier) {
                CompoundTag bezier = new CompoundTag();
                bezier.put("Key", NbtUtils.writeBlockPos(bezierKey));
                bezier.putInt("Segment", bezierSegment);
                tag.put("Bezier", bezier);
            }
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        hasTarget = tag.contains("TargetTrack");
        if (hasTarget) {
            targetTrack = NBTHelper.readBlockPos(tag, "TargetTrack");
            targetPositive = tag.getBoolean("TargetDirection");
            hasBezier = tag.contains("Bezier");
            if (hasBezier) {
                CompoundTag bezier = tag.getCompound("Bezier");
                bezierKey = NBTHelper.readBlockPos(bezier, "Key");
                bezierSegment = bezier.getInt("Segment");
            }
        }
    }
}
