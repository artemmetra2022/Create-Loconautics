package com.lycoris.loconautics.mixin.sable.collision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SubLevelEntityCollision.class, remap = false)
public abstract class SubLevelEntityCollisionPadMixin {

    @WrapOperation(
            method = "collide",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/core/BlockPos;betweenClosed(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Ljava/lang/Iterable;"))
    private static Iterable<BlockPos> loconautics$padBlockRange(BlockPos min, BlockPos max,
                                                               Operation<Iterable<BlockPos>> original) {
        // Pad sideways only. The extra block above used to pull a leaning car's roof into the player's
        // headroom check in curves, and vanilla then forced the player into a crouch.
        return original.call(min.offset(-1, 0, -1), max.offset(1, 0, 1));
    }
}
