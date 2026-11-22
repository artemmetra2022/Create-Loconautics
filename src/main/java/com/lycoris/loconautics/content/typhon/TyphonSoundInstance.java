package com.lycoris.loconautics.content.typhon;

import com.lycoris.loconautics.registry.LoconauticsSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TyphonSoundInstance extends AbstractTickableSoundInstance {

    private final TyphonBlockEntity typhon;

    public TyphonSoundInstance(TyphonBlockEntity typhon) {
        super(LoconauticsSounds.TYPHON.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.typhon = typhon;
        this.looping = true;
        this.delay = 0;
        this.volume = 1.0f;
        this.pitch = 1.0f;
        place();
    }

    @Override
    public void tick() {
        if (typhon.isRemoved() || !typhon.isSounding()) {
            stop();
            return;
        }
        place();
    }

    private void place() {
        Vec3 at = typhon.soundPosition();
        this.x = at.x;
        this.y = at.y;
        this.z = at.z;
    }
}
