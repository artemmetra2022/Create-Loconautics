package com.lycoris.loconautics.mixin.client.camera;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Access to Minecraft's active render target for the off-screen camera pass. */
@Mixin(Minecraft.class)
public interface MinecraftRenderTargetAccessor {

    @Accessor("mainRenderTarget")
    void loconautics$setMainRenderTarget(RenderTarget target);
}
