package com.lycoris.loconautics.client.boilerbogey;

import com.lycoris.loconautics.client.BoilerBogeyPartials;
import com.lycoris.loconautics.content.boiler.bogey.BoilerBogeyWheels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.trains.bogey.BogeyRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// client only, the immediate draw path of the steam bogey, used when flywheel is off
@OnlyIn(Dist.CLIENT)
public class BoilerBogeyRenderer implements BogeyRenderer {

    private final BoilerBogeyGear.Sample sample = new BoilerBogeyGear.Sample();

    @Override
    public void render(CompoundTag bogeyData, float wheelAngle, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int light, int overlay, boolean inContraption) {
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.cutoutMipped());
        int pairs = BoilerBogeyWheels.read(bogeyData);
        float turn = BoilerBogeyWheels.pistonsRear(bogeyData) ? 180f : 0f;
        float angle = turn == 0f ? wheelAngle : -wheelAngle;
        BoilerBogeyGearVariant gear = BoilerBogeyGear.variant(pairs);
        BoilerBogeyGear.sample(gear, angle, sample);
        PartialModel[] moving = BoilerBogeyPartials.GEAR_PART[pairs];
        for (int i = 0; i < moving.length; i++) {
            SuperByteBuffer part = buffer(moving[i]).rotateYDegrees(turn);
            for (int k : gear.chain[gear.partBone[i]]) {
                float[] pivot = gear.origin[k];
                part.translate(sample.x[k] / 16f, sample.y[k] / 16f, sample.z[k] / 16f)
                        .translate(pivot[0] / 16f, pivot[1] / 16f, pivot[2] / 16f).rotateXDegrees(sample.turn[k])
                        .translate(-pivot[0] / 16f, -pivot[1] / 16f, -pivot[2] / 16f);
            }
            float[] center = gear.partCenter[i];
            part.translate(center[0] / 16f, center[1] / 16f, center[2] / 16f).translate(-0.5f, -0.5f, -0.5f)
                    .light(light).overlay(overlay).renderInto(poseStack, buffer);
        }
        PartialModel[] fixed = BoilerBogeyPartials.GEAR_STATIC[pairs];
        for (int i = 0; i < fixed.length; i++) {
            float[] center = gear.staticCenter[i];
            buffer(fixed[i]).rotateYDegrees(turn)
                    .translate(center[0] / 16f, center[1] / 16f, center[2] / 16f).translate(-0.5f, -0.5f, -0.5f)
                    .light(light).overlay(overlay).renderInto(poseStack, buffer);
        }
    }

    private static SuperByteBuffer buffer(PartialModel model) {
        return CachedBuffers.partial(model, Blocks.AIR.defaultBlockState());
    }
}
