package com.lycoris.loconautics.mixin.create.whistle;

import com.lycoris.loconautics.content.boiler.BoilerAttach;
import com.lycoris.loconautics.foundation.pick.OversizeShape;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.steamWhistle.WhistleBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
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

@Mixin(WhistleBlock.class)
public abstract class WhistleRaiseMixin extends Block implements OversizeShape {

    private static final int MAX_EXTENSIONS = 7;

    protected WhistleRaiseMixin(Properties properties) {
        super(properties);
    }

    @Inject(method = "createBlockStateDefinition", at = @At("HEAD"))
    private void loconautics$addRaised(StateDefinition.Builder<Block, BlockState> builder, CallbackInfo ci) {
        builder.add(BoilerAttach.RAISED);
    }

    // a boolean property defaults to true, so without this every old and unplaced whistle comes out raised
    @Inject(method = "<init>", at = @At("TAIL"))
    private void loconautics$defaultNotRaised(CallbackInfo ci) {
        registerDefaultState(defaultBlockState().setValue(BoilerAttach.RAISED, false));
    }

    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
    private void loconautics$raiseOnBoiler(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
        BlockState state = cir.getReturnValue();
        if (state == null) return;
        Direction attached = WhistleBlock.getAttachedDirection(state);
        cir.setReturnValue(state.setValue(BoilerAttach.RAISED,
                BoilerAttach.raisedOn(context.getLevel(), context.getClickedPos(), attached)));
    }

    @Inject(method = "getShape", at = @At("RETURN"), cancellable = true)
    private void loconautics$raisedShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
                                         CallbackInfoReturnable<VoxelShape> cir) {
        if (!state.getValue(BoilerAttach.RAISED)) return;
        Direction out = WhistleBlock.getAttachedDirection(state).getOpposite();
        cir.setReturnValue(cir.getReturnValue().move(out.getStepX() * BoilerAttach.BULGE,
                out.getStepY() * BoilerAttach.BULGE, out.getStepZ() * BoilerAttach.BULGE));
    }

    // culling reads this, keep it in the cell so the cap hides under a tube
    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        VoxelShape shape = getShape(state, level, pos, CollisionContext.empty());
        if (!state.getValue(BoilerAttach.RAISED)) return shape;
        Direction in = WhistleBlock.getAttachedDirection(state);
        return shape.move(in.getStepX() * BoilerAttach.BULGE, in.getStepY() * BoilerAttach.BULGE,
                in.getStepZ() * BoilerAttach.BULGE);
    }

    // new extensions are created from the default state, make them match the whistle below
    @Inject(method = "incrementSize", at = @At("RETURN"))
    private static void loconautics$matchExtensions(LevelAccessor level, BlockPos pos, CallbackInfo ci) {
        BlockState base = level.getBlockState(pos);
        if (!base.hasProperty(BoilerAttach.RAISED)) return;
        boolean raised = base.getValue(BoilerAttach.RAISED);
        for (int i = 1; i <= MAX_EXTENSIONS; i++) {
            BlockPos at = pos.above(i);
            BlockState state = level.getBlockState(at);
            if (!AllBlocks.STEAM_WHISTLE_EXTENSION.has(state)) return;
            if (state.getValue(BoilerAttach.RAISED) != raised) {
                level.setBlock(at, state.setValue(BoilerAttach.RAISED, raised), 3);
            }
        }
    }
}
