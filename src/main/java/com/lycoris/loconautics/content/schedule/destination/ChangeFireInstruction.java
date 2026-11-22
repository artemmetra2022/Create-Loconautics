package com.lycoris.loconautics.content.schedule.destination;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.content.boiler.firebox.FireboxBlockEntity;
import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.content.schedule.SchedulePath;
import com.lycoris.loconautics.trainphysics.SableTrain;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class ChangeFireInstruction extends ScheduleInstruction {

	public ChangeFireInstruction() {
		super();
		data.putInt("Value", 0);
	}

	@Override
	public ResourceLocation getId() {
		return LoconauticsConstants.id("fire");
	}

	@Override
	public boolean supportsConditions() {
		return false;
	}

	public int signal() {
		return Math.max(0, Math.min(15, intData("Value")));
	}

	@Override
	@Nullable
	public SchedulePath start(SableScheduleRuntime runtime, Level level) {
		SableTrain train = runtime.train();
		if (level instanceof ServerLevel serverLevel && train != null
				&& SubLevelData.resolve(serverLevel, train.id()) instanceof ServerSubLevel sub) {
			for (FireboxBlockEntity firebox : SubLevelBlocks.findAll(sub, FireboxBlockEntity.class)) {
				firebox.setScheduleSignal(signal());
			}
		}
		return finishInstant(runtime);
	}

}
