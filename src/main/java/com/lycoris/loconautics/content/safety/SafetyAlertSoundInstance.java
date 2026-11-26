package com.lycoris.loconautics.content.safety;

import com.lycoris.loconautics.registry.LoconauticsSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SafetyAlertSoundInstance extends AbstractTickableSoundInstance {

    private final SafetyBlockEntity unit;

    public SafetyAlertSoundInstance(SafetyBlockEntity unit) {
        super(LoconauticsSounds.SAFETY_ALERT.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.unit = unit;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.7f;
        this.pitch = 1.0f;
        place();
    }

    @Override
    public void tick() {
        int alarm = unit.alarmState();
        if (unit.isRemoved() || alarm == SafetyBlockEntity.ALARM_NONE) {
            stop();
            return;
        }
        boolean emergency = SafetyBlockEntity.isEmergencyAlarm(alarm);
        this.volume = emergency ? 0.85f : 0.45f;
        this.pitch = emergency ? 1.1f : 0.9f;
        place();
    }

    private void place() {
        Vec3 at = unit.worldPositionProjected();
        this.x = at.x;
        this.y = at.y;
        this.z = at.z;
    }
}
