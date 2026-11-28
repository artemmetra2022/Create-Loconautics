package com.lycoris.loconautics.trainphysics.drive;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

// server only, anything that turns a car into a driven car, the bearing axle and the boiler bogey
public interface TrainDriveSource {

    BlockPos getBlockPos();

    BlockState getBlockState();

    @Nullable
    BlockPos driverBogeyPos();

    // which way the car front points, in the frame of the sub level plot
    Direction frontLocal();

    // signed rpm the car is asked to turn at, before anything stops it
    double rawRpm();

    // rpm after overstress or a dead power source, zero when nothing drives
    double effectiveRpm();

    // true when there is a power source behind it, a kinetic network or a lit boiler
    boolean isPlumbed();

    long getPlacedTime();

    // how much of its full pull the source has right now, a boiler with low steam pulls less
    default double powerFraction() {
        return 1.0;
    }

    // the car mass this source was last solved with
    double trainMass();

    int getSteerInput();

    int gear();

    double gearRatio();

    double maxGearRatio();

    AxleGearing.Load gearingLoad(double massShare, boolean opposed, boolean gearsEnabled);

    double directImpact(double massShare, boolean opposed);

    void setConsistLoad(double massShare, boolean opposed, int group, double ownStrength, double netStrength,
                        double slipFactor, int groupSize, AxleGearing.Gearing gearing);

    void setSlopeAngle(double degrees);

    void resetGearing();
}
