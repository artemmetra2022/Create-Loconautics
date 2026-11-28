package com.lycoris.loconautics.content.airbrake;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

public class AirReservoirBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    private static final float EQUALIZE_RATE = 0.25f;
    private static final float SYNC_STEP = 0.05f;

    private float pressureBar;
    private float syncedPressure;

    public AirReservoirBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public float getPressureBar() {
        return pressureBar;
    }

    public float addAir(float amountBar) {
        if (amountBar <= 0f || pressureBar >= AirBrakeTuning.MAX_MAIN_RESERVOIR_BAR) {
            return 0f;
        }
        float space = AirBrakeTuning.MAX_MAIN_RESERVOIR_BAR - pressureBar;
        float added = Math.min(space, amountBar);
        pressureBar += added;
        setChanged();
        return added;
    }

    public float drawAir(float requestedBar) {
        if (requestedBar <= 0f || pressureBar <= 0f) {
            return 0f;
        }
        float taken = Math.min(pressureBar, requestedBar);
        pressureBar -= taken;
        setChanged();
        return taken;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }

        for (Direction dir : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(dir)) instanceof AirReservoirBlockEntity neighbor) {
                float diff = neighbor.pressureBar - pressureBar;
                if (Math.abs(diff) > 0.005f) {
                    float flow = diff * EQUALIZE_RATE * 0.5f;
                    pressureBar = Mth.clamp(pressureBar + flow, 0f, AirBrakeTuning.MAX_MAIN_RESERVOIR_BAR);
                    neighbor.pressureBar = Mth.clamp(neighbor.pressureBar - flow, 0f, AirBrakeTuning.MAX_MAIN_RESERVOIR_BAR);
                    setChanged();
                    neighbor.setChanged();
                }
            }
        }

        if (Math.abs(pressureBar - syncedPressure) >= SYNC_STEP) {
            syncedPressure = pressureBar;
            sendData();
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("loconautics.air_reservoir.goggles.header")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(" ")
                .append(Component.translatable("loconautics.air_brake.goggles.pressure",
                        String.format(Locale.ROOT, "%.2f", pressureBar)))
                .withStyle(ChatFormatting.AQUA));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("Pressure", pressureBar);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        pressureBar = Mth.clamp(tag.getFloat("Pressure"), 0f, AirBrakeTuning.MAX_MAIN_RESERVOIR_BAR);
        syncedPressure = pressureBar;
    }
}
