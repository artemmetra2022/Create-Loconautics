package com.lycoris.loconautics.content.boiler.safetyvalve;

import net.createmod.ponder.api.level.PonderLevel;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.WeakHashMap;

@OnlyIn(Dist.CLIENT)
final class SafetyValveHissClient {

    private static final Map<SafetyValveBlockEntity, SafetyValveHissInstance> ACTIVE = new WeakHashMap<>();

    private SafetyValveHissClient() {
    }

    static void tick(SafetyValveBlockEntity valve, boolean venting) {
        SafetyValveHissInstance hiss = ACTIVE.get(valve);
        if (venting && !(valve.getLevel() instanceof PonderLevel) && (hiss == null || hiss.isStopped())) {
            hiss = new SafetyValveHissInstance(valve);
            ACTIVE.put(valve, hiss);
            Minecraft.getInstance().getSoundManager().play(hiss);
        } else if (!venting && hiss != null && hiss.isStopped()) {
            ACTIVE.remove(valve);
        }
    }
}
