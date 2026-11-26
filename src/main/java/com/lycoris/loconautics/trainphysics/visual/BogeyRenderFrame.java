package com.lycoris.loconautics.trainphysics.visual;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.config.BogeyRenderConfig;
import com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

@OnlyIn(Dist.CLIENT)
public final class BogeyRenderFrame {

    public record Frame(Quaternionf local, Vector3f sway, Vector3f railOffset, Vector3f bodyCounter,
                        boolean railed) { }

    private static final Set<AbstractBogeyBlockEntity> RAILED_BACKWARDS =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private BogeyRenderFrame() {
    }

    static boolean facesBackwards(AbstractBogeyBlockEntity be) {
        return RAILED_BACKWARDS.contains(be);
    }

    public static Frame of(AbstractBogeyBlockEntity be, ClientSubLevel sub, float partialTick) {
        Vector3f offset = BogeyYawVisual.getRailLocalOffset(be, new Vector3f());
        Quaternionf local = new Quaternionf();
        Vector3f sway = new Vector3f(offset);
        // the server publishes a swing for exactly the bogeys that are off the rail, one loose bogey on an otherwise
        Quaternionf swing = BogeyYawVisual.getWheelSwing(be, new Quaternionf());
        BogeyRailPoints.Chord walked = sub == null || derailed(sub) || swing != null
                ? null : BogeyRailPoints.chordAt(be, sub);
        boolean railed = walked != null;

        boolean axisX = be.getBlockState().getValue(AbstractBogeyBlock.AXIS) == Direction.Axis.X;
        if (walked == null) {
            double yaw = (axisX ? 90.0 : 0.0) + (RAILED_BACKWARDS.contains(be) ? 180.0 : 0.0);
            local.rotationY((float) Math.toRadians(yaw));
            if (swing != null) {
                local.premul(BogeySwingSmoother.interp(be, swing, partialTick, new Quaternionf())).normalize();
            }
            sway.zero();
        } else {
            Quaterniondc bodyQ = sub.renderPose().orientation();
            Quaternionf body = new Quaternionf((float) bodyQ.x(), (float) bodyQ.y(),
                    (float) bodyQ.z(), (float) bodyQ.w());
            Quaternionf bodyInverse = new Quaternionf(body).conjugate();
            Vector3f forwardCar = bodyInverse.transform(new Vector3f((float) walked.forward().x,
                    (float) walked.forward().y, (float) walked.forward().z), new Vector3f());
            boolean backwards = (axisX ? forwardCar.x : forwardCar.z) < 0.0f;
            if (backwards ? RAILED_BACKWARDS.add(be) : RAILED_BACKWARDS.remove(be)) {
                LoconauticsConstants.TRACE.warn("[bogeyroll] bogey={} now faces {} along its axis", be.getBlockPos(),
                        backwards ? "backwards" : "forwards");
            }
            Vector3f up = new Vector3f((float) walked.up().x, (float) walked.up().y, (float) walked.up().z);
            if (BogeyRenderConfig.rolling()) {
                Quaternionf lean = SableTrainClientRegistry.lean(sub.getUniqueId(), partialTick);
                Quaternionf level = lean == null ? new Quaternionf(body)
                        : new Quaternionf(body).mul(new Quaternionf(lean).conjugate());
                Vector3f carUp = level.transform(new Vector3f(0.0f, 1.0f, 0.0f), new Vector3f());
                if (carUp.dot(up) > 0.0f) {
                    up.set(carUp);
                }
            }
            local.set(bodyInverse.mul(BogeyRailPoints.worldFrame(walked, up, new Quaternionf())));
            scaleTurn(local);
        }

        Vector3f counter = railed ? BogeyYawVisual.getBodyCounter(be, new Vector3f()) : new Vector3f();
        return new Frame(local, sway, offset, counter, railed);
    }

    private static boolean derailed(ClientSubLevel sub) {
        SableTrainClientRegistry.TrainMarker marker = SableTrainClientRegistry.get(sub.getUniqueId());
        return marker != null && marker.derailed();
    }

    private static void scaleTurn(Quaternionf localFrame) {
        float gain = BogeyTurnTweak.gain();
        if (gain == 0.0f) {
            return;
        }
        Vector3f f = localFrame.transform(new Vector3f(0.0f, 0.0f, 1.0f), new Vector3f());
        float yaw = (float) Math.atan2(f.x, f.z);
        localFrame.rotateY(yaw * gain).normalize();
    }
}
