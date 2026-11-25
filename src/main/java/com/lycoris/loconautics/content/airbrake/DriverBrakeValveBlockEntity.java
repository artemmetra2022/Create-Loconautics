package com.lycoris.loconautics.content.airbrake;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
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

public class DriverBrakeValveBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    private static final float CHARGE_FLOW_PER_TICK = 0.08f;
    private static final float EXHAUST_FLOW_PER_TICK = 0.14f;
    private static final float EMERGENCY_EXHAUST_PER_TICK = 0.45f;
    private static final float SYNC_STEP = 0.05f;

    private int notch;
    private boolean isolated;
    private float pipePressure;
    private float mainPressure;
    private float syncedPipe;
    private float syncedMain;
    private boolean restoredToBus;

    public DriverBrakeValveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public int getNotch() {
        return notch;
    }

    public boolean isIsolated() {
        return isolated;
    }

    public void setIsolated(boolean isolated) {
        if (this.isolated == isolated) {
            return;
        }
        this.isolated = isolated;
        setChanged();
        sendData();
    }

    public void setNotch(int notch) {
        int clamped = Mth.clamp(notch, 0, AirBrakeTuning.MAX_NOTCH);
        if (this.notch == clamped && !isolated) {
            return;
        }
        this.notch = clamped;
        this.isolated = false;
        setChanged();
        sendData();
    }

    public void stepNotch(int delta) {
        if (isolated) {
            if (delta > 0) {
                setIsolated(false);
            }
            return;
        }
        if (delta < 0 && notch == 0) {
            setIsolated(true);
            return;
        }
        setNotch(notch + delta);
    }

    public float getPipePressure() {
        return pipePressure;
    }

    public float getMainPressure() {
        return mainPressure;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }

        if (!restoredToBus) {
            restoredToBus = true;
            if (pipePressure > 0f) {
                AirPipeBus.writePipePressure(this, Math.max(pipePressure, AirPipeBus.readPipePressure(this)));
            }
        }

        float current = AirPipeBus.readPipePressure(this);
        mainPressure = sampleMainSupply();

        if (isolated) {
            pipePressure = current;
            if (Math.abs(pipePressure - syncedPipe) >= SYNC_STEP
                    || Math.abs(mainPressure - syncedMain) >= SYNC_STEP) {
                syncedPipe = pipePressure;
                syncedMain = mainPressure;
                setChanged();
                sendData();
            }
            return;
        }

        float leak = LoconauticsConfig.AIR_BRAKE_LEAK_RATE.get().floatValue();
        if (leak > 0f && current > 0f) {
            current = Math.max(0f, current - leak);
        }

        float target = AirBrakeTuning.targetPipeBarForNotch(notch);

        boolean exhausting = false;
        if (notch >= AirBrakeTuning.MAX_NOTCH) {
            if (current > 0.05f) exhausting = true;
            current = Math.max(0f, current - EMERGENCY_EXHAUST_PER_TICK);
        } else if (current < target) {
            float maxCharge = Math.min(CHARGE_FLOW_PER_TICK, target - current);
            float drawn = drawFromSupply(maxCharge, current);
            current = Mth.clamp(current + drawn, 0f, AirBrakeTuning.WORKING_PIPE_BAR);
        } else if (current > target) {
            if (current - target > 0.02f) exhausting = true;
            current = Math.max(target, current - EXHAUST_FLOW_PER_TICK);
        }

        if (exhausting && level instanceof ServerLevel sl && sl.getGameTime() % 4 == 0) {
            sl.sendParticles(ParticleTypes.CLOUD,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 0.85, worldPosition.getZ() + 0.5,
                    notch >= AirBrakeTuning.MAX_NOTCH ? 2 : 1, 0.08, 0.06, 0.08, 0.02);
            if (sl.getGameTime() % 8 == 0) {
                sl.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
                        notch >= AirBrakeTuning.MAX_NOTCH ? 0.35f : 0.16f, 1.6f);
            }
        }

        AirPipeBus.writePipePressure(this, current);
        pipePressure = AirPipeBus.readPipePressure(this);

        if (Math.abs(pipePressure - syncedPipe) >= SYNC_STEP
                || Math.abs(mainPressure - syncedMain) >= SYNC_STEP) {
            syncedPipe = pipePressure;
            syncedMain = mainPressure;
            setChanged();
            sendData();
        }
    }

    private float sampleMainSupply() {
        if (level == null) {
            return 0f;
        }
        float max = 0f;
        for (AirReservoirBlockEntity reservoir : AirReservoirScanner.findReservoirs(this)) {
            max = Math.max(max, reservoir.getPressureBar());
        }
        for (AirCompressorBlockEntity compressor : AirReservoirScanner.findCompressors(this)) {
            max = Math.max(max, compressor.getPressureBar());
        }
        return max;
    }

    private float drawFromSupply(float requestedBar, float currentPipe) {
        if (level == null || requestedBar <= 0f) {
            return 0f;
        }
        float remaining = requestedBar;
        for (AirReservoirBlockEntity reservoir : AirReservoirScanner.findReservoirs(this)) {
            if (remaining <= 0.0001f) {
                break;
            }
            if (reservoir.getPressureBar() > currentPipe) {
                float available = Math.min(remaining, reservoir.getPressureBar() - currentPipe);
                remaining -= reservoir.drawAir(available);
            }
        }
        for (AirCompressorBlockEntity compressor : AirReservoirScanner.findCompressors(this)) {
            if (remaining <= 0.0001f) {
                break;
            }
            if (compressor.getPressureBar() > currentPipe) {
                float available = Math.min(remaining, compressor.getPressureBar() - currentPipe);
                remaining -= compressor.drawAir(available);
            }
        }
        return requestedBar - remaining;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("loconautics.driver_brake_valve.goggles.header")
                .withStyle(ChatFormatting.GRAY));
        if (isolated) {
            tooltip.add(Component.literal(" ")
                    .append(Component.translatable("loconautics.driver_brake_valve.goggles.isolated"))
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.literal(" ")
                    .append(Component.translatable("loconautics.driver_brake_valve.goggles.notch",
                            notch, AirBrakeTuning.MAX_NOTCH))
                    .withStyle(notch >= AirBrakeTuning.MAX_NOTCH ? ChatFormatting.RED : ChatFormatting.GOLD));
        }
        tooltip.add(Component.literal(" ")
                .append(Component.translatable("loconautics.driver_brake_valve.goggles.pipe",
                        String.format(Locale.ROOT, "%.2f", pipePressure)))
                .withStyle(ChatFormatting.AQUA));
        String modeKey = notch >= AirBrakeTuning.EMERGENCY_NOTCH ? "emergency"
                : notch > 0 ? "service" : "charge";
        tooltip.add(Component.literal(" ")
                .append(Component.translatable("loconautics.driver_brake_valve.goggles.mode",
                        Component.translatable("loconautics.driver_brake_valve.mode." + modeKey)))
                .withStyle(notch >= AirBrakeTuning.EMERGENCY_NOTCH ? ChatFormatting.RED
                        : notch > 0 ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        tooltip.add(Component.literal(" ")
                .append(Component.translatable("loconautics.driver_brake_valve.goggles.main",
                        String.format(Locale.ROOT, "%.2f", mainPressure)))
                .withStyle(ChatFormatting.DARK_AQUA));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Notch", notch);
        tag.putInt("NotchVersion", AirBrakeTuning.NOTCH_DATA_VERSION);
        tag.putBoolean("Isolated", isolated);
        tag.putFloat("PipePressure", pipePressure);
        tag.putFloat("MainPressure", mainPressure);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        int savedNotch = tag.getInt("Notch");
        if (tag.getInt("NotchVersion") < AirBrakeTuning.NOTCH_DATA_VERSION) {
            savedNotch = AirBrakeTuning.migrateLegacyNotch(savedNotch);
        }
        notch = Mth.clamp(savedNotch, 0, AirBrakeTuning.MAX_NOTCH);
        isolated = tag.getBoolean("Isolated");
        pipePressure = Mth.clamp(tag.getFloat("PipePressure"), 0f, AirBrakeTuning.WORKING_PIPE_BAR);
        mainPressure = Mth.clamp(tag.getFloat("MainPressure"), 0f, AirBrakeTuning.MAX_MAIN_RESERVOIR_BAR);
        syncedPipe = pipePressure;
        syncedMain = mainPressure;
    }
}
