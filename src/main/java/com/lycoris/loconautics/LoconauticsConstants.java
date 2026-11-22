package com.lycoris.loconautics;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LoconauticsConstants {

    private LoconauticsConstants() {
    }

    public static final String MOD_ID = "loconautics";

    public static final Logger LOGGER = LogUtils.getLogger();

    public static final Logger TRACE = LoggerFactory.getLogger("com.lycoris.loconautics.trace");

    public static final int NETWORK_VERSION = 85;

    public static final String CARRIAGE_NAME_INFIX = " Carriage ";

    public static final int CREATE_CARRIAGE_MIDDLE = 1;

    public static final String GHOSTCREATE_TAG = "[ghost]";

    public static final int GHOSTCREATE_WARN_TICKS = 200;

    public static final int GHOSTCREATE_PORTAL_RETRY_TICKS = 20;

    public static final double GHOSTCREATE_GAP_TOLERANCE_BLOCKS = 0.75;

    public static final double GHOSTCREATE_NOMINAL_SLACK_BLOCKS = 1.5;

    public static final double GHOSTCREATE_REPAIR_EPSILON_BLOCKS = 0.02;

    public static final int GHOSTCREATE_REPAIR_STEPS = 8;

    public static final double GHOSTCREATE_HINGE_TOLERANCE_BLOCKS = 0.75;

    public static final int MANEUVER_DEAD_END_RETRY_TICKS = 100;

    public static final int MANEUVER_DEFAULT_PERCENT = 10;

    public static final int MANEUVER_MAX_PERCENT = 75;

    public static final double MANEUVER_REVERSAL_MARGIN = 2.0;

    public static final double MANEUVER_JUNCTION_CLEAR = 1.0;

    public static final int MANEUVER_REVERSAL_MAX_TRIES = 128;

    public static final double MANEUVER_BERTH_GAP = 6.0;

    public static final int INDUSTRIAL_TANK_MAX_WIDTH = 5;

    public static final double RAIL_HEAD_HEIGHT = 5.0 / 16.0;

    public static final double TRACK_COLLISION_HEIGHT = 2.0 / 16.0;

    public static final ResourceLocation RAILWAYS_MONORAIL_MATERIAL =
            ResourceLocation.fromNamespaceAndPath("railways", "monorail");

    public static final double MONORAIL_COLLISION_HEIGHT = 15.0 / 16.0;

    public static final double MONORAIL_PAD_FLOOR = MONORAIL_COLLISION_HEIGHT + 1.0 / 32.0;

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
