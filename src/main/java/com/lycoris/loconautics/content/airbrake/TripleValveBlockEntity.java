package com.lycoris.loconautics.content.airbrake;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

public class TripleValveBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    private static final float AUX_CHARGE_RATE = 0.05f;
    private static final float AUX_HOLD_DRAIN = 0.0005f;
    private static final float FULL_SERVICE_DROP_BAR = 1.5f;
    private static final float MIN_TRIGGER_DROP_BAR = 0.1f;
    private static final float SYNC_STEP = 0.05f;

    private float auxPressure;
    private float pipePressure;
    private float cylinderFraction;
    private boolean emergency;
    private float syncedCylinder;
    private boolean restoredToBus;

    public TripleValveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public float getAuxPressure() {
        return auxPressure;
    }

    public float getPipePressure() {
        return pipePressure;
    }

    public float getCylinderFraction() {
        return cylinderFraction;
    }

    public boolean isEmergency() {
        return emergency;
    }

    public void releaseManually() {
        auxPressure = 0f;
        cylinderFraction = 0f;
        emergency = false;
        syncedCylinder = 0f;
        setChanged();
        sendData();
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

        pipePressure = AirPipeBus.readPipePressure(this);

        if (pipePressure > auxPressure) {
            auxPressure = Math.min(AirBrakeTuning.MAX_AUX_RESERVOIR_BAR,
                    Math.min(pipePressure, auxPressure + AUX_CHARGE_RATE));
        }

        float drop = auxPressure - pipePressure;
        if (auxPressure > 0.3f && pipePressure < AirBrakeTuning.EMERGENCY_PIPE_BAR && drop > MIN_TRIGGER_DROP_BAR) {
            emergency = true;
            cylinderFraction = 1.0f;
            auxPressure = Math.max(0f, auxPressure - AUX_HOLD_DRAIN);
        } else if (drop > MIN_TRIGGER_DROP_BAR) {
            emergency = false;
            float reserveFactor = Mth.clamp(auxPressure / AirBrakeTuning.MAX_AUX_RESERVOIR_BAR, 0.2f, 1.0f);
            cylinderFraction = Mth.clamp(drop / FULL_SERVICE_DROP_BAR, 0f, 1.0f) * reserveFactor;
            auxPressure = Math.max(0f, auxPressure - AUX_HOLD_DRAIN * cylinderFraction);
        } else {
            emergency = false;
            cylinderFraction = 0f;
        }

        if (Math.abs(cylinderFraction - syncedCylinder) >= SYNC_STEP) {
            syncedCylinder = cylinderFraction;
            setChanged();
            sendData();
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("loconautics.triple_valve.goggles.header")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(" ")
                .append(Component.translatable("loconautics.triple_valve.goggles.aux",
                        String.format(Locale.ROOT, "%.2f", auxPressure)))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal(" ")
                .append(Component.translatable("loconautics.triple_valve.goggles.cylinder",
                        Math.round(cylinderFraction * 100f)))
                .withStyle(emergency ? ChatFormatting.RED
                        : cylinderFraction > 0f ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("AuxPressure", auxPressure);
        tag.putFloat("PipePressure", pipePressure);
        tag.putFloat("Cylinder", cylinderFraction);
        tag.putBoolean("Emergency", emergency);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        auxPressure = Mth.clamp(tag.getFloat("AuxPressure"), 0f, AirBrakeTuning.MAX_AUX_RESERVOIR_BAR);
        pipePressure = Mth.clamp(tag.getFloat("PipePressure"), 0f, AirBrakeTuning.WORKING_PIPE_BAR);
        cylinderFraction = Mth.clamp(tag.getFloat("Cylinder"), 0f, 1.0f);
        emergency = tag.getBoolean("Emergency");
        syncedCylinder = cylinderFraction;
    }
}
