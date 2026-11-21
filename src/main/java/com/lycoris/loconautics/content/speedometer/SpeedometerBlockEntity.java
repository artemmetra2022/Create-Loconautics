package com.lycoris.loconautics.content.speedometer;

import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

import java.util.List;
import java.util.Locale;

public class SpeedometerBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    /** blocks per second that give a full 15 comparator signal, 50 is 180 km/h */
    public static final double FULL_SCALE = 50.0;

    private static final int SAMPLE_TICKS = 2;
    private static final float SMOOTHING = 0.5f;
    private static final float SYNC_STEP = 0.05f;

    private float speed;
    private float syncedSpeed;
    private int signal;

    public SpeedometerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    /** blocks per second */
    public float getSpeed() {
        return speed;
    }

    public int getSignal() {
        return signal;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if ((level.getGameTime() + worldPosition.asLong()) % SAMPLE_TICKS != 0) return;

        float target = (float) measure();
        speed += (target - speed) * SMOOTHING;
        if (target <= 0f && speed < SYNC_STEP) speed = 0f;

        if (Math.abs(speed - syncedSpeed) >= SYNC_STEP) {
            syncedSpeed = speed;
            setChanged();
            sendData();
        }

        int newSignal = Mth.clamp(Math.round((float) (speed / FULL_SCALE * 15.0)), 0, 15);
        if (newSignal != signal) {
            signal = newSignal;
            setChanged();
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    // a speedometer standing in the world and not on a carriage reads zero
    private double measure() {
        SubLevel sub = Sable.HELPER.getContaining(this);
        if (!(sub instanceof ServerSubLevel)) return 0.0;
        Vector3d velocity = SubLevelData.linearVelocity(sub);
        return velocity == null ? 0.0 : velocity.length();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        LoconauticsLang.translate("gui.goggles.speedometer_stats").forGoggles(tooltip);
        LoconauticsLang.translate("gui.goggles.speedometer_speed")
                .style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.builder()
                .text(String.format(Locale.US, "%.1f km/h", speed * 3.6f))
                .style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        LoconauticsLang.builder()
                .text(String.format(Locale.US, "%.1f m/s", speed))
                .style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("Speed", speed);
        tag.putInt("Signal", signal);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        speed = tag.getFloat("Speed");
        signal = tag.getInt("Signal");
        syncedSpeed = speed;
    }
}
