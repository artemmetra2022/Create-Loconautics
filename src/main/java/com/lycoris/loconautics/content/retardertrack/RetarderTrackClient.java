package com.lycoris.loconautics.content.retardertrack;

import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.AllBlocks;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The retarder rail looks like Create's own track, only tinted, so it needs no model files of its own: every state
 * borrows the baked model of the plain track in the same shape.
 */
@OnlyIn(Dist.CLIENT)
public final class RetarderTrackClient {

    /** the tint of the rail, a warm red that shows against the grey of the andesite track */
    public static final int TINT = 0xE8705A;

    private RetarderTrackClient() {
    }

    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        Block ours = LoconauticsRegistries.RETARDER_TRACK.get();
        BlockState reference = AllBlocks.TRACK.getDefaultState();
        for (BlockState state : ours.getStateDefinition().getPossibleStates()) {
            BlockState source = reference;
            for (Property<?> property : state.getProperties()) {
                source = copyValue(source, state, property);
            }
            BakedModel base = models.get(BlockModelShaper.stateToModelLocation(source));
            if (base != null) {
                models.put(BlockModelShaper.stateToModelLocation(state), new TintedModel(base));
            }
        }
    }

    private static <T extends Comparable<T>> BlockState copyValue(BlockState to, BlockState from,
                                                                  Property<T> property) {
        return to.hasProperty(property) ? to.setValue(property, from.getValue(property)) : to;
    }

    private static final class TintedModel extends BakedModelWrapper<BakedModel> {

        TintedModel(BakedModel original) {
            super(original);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                        ModelData data, @Nullable RenderType renderType) {
            List<BakedQuad> quads = originalModel.getQuads(state, side, rand, data, renderType);
            List<BakedQuad> tinted = new ArrayList<>(quads.size());
            for (BakedQuad quad : quads) {
                tinted.add(quad.getTintIndex() >= 0 ? quad
                        : new BakedQuad(quad.getVertices(), 0, quad.getDirection(), quad.getSprite(),
                        quad.isShade()));
            }
            return tinted;
        }
    }
}
