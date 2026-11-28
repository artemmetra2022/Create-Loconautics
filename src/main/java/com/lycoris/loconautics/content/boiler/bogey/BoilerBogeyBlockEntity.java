package com.lycoris.loconautics.content.boiler.bogey;

import com.lycoris.loconautics.common.LinkHolder;
import com.lycoris.loconautics.common.RedstoneLinkPair;
import com.lycoris.loconautics.content.boiler.BoilerLookup;
import com.lycoris.loconautics.content.boiler.BoilerTags;
import com.lycoris.loconautics.content.boiler.firebox.FireboxBlockEntity;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.lycoris.loconautics.trainphysics.drive.AxleGearing;
import com.lycoris.loconautics.trainphysics.drive.AxleImpact;
import com.lycoris.loconautics.trainphysics.drive.TrainDriveSource;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

public class BoilerBogeyBlockEntity extends AbstractBogeyBlockEntity implements MenuProvider, LinkHolder, TrainDriveSource {

    private final RedstoneLinkPair throttleLinks = new RedstoneLinkPair(this,
            this::setSpeedPower, s -> setReversed(s > 0));
    private final RedstoneLinkPair steerLinks = new RedstoneLinkPair(this,
            s -> setLeft(s > 0), s -> setRight(s > 0));

    private int speedPower;
    private boolean reversed;
    private boolean left;
    private boolean right;

    private static final long LOOKUP_TICKS = 20L;

    private long placedTime;
    private double slopeAngleDeg;
    private double massShare;
    private boolean opposed;
    private long lookedUp = -LOOKUP_TICKS;
    @Nullable private FireboxBlockEntity boiler;
    private final BoilerBogeySteam steam = new BoilerBogeySteam();

    public void clientTick() {
        steam.tick(level, worldPosition);
    }

