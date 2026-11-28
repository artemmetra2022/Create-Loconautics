package com.lycoris.loconautics.common;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface PreviewStrandHolder {

    @Nullable
    List<Vec3> previewStrand();

    void setPreviewStrand(@Nullable List<Vec3> points);
}
