package com.lycoris.loconautics.mixin.client.camera;

import com.lycoris.loconautics.content.camera.CameraFeedManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures requested security cameras after the player's world frame is complete. */
@Mixin(GameRenderer.class)
public abstract class GameRendererCameraMixin {

    @Inject(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;tryTakeScreenshotIfNeeded()V"))
    private void loconautics$captureCameraFeeds(DeltaTracker deltaTracker, boolean renderLevel,
                                                CallbackInfo ci) {
        CameraFeedManager.captureRequested(deltaTracker);
    }
}
