package com.lycoris.loconautics.content.controls.panel.target;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;

public record RedstoneTarget(Kind kind, int threshold) {

    public static final int MAX_SIGNAL = 15;

    public static final Codec<RedstoneTarget> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Kind.CODEC.fieldOf("kind").forGetter(RedstoneTarget::kind),
            Codec.intRange(1, MAX_SIGNAL).optionalFieldOf("threshold", 1).forGetter(RedstoneTarget::threshold)
    ).apply(instance, RedstoneTarget::new));

    public enum Kind implements StringRepresentable {
        BINARY("binary"),
        ANALOG("analog");

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    public int level(int partSignal) {
        if (kind == Kind.ANALOG) return Math.max(0, Math.min(MAX_SIGNAL, partSignal));
        return partSignal >= threshold ? MAX_SIGNAL : 0;
    }
}
