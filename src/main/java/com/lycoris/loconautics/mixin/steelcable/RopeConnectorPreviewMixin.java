package com.lycoris.loconautics.mixin.steelcable;

import com.lycoris.loconautics.common.PreviewStrandHolder;
import dev.simulated_team.simulated.content.blocks.rope.rope_connector.RopeConnectorBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

@Mixin(value = RopeConnectorBlockEntity.class, remap = false)
public abstract class RopeConnectorPreviewMixin implements PreviewStrandHolder {

    @Unique
    @Nullable private List<Vec3> loconautics$previewStrand;

    @Override
    @Nullable
    public List<Vec3> previewStrand() {
        return loconautics$previewStrand;
    }

    @Override
    public void setPreviewStrand(@Nullable List<Vec3> points) {
        loconautics$previewStrand = points;
    }
}
