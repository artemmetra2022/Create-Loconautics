package com.lycoris.loconautics.mixin;

import net.minecraft.world.level.ClipContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ClipContext.class, remap = false)
public interface ClipContextAccessor {

    @Accessor("block")
    ClipContext.Block loconautics$getBlock();
}
