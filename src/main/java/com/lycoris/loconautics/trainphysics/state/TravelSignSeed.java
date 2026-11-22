package com.lycoris.loconautics.trainphysics.state;

import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.UUID;

public final class TravelSignSeed implements CartStateStore {

    @Override
    public void forget(SableTrain train, UUID subId, CartEvent event) {
        if (event == CartEvent.HANDED_OVER || event == CartEvent.GHOSTED || event == CartEvent.REMOVED
                || event == CartEvent.DERAILED) {
            return;
        }
        RailCarriage carriage = train.car().carriage();
        ServerSubLevel sub = SubLevelData.resolve(train.level(), subId);
        if (carriage == null || sub == null) {
            return;
        }
        Vec3 facing = SableTrainSpawner.currentFacing(sub, train.car());
        seed(carriage, facing);
        for (SableTrain.Bogey bogey : train.car().bogeys()) {
            seed(bogey.rail(), facing);
        }
    }

    private static void seed(RailCarriage rail, Vec3 facing) {
        if (rail != null && rail.center() != null) {
            TrainRailAdvance.seedTravelSign(rail, facing);
        }
    }

    @Override
    public void retain(Set<UUID> liveTrains, Set<UUID> liveSubs) {
    }

    @Override
    public String describe(UUID trainId, UUID subId) {
        return "";
    }
}
