package com.lycoris.loconautics.mixin.create.whistle;

import com.lycoris.loconautics.content.boiler.BoilerAttach;
import com.lycoris.loconautics.foundation.pick.OversizeShape;
import com.simibubi.create.content.decoration.steamWhistle.WhistleExtenderBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WhistleExtenderBlock.class)
public abstract class WhistleExtenderRaiseMixin extends Block implements OversizeShape {

    protected WhistleExtenderRaiseMixin(Properties properties) {
        super(properties);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void loconautics$defaultNotRaised(CallbackInfo ci) {
        registerDefaultState(defaultBlockState().setValue(BoilerAttach.RAISED, false));
    }

    @Inject(method = "createBlockStateDefinition", at = @At("HEAD"))
    private void loconautics$addRaised(StateDefinition.Builder<Block, BlockState> builder, CallbackInfo ci) {
        builder.add(BoilerAttach.RAISED);
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        VoxelShape shape = getShape(state, level, pos, CollisionContext.empty());
        if (!state.getValue(BoilerAttach.RAISED)) return shape;
        return shape.move(0.0, -BoilerAttach.BULGE, 0.0);
    }

    @Inject(method = "getShape", at = @At("RETURN"), cancellable = true)
    private void loconautics$raisedShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
                                         CallbackInfoReturnable<VoxelShape> cir) {
        if (!state.getValue(BoilerAttach.RAISED)) return;
        cir.setReturnValue(cir.getReturnValue().move(0.0, BoilerAttach.BULGE, 0.0));
    }
}
