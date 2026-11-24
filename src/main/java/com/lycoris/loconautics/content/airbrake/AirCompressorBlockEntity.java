package com.lycoris.loconautics.content.airbrake;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

public class AirCompressorBlockEntity extends KineticBlockEntity {

    private static final float SYNC_STEP = 0.05f;
    private static final float IDLE_STRESS_FRACTION = 0.1f;

    private float internalPressure;
    private float syncedPressure;
    private boolean pumping;

    public AirCompressorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public float getPressureBar() {
        return internalPressure;
    }

    public boolean isPumping() {
        return pumping;
    }

    public float drawAir(float requestedBar) {
        if (requestedBar <= 0f || internalPressure <= 0f) {
            return 0f;
        }
        float taken = Math.min(internalPressure, requestedBar);
        internalPressure -= taken;
        setChanged();
        return taken;
    }

    @Override
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        updateStress();
    }

    @Override
    public float calculateStressApplied() {
        float base = LoconauticsConfig.AIR_COMPRESSOR_SU_IMPACT.get().floatValue();
        float applied = pumping ? base : base * IDLE_STRESS_FRACTION;
        this.lastStressApplied = applied;
        return applied;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }

        List<AirReservoirBlockEntity> targets = AirReservoirScanner.findReservoirs(this);
        float rpm = Math.abs(getSpeed());
        // No main reservoir on the consist: nowhere to pump air into, so stay idle.
        boolean hasTargets = !targets.isEmpty();
        boolean needsCharge = internalPressure < AirBrakeTuning.COMPRESSOR_RESUME_BAR;
        boolean allFull = internalPressure >= AirBrakeTuning.MAX_MAIN_RESERVOIR_BAR - 0.001f;

        for (AirReservoirBlockEntity reservoir : targets) {
            float p = reservoir.getPressureBar();
            if (p < AirBrakeTuning.COMPRESSOR_RESUME_BAR) {
                needsCharge = true;
            }
            if (p < AirBrakeTuning.MAX_MAIN_RESERVOIR_BAR - 0.001f) {
                allFull = false;
            }
        }

        boolean nextPumping;
        if (rpm <= 0.01f || !hasTargets) {
            nextPumping = false;
        } else if (pumping) {
            nextPumping = !allFull;
        } else {
            nextPumping = needsCharge;
        }

        if (nextPumping != pumping) {
            boolean stoppedFull = pumping && !nextPumping && rpm > 0.01f && allFull;
            pumping = nextPumping;
            updateStress();
            setChanged();
            sendData();
            if (stoppedFull && level instanceof ServerLevel sl) {
                sl.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.18f, 1.8f);
                sl.sendParticles(ParticleTypes.CLOUD,
                        worldPosition.getX() + 0.5, worldPosition.getY() + 1.05, worldPosition.getZ() + 0.5,
                        2, 0.1, 0.05, 0.1, 0.02);
            }
        }

        if (pumping && rpm > 0.01f && level instanceof ServerLevel sl) {
            int interval = Math.max(4, Math.round(240f / Math.max(8f, rpm)));
            if (sl.getGameTime() % interval == 0) {
                sl.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.22f, 1.45f);
            }
        }

        if (pumping && rpm > 0.01f) {
            float chargePerTick = LoconauticsConfig.AIR_COMPRESSOR_CHARGE_RATE.get().floatValue() * (rpm / 64.0f);
            float remaining = chargePerTick;
            for (AirReservoirBlockEntity reservoir : targets) {
                remaining -= reservoir.addAir(remaining);
                if (remaining <= 0.0001f) {
                    break;
                }
            }
            if (remaining > 0.0001f) {
                internalPressure = Mth.clamp(internalPressure + remaining, 0f, AirBrakeTuning.MAX_MAIN_RESERVOIR_BAR);
            }
        }

        if (Math.abs(internalPressure - syncedPressure) >= SYNC_STEP) {
            syncedPressure = internalPressure;
            sendData();
        }
    }

    private void updateStress() {
        if (level == null || level.isClientSide || !hasNetwork()) {
            return;
        }
        getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("loconautics.air_compressor.goggles.header")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(" ")
                .append(Component.translatable(
                        pumping ? "loconautics.air_compressor.goggles.pumping" : "loconautics.air_compressor.goggles.idle"))
                .withStyle(pumping ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal(" ")
                .append(Component.translatable("loconautics.air_brake.goggles.pressure",
                        String.format(Locale.ROOT, "%.2f", internalPressure)))
                .withStyle(ChatFormatting.AQUA));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("Pressure", internalPressure);
        tag.putBoolean("Pumping", pumping);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        internalPressure = Mth.clamp(tag.getFloat("Pressure"), 0f, AirBrakeTuning.MAX_MAIN_RESERVOIR_BAR);
        syncedPressure = internalPressure;
        pumping = tag.getBoolean("Pumping");
    }
}
