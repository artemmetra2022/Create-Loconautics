package com.lycoris.loconautics.client.boilerbogey;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// client only, the bones, models and keys of the bogey for one wheel pair count
@OnlyIn(Dist.CLIENT)
public final class BoilerBogeyGearVariant {

    public final int bones;
    public final String[] names;
    public final int[] parent;
    public final float[][] origin;
    public final String[] part;
    public final int[] partBone;
    public final float[][] partCenter;
    public final String[] staticPart;
    public final float[][] staticCenter;
    final float[][] rotTime;
    final float[][] rotValue;
    final int[][] rotSmooth;
    final float[][] posTime;
    final float[][] posValue;
    final int[][] posSmooth;
    public final int[][] chain;

    BoilerBogeyGearVariant(String[] names, int[] parent, float[][] origin, String[] part, int[] partBone,
                           float[][] partCenter, String[] staticPart, float[][] staticCenter, float[][] rotTime,
                           float[][] rotValue, int[][] rotSmooth, float[][] posTime, float[][] posValue,
                           int[][] posSmooth) {
        this.bones = names.length;
        this.names = names;
        this.parent = parent;
        this.origin = origin;
        this.part = part;
        this.partBone = partBone;
        this.partCenter = partCenter;
        this.staticPart = staticPart;
        this.staticCenter = staticCenter;
        this.rotTime = rotTime;
        this.rotValue = rotValue;
        this.rotSmooth = rotSmooth;
        this.posTime = posTime;
        this.posValue = posValue;
        this.posSmooth = posSmooth;
        this.chain = chains(parent);
    }

    // root first, a bone turns about its own pivot inside every bone above it
    private static int[][] chains(int[] parent) {
        int[][] out = new int[parent.length][];
        for (int i = 0; i < parent.length; i++) {
            int depth = 0;
            for (int b = i; b >= 0; b = parent[b]) depth++;
            out[i] = new int[depth];
            for (int b = i, k = depth - 1; b >= 0; b = parent[b], k--) out[i][k] = b;
        }
        return out;
    }
}
