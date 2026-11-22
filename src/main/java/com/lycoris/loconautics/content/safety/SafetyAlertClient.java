package com.lycoris.loconautics.content.safety;

import net.createmod.ponder.api.level.PonderLevel;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.WeakHashMap;

@OnlyIn(Dist.CLIENT)
final class SafetyAlertClient {

    private static final Map<SafetyBlockEntity, SafetyAlertSoundInstance> ACTIVE = new WeakHashMap<>();

    private SafetyAlertClient() {
    }

    static void tick(SafetyBlockEntity unit) {
        int alarm = unit.alarmState();
        boolean active = alarm != SafetyBlockEntity.ALARM_NONE;
        SafetyAlertSoundInstance instance = ACTIVE.get(unit);
        if (active && !(unit.getLevel() instanceof PonderLevel) && (instance == null || instance.isStopped())) {
            instance = new SafetyAlertSoundInstance(unit);
            ACTIVE.put(unit, instance);
            Minecraft.getInstance().getSoundManager().play(instance);
        } else if (!active && instance != null && instance.isStopped()) {
            ACTIVE.remove(unit);
        }
    }
}
