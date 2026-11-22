package com.lycoris.loconautics.client;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.client.boilerbogey.BoilerBogeyGearData;
import com.lycoris.loconautics.client.boilerbogey.BoilerBogeyGearVariant;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

// client only, own models, not shared with other bogey styles
@OnlyIn(Dist.CLIENT)
public final class BoilerBogeyPartials {

    private static final Map<String, PartialModel> BY_NAME = new HashMap<>();
    // indexed by wheel pair count, then by model
    public static final PartialModel[][] GEAR_PART = new PartialModel[BoilerBogeyGearData.BY_PAIRS.length][];
    public static final PartialModel[][] GEAR_STATIC = new PartialModel[BoilerBogeyGearData.BY_PAIRS.length][];

    static {
        for (int pairs = 0; pairs < BoilerBogeyGearData.BY_PAIRS.length; pairs++) {
            BoilerBogeyGearVariant gear = BoilerBogeyGearData.BY_PAIRS[pairs];
            GEAR_PART[pairs] = gear(gear.part);
            GEAR_STATIC[pairs] = gear(gear.staticPart);
        }
    }

    private BoilerBogeyPartials() {
    }

    private static PartialModel[] gear(String[] names) {
        PartialModel[] out = new PartialModel[names.length];
        for (int i = 0; i < names.length; i++) {
            out[i] = BY_NAME.computeIfAbsent(names[i],
                    name -> PartialModel.of(LoconauticsConstants.id("block/boiler/bogey/gear/" + name)));
        }
        return out;
    }

    public static void init() {
    }
}
