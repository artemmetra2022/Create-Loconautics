package com.lycoris.loconautics.mixin.client.goggles;

import com.lycoris.loconautics.content.boiler.BoilerLookup;
import com.lycoris.loconautics.content.boiler.BoilerTags;
import com.lycoris.loconautics.content.boiler.firebox.FireboxBlockEntity;
import com.simibubi.create.content.equipment.goggles.GoggleOverlayRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// client only, reads the tag so datapack bodies show the firebox info too
@Mixin(value = GoggleOverlayRenderer.class, remap = false)
public abstract class GoggleBoilerBodyMixin {

    @Unique
    private static final long LOOKUP_TICKS = 10L;
    @Unique
    private static BlockPos loconautics$lastBody;
    @Unique
    private static BlockPos loconautics$lastController;
    @Unique
    private static long loconautics$lastTick = Long.MIN_VALUE;

    @Inject(method = "proxiedOverlayPosition", at = @At("HEAD"), cancellable = true)
    private static void loconautics$boilerBody(Level level, BlockPos pos, CallbackInfoReturnable<BlockPos> cir) {
        if (!level.getBlockState(pos).is(BoilerTags.BOILER_BODY)) return;
        long now = level.getGameTime();
        if (!pos.equals(loconautics$lastBody) || now - loconautics$lastTick >= LOOKUP_TICKS) {
            loconautics$lastBody = pos.immutable();
            loconautics$lastTick = now;
            BlockPos controller = BoilerLookup.controllerPos(level, pos);
            loconautics$lastController = controller != null && level.getBlockEntity(controller) instanceof FireboxBlockEntity
                    ? controller : null;
        }
        if (loconautics$lastController != null) cir.setReturnValue(loconautics$lastController);
    }
}
