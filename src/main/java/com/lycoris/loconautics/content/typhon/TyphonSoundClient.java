package com.lycoris.loconautics.content.typhon;

import net.createmod.ponder.api.level.PonderLevel;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.WeakHashMap;

@OnlyIn(Dist.CLIENT)
final class TyphonSoundClient {

    private static final Map<TyphonBlockEntity, TyphonSoundInstance> ACTIVE = new WeakHashMap<>();

    private TyphonSoundClient() {
    }

    static void tick(TyphonBlockEntity typhon, boolean sounding) {
        TyphonSoundInstance instance = ACTIVE.get(typhon);
        if (sounding && !(typhon.getLevel() instanceof PonderLevel) && (instance == null || instance.isStopped())) {
            instance = new TyphonSoundInstance(typhon);
            ACTIVE.put(typhon, instance);
            Minecraft.getInstance().getSoundManager().play(instance);
        } else if (!sounding && instance != null && instance.isStopped()) {
            ACTIVE.remove(typhon);
        }
    }
}
