package com.lycoris.loconautics.content.boiler.firebox;


import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.strand.StrandTemplates;
import com.lycoris.loconautics.common.signal.SignalSpeed;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.content.boiler.BoilerStopAlert;
import com.lycoris.loconautics.content.boiler.BoilerSummary;
import com.lycoris.loconautics.content.boiler.bogey.BoilerBogeyBlockEntity;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.drive.DriveSources;
import com.lycoris.loconautics.trainphysics.drive.TrainDriveSource;
import com.lycoris.loconautics.content.controls.panel.PanelDriveBus;
import com.lycoris.loconautics.content.boiler.BoilerContributions;
import com.lycoris.loconautics.content.boiler.BoilerLevel;
import com.lycoris.loconautics.content.boiler.BoilerPool;
import com.lycoris.loconautics.content.boiler.FuelGrade;
import com.lycoris.loconautics.content.boiler.FuelGrades;
import com.lycoris.loconautics.content.boiler.BoilerMultiblockValidator;
import com.lycoris.loconautics.common.advancement.TrainAdvancements;
import com.lycoris.loconautics.content.boiler.BoilerState;
import com.lycoris.loconautics.content.boiler.BoilerStructure;
import com.lycoris.loconautics.content.boiler.BoilerTags;
import com.lycoris.loconautics.content.boiler.BoilerWaterSource;
import com.lycoris.loconautics.content.boiler.barrel.BarrelConnectorBlockEntity;
import com.lycoris.loconautics.content.boiler.barrel.BoilerBarrelBlockEntity;
import com.lycoris.loconautics.content.boiler.safetyvalve.SafetyValveBlockEntity;
import com.lycoris.loconautics.content.boiler.safetyvalve.SmartSafetyValveBlockEntity;
import com.lycoris.loconautics.content.boiler.steamtakeoff.SteamTakeoffBlockEntity;
import com.lycoris.loconautics.content.hatch.HullsteelSocketBridge;
import com.lycoris.loconautics.content.smokestack.SmokestackBlock;
import com.lycoris.loconautics.content.smokestack.SmokestackBlockEntity;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.lycoris.loconautics.foundation.smoke.SmokeOptions;
import com.lycoris.loconautics.foundation.smoke.SmokeStyle;
import com.lycoris.loconautics.trainphysics.fx.ParticleWorldLock;
import com.lycoris.loconautics.trainphysics.portal.PortalCutsClient;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import com.lycoris.loconautics.content.boiler.BoilerGoggleBars;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class FireboxBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    static final float WATER_PER_BODY_PER_TICK = 2.0f;
    static final float WATER_RECYCLE_FRACTION = 0.65f;
    static final float TEMPERATURE_JITTER_C = 10.0f;
    static final float WATER_JITTER_MB = 120.0f;
    static final float TAKEOFF_MIN_RPM = 4.0f;
    public static final float TAKEOFF_MAX_RPM = 128.0f;
    static final float PRESSURE_VENT_PER_TICK = 0.004f;
    static final float PRESSURE_IDLE_BLEED_PER_TICK = 0.002f;


    public static final int FUEL_SLOTS = 1;

    public static final int RESCAN_TICKS = 20;

    private static final int DEFAULT_BURN_TICKS = 1600;

    public static final double NO_TRAIN = -1.0;

    private final ItemStackHandler inventory = new ItemStackHandler(FUEL_SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isFuel(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) refreshGroup();
        }
    };

    private final FluidTank waterTank =
            new FluidTank(0,
                    stack -> stack.getFluid() == Fluids.WATER) {
                @Override
                protected void onContentsChanged() {
                    setChanged();
                }
            };

    private final BoilerWaterSource tankSource = new TankSource();

    private final Map<BlockPos, Integer> trackedBarrels = new HashMap<>();

    private static final float WHISTLE_MIN_PRESSURE = 0.3f;

    private static final double BOILING_C = 100.0;
    private static final float COLD_FIRE_EFFICIENCY = 0.15f;

    private BoilerState boilerState = BoilerState.COLD;
    private int warmupTicks;
    private float burnTimeRemaining;
    private FuelGrade burningGrade = FuelGrade.NORMAL;
    private int rescanCooldown;
    private int inflowCooldown;

    private int dryTicks;
    private int wasteGas;
    private boolean doorsOpenNow;
    private int doorCheck;
    private boolean ignited;
    @Nullable
    private final BoilerWhistles whistles = new BoilerWhistles();

    private FailureCause lastFailure;

    private static final float POOL_DRAW_MULTIPLIER = 2.5f;
    private static final float BOGEY_TARGET_PRESSURE = 0.7f;
    private static final float BOGEY_TRIM_PER_TICK = 0.0008f;
    private static final float BOGEY_TRIM_EASE = 0.05f;
    private static final float COAST_RISE_MULTIPLIER = 2f;
    private static final int COOLING_STEAM_COLOR = 0xD6D6D6;

    private static final float BLOCKED_SMOKE_MIN_DENSITY = 0.5f;
    private static final float BLOCKED_SMOKE_SCALE = 0.35f;
    private static final int DOOR_FUME_RELEASE = 6;
    private static final int DOOR_CHECK_TICKS = 5;
    private static final int BLOCKED_SMOKE_COLOR = 0x2A2622;

    private static final float LOW_WATER_RISE_BOOST = 1.0f;

    private static final float LOW_WATER_HEAT_SHARE = 0.25f;

    public enum FailureCause {
        CROWN_SHEET, OVERPRESSURE;

        public String langKey() {
            return "gui.goggles.boiler_failure_" + name().toLowerCase(Locale.ROOT);
        }

        public String chatKey() {
            return "loconautics.boiler.cause." + name().toLowerCase(Locale.ROOT);
        }

        public String fixKey() {
            return "loconautics.boiler.fix." + name().toLowerCase(Locale.ROOT);
        }
    }

    private BoilerStructure structure;
    @Nullable private String missingKey;

    public static final int DISPLAY_TICKS = 20;
    private static final int AWAY_TICKS = DISPLAY_TICKS * 4;

    private int displayCooldown;
    private int jitterCooldown;
    private boolean dispValid;
    private float dispSu;
    private float dispRpm;
    private float dispEfficiency;
    private boolean dispOverfired;
    private int dispWater;
    private int dispWasteGas;
    private int dispRemainingSeconds;
    private float dispTemperatureJitter;
    private float dispWaterJitter;
    private float dispPressure;
    private float dispMaxSu;
    private float dispBogeyDemand;
    private float dispFire;
    private boolean dispNoStack;
    private int dispWaterCapacity;
    private int dispSizeLevels;
    private int dispFireLevels;
    private int dispLevel;
    private int dispMaxLevel = 12;
    private boolean dispBogeys;
    @Nullable private String dispStep;
    @Nullable private String dispOptionalStep;

    private float steamPressure;
    private boolean lastBurning;
    private float lastBurnRate;
    private float lastVented;
    private int scheduleSignal;
    private float lastStressRatio;
    private double lastTrainMass = NO_TRAIN;
    private int lastWaterDrawn;
    private float lastSubSpeed;
    private boolean inSubLevel;

    public static final int DOOR_SWING_TICKS = 10;

    private static final float DOOR_SWING_SPEED = 1.35f;

    private static final double DOOR_REACH_PADDING = 0.7;

    private boolean manualDoorsOpen;
    private BlockPos controllerPos;

    private boolean doorsSwingingOpen;
    private float doorOpenTime;
    private float lastDoorOpenTime;

    public FireboxBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static boolean isFuel(ItemStack stack) {
        return !stack.isEmpty() && stack.getBurnTime(RecipeType.SMELTING) > 0;
    }

    public boolean toggleDoors() {
        manualDoorsOpen = !manualDoorsOpen;
        setChanged();
        sendData();
        return manualDoorsOpen;
    }

    public boolean areDoorsManuallyOpen() {
        return manualDoorsOpen;
    }

    // the blaze burner's campfire crackle, quiet while it lights, loud when hot, weak when starved
    // animateTick never runs for blocks on a train, so the block entity does it
    private void tickCrackle() {
        if (!getBlockState().getValue(BlockStateProperties.LIT) || dispFire <= 0f) return;
        int rarity;
        float loudness;
        float lowPitch;
        float highPitch;
        switch (boilerState) {
            case COLD -> { rarity = 40; loudness = 0.3f; lowPitch = 0.5f; highPitch = 0.8f; }
            case WARMING -> { rarity = 30; loudness = 0.6f; lowPitch = 0.6f; highPitch = 1.1f; }
            case HOT -> { rarity = 18; loudness = 0.9f; lowPitch = 0.8f; highPitch = 1.3f; }
            default -> { rarity = 48; loudness = 0.3f; lowPitch = 0.5f; highPitch = 0.7f; }
        }
        RandomSource random = level.random;
        if (random.nextInt(rarity) != 0) return;
        Vec3 at = Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(worldPosition));
        level.playLocalSound(at.x, at.y, at.z, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                (loudness + random.nextFloat() * 0.5f) * (0.4f + 0.6f * dispFire),
                lowPitch + random.nextFloat() * (highPitch - lowPitch), false);
    }

    // plot space positions on purpose, sable glues these to the body so they ride along with the train
    private void spawnFlames() {
        BlockState state = getBlockState();
        if (!state.getValue(BlockStateProperties.LIT) || dispFire <= 0f) return;
        boolean open = doorOpenTime > DOOR_SWING_TICKS * 0.3f;
        float base;
        switch (boilerState) {
            case COLD -> base = 0.6f;
            case WARMING -> base = 1.4f;
            case HOT -> base = 2.6f;
            default -> base = 0.4f;
        }
        RandomSource random = level.random;
        // behind a shut door nobody sees them, so only a few
        float expected = base * (0.3f + 0.7f * dispFire) * (open ? 1f : 0.2f);
        int count = (int) expected + (random.nextFloat() < expected - (int) expected ? 1 : 0);
        Direction face = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        Direction side = face.getClockWise();
        Vec3 center = Vec3.atCenterOf(worldPosition);
        if (PortalCutsClient.hiddenAtPlot(center.x, center.y, center.z)) return;
        for (BlockPos member : FireboxGroups.members(level, worldPosition)) {
            Vec3 cell = Vec3.atCenterOf(member);
            for (int i = 0; i < count; i++) {
                double along = (random.nextDouble() - 0.5) * 0.7;
                double across = (random.nextDouble() - 0.5) * 0.55;
                double up = -0.32 + random.nextDouble() * 0.25;
                ParticleOptions type = random.nextFloat() < 0.6f ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME;
                level.addParticle(type,
                        cell.x + face.getStepX() * along + side.getStepX() * across, cell.y + up,
                        cell.z + face.getStepZ() * along + side.getStepZ() * across,
                        (random.nextDouble() - 0.5) * 0.01, 0.02 + random.nextDouble() * 0.03,
                        (random.nextDouble() - 0.5) * 0.01);
            }
        }
        if (!open) return;
        if (random.nextInt(4) == 0) {
            // thrown out of the door in a loose fan so they do not pile up at the sill
            double lateral = (random.nextDouble() - 0.5) * 0.3;
            double out = 0.05 + random.nextDouble() * 0.12;
            double spread = (random.nextDouble() - 0.5) * 0.08;
            level.addParticle(ParticleTypes.LAVA,
                    center.x + face.getStepX() * 0.42 + side.getStepX() * lateral, center.y - 0.25 + random.nextDouble() * 0.2,
                    center.z + face.getStepZ() * 0.42 + side.getStepZ() * lateral,
                    face.getStepX() * out + side.getStepX() * spread, 0.04 + random.nextDouble() * 0.1,
                    face.getStepZ() * out + side.getStepZ() * spread);
        }
        if (random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.SMOKE, center.x + (random.nextDouble() - 0.5) * 0.4, center.y + 0.2,
                    center.z + (random.nextDouble() - 0.5) * 0.4, 0.0, 0.015, 0.0);
        }
    }

    public void clientTick() {
        if (level == null) return;
        tickCrackle();
        spawnFlames();

        if (--jitterCooldown <= 0) {
            jitterCooldown = DISPLAY_TICKS;
            dispWaterJitter = rollWaterJitter();
        }

        lastDoorOpenTime = doorOpenTime;
        boolean open = manualDoorsOpen || stoker() != null;
        if (open != doorsSwingingOpen) {
            level.playLocalSound(worldPosition,
                    open ? SoundEvents.IRON_TRAPDOOR_OPEN
                            : SoundEvents.IRON_TRAPDOOR_CLOSE,
                    SoundSource.BLOCKS, 0.7f, 1.1f, false);
        }
        doorsSwingingOpen = open;
        spewSmoke();
        float dir = open ? 1f : -1f;
        doorOpenTime = Math.max(0f, Math.min(DOOR_SWING_TICKS, doorOpenTime + dir * DOOR_SWING_SPEED));
    }

    // shut doors leak a little, open doors let the fumes out, and the first burst is what piled up inside
    private void spewSmoke() {
        BlockState state = getBlockState();
        if (!state.getValue(BlockStateProperties.LIT) || !dispNoStack) return;
        boolean open = doorOpenTime > DOOR_SWING_TICKS * 0.3f;
        if (!open && dispWasteGas <= 0) return;
        float choke = Math.min(1f, dispWasteGas / (float) LoconauticsConfig.DRAFT_CHOKE_TICKS.get());
        float density = open ? 1f : BLOCKED_SMOKE_MIN_DENSITY + (1f - BLOCKED_SMOKE_MIN_DENSITY) * choke;
        int count = open ? 2 + Math.round(choke * 6f) : 2;
        float scale = BLOCKED_SMOKE_SCALE * (open ? 1f + choke * 1.5f : 1f);
        double push = open ? 0.04 + 0.08 * choke : 0.03;
        double rise = open ? 0.01 + 0.03 * choke : 0.0;
        Direction face = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        Vec3 mouth = Vec3.atCenterOf(worldPosition).add(face.getStepX() * 0.6, -0.1, face.getStepZ() * 0.6);
        if (PortalCutsClient.hiddenAtPlot(mouth.x, mouth.y, mouth.z)) {
            return;
        }
        Vec3 world = Sable.HELPER.projectOutOfSubLevel(level, mouth);
        Vec3 outward = Sable.HELPER.projectOutOfSubLevel(level, mouth.add(face.getStepX(), 0.0, face.getStepZ()))
                .subtract(world);
        for (int i = 0; i < count; i++) {
            if (level.random.nextFloat() > density) continue;
            ParticleWorldLock.addParticle(level, new SmokeOptions(BLOCKED_SMOKE_COLOR, SmokeStyle.LEAK, scale),
                    world.x + (level.random.nextDouble() - 0.5) * 0.3,
                    world.y + level.random.nextDouble() * 0.3,
                    world.z + (level.random.nextDouble() - 0.5) * 0.3,
                    outward.x * push, rise, outward.z * push);
        }
    }

    public float getDoorOpenTime(float partialTicks) {
        return Mth.lerp(partialTicks, lastDoorOpenTime, doorOpenTime);
    }

    @Nullable
    private Player stoker() {
        Vec3 center = worldPosition.getCenter();
        for (Player player : level.getEntitiesOfClass(Player.class,
                new AABB(worldPosition).inflate(8.0))) {
            if (!isFuel(player.getMainHandItem()) && !isFuel(player.getOffhandItem())) continue;
            double reach = player.getAttributeValue(Attributes
                    .BLOCK_INTERACTION_RANGE) + DOOR_REACH_PADDING;
            if (player.getEyePosition().distanceToSqr(center) < reach * reach) return player;
        }
        return null;
    }

    public void tick() {
        if (level == null || level.isClientSide) return;

        if (--rescanCooldown <= 0) {
            rescanCooldown = RESCAN_TICKS;
            rescan();
        }

        readSubLevelSpeed();
        tickWhistles();

        if (structure == null) {
            forceCold();
            return;
        }

        readAxle();

        drainInlets();
        boolean burning = consumeFuel();
        boolean watered = drawWater();
        refillTank();

        tickStateMachine(burning, watered);
        if (tickPressure(burning && watered, watered)) return;
        tickWasteGas(burning);
        if (tickFailure(watered)) return;
        updateLit(burning);
        tickDisplay();
    }

    private void tickWhistles() {
        if (!(level instanceof ServerLevel server)) return;
        boolean can = canWhistle();
        whistles.tick(server, worldPosition, structure, can, can ? whistleStrength() : 0f);
    }

    private void rescan() {
        BoilerMultiblockValidator.Scan result = BoilerMultiblockValidator.scan(level, worldPosition);
        boolean wasValid = structure != null;
        structure = result.structure();
        missingKey = result.missingKey();
        reconcileBarrels();

        if (structure == null) {
            boilerState = BoilerState.COLD;
            warmupTicks = 0;
        } else {
            tankSource.setWaterCapacity(structure.waterCapacity());
            reclaimParkedWater();
            resizeFuel(Math.max(1, structure.fireboxPositions().size()));
        }

        if (wasValid == (structure == null)) {
            setChanged();
            sendData();
        }
    }

    @Nullable
    private String computeNextStep() {
        if (structure == null) return missingKey == null ? "add_boiler_body" : missingKey;
        if (!structure.hasWaterInlet()) return "add_water_inlet";
        if (!structure.hasTakeoff() && bogeyDemands.isEmpty()) return "add_output";
        if (!structure.hasStack()) return "add_smokestack";
        BoilerWaterSource injector = water();
        if (injector == null || injector.getWaterAmount() <= 0) return "add_water";
        if (burnTimeRemaining <= 0f && firstFuelSlot() < 0) return "add_fuel";
        if (!ignited) return "light_fire";
        return null;
    }

    @Nullable
    public String nextStepKey() {
        return dispStep == null || dispStep.isEmpty() ? null : dispStep;
    }

    @Nullable
    private String computeOptionalStep() {
        if (structure == null) return null;
        if (boilerState == BoilerState.WARMING || boilerState == BoilerState.HOT) return null;
        return structure.hasSafetyValve() || hasBoundValve() ? null : "add_safety_valve";
    }

    @Nullable
    private SafetyValveBlockEntity safetyValve() {
        if (structure == null || structure.safetyValvePos() == null || level == null) return null;
        return level.getBlockEntity(structure.safetyValvePos())
                instanceof SafetyValveBlockEntity valve ? valve : null;
    }

    public boolean hasBoundValve() {
        return safetyValve() != null;
    }

    public float valveOpening() {
        SafetyValveBlockEntity valve = safetyValve();
        return valve == null ? 0f : valve.opening();
    }

    private boolean canWhistle() {
        return ignited && pressureFraction() >= WHISTLE_MIN_PRESSURE;
    }

    private float whistleStrength() {
        return Math.min(1f, (pressureFraction() - WHISTLE_MIN_PRESSURE) / (1f - WHISTLE_MIN_PRESSURE));
    }

    private float pressureFraction() {
        float burst = (float) (double) LoconauticsConfig.BURST_PRESSURE.get();
        return burst <= 0f ? 1f : Math.min(1f, steamPressure / burst);
    }

    public float steamPressure() {
        return steamPressure;
    }

    // a bogey reports every solve, a report older than this belongs to a bogey that is gone
    private static final long DEMAND_TICKS = 40L;
    private record Demand(float su, long tick) {}

    private final Map<Long, Demand> bogeyDemands = new HashMap<>();

    public void reportBogeyDemand(BlockPos bogey, float su) {
        if (level == null) return;
        bogeyDemands.put(bogey.asLong(), new Demand(su, level.getGameTime()));
    }

    public void clearBogeyDemand(BlockPos bogey) {
        bogeyDemands.remove(bogey.asLong());
    }

    public float bogeyDemand() {
        if (level == null) return 0f;
        long now = level.getGameTime();
        float total = 0f;
        Iterator<Demand> it = bogeyDemands.values().iterator();
        while (it.hasNext()) {
            Demand entry = it.next();
            if (now - entry.tick() > DEMAND_TICKS) {
                it.remove();
                continue;
            }
            total += entry.su();
        }
        return total;
    }

    public float poolMax() {
        if (structure == null) return 0f;
        return BoilerLevel.poolSu(structure.level(fireGrade()), (float) (double) LoconauticsConfig.SU_PER_LEVEL.get(),
                drivePressure());
    }

    public BoilerPool pool() {
        return new BoilerPool(poolMax(), bogeyDemand(), 0f);
    }

    // create counts capacity per rpm, the outputs get what the bogeys leave of the pool
    public float outputSuPerRpm() {
        float rpm = shaftRpm();
        return rpm <= 0f ? 0f : quantize2(pool().remainder()) / rpm;
    }

    // a fire that just went out still has steam, the output coasts down as the pressure bleeds off
    public float drivePressure() {
        return outputFraction();
    }

    public float getDisplayFire() { return dispFire; }

    private boolean tickPressure(boolean fed, boolean watered) {
        float burst = (float) (double) LoconauticsConfig.BURST_PRESSURE.get();
        SafetyValveBlockEntity valve = safetyValve();
        if (valve != null) {
            valve.boilerSeen();
            valve.setVenting(steamPressure > 0f);
        }
        if (valve instanceof SmartSafetyValveBlockEntity smart) {
            smart.updateFromBoiler(burst <= 0f ? 0f : steamPressure / burst);
        }
        float rise = fed
                ? (float) (double) LoconauticsConfig.PRESSURE_RISE_PER_TICK.get()
                        * structure.temperatureCap() * draftChoke() * warmthEfficiency() * masterFraction()
                        * (1f + LOW_WATER_RISE_BOOST * lowWater(waterTank.getFluidAmount(), waterTank.getCapacity()))
                : coastRise(watered);
        float vent = (float) PRESSURE_VENT_PER_TICK * valveOpening();
        float bleed = fed ? 0f : idleBleed() * (1f - temperatureFraction());
        float draw = poolDraw() + bogeyTrim(burst);
        lastVented = Math.min(vent, steamPressure + rise);
        steamPressure = Math.max(0f, steamPressure + rise - vent - bleed - draw);

        if (steamPressure >= (float) (double) LoconauticsConfig.BURST_PRESSURE.get()) {
            steamPressure = 0f;
            fail(worldPosition, FailureCause.OVERPRESSURE);
            if (level instanceof ServerLevel server) TrainAdvancements.boilerBurst(server, worldPosition);
            return true;
        }
        return false;
    }

    // a fire that went out leaves a hot shell, it keeps the steam up to what its warmth can hold while it cools
    private float coastRise(boolean watered) {
        float warmth = temperatureFraction();
        if (!watered || steamPressure >= warmth) return 0f;
        return (float) (double) LoconauticsConfig.PRESSURE_RISE_PER_TICK.get() * COAST_RISE_MULTIPLIER * warmth;
    }

    // steam that went out of the valve was water too, full use is the same as pressure drain of 2.5 rises
    private float ventedFraction() {
        float full = (float) (double) LoconauticsConfig.PRESSURE_RISE_PER_TICK.get() * POOL_DRAW_MULTIPLIER;
        return full <= 0f ? 0f : Math.min(1f, lastVented / full);
    }

    // same 1 to 2 times as the fuel burn
    private float steamLeavingNow() {
        return Math.max(outputFraction(), ventedFraction()) * (1f + poolUsedFraction());
    }

    // bogeys and outputs together, as a share of what the pool holds right now
    private float poolUsedFraction() {
        float outputUsed = suOutput() * Math.max(0f, Math.min(1f, lastStressRatio));
        return new BoilerPool(poolMax(), bogeyDemand(), outputUsed).usedFraction();
    }

    private float bogeyTrim(float burst) {
        if (pool().bogeyDraw() <= 0f) return 0f;
        float excess = steamPressure - BOGEY_TARGET_PRESSURE * burst;
        return Math.max(0f, Math.min(excess * BOGEY_TRIM_EASE, BOGEY_TRIM_PER_TICK));
    }

    private float poolDraw() {
        return (float) (double) LoconauticsConfig.PRESSURE_RISE_PER_TICK.get() * poolUsedFraction()
                * POOL_DRAW_MULTIPLIER;
    }

    private static float lowWater(int amount, int capacity) {
        if (capacity <= 0) return 0f;
        return 1f - Math.min(1f, Math.max(0, amount) / (float) capacity);
    }

    private float idleBleed() {
        boolean canLose = structure != null
                && (structure.hasTakeoff() || structure.hasSafetyValve() || hasBoundValve());
        return canLose ? (float) PRESSURE_IDLE_BLEED_PER_TICK : 0f;
    }

    @Nullable
    public String optionalStepKey() {
        return dispOptionalStep == null || dispOptionalStep.isEmpty() ? null : dispOptionalStep;
    }

    private void forceCold() {
        steamPressure = Math.max(0f, steamPressure - idleBleed());
        lastBurning = false;
        ignited = false;
        ventWasteGas();
        tickDisplay();
        if (boilerState == BoilerState.COLD && warmupTicks == 0) return;
        boilerState = BoilerState.COLD;
        warmupTicks = 0;
        updateLit(false);
        setChanged();
        sendData();
    }

    // a schedule can only raise the burn signal, 0 leaves it to the redstone and the panel parts
    public void setScheduleSignal(int signal) {
        int clamped = Mth.clamp(signal, 0, SignalSpeed.OFF_SIGNAL);
        if (clamped == scheduleSignal) return;
        scheduleSignal = clamped;
        setChanged();
    }

    private int masterSignal() {
        int signal = Math.max(scheduleSignal, Math.round(PanelDriveBus.report(level, worldPosition, 0f)));
        if (structure != null) {
            for (BlockPos pos : structure.fireboxPositions()) {
                signal = Math.max(signal, level.getBestNeighborSignal(pos));
            }
        }
        return Mth.clamp(signal, 0, SignalSpeed.OFF_SIGNAL);
    }

    private float masterFraction() {
        return SignalSpeed.fractionWithBank(masterSignal(), (float) (double) LoconauticsConfig.BANKED_BURN_FRACTION.get());
    }

    private float burnRate() {
        return (1f + poolUsedFraction()) * structure.burnMultiplier() * masterFraction()
                * (float) (double) LoconauticsConfig.BURN_RATE_SCALE.get();
    }

    private boolean consumeFuel() {
        if (!ignited) {
            lastBurning = false;
            return false;
        }
        lastBurnRate = burnRate();
        lastBurning = burnFuel(lastBurnRate);
        setChanged();
        return lastBurning;
    }

    private boolean burnFuel(float amount) {
        boolean burned = false;
        while (amount > 0f) {
            if (burnTimeRemaining > 0f) {
                float used = Math.min(amount, burnTimeRemaining);
                burnTimeRemaining -= used;
                amount -= used;
                burned = true;
                continue;
            }
            int slot = firstFuelSlot();
            if (slot < 0) {
                ignited = false;
                break;
            }
            burnTimeRemaining = takeFuel(slot);
        }
        if (burnTimeRemaining <= 0f && firstFuelSlot() < 0) ignited = false;
        return burned;
    }

    private int takeFuel(int slot) {
        ItemStack stack = inventory.getStackInSlot(slot);
        int burnTime = stack.getBurnTime(RecipeType.SMELTING);
        burningGrade = FuelGrades.of(stack);
        ItemStack remainder = stack.getCraftingRemainingItem();
        inventory.extractItem(slot, 1, false);
        spill(remainder);
        return burnTime <= 0 ? DEFAULT_BURN_TICKS : burnTime;
    }

    @Nullable
    private BoilerWaterSource socket() {
        if (structure == null || structure.injectorPos() == null) return null;
        BlockEntity be = level.getBlockEntity(structure.injectorPos());
        if (be instanceof BoilerWaterSource socket) return socket;
        return be == null ? null : HullsteelSocketBridge.waterSource(be);
    }

    @Nullable
    private BoilerWaterSource water() {
        return structure == null ? null : tankSource;
    }

    private void drainInlets() {
        if (inflowCooldown > 0) inflowCooldown--;
        refillTank();
    }

    private void refillTank() {
        if (structure == null) return;
        int room = waterTank.getSpace();
        if (room <= 0) return;

        for (BlockPos connector : structure.connectorPositions()) {
            if (room <= 0) break;
            if (level.getBlockEntity(connector)
                    instanceof BarrelConnectorBlockEntity be) {
                room -= acceptWater(be.drainBuffer(room));
            }
        }

        BoilerWaterSource socket = socket();
        if (socket != null && room > 0) {
            acceptWater(socket.drawWater(room));
        }
    }

    private int acceptWater(int millibuckets) {
        if (millibuckets <= 0) return 0;
        inflowCooldown = DISPLAY_TICKS;
        return waterTank.fill(new FluidStack(
                        Fluids.WATER, millibuckets),
                IFluidHandler.FluidAction.EXECUTE);
    }

    public FluidTank getWaterTank() {
        return waterTank;
    }

    public int fillFromHose(FluidStack resource, IFluidHandler.FluidAction action) {
        if (structure == null || resource.getFluid() != Fluids.WATER) return 0;
        int filled = waterTank.fill(resource, action);
        if (filled > 0 && action.execute()) inflowCooldown = DISPLAY_TICKS;
        return filled;
    }

    private boolean drawWater() {
        BoilerWaterSource injector = water();
        if (injector == null) {
            lastWaterDrawn = 0;
            return false;
        }

        float perTick = (float) WATER_PER_BODY_PER_TICK;
        float steamLeaving = steamLeavingNow();
        int wanted = Math.round(structure.boilerCapacity() * perTick * steamLeaving);
        if (wanted <= 0) {
            lastWaterDrawn = 0;
            return injector.getWaterAmount() > 0;
        }

        int drawn = injector.drawWater(wanted);
        lastWaterDrawn = drawn;
        if (drawn <= 0) {
            return false;
        }

        float recycle = (float) WATER_RECYCLE_FRACTION;
        injector.returnWater((int) (drawn * recycle));
        return true;
    }

    private int warmupTarget() {
        int sizeLevels = structure == null ? 0 : structure.sizeLevels();
        return BoilerLevel.warmupTicks(sizeLevels, LoconauticsConfig.WARMUP_TICKS.get(),
                LoconauticsConfig.WARMUP_PER_SIZE_LEVEL_TICKS.get());
    }

    private void tickStateMachine(boolean burning, boolean watered) {
        BoilerState prev = boilerState;
        int warmupTarget = warmupTarget();
        boolean fed = burning && watered;

        switch (boilerState) {
            case COLD -> {
                if (fed) {
                    boilerState = BoilerState.WARMING;
                }
            }
            case WARMING -> {
                if (fed) {
                    warmupTicks = Math.min(warmupTarget, warmupTicks + 1);
                    if (warmupTicks >= warmupTarget) boilerState = BoilerState.HOT;
                } else if (burning || watered) {
                    boilerState = BoilerState.STARVED;
                } else {
                    coolDown();
                }
            }
            case HOT -> {
                if (!fed) boilerState = BoilerState.STARVED;
            }
            case STARVED -> {
                if (fed) {
                    boilerState = BoilerState.WARMING;
                } else if (burning) {
                    warmupTicks = Math.min(warmupTarget, warmupTicks + 1);
                } else {
                    coolDown();
                }
            }
        }

        if (boilerState != prev) {
            setChanged();
            sendData();
        }
    }

    private void coolDown() {
        warmupTicks = Math.max(0, warmupTicks - 1);
        if (warmupTicks == 0) boilerState = BoilerState.COLD;
    }

    public float outputFraction() {
        if (structure == null) return 0f;

        float raw = Math.min(1f, steamPressure);
        return Math.min(raw, structure.temperatureCap());
    }

    private float draftChoke() {
        float full = LoconauticsConfig.DRAFT_CHOKE_TICKS.get();
        return Math.max(0f, 1f - wasteGas / full);
    }

    public static float quantize2(float value) {
        return Math.round(value / 2f) * 2f;
    }

    public float shaftRpm() {
        if (structure == null) return 0f;
        float fraction = outputFraction();
        if (fraction <= 0f) return 0f;
        float min = (float) TAKEOFF_MIN_RPM;
        float max = (float) TAKEOFF_MAX_RPM;
        return quantize2((min + (max - min) * fraction) * takeoffRegulator());
    }

    private float takeoffRegulator() {
        if (structure == null || structure.takeoffPos() == null) return 1f;
        return level.getBlockEntity(structure.takeoffPos()) instanceof SteamTakeoffBlockEntity takeoff
                ? takeoff.regulator() : 1f;
    }

    public int remainingSeconds() {
        if (structure == null || lastBurnRate <= 0f) return 0;
        float queued = 0f;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack fuel = inventory.getStackInSlot(slot);
            if (isFuel(fuel)) queued += (float) fuel.getBurnTime(RecipeType.SMELTING) * fuel.getCount();
        }
        float ticks = (burnTimeRemaining + queued) / lastBurnRate;
        int fuelSeconds = Math.round(ticks / 20f);
        int waterSeconds = waterSecondsLeft();
        return waterSeconds < 0 ? fuelSeconds : Math.min(fuelSeconds, waterSeconds);
    }

    // minus one when the water is no limit, a supply that keeps filling the tank never runs it dry
    private int waterSecondsLeft() {
        BoilerWaterSource injector = water();
        if (injector == null || inflowCooldown > 0) return -1;
        float steamLeaving = steamLeavingNow();
        float net = structure.boilerCapacity() * (float) WATER_PER_BODY_PER_TICK * steamLeaving
                * (1f - (float) WATER_RECYCLE_FRACTION);
        if (net <= 0f) return -1;
        return Math.round(injector.getWaterAmount() / net / 20f);
    }

    public static String formatTime(int sec) {
        int min = sec / 60;
        int hour = min / 60;
        sec = Math.floorMod(sec, 60);
        min = Math.floorMod(min, 60);
        StringBuilder out = new StringBuilder();
        if (hour > 0) out.append(hour).append("h ");
        if (min < 10 && hour > 0) out.append('0');
        if (min > 0 || hour > 0) out.append(min).append("m ");
        if (sec < 10 && (min > 0 || hour > 0)) out.append('0');
        return out.append(sec).append('s').toString();
    }

    public float temperatureFraction() {
        int target = warmupTarget();
        return target <= 0 ? 0f : warmupTicks / (float) target;
    }

    private static float boilingFraction() {
        double ambient = LoconauticsConfig.AMBIENT_TEMPERATURE_C.get();
        double working = LoconauticsConfig.WORKING_TEMPERATURE_C.get();
        if (working <= BOILING_C) return 0f;
        double ratio = Math.max(0.0, (BOILING_C - ambient) / (working - ambient));
        return (float) (ratio * ratio);
    }

    private float warmthEfficiency() {
        float boiling = boilingFraction();
        float t = temperatureFraction();
        if (t < boiling) return 0f;
        if (boiling >= 1f) return 1f;
        float span = Math.min(1f, (t - boiling) / (1f - boiling));
        return COLD_FIRE_EFFICIENCY + (1f - COLD_FIRE_EFFICIENCY) * span;
    }

    public float steamTemperatureCelsius() {
        double ambient = LoconauticsConfig.AMBIENT_TEMPERATURE_C.get();
        double working = LoconauticsConfig.WORKING_TEMPERATURE_C.get();
        double curve = Math.sqrt(Math.max(0f, temperatureFraction()));
        double lowWaterHeat = 1.0 + LOW_WATER_HEAT_SHARE * lowWater(dispWater, dispWaterCapacity);
        return (float) (ambient + (working - ambient) * curve * lowWaterHeat) + dispTemperatureJitter;
    }

    private float rollTemperatureJitter() {
        float amplitude = (float) TEMPERATURE_JITTER_C;
        if (amplitude <= 0f || level == null) return 0f;
        float triangular = level.random.nextFloat() - level.random.nextFloat();
        return triangular * amplitude * temperatureFraction();
    }

    private float rollWaterJitter() {
        float amplitude = (float) WATER_JITTER_MB;
        if (amplitude <= 0f || level == null) return 0f;
        if (dispWater <= 0 || inflowCooldown <= 0) return 0f;
        float triangular = level.random.nextFloat() - level.random.nextFloat();
        return triangular * amplitude * temperatureFraction();
    }

    public static String formattedTemperature(FireboxBlockEntity be) {
        float celsius = be.steamTemperatureCelsius();
        return format(celsius);
    }

    private static String format(float celsius) {
        return switch (LoconauticsConfig.TEMPERATURE_UNIT.get()) {
            case FAHRENHEIT -> String.format(Locale.ROOT, "%.0f F", celsius * 9f / 5f + 32f);
            case KELVIN -> String.format(Locale.ROOT, "%.0f K", celsius + 273.15f);
            case CELSIUS -> String.format(Locale.ROOT, "%.0f C", celsius);
        };
    }

    public String temperatureFeelKey() {
        float t = temperatureFraction();
        if (t < 0.33f) return "cold";
        if (t < 0.66f) return "warm";
        return "hot";
    }

    public float maxPossibleSu() {
        if (structure == null) return 0f;
        return suPerRpm() * (float) TAKEOFF_MAX_RPM;
    }

    // the grade of what burns now decides the fire limit, with no fire it is the grade of the next fuel in the slot
    private FuelGrade fireGrade() {
        if (burnTimeRemaining > 0f || firstFuel().isEmpty()) return burningGrade;
        return FuelGrades.of(firstFuel());
    }

    // create counts stress capacity per rpm, so the pool at full pressure is spread over the top rpm
    public float suPerRpm() {
        if (structure == null) return 0f;
        return BoilerLevel.poolSu(structure.level(fireGrade()), (float) (double) LoconauticsConfig.SU_PER_LEVEL.get(), 1f)
                / (float) TAKEOFF_MAX_RPM;
    }

    public int getWaterCapacity() {
        return Math.max(0, dispWaterCapacity);
    }

    public boolean isDisplayValid() { return dispValid; }
    public float getDisplayPressure() { return dispPressure; }
    public float getDisplayMaxSu() { return dispMaxSu; }
    public boolean isIgnited() { return ignited; }
    public int getDisplayLevel() { return dispLevel; }
    public int getDisplayMaxLevel() { return dispMaxLevel; }
    public float getDisplayBogeyDemand() { return dispBogeyDemand; }
    public float getDisplaySu() { return dispSu; }
    public float getDisplayRpm() { return dispRpm; }
    public float getDisplayEfficiency() { return dispEfficiency; }
    public int getDisplayWater() {
        int capacity = getWaterCapacity();
        return Math.max(0, Math.min(capacity, Math.round(dispWater + dispWaterJitter)));
    }
    public int getDisplayRemaining() { return dispRemainingSeconds; }
    public int getDisplayWasteGas() { return dispWasteGas; }
    public float getLastStressRatio() { return lastStressRatio; }
    public double getLastTrainMass() { return lastTrainMass; }
    public boolean onATrain() { return lastTrainMass >= 0.0; }
    public float getSubLevelSpeed() { return lastSubSpeed; }
    public boolean inSubLevel() { return inSubLevel; }

    public float suOutput() {
        if (structure == null) return 0f;
        return quantize2(outputSuPerRpm() * shaftRpm());
    }

    // with no smokestack the fumes pile up inside, open doors let them out
    private void tickWasteGas(boolean burning) {
        if (burning && !hasStack()) {
            if (doorsOpen()) {
                wasteGas = Math.max(0, wasteGas - DOOR_FUME_RELEASE);
            } else {
                wasteGas = Math.min(wasteGas + 1, LoconauticsConfig.DRAFT_CHOKE_TICKS.get());
            }
        } else {
            ventWasteGas();
        }
    }

    private boolean doorsOpen() {
        if (--doorCheck <= 0) {
            doorCheck = DOOR_CHECK_TICKS;
            doorsOpenNow = manualDoorsOpen || stoker() != null;
        }
        return doorsOpenNow;
    }

    private void ventWasteGas() {
        wasteGas = Math.max(0, wasteGas - 2);
    }

    private void tickDisplay() {
        if (--displayCooldown > 0) return;
        displayCooldown = DISPLAY_TICKS;
        syncTrainSummary();

        BoilerWaterSource injector = water();

        dispValid = structure != null;
        dispSu = suOutput();
        dispRpm = shaftRpm();
        dispEfficiency = structure == null ? 0f : Math.min(1f, structure.heatRatio()) * warmthEfficiency();
        dispOverfired = structure != null && structure.isOverfired();
        dispWater = injector == null ? 0 : injector.getWaterAmount();
        dispWasteGas = wasteGas;
        dispStep = computeNextStep();
        dispOptionalStep = computeOptionalStep();
        dispRemainingSeconds = remainingSeconds();
        dispTemperatureJitter = rollTemperatureJitter();
        float burstPressure = (float) (double) LoconauticsConfig.BURST_PRESSURE.get();
        dispPressure = burstPressure <= 0f ? 0f : steamPressure / burstPressure;
        dispMaxSu = maxPossibleSu();
        dispBogeyDemand = bogeyDemand();
        dispFire = lastBurning ? masterFraction() : 0f;
        dispNoStack = structure != null && !structure.hasStack();
        dispWaterCapacity = structure == null ? 0 : structure.waterCapacity();
        dispSizeLevels = structure == null ? 0 : structure.sizeLevels();
        dispFireLevels = structure == null ? 0 : structure.fireLevels(fireGrade());
        dispLevel = structure == null ? 0 : structure.level(fireGrade());
        dispMaxLevel = LoconauticsConfig.BOILER_MAX_LEVEL.get();
        dispBogeys = !bogeyDemands.isEmpty();

        pushTakeoff();
        pushSmokestack();
        setChanged();
        sendData();
    }

    // a train that is unloaded keeps what is left of the fuel and the water, the loaded boiler hands it over
    private void syncTrainSummary() {
        if (!(Sable.HELPER.getContaining(this) instanceof ServerSubLevel sub)) return;
        SableTrain train = SableTrainRegistry.bySubLevel(sub.getUniqueId());
        if (train == null) return;
        boolean hasBogey = !SubLevelBlocks.findAll(sub, BoilerBogeyBlockEntity.class).isEmpty();
        if (!hasBogey) {
            train.setBoilerSummary(null);
            return;
        }
        BoilerSummary old = train.boilerSummary();
        if (old != null) {
            long away = old.ticksAway(level.getGameTime());
            float cooling = away - old.burnedTicks();
            if (old.hasSpent()) {
                applySpent(old.takeSpentFuel(), old.takeSpentWater());
            }
            if (away > AWAY_TICKS && old.fireOut()) coolWhileAway(cooling);
        }
        BoilerSummary fresh = new BoilerSummary(ignited, fuelLeft(), lastBurnRate,
                water() == null ? 0 : water().getWaterAmount(), netWaterPerTick());
        fresh.stamp(level.getGameTime());
        train.setBoilerSummary(fresh);
        BoilerStopAlert.check(train, BoilerStopAlert.rake(train), fresh, steamPressure);
    }

    private void coolWhileAway(float ticks) {
        if (ticks <= 0f || warmupTicks <= 0) return;
        warmupTicks = Math.max(0, warmupTicks - Math.round(ticks));
        if (warmupTicks == 0) boilerState = BoilerState.COLD;
        steamPressure = Math.min(steamPressure, temperatureFraction());
        setChanged();
    }

    private float fuelLeft() {
        float queued = 0f;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack fuel = inventory.getStackInSlot(slot);
            if (isFuel(fuel)) queued += (float) fuel.getBurnTime(RecipeType.SMELTING) * fuel.getCount();
        }
        return burnTimeRemaining + queued;
    }

    private float netWaterPerTick() {
        if (structure == null) return 0f;
        float steamLeaving = steamLeavingNow();
        return structure.boilerCapacity() * (float) WATER_PER_BODY_PER_TICK * steamLeaving
                * (1f - (float) WATER_RECYCLE_FRACTION);
    }

    // the same burn and water the loaded tick would have used, taken in one go when the car loads back in
    private void applySpent(float burn, int waterUsed) {
        burnFuel(burn);
        BoilerWaterSource injector = water();
        if (injector != null && waterUsed > 0) injector.drawWater(waterUsed);
        setChanged();
    }

    private boolean hasStack() {
        return structure != null && structure.hasStack();
    }

    private void pushSmokestack() {
        BlockPos base = structure == null ? null : structure.stackPos();
        if (base == null) return;
        BlockPos top = base;
        while (top.getY() - base.getY() + 1 < SmokestackBlock.MAX_HEIGHT && top.getY() < level.getMaxBuildHeight() - 1
                && level.getBlockState(top.above()).is(BoilerTags.SMOKESTACK)) {
            top = top.above();
        }
        int warm = Math.round(temperatureFraction() * 15f);
        int power = lastBurning ? Math.max(1, warm) : steamPressure > 0f ? warm : 0;
        for (BlockPos at = base; at.getY() <= top.getY(); at = at.above()) {
            if (level.getBlockEntity(at) instanceof SmokestackBlockEntity stack) {
                stack.setBoilerDriven(true);
                stack.setPower(at.equals(top) ? power : 0);
                stack.setColor(smokeColor());
            }
        }
    }

    private int smokeColor() {
        if (!lastBurning && warmupTicks > 0) return COOLING_STEAM_COLOR;
        return switch (boilerState) {
            case COLD -> 0x201C18;
            case WARMING, HOT -> blendSmoke(warmthEfficiency());
            case STARVED -> 0x3A3430;
        };
    }

    private static int blendSmoke(float t) {
        int dark = 0x201C18;
        int light = 0xF0F0F0;
        int out = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int a = (dark >> shift) & 0xFF;
            int b = (light >> shift) & 0xFF;
            out |= Math.round(a + (b - a) * t) << shift;
        }
        return out;
    }

    private void pushTakeoff() {
        if (structure == null || structure.takeoffPos() == null) return;
        if (level.getBlockEntity(structure.takeoffPos()) instanceof SteamTakeoffBlockEntity takeoff) {
            takeoff.supply(shaftRpm(), outputSuPerRpm());
        }
    }

    private boolean tickFailure(boolean watered) {
        boolean dryAndHot = !watered && warmupTicks > 0;
        dryTicks = dryAndHot ? dryTicks + 1 : Math.max(0, dryTicks - 2);

        if (dryTicks >= LoconauticsConfig.CROWN_SHEET_TICKS.get()) {
            fail(worldPosition, FailureCause.CROWN_SHEET);
            return true;
        }
        return false;
    }

    private void announceFailure(BlockPos at, FailureCause cause) {
        if (!(level instanceof ServerLevel server)) return;
        Component message = Component.translatable("loconautics.boiler.failed",
                        Component.translatable(cause.chatKey()).withStyle(ChatFormatting.RED))
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(" "))
                .append(Component.translatable(cause.fixKey()).withStyle(ChatFormatting.GRAY));
        for (ServerPlayer player : server.players()) {
            if (player.blockPosition().distSqr(at) < 64 * 64) player.sendSystemMessage(message);
        }
    }

    private void fail(BlockPos at, FailureCause cause) {
        announceFailure(at, cause);
        lastFailure = cause;
        steamPressure = 0f;
        dryTicks = 0;
        warmupTicks = 0;
        boilerState = BoilerState.COLD;
        ignited = false;
        burnTimeRemaining = 0f;
        updateLit(false);
        setChanged();
        sendData();
    }

    private void readSubLevelSpeed() {
        SubLevel sub = Sable.HELPER.getContaining(this);
        inSubLevel = sub != null;
        if (sub == null) {
            lastSubSpeed = 0f;
            return;
        }
        org.joml.Vector3d center = new org.joml.Vector3d(
                worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
        org.joml.Vector3d now = sub.logicalPose().transformPosition(center, new org.joml.Vector3d());
        org.joml.Vector3d before = sub.lastPose().transformPosition(center, new org.joml.Vector3d());
        lastSubSpeed = (float) (now.sub(before).length() * 20.0);
    }

    private void readAxle() {
        lastStressRatio = readNetworkStress();
        lastTrainMass = readTrainMass();
    }

    private float readNetworkStress() {
        if (structure == null || structure.takeoffPos() == null) return 0f;
        return level.getBlockEntity(structure.takeoffPos()) instanceof SteamTakeoffBlockEntity takeoff
                ? takeoff.networkStressRatio() : 0f;
    }

    private double readTrainMass() {
        if (!(level instanceof ServerLevel)) return NO_TRAIN;
        var sub = Sable.HELPER.getContaining(this);
        if (!(sub instanceof ServerSubLevel serverSub)) return NO_TRAIN;
        TrainDriveSource source = DriveSources.resolve(serverSub);
        return source == null ? NO_TRAIN : source.trainMass();
    }

    @Nullable
    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public void setControllerPos(@Nullable BlockPos pos) {
        BlockPos next = pos == null ? null : pos.immutable();
        if (Objects.equals(controllerPos, next)) return;
        controllerPos = next;
        setChanged();
        sendData();
    }

    public boolean ignite(Player player, ItemStack igniter, InteractionHand hand) {
        if (structure == null || ignited) return false;
        if (burnTimeRemaining <= 0f && firstFuelSlot() < 0) return false;
        ignited = true;
        if (!player.isCreative()) {
            if (igniter.isDamageableItem()) {
                igniter.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            } else {
                ItemStack remainder = igniter.getCraftingRemainingItem();
                igniter.shrink(1);
                if (!remainder.isEmpty()) player.getInventory().placeItemBackInInventory(remainder);
            }
        }
        playAt(SoundEvents.FLINTANDSTEEL_USE, 1f);
        setChanged();
        sendData();
        return true;
    }

    public boolean insertFuel(Player player, ItemStack held) {
        if (!isFuel(held)) return false;
        ItemStack leftover = ItemHandlerHelper.insertItem(inventory, held.copy(), false);
        if (leftover.getCount() == held.getCount()) return false;
        if (!player.isCreative()) held.setCount(leftover.getCount());
        return true;
    }

    private void resizeFuel(int slots) {
        if (inventory.getSlots() == slots) return;
        List<ItemStack> kept = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            kept.add(inventory.getStackInSlot(slot));
        }
        inventory.setSize(slots);
        for (int slot = 0; slot < kept.size(); slot++) {
            ItemStack stack = kept.get(slot);
            if (stack.isEmpty()) continue;
            if (slot < slots) {
                inventory.setStackInSlot(slot, stack);
            } else {
                spill(stack);
            }
        }
        setChanged();
    }

    private void spill(ItemStack stack) {
        if (level == null || stack.isEmpty()) return;
        Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY() + 1,
                worldPosition.getZ(), stack);
    }

    private int firstFuelSlot() {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (isFuel(inventory.getStackInSlot(slot))) return slot;
        }
        return -1;
    }

    public ItemStack firstFuel() {
        int slot = firstFuelSlot();
        return slot < 0 ? ItemStack.EMPTY : inventory.getStackInSlot(slot);
    }

    public int fuelCapacity() {
        ItemStack fuel = firstFuel();
        int perSlot = Math.min(inventory.getSlotLimit(0),
                fuel.isEmpty() ? Item.DEFAULT_MAX_STACK_SIZE : fuel.getMaxStackSize());
        return inventory.getSlots() * perSlot;
    }

    public int fuelHeld() {
        int held = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            held += inventory.getStackInSlot(slot).getCount();
        }
        return held;
    }

    public void refreshGroup() {
        if (level != null) FireboxGroups.rerender(level, worldPosition);
    }

    // a boiler on a train sits in plot space, a sound there is too far away to hear
    private void playAt(SoundEvent sound, float volume) {
        Vec3 at = Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(worldPosition));
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.BLOCKS, volume, 1f);
    }

    public boolean extinguish() {
        if (!ignited) return false;
        ignited = false;
        burnTimeRemaining = 0f;
        lastBurning = false;
        playAt(SoundEvents.FIRE_EXTINGUISH, 0.8f);
        setChanged();
        sendData();
        return true;
    }

    public void dropFuel() {
        if (level == null) return;
        // extractitem never hands back more than one vanilla stack, and a wide firebox holds several
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack held = inventory.getStackInSlot(slot).copy();
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
            spill(held);
        }
    }

    public void parkWaterInBarrels() {
        if (level == null) return;
        int totalCapacity = trackedCapacity();
        int water = waterTank.getFluidAmount();
        for (Map.Entry<BlockPos, Integer> barrel : trackedBarrels.entrySet()) {
            if (level.getBlockEntity(barrel.getKey()) instanceof BoilerBarrelBlockEntity be) {
                be.storeWater(shareOf(water, barrel.getValue(), totalCapacity));
            }
        }
        trackedBarrels.clear();
        waterTank.setFluid(FluidStack.EMPTY);
    }

    private void reconcileBarrels() {
        Set<BlockPos> current = structure == null ? Set.of() : new HashSet<>(structure.boilerPositions());
        int totalCapacity = trackedCapacity();
        int water = waterTank.getFluidAmount();
        int leaving = 0;
        Iterator<Map.Entry<BlockPos, Integer>> it = trackedBarrels.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Integer> barrel = it.next();
            BlockPos pos = barrel.getKey();
            if (current.contains(pos) || !level.isLoaded(pos)) continue;
            int share = shareOf(water, barrel.getValue(), totalCapacity);
            if (!level.getBlockState(pos).is(BoilerTags.BOILER_BODY)) {
                leaving += share;
            } else if (level.getBlockEntity(pos) instanceof BoilerBarrelBlockEntity be) {
                be.storeWater(share);
                leaving += share;
            }
            it.remove();
        }
        if (leaving > 0) waterTank.drain(leaving, IFluidHandler.FluidAction.EXECUTE);
        for (BlockPos pos : current) {
            trackedBarrels.put(pos, BoilerContributions.waterCapacityOf(level.getBlockState(pos)));
        }
        setChanged();
    }

    private int trackedCapacity() {
        int total = 0;
        for (int capacity : trackedBarrels.values()) total += capacity;
        return total;
    }

    private static int shareOf(int water, int capacity, int totalCapacity) {
        return totalCapacity <= 0 ? 0 : (int) ((long) water * capacity / totalCapacity);
    }

    private void reclaimParkedWater() {
        for (BlockPos pos : structure.boilerPositions()) {
            int room = waterTank.getSpace();
            if (room <= 0) return;
            if (level.getBlockEntity(pos) instanceof BoilerBarrelBlockEntity barrel) {
                int taken = barrel.takeWater(room);
                if (taken > 0) waterTank.fill(new FluidStack(Fluids.WATER, taken), IFluidHandler.FluidAction.EXECUTE);
            }
        }
    }

    public static void transferControllerData(FireboxBlockEntity oldController, Level level,
                                                BlockPos oldPos, BlockPos newPos, Direction face) {
        if (!(level.getBlockEntity(newPos) instanceof FireboxBlockEntity newController)) return;
        newController.copyControllerDataFrom(oldController);
        oldController.resetControllerData();
        newController.rescanCooldown = 0;
        newController.setChanged();
        newController.sendData();
        oldController.setChanged();
        oldController.sendData();
    }

    private void copyControllerDataFrom(FireboxBlockEntity other) {
        inventory.setSize(other.inventory.getSlots());
        for (int slot = 0; slot < other.inventory.getSlots(); slot++) {
            inventory.setStackInSlot(slot, other.inventory.getStackInSlot(slot).copy());
        }
        waterTank.setCapacity(other.waterTank.getCapacity());
        waterTank.setFluid(other.waterTank.getFluid().copy());
        trackedBarrels.clear();
        trackedBarrels.putAll(other.trackedBarrels);
        boilerState = other.boilerState;
        warmupTicks = other.warmupTicks;
        burnTimeRemaining = other.burnTimeRemaining;
        burningGrade = other.burningGrade;
        ignited = other.ignited;
        scheduleSignal = other.scheduleSignal;
        wasteGas = other.wasteGas;
        dryTicks = other.dryTicks;
        lastFailure = other.lastFailure;
        steamPressure = other.steamPressure;
        lastBurning = other.lastBurning;
        lastBurnRate = other.lastBurnRate;
        manualDoorsOpen = other.manualDoorsOpen;
    }

    private void resetControllerData() {
        inventory.setSize(FUEL_SLOTS);
        inventory.setStackInSlot(0, ItemStack.EMPTY);
        waterTank.setFluid(FluidStack.EMPTY);
        waterTank.setCapacity(0);
        trackedBarrels.clear();
        boilerState = BoilerState.COLD;
        warmupTicks = 0;
        burnTimeRemaining = 0f;
        burningGrade = FuelGrade.NORMAL;
        ignited = false;
        scheduleSignal = 0;
        wasteGas = 0;
        dryTicks = 0;
        lastFailure = null;
        steamPressure = 0f;
        lastBurning = false;
        lastBurnRate = 0f;
        manualDoorsOpen = false;
        structure = null;
    }

    public void shiftReferences(BlockPos shift) {
        if (controllerPos != null) controllerPos = controllerPos.offset(shift);
        Map<BlockPos, Integer> moved = new HashMap<>();
        trackedBarrels.forEach((pos, capacity) -> moved.put(pos.offset(shift), capacity));
        trackedBarrels.clear();
        trackedBarrels.putAll(moved);
        rescanCooldown = 0;
        setChanged();
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel server) {
            whistles.stopAll(server);
            PanelDriveBus.release(server, worldPosition);
        }
        whistles.forget();
        super.setRemoved();
    }

    public void onBecameController() {
        rescanCooldown = 0;
        setChanged();
        sendData();
    }

    private void updateLit(boolean burning) {
        BlockState state = getBlockState();
        if (!state.hasProperty(BlockStateProperties.LIT)) return;
        if (state.getValue(BlockStateProperties.LIT) == burning) return;
        level.setBlock(worldPosition, state.setValue(BlockStateProperties.LIT, burning), 3);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (level != null) {
            BlockPos controller = FireboxGroups.controllerOf(level, worldPosition);
            if (controller != null && !controller.equals(worldPosition)
                    && level.getBlockEntity(controller) instanceof FireboxBlockEntity owner) {
                return owner.addToGoggleTooltip(tooltip, isPlayerSneaking);
            }
        }
        if (!dispValid) {
            LoconauticsLang.translate("gui.goggles.boiler_stats").forGoggles(tooltip);
            addFuelSlot(tooltip);
            addFailureWarning(tooltip);
            LoconauticsLang.translate("gui.goggles.boiler_incomplete")
                    .style(ChatFormatting.RED).forGoggles(tooltip);
            addNextStep(tooltip);
            return true;
        }

        int sizeLevels = dispSizeLevels;
        int fireLevels = ignited ? dispFireLevels : 0;
        int waterLevels = dispWaterCapacity <= 0 ? 0 : Math.round((float) dispMaxLevel * dispWater / dispWaterCapacity);
        int boilerLevel = ignited ? dispLevel : 0;
        boolean producing = ignited || dispPressure > 0f || dispSu > 0f;

        boolean fireOut = boilerState == BoilerState.COLD;
        MutableComponent status;
        if (fireOut && lastFailure != null) {
            status = LoconauticsLang.translate("gui.goggles.boiler_state_cold").component().withStyle(ChatFormatting.RED);
        } else if (!producing) {
            status = LoconauticsLang.translate("gui.goggles.boiler_idle").component().withStyle(ChatFormatting.GRAY);
        } else if (fireOut || boilerLevel <= 0) {
            status = LoconauticsLang.translate("gui.goggles.boiler_state_cold").component().withStyle(ChatFormatting.GRAY);
        } else {
            MutableComponent level = boilerLevel >= dispMaxLevel
                    ? LoconauticsLang.translate("gui.goggles.boiler_max").component()
                    : LoconauticsLang.translate("gui.goggles.boiler_lvl", boilerLevel).component();
            status = LoconauticsLang.translate("gui.goggles.boiler_state_" + boilerState.name().toLowerCase(Locale.ROOT))
                    .component().withStyle(stateColor())
                    .append(Component.literal("  ").append(level).withStyle(ChatFormatting.DARK_GRAY));
        }
        LoconauticsLang.translate("gui.goggles.boiler_status", status).forGoggles(tooltip);

        BoilerGoggleBars bars = new BoilerGoggleBars(dispMaxLevel, sizeLevels, fireLevels, waterLevels);
        LoconauticsLang.builder().add(bars.row("size", sizeLevels)).forGoggles(tooltip, 1);
        LoconauticsLang.builder().add(bars.row("water", waterLevels)).forGoggles(tooltip, 1);
        LoconauticsLang.builder().add(bars.row("fire", fireLevels)).forGoggles(tooltip, 1);

        addFuelSlot(tooltip);
        addFailureWarning(tooltip);

        if (dispBogeys) {
            LoconauticsLang.translate("gui.goggles.boiler_bar_bogey").style(ChatFormatting.WHITE).forGoggles(tooltip);
            LoconauticsLang.builder().add(bars.powerRow("power", dispBogeyDemand, dispMaxSu)).forGoggles(tooltip, 1);
        }

        addNextStep(tooltip);
        addOptionalStep(tooltip);
        return true;
    }

    private void addFuelSlot(List<Component> tooltip) {
        LoconauticsLang.translate("gui.goggles.boiler_fuel").style(ChatFormatting.WHITE).forGoggles(tooltip);
        if (firstFuel().isEmpty()) {
            LoconauticsLang.translate("gui.goggles.boiler_fuel_empty")
                    .style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
            return;
        }
        LoconauticsLang.builder().add(firstFuel().getHoverName().copy())
                .text(" " + fuelHeld() + "/" + fuelCapacity())
                .style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
    }

    private static FailureCause readFailure(String name) {
        if (name == null || name.isEmpty()) return null;
        try {
            return FailureCause.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void addFailureWarning(List<Component> tooltip) {
        if (lastFailure == null) return;
        LoconauticsLang.translate("gui.goggles.boiler_last_failure")
                .style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.translate(lastFailure.langKey())
                .style(ChatFormatting.RED).forGoggles(tooltip, 1);
    }

    private void addNextStep(List<Component> tooltip) {
        String step = nextStepKey();
        if (step == null) return;
        LoconauticsLang.translate("gui.goggles.boiler_next_step")
                .style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.translate("gui.goggles.boiler_step_" + step)
                .style(ChatFormatting.YELLOW).forGoggles(tooltip, 1);
    }

    private void addOptionalStep(List<Component> tooltip) {
        String step = optionalStepKey();
        if (step == null) return;
        LoconauticsLang.translate("gui.goggles.boiler_optional_step")
                .style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.translate("gui.goggles.boiler_step_" + step)
                .style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
    }

    private ChatFormatting stateColor() {
        return switch (boilerState) {
            case COLD -> ChatFormatting.DARK_GRAY;
            case WARMING -> ChatFormatting.GOLD;
            case HOT -> ChatFormatting.GREEN;
            case STARVED -> ChatFormatting.RED;
        };
    }

    public BoilerState getBoilerState() { return boilerState; }
    public int getWarmupTicks() { return warmupTicks; }
    public float getBurnTimeRemaining() { return burnTimeRemaining; }
    public float getLastBurnRate() { return lastBurnRate; }
    public int getLastWaterDrawn() { return lastWaterDrawn; }
    public boolean hasValidStructure() { return structure != null; }
    @Nullable public BoilerStructure getStructure() { return structure; }
    public ItemStackHandler getInventory() { return inventory; }

    private final class TankSource implements BoilerWaterSource {

        @Override
        public int getWaterAmount() {
            return waterTank.getFluidAmount();
        }

        @Override
        public int getWaterCapacity() {
            return waterTank.getCapacity();
        }

        @Override
        public int drawWater(int millibuckets) {
            if (millibuckets <= 0) return 0;
            return waterTank.drain(millibuckets,
                    IFluidHandler.FluidAction.EXECUTE).getAmount();
        }

        @Override
        public void returnWater(int millibuckets) {
            if (millibuckets <= 0) return;
            waterTank.fill(new FluidStack(
                            Fluids.WATER, millibuckets),
                    IFluidHandler.FluidAction.EXECUTE);
        }

        @Override
        public void setWaterCapacity(int millibuckets) {
            int capacity = Math.max(0, millibuckets);
            if (waterTank.getCapacity() == capacity) return;
            waterTank.setCapacity(capacity);
            int overflow = waterTank.getFluidAmount() - capacity;
            if (overflow > 0) {
                waterTank.drain(overflow,
                        IFluidHandler.FluidAction.EXECUTE);
            }
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.put("WaterTank", waterTank.writeToNBT(registries, new CompoundTag()));
        tag.putInt("TankCapacity", waterTank.getCapacity());
        tag.putString("BoilerState", boilerState.name());
        tag.putInt("WarmupTicks", warmupTicks);
        tag.putFloat("BurnTimeRemaining", burnTimeRemaining);
        tag.putString("BurningGrade", burningGrade.name());
        tag.putBoolean("Ignited", ignited);
        tag.putInt("ScheduleSignal", scheduleSignal);
        tag.putInt("DryTicks", dryTicks);
        tag.putInt("WasteGas", wasteGas);
        tag.putString("LastFailure", lastFailure == null ? "" : lastFailure.name());
        tag.putBoolean("DispValid", dispValid);
        tag.putFloat("DispSu", dispSu);
        tag.putFloat("DispRpm", dispRpm);
        tag.putFloat("DispEfficiency", dispEfficiency);
        tag.putBoolean("DispOverfired", dispOverfired);
        tag.putInt("DispWater", dispWater);
        tag.putInt("DispWasteGas", dispWasteGas);
        tag.putInt("DispRemaining", dispRemainingSeconds);
        tag.putFloat("DispTempJitter", dispTemperatureJitter);
        tag.putInt("DispWaterDrawn", lastWaterDrawn);
        tag.putFloat("DispPressure", dispPressure);
        tag.putFloat("SteamPressure", steamPressure);
        tag.putFloat("DispMaxSu", dispMaxSu);
        tag.putFloat("DispBogeyDemand", dispBogeyDemand);
        tag.putFloat("DispFire", dispFire);
        tag.putBoolean("DispNoStack", dispNoStack);
        tag.putInt("DispWaterCapacity", dispWaterCapacity);
        tag.putInt("DispSizeLevels", dispSizeLevels);
        tag.putInt("DispFireLevels", dispFireLevels);
        tag.putInt("DispLevel", dispLevel);
        tag.putInt("DispMaxLevel", dispMaxLevel);
        tag.putBoolean("DispBogeys", dispBogeys);
        tag.putFloat("DispBurnRate", lastBurnRate);
        tag.putFloat("DispStress", lastStressRatio);
        tag.putDouble("DispMass", lastTrainMass);
        tag.putFloat("DispSubSpeed", lastSubSpeed);
        tag.putBoolean("DispInSubLevel", inSubLevel);
        tag.putString("DispStep", dispStep == null ? "" : dispStep);
        tag.putString("DispOptionalStep", dispOptionalStep == null ? "" : dispOptionalStep);
        tag.putBoolean("DoorsOpen", manualDoorsOpen);
        if (controllerPos != null) tag.putLong("Controller", controllerPos.asLong());
        ListTag barrels = new ListTag();
        for (Map.Entry<BlockPos, Integer> barrel : trackedBarrels.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", barrel.getKey().asLong());
            entry.putInt("Capacity", barrel.getValue());
            barrels.add(entry);
        }
        tag.put("TrackedBarrels", barrels);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ItemStack fuelBefore = inventory.getStackInSlot(0).copy();
        BlockPos ownerBefore = controllerPos;
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        controllerPos = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
        waterTank.setCapacity(tag.getInt("TankCapacity"));
        waterTank.readFromNBT(registries, tag.getCompound("WaterTank"));
        try {
            boilerState = BoilerState.valueOf(tag.getString("BoilerState"));
        } catch (IllegalArgumentException e) {
            boilerState = BoilerState.COLD;
        }
        warmupTicks = tag.getInt("WarmupTicks");
        burnTimeRemaining = tag.getFloat("BurnTimeRemaining");
        try {
            burningGrade = FuelGrade.valueOf(tag.getString("BurningGrade"));
        } catch (IllegalArgumentException e) {
            burningGrade = FuelGrade.NORMAL;
        }
        dryTicks = tag.getInt("DryTicks");
        ignited = tag.contains("Ignited") ? tag.getBoolean("Ignited") : burnTimeRemaining > 0f;
        scheduleSignal = tag.getInt("ScheduleSignal");
        wasteGas = tag.getInt("WasteGas");
        lastFailure = readFailure(tag.getString("LastFailure"));
        dispValid = tag.getBoolean("DispValid");
        dispSu = tag.getFloat("DispSu");
        dispRpm = tag.getFloat("DispRpm");
        dispEfficiency = tag.getFloat("DispEfficiency");
        dispOverfired = tag.getBoolean("DispOverfired");
        dispWater = tag.getInt("DispWater");
        dispWasteGas = tag.getInt("DispWasteGas");
        dispRemainingSeconds = tag.getInt("DispRemaining");
        dispTemperatureJitter = tag.getFloat("DispTempJitter");
        lastWaterDrawn = tag.getInt("DispWaterDrawn");
        dispPressure = tag.getFloat("DispPressure");
        steamPressure = tag.getFloat("SteamPressure");
        dispMaxSu = tag.getFloat("DispMaxSu");
        dispBogeyDemand = tag.getFloat("DispBogeyDemand");
        dispFire = tag.getFloat("DispFire");
        dispNoStack = tag.getBoolean("DispNoStack");
        dispWaterCapacity = tag.getInt("DispWaterCapacity");
        dispSizeLevels = tag.getInt("DispSizeLevels");
        dispFireLevels = tag.getInt("DispFireLevels");
        dispLevel = tag.getInt("DispLevel");
        dispMaxLevel = tag.contains("DispMaxLevel") ? tag.getInt("DispMaxLevel") : 12;
        dispBogeys = tag.getBoolean("DispBogeys");
        lastBurnRate = tag.getFloat("DispBurnRate");
        lastStressRatio = tag.getFloat("DispStress");
        lastTrainMass = tag.contains("DispMass") ? tag.getDouble("DispMass") : NO_TRAIN;
        lastSubSpeed = tag.getFloat("DispSubSpeed");
        inSubLevel = tag.getBoolean("DispInSubLevel");
        dispStep = tag.getString("DispStep");
        dispOptionalStep = tag.getString("DispOptionalStep");
        manualDoorsOpen = tag.getBoolean("DoorsOpen");
        trackedBarrels.clear();
        ListTag barrels = tag.getList("TrackedBarrels", Tag.TAG_COMPOUND);
        for (int i = 0; i < barrels.size(); i++) {
            CompoundTag entry = barrels.getCompound(i);
            trackedBarrels.put(BlockPos.of(entry.getLong("Pos")), entry.getInt("Capacity"));
        }
        if (StrandTemplates.isPlacing()) {
            trackedBarrels.clear();
            steamPressure = 0f;
            dispPressure = 0f;
        }

        if (level != null && level.isClientSide
                && (!ItemStack.matches(fuelBefore, inventory.getStackInSlot(0))
                || !Objects.equals(ownerBefore, controllerPos))) {
            refreshGroup();
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void sendData() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
}
