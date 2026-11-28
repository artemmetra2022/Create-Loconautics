package com.lycoris.loconautics.mixin.client.whistle;

import com.lycoris.loconautics.content.analogcontroller.WhistleStrength;
import com.simibubi.create.content.decoration.steamWhistle.WhistleSoundInstance;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WhistleSoundInstance.class, remap = false)
public abstract class WhistleSoundInstanceMixin extends AbstractTickableSoundInstance implements WhistleStrength {

    private static final float QUIETEST = 0.2f;

    @Unique
    private float loconautics$strength = 1f;

    protected WhistleSoundInstanceMixin(SoundEvent sound, SoundSource source, RandomSource random) {
        super(sound, source, random);
    }

    @Override
    public void loconautics$setStrength(float strength) {
        loconautics$strength = strength;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void loconautics$capVolume(CallbackInfo ci) {
        volume = Math.min(volume, QUIETEST + (1f - QUIETEST) * loconautics$strength);
    }
}
