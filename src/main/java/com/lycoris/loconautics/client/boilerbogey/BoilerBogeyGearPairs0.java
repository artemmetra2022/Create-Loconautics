package com.lycoris.loconautics.client.boilerbogey;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// client only, written by the gen boiler bogey gear script, do not edit by hand
@OnlyIn(Dist.CLIENT)
final class BoilerBogeyGearPairs0 {

    private static final String[] NAMES = {"Bogey Block", "Steel Frame", "Bogey Chassis"};
    private static final int[] PARENT = {-1, -1, 1};
    private static final float[][] ORIGIN = {{0f, 16f, 0f}, {0f, 16f, 0f}, {0f, 29f, 26f}};
    // model file, bone and center of every moving model
    private static final String[] PART = {};
    private static final int[] PART_BONE = {};
    private static final float[][] PART_CENTER = {};
    private static final String[] STATIC_PART = {"bogey_chassis_0", "bogey_chassis_1"};
    private static final float[][] STATIC_CENTER = {{0f, 18f, 13f}, {0f, 18f, 27.5f}};

    // time per key, then x y z per key, then 1 where the key is a catmull rom key
    private static final float[][] ROT_TIME = {null, null, null};
    private static final float[][] ROT_VALUE = {null, null, null};
    private static final int[][] ROT_SMOOTH = {null, null, null};

    // time per key, then x y z per key, then 1 where the key is a catmull rom key
    private static final float[][] POS_TIME = {null, null, null};
    private static final float[][] POS_VALUE = {null, null, null};
    private static final int[][] POS_SMOOTH = {null, null, null};

    static final BoilerBogeyGearVariant VARIANT = new BoilerBogeyGearVariant(NAMES, PARENT, ORIGIN, PART, PART_BONE, PART_CENTER,
            STATIC_PART, STATIC_CENTER, ROT_TIME, ROT_VALUE, ROT_SMOOTH, POS_TIME, POS_VALUE, POS_SMOOTH);

    private BoilerBogeyGearPairs0() {
    }
}
