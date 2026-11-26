package com.lycoris.loconautics.content.boiler.safetyvalve;

import com.lycoris.loconautics.registry.LoconauticsSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SafetyValveHissInstance extends AbstractTickableSoundInstance {

    private static final float HALF_OPEN = 0.5f;
    private static final float QUIET = 0.2f;
    private static final float LOUD = 1.0f;
    private static final float LOW_PITCH = 0.9f;
    private static final float HIGH_PITCH = 1.05f;

    private final SafetyValveBlockEntity valve;

    public SafetyValveHissInstance(SafetyValveBlockEntity valve) {
        super(LoconauticsSounds.SAFETY_VALVE_HISS.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.valve = valve;
        this.looping = true;
        this.delay = 0;
        this.volume = 1.0e-4f;
        place();
    }

    @Override
    public void tick() {
        if (valve.isRemoved() || !valve.isVenting() || valve.opening() <= 0f) {
            stop();
            return;
        }
        float opening = valve.opening();
        volume = opening < HALF_OPEN
                ? QUIET * opening / HALF_OPEN
                : QUIET + (LOUD - QUIET) * (opening - HALF_OPEN) / (1f - HALF_OPEN);
        pitch = LOW_PITCH + (HIGH_PITCH - LOW_PITCH) * opening;
        place();
    }

    private void place() {
        Vec3 at = valve.hissPosition();
        this.x = at.x;
        this.y = at.y;
        this.z = at.z;
    }
}
