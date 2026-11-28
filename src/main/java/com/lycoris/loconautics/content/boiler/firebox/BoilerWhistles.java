package com.lycoris.loconautics.content.boiler.firebox;

import com.lycoris.loconautics.content.analogcontroller.TrainHorn;
import com.lycoris.loconautics.content.boiler.BoilerStructure;
import com.simibubi.create.content.decoration.steamWhistle.WhistleBlock;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class BoilerWhistles {

    private static final int RESEND_TICKS = 5;
    private static final double REACH = 1.6;
    private static final double HEIGHT = 2.2;

    private BlockPos held;
    private int heldCooldown;
    private final Set<BlockPos> powered = new HashSet<>();
    private int poweredCooldown;

    void tick(ServerLevel server, BlockPos controller, @Nullable BoilerStructure structure, boolean canWhistle,
              float strength) {
        tickRider(server, controller, structure, canWhistle, strength);
        tickPowered(server, structure, canWhistle, strength);
    }

    void stopAll(ServerLevel server) {
        if (held != null) TrainHorn.blow(server, held, false);
        for (BlockPos pos : powered) TrainHorn.blow(server, pos, false);
        held = null;
        powered.clear();
    }

    void forget() {
        held = null;
        powered.clear();
    }

    private void tickRider(ServerLevel server, BlockPos controller, @Nullable BoilerStructure structure,
                           boolean canWhistle, float strength) {
        ServerPlayer rider = canWhistle ? sneakingRider(server, structure) : null;
        if (rider == null) {
            if (held != null) {
                TrainHorn.blow(server, held, false);
                held = null;
            }
            return;
        }
        if (held == null) held = TrainHorn.nearestWhistle(server, controller);
        if (held == null) return;
        if (--heldCooldown > 0) return;
        heldCooldown = RESEND_TICKS;
        TrainHorn.sound(rider, held, true, strength);
    }

    private void tickPowered(ServerLevel server, @Nullable BoilerStructure structure, boolean canWhistle,
                             float strength) {
        if (--poweredCooldown > 0) return;
        poweredCooldown = RESEND_TICKS;
        Set<BlockPos> sounding = canWhistle ? poweredOnBoiler(server, structure) : Set.of();
        for (BlockPos pos : sounding) TrainHorn.blow(server, pos, true, strength);
        for (BlockPos pos : powered) {
            if (!sounding.contains(pos)) TrainHorn.blow(server, pos, false);
        }
        powered.clear();
        powered.addAll(sounding);
    }

    private static Set<BlockPos> poweredOnBoiler(ServerLevel server, @Nullable BoilerStructure structure) {
        Set<BlockPos> found = new HashSet<>();
        if (structure == null) return found;
        List<BlockPos> hosts = new ArrayList<>(structure.boilerPositions());
        hosts.addAll(structure.fireboxPositions());
        for (BlockPos host : hosts) {
            for (Direction dir : Direction.values()) {
                BlockPos pos = host.relative(dir);
                BlockState state = server.getBlockState(pos);
                if (!(state.getBlock() instanceof WhistleBlock) || !state.getValue(WhistleBlock.POWERED)) continue;
                if (WhistleBlock.getAttachedDirection(state) == dir.getOpposite()) found.add(pos);
            }
        }
        return found;
    }

    @Nullable
    private static ServerPlayer sneakingRider(ServerLevel server, @Nullable BoilerStructure structure) {
        if (structure == null) return null;
        List<ServerPlayer> sneaking = new ArrayList<>();
        for (ServerPlayer player : server.players()) {
            if (player.isShiftKeyDown() && !player.isSpectator()) sneaking.add(player);
        }
        if (sneaking.isEmpty()) return null;
        List<Vec3> tops = new ArrayList<>();
        for (BlockPos pos : structure.boilerPositions()) {
            tops.add(Sable.HELPER.projectOutOfSubLevel(server, Vec3.atCenterOf(pos)));
        }
        for (BlockPos pos : structure.fireboxPositions()) {
            tops.add(Sable.HELPER.projectOutOfSubLevel(server, Vec3.atCenterOf(pos)));
        }
        for (ServerPlayer player : sneaking) {
            Vec3 feet = player.position();
            for (Vec3 top : tops) {
                double dx = feet.x - top.x;
                double dz = feet.z - top.z;
                double above = feet.y - top.y;
                if (dx * dx + dz * dz <= REACH * REACH && above >= 0.0 && above <= HEIGHT) {
                    return player;
                }
            }
        }
        return null;
    }
}
