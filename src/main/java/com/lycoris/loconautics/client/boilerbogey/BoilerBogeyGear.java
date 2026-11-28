package com.lycoris.loconautics.client.boilerbogey;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// client only, plays the keyframes of the steam bogey the way blockbench plays them
@OnlyIn(Dist.CLIENT)
public final class BoilerBogeyGear {

    public static final class Sample {
        public final float[] x = new float[BoilerBogeyGearData.MAX_BONES];
        public final float[] y = new float[BoilerBogeyGearData.MAX_BONES];
        public final float[] z = new float[BoilerBogeyGearData.MAX_BONES];
        public final float[] turn = new float[BoilerBogeyGearData.MAX_BONES];
    }

    private BoilerBogeyGear() {
    }

    public static BoilerBogeyGearVariant variant(int pairs) {
        return BoilerBogeyGearData.BY_PAIRS[Math.max(0, Math.min(BoilerBogeyGearData.BY_PAIRS.length - 1, pairs))];
    }

    public static void sample(BoilerBogeyGearVariant gear, float wheelAngleDegrees, Sample out) {
        double angle = wheelAngleDegrees % 360.0;
        if (angle < 0) angle += 360.0;
        double time = BoilerBogeyGearData.LENGTH * angle / 360.0;
        double[] value = new double[3];
        for (int i = 0; i < gear.bones; i++) {
            out.x[i] = 0;
            out.y[i] = 0;
            out.z[i] = 0;
            out.turn[i] = 0;
            if (gear.posTime[i] != null) {
                channel(gear.posTime[i], gear.posValue[i], gear.posSmooth[i], time, value);
                out.x[i] = (float) value[0];
                out.y[i] = (float) value[1];
                out.z[i] = (float) value[2];
            }
            if (gear.rotTime[i] != null) {
                channel(gear.rotTime[i], gear.rotValue[i], gear.rotSmooth[i], time, value);
                out.turn[i] = (float) value[0];
            }
        }
    }

    // the keyframe lookup of blockbench, a straight line between two linear keys and a catmull rom spline when either key is one
    private static void channel(float[] time, float[] values, int[] smooth, double t, double[] out) {
        int n = time.length;
        int before = -1;
        for (int i = 0; i < n && time[i] <= t; i++) before = i;
        if (before < 0) {
            copy(values, 0, out);
            return;
        }
        if (before == n - 1) {
            copy(values, before, out);
            return;
        }
        int after = before + 1;
        double alpha = (t - time[before]) / (time[after] - time[before]);
        if (smooth[before] == 0 && smooth[after] == 0) {
            for (int a = 0; a < 3; a++) {
                double from = values[before * 3 + a];
                out[a] = from + (values[after * 3 + a] - from) * alpha;
            }
            return;
        }
        // an animation that loops reaches over the end for the missing neighbor
        int beforePlus = before > 0 ? before - 1 : (n >= 3 ? n - 2 : -1);
        int afterPlus = after + 1 < n ? after + 1 : (n >= 3 ? 1 : -1);
        for (int a = 0; a < 3; a++) {
            double[] p = new double[4];
            int len = 0;
            if (beforePlus >= 0) p[len++] = values[beforePlus * 3 + a];
            p[len++] = values[before * 3 + a];
            p[len++] = values[after * 3 + a];
            if (afterPlus >= 0) p[len++] = values[afterPlus * 3 + a];
            double at = alpha + (beforePlus >= 0 ? 1 : 0);
            int index = (int) Math.floor(at);
            double weight = at - index;
            double p0 = p[index == 0 ? index : index - 1];
            double p1 = p[index];
            double p2 = p[index > len - 2 ? len - 1 : index + 1];
            double p3 = p[index > len - 3 ? len - 1 : index + 2];
            double v0 = (p2 - p0) * 0.5;
            double v1 = (p3 - p1) * 0.5;
            double w2 = weight * weight;
            double w3 = weight * w2;
            out[a] = (2 * p1 - 2 * p2 + v0 + v1) * w3 + (-3 * p1 + 3 * p2 - 2 * v0 - v1) * w2 + v0 * weight + p1;
        }
    }

    private static void copy(float[] values, int key, double[] out) {
        for (int a = 0; a < 3; a++) out[a] = values[key * 3 + a];
    }
}
