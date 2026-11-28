package com.lycoris.loconautics.content.boiler;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record FuelGradeEntry(FuelGrade grade) {

    public static final Codec<FuelGradeEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            FuelGrade.CODEC.fieldOf("grade").forGetter(FuelGradeEntry::grade)
    ).apply(instance, FuelGradeEntry::new));
}