    public BoilerBogeyBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public BogeyStyle getDefaultStyle() {
        return BoilerBogeyStyles.BOILER;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null || level.isClientSide) return;
        if (placedTime == 0L) placedTime = level.getGameTime();
        registerLinks();
    }

    @Override
    public void setRemoved() {
        releaseLinks();
        FireboxBlockEntity fire = boiler;
        if (fire != null) fire.clearBogeyDemand(worldPosition);
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        releaseLinks();
    }

    @Override
    public void releaseLinks() {
        if (level == null) return;
        throttleLinks.unregister(level);
        steerLinks.unregister(level);
    }

    private void registerLinks() {
        throttleLinks.register(level);
        steerLinks.register(level);
    }

    public int speedPower() {
        return speedPower;
    }

    public boolean isReversed() {
        return reversed;
    }

    public int steerInput() {
        if (left == right) return 0;
        return left ? -1 : 1;
    }

    private void setSpeedPower(int power) {
        int clamped = Math.max(0, Math.min(15, power));
        if (speedPower == clamped) return;
        speedPower = clamped;
        setChanged();
    }

    private void setReversed(boolean active) {
        if (reversed == active) return;
        reversed = active;
        setChanged();
    }

    private void setLeft(boolean active) {
        if (left == active) return;
        left = active;
        setChanged();
    }

    private void setRight(boolean active) {
        if (right == active) return;
        right = active;
        setChanged();
    }

    public void setSpeedFrequency(ItemStack first, ItemStack second) {
        throttleLinks.setFirstFrequency(first, second);
        rebind(throttleLinks);
    }

    public void setDirectionFrequency(ItemStack first, ItemStack second) {
        throttleLinks.setSecondFrequency(first, second);
        rebind(throttleLinks);
    }

    public void setLeftFrequency(ItemStack first, ItemStack second) {
        steerLinks.setFirstFrequency(first, second);
        rebind(steerLinks);
    }

    public void setRightFrequency(ItemStack first, ItemStack second) {
        steerLinks.setSecondFrequency(first, second);
        rebind(steerLinks);
    }

    private void rebind(RedstoneLinkPair pair) {
        if (level != null && !level.isClientSide) pair.register(level);
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Nullable
    public FireboxBlockEntity boiler() {
        if (level == null || level.isClientSide) return null;
        long now = level.getGameTime();
        if (boiler != null && boiler.isRemoved()) {
            boiler = null;
            lookedUp = -LOOKUP_TICKS;
        }
        if (now - lookedUp < LOOKUP_TICKS) return boiler;
        lookedUp = now;
        BlockPos above = worldPosition.above();
        boolean onBody = level.getBlockState(above).is(BoilerTags.BOILER_BODY)
                || level.getBlockState(above).is(BoilerTags.BOILER_FIREBOX);
        boiler = onBody ? BoilerLookup.controllerFor(level, above) : null;
        return boiler;
    }

    private float pressure() {
        FireboxBlockEntity fire = boiler();
        return fire == null ? 0f : fire.drivePressure();
    }

    @Override
    public double powerFraction() {
        return pressure();
    }

    @Override
    public double trainMass() {
        return massShare;
    }

    public int wheelPairs() {
        return BoilerBogeyWheels.read(getBogeyData());
    }

    public void flipPistons() {
        CompoundTag data = getBogeyData();
        data.putBoolean(BoilerBogeyWheels.PISTONS_REAR_KEY, !data.getBoolean(BoilerBogeyWheels.PISTONS_REAR_KEY));
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public boolean addWheelPair() {
        int pairs = wheelPairs();
        if (pairs >= BoilerBogeyWheels.MAX_PAIRS) return false;
        getBogeyData().putInt(BoilerBogeyWheels.KEY, pairs + 1);
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return true;
    }

    public boolean removeWheelPair() {
        int pairs = wheelPairs();
        if (pairs <= BoilerBogeyWheels.MIN_PAIRS) return false;
        getBogeyData().putInt(BoilerBogeyWheels.KEY, pairs - 1);
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return true;
    }

    public boolean isOpposed() {
        return opposed;
    }

    @Override
    public BlockPos driverBogeyPos() {
        return worldPosition;
    }

    @Override
    public Direction frontLocal() {
        Direction.Axis axis = getBlockState().getValue(AbstractBogeyBlock.AXIS);
        FireboxBlockEntity fire = boiler();
        if (fire != null) {
            // the firebox door is the rear of the engine, the body runs forward from it
            Direction forward = level.getBlockState(fire.getBlockPos())
                    .getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
            if (forward.getAxis() == axis) return forward;
        }
        return Direction.get(Direction.AxisDirection.POSITIVE, axis);
    }

    @Override
    public double rawRpm() {
        double rpm = Math.round(Math.ceil(speedPower * AxleGearing.FULL_RPM / 15.0) * pressure() / 2.0) * 2.0;
        return reversed ? -rpm : rpm;
    }

    @Override
    public double effectiveRpm() {
        FireboxBlockEntity fire = boiler();
        if (fire == null || !isPlumbed() || fire.pool().bogeysOverdrawn()) return 0.0;
        return rawRpm();
    }

    @Override
    public boolean isPlumbed() {
        return pressure() > 0f;
    }

    @Override
    public long getPlacedTime() {
        return placedTime;
    }

    @Override
    public int getSteerInput() {
        return steerInput();
    }

    @Override
    public int gear() {
        return 0;
    }

    @Override
    public double gearRatio() {
        return 1.0;
    }

    @Override
    public double maxGearRatio() {
        return 1.0;
    }

    @Override
    public AxleGearing.Load gearingLoad(double massShare, boolean opposed, boolean gearsEnabled) {
        return new AxleGearing.Load(null, 0.0, 0.0, 0.0, directImpact(massShare, opposed), 0);
    }

    @Override
    public double directImpact(double massShare, boolean opposed) {
        return AxleImpact.of(massShare, slopeAngleDeg, opposed);
    }

    @Override
    public void setConsistLoad(double massShare, boolean opposed, int group, double ownStrength,
                               double netStrength, double slipFactor, int groupSize,
                               AxleGearing.Gearing gearing) {
        this.massShare = massShare;
        this.opposed = opposed;
        FireboxBlockEntity fire = boiler();
        if (fire != null) {
            fire.reportBogeyDemand(worldPosition, (float) (directImpact(massShare, opposed) * Math.abs(rawRpm())));
        }
    }

    @Override
    public void setSlopeAngle(double degrees) {
        this.slopeAngleDeg = Math.max(0.0, degrees);
    }

    @Override
    public void resetGearing() {
    }

    public RedstoneLinkPair throttleLinks() {
        return throttleLinks;
    }

    public RedstoneLinkPair steerLinks() {
        return steerLinks;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.loconautics.boiler_bogey");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new BoilerBogeyMenu(LoconauticsRegistries.BOILER_BOGEY_MENU.get(), id, inventory, this);
    }

    public void sendToMenu(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(worldPosition);
        buffer.writeNbt(saveWithoutMetadata(buffer.registryAccess()));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("SpeedPower", speedPower);
        tag.putBoolean("Reversed", reversed);
        tag.putBoolean("Left", left);
        tag.putBoolean("Right", right);
        tag.putLong("PlacedTime", placedTime);
        CompoundTag freq = new CompoundTag();
        freq.put("SpeedFirst", throttleLinks.getFirstFreqA().saveOptional(registries));
        freq.put("SpeedSecond", throttleLinks.getFirstFreqB().saveOptional(registries));
        freq.put("DirFirst", throttleLinks.getSecondFreqA().saveOptional(registries));
        freq.put("DirSecond", throttleLinks.getSecondFreqB().saveOptional(registries));
        freq.put("LeftFirst", steerLinks.getFirstFreqA().saveOptional(registries));
        freq.put("LeftSecond", steerLinks.getFirstFreqB().saveOptional(registries));
        freq.put("RightFirst", steerLinks.getSecondFreqA().saveOptional(registries));
        freq.put("RightSecond", steerLinks.getSecondFreqB().saveOptional(registries));
        tag.put("Frequency", freq);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        speedPower = tag.getInt("SpeedPower");
        reversed = tag.getBoolean("Reversed");
        left = tag.getBoolean("Left");
        right = tag.getBoolean("Right");
        placedTime = tag.getLong("PlacedTime");
        if (!tag.contains("Frequency")) return;
        CompoundTag freq = tag.getCompound("Frequency");
        throttleLinks.setFirstFrequency(
                ItemStack.parseOptional(registries, freq.getCompound("SpeedFirst")),
                ItemStack.parseOptional(registries, freq.getCompound("SpeedSecond")));
        throttleLinks.setSecondFrequency(
                ItemStack.parseOptional(registries, freq.getCompound("DirFirst")),
                ItemStack.parseOptional(registries, freq.getCompound("DirSecond")));
        steerLinks.setFirstFrequency(
                ItemStack.parseOptional(registries, freq.getCompound("LeftFirst")),
                ItemStack.parseOptional(registries, freq.getCompound("LeftSecond")));
        steerLinks.setSecondFrequency(
                ItemStack.parseOptional(registries, freq.getCompound("RightFirst")),
                ItemStack.parseOptional(registries, freq.getCompound("RightSecond")));
    }
}
