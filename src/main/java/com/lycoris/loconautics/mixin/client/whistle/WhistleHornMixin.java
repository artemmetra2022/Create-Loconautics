package com.lycoris.loconautics.mixin.client.whistle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lycoris.loconautics.content.analogcontroller.WhistleHonks;
import com.lycoris.loconautics.content.analogcontroller.WhistleStrength;
import com.lycoris.loconautics.content.boiler.BoilerAttach;
import com.simibubi.create.content.decoration.steamWhistle.WhistleBlock;
import com.simibubi.create.content.decoration.steamWhistle.WhistleBlockEntity;
import com.simibubi.create.content.decoration.steamWhistle.WhistleSoundInstance;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WhistleBlockEntity.class, remap = false)
public abstract class WhistleHornMixin {

    private static final float MIN_JET = 0.4f;

    @Shadow
    protected WhistleSoundInstance soundInstance;

    @ModifyVariable(method = "tick", at = @At("STORE"), ordinal = 0)
    private boolean loconautics$honk(boolean powered) {
        return powered || WhistleHonks.isHonking(((WhistleBlockEntity) (Object) this).getBlockPos());
    }

    @Inject(method = "tickAudio", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/decoration/steamWhistle/WhistleSoundInstance;keepAlive()V",
            shift = At.Shift.AFTER))
    private void loconautics$pressureVolume(WhistleBlock.WhistleSize size, boolean powered, CallbackInfo ci) {
        float strength = WhistleHonks.strength(((WhistleBlockEntity) (Object) this).getBlockPos());
        ((WhistleStrength) soundInstance).loconautics$setStrength(strength);
    }

    @WrapOperation(method = "tickAudio", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private void loconautics$liftedJet(Level level, ParticleOptions data, double x, double y, double z,
                                       double mx, double my, double mz, Operation<Void> original) {
        WhistleBlockEntity self = (WhistleBlockEntity) (Object) this;
        float strength = WhistleHonks.strength(self.getBlockPos());
        if (strength < 1f && level.random.nextFloat() > strength) return;
        float push = MIN_JET + (1f - MIN_JET) * strength;
        BlockState state = self.getBlockState();
        if (state.hasProperty(BoilerAttach.RAISED) && state.getValue(BoilerAttach.RAISED)) {
            Direction out = WhistleBlock.getAttachedDirection(state).getOpposite();
            x += out.getStepX() * BoilerAttach.BULGE;
            y += out.getStepY() * BoilerAttach.BULGE;
            z += out.getStepZ() * BoilerAttach.BULGE;
        }
        original.call(level, data, x, y, z, mx * push, my * push, mz * push);
    }
}
