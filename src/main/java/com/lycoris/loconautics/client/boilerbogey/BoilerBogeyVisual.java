package com.lycoris.loconautics.client.boilerbogey;

import com.lycoris.loconautics.client.BoilerBogeyPartials;
import com.lycoris.loconautics.content.boiler.bogey.BoilerBogeyWheels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.trains.bogey.BogeyVisual;
import com.simibubi.create.foundation.render.SpecialModels;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// client only, the instanced draw path of the steam bogey
@OnlyIn(Dist.CLIENT)
public class BoilerBogeyVisual implements BogeyVisual {

    private final List<TransformedInstance> all = new ArrayList<>();
    private final TransformedInstance[][] parts = new TransformedInstance[BoilerBogeyGearData.BY_PAIRS.length][];
    private final TransformedInstance[][] statics = new TransformedInstance[BoilerBogeyGearData.BY_PAIRS.length][];
    private final BoilerBogeyGear.Sample sample = new BoilerBogeyGear.Sample();
    private int shown = -1;

    public BoilerBogeyVisual(VisualizationContext ctx, float partialTick, boolean inContraption) {
        for (int pairs = 0; pairs < parts.length; pairs++) {
            parts[pairs] = create(ctx, BoilerBogeyPartials.GEAR_PART[pairs]);
            statics[pairs] = create(ctx, BoilerBogeyPartials.GEAR_STATIC[pairs]);
        }
        for (TransformedInstance instance : all) instance.setZeroTransform().setChanged();
    }

    private TransformedInstance[] create(VisualizationContext ctx, PartialModel[] models) {
        TransformedInstance[] out = new TransformedInstance[models.length];
        for (int i = 0; i < models.length; i++) {
            out[i] = ctx.instancerProvider()
                    .instancer(InstanceTypes.TRANSFORMED, SpecialModels.smoothLit(models[i]))
                    .createInstance();
            all.add(out[i]);
        }
        return out;
    }

    @Override
    public void update(CompoundTag bogeyData, float wheelAngle, PoseStack poseStack) {
        int pairs = BoilerBogeyWheels.read(bogeyData);
        float turn = BoilerBogeyWheels.pistonsRear(bogeyData) ? 180f : 0f;
        float angle = turn == 0f ? wheelAngle : -wheelAngle;
        if (shown != pairs) {
            if (shown >= 0) hide(shown);
            shown = pairs;
        }
        BoilerBogeyGearVariant gear = BoilerBogeyGear.variant(pairs);
        BoilerBogeyGear.sample(gear, angle, sample);
        TransformedInstance[] moving = parts[pairs];
        for (int i = 0; i < moving.length; i++) {
            TransformedInstance instance = moving[i].setTransform(poseStack).rotateYDegrees(turn);
            for (int k : gear.chain[gear.partBone[i]]) {
                float[] pivot = gear.origin[k];
                instance.translate(sample.x[k] / 16f, sample.y[k] / 16f, sample.z[k] / 16f)
                        .translate(pivot[0] / 16f, pivot[1] / 16f, pivot[2] / 16f).rotateXDegrees(sample.turn[k])
                        .translate(-pivot[0] / 16f, -pivot[1] / 16f, -pivot[2] / 16f);
            }
            float[] center = gear.partCenter[i];
            instance.translate(center[0] / 16f, center[1] / 16f, center[2] / 16f).translate(-0.5f, -0.5f, -0.5f).setChanged();
        }
        TransformedInstance[] fixed = statics[pairs];
        for (int i = 0; i < fixed.length; i++) {
            float[] center = gear.staticCenter[i];
            fixed[i].setTransform(poseStack).rotateYDegrees(turn)
                    .translate(center[0] / 16f, center[1] / 16f, center[2] / 16f).translate(-0.5f, -0.5f, -0.5f).setChanged();
        }
    }

    private void hide(int pairs) {
        for (TransformedInstance instance : parts[pairs]) instance.setZeroTransform().setChanged();
        for (TransformedInstance instance : statics[pairs]) instance.setZeroTransform().setChanged();
    }

    @Override
    public void hide() {
        shown = -1;
        for (TransformedInstance instance : all) instance.setZeroTransform().setChanged();
    }

    @Override
    public void updateLight(int packedLight) {
        for (TransformedInstance instance : all) instance.light(packedLight).setChanged();
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        all.forEach(consumer);
    }

    @Override
    public void delete() {
        for (TransformedInstance instance : all) instance.delete();
    }
}
