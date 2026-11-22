package com.lycoris.loconautics.content.boiler;

public record BoilerPool(float max, float bogeyDemand, float outputUsed) {

    // bogeys that are stopped for overdraw draw nothing, so the outputs get the whole pool
    public float bogeyDraw() {
        return bogeysOverdrawn() ? 0f : bogeyDemand;
    }

    public float remainder() {
        return Math.max(0f, max - bogeyDraw());
    }

    public float used() {
        return bogeyDraw() + outputUsed;
    }

    public float usedFraction() {
        return max <= 0f ? 0f : Math.min(1f, used() / max);
    }

    public boolean bogeysOverdrawn() {
        return bogeyDemand > max;
    }
}
