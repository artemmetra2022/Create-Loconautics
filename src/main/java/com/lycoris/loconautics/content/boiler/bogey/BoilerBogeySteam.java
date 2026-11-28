package com.lycoris.loconautics.content.boiler.bogey;

import com.lycoris.loconautics.content.smokestack.SmokestackExhaust;
import com.simibubi.create.AllSoundEvents;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class BoilerBogeySteam {

    private static final float RELEASE_VOLUME = 0.9f;
    private static final float MAX_STEAM_SPENT = 10.0f;

    private float steamSpent;

    public void tick(Level level, BlockPos pos) {
        double wheelSpeed = SmokestackExhaust.wheelSpeed(pos);
        if (SmokestackExhaust.isMoving(wheelSpeed)) {
            steamSpent = Math.min(steamSpent + (float) Math.min(0.5, wheelSpeed / 10.0), MAX_STEAM_SPENT);
            return;
        }
        if (steamSpent > 1.0f) {
            release(level, Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(pos)));
        }
        steamSpent = 0.0f;
    }

    private void release(Level level, Vec3 at) {
        float volume = steamSpent / MAX_STEAM_SPENT * RELEASE_VOLUME;
        level.playLocalSound(at.x, at.y, at.z, SoundEvents.LAVA_EXTINGUISH, SoundSource.NEUTRAL,
                0.25f * volume, 0.78f, false);
        AllSoundEvents.STEAM.playAt(level, at, 0.75f * volume, 0.5f, false);
    }
}
