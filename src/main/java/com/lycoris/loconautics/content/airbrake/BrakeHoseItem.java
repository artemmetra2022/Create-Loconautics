package com.lycoris.loconautics.content.airbrake;

import com.lycoris.loconautics.content.steelcable.SteelCableItem;
import com.lycoris.loconautics.content.steelcable.SteelCableType;

public class BrakeHoseItem extends SteelCableItem {

    public BrakeHoseItem(Properties properties) {
        super(properties);
    }

    @Override
    public SteelCableType cableType() {
        return SteelCableType.BRAKE_HOSE;
    }

    @Override
    public double maxRange() {
        return 12.0;
    }
}
