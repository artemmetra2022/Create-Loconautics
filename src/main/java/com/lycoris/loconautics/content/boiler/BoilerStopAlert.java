package com.lycoris.loconautics.content.boiler;

import com.lycoris.loconautics.common.train.TrainStatusChat;
import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// server only, the same rule for a loaded and an unloaded train
public final class BoilerStopAlert {

    private static final double STOPPED_SPEED = 1.0e-3;

    private BoilerStopAlert() {
    }

    public static List<SableTrain> rake(SableTrain train) {
        List<SableTrain> group = new ArrayList<>();
        group.add(train);
        for (UUID id : train.consistCache()) {
            SableTrain mate = SableTrainRegistry.bySubLevel(id);
            if (mate != null && !group.contains(mate)) group.add(mate);
        }
        return group;
    }

    public static boolean hasWorkingSchedule(List<SableTrain> group) {
        for (SableTrain car : group) {
            SableScheduleRuntime runtime = SableScheduleRuntime.get(car.id());
            if (runtime != null && !runtime.completed && !runtime.paused) return true;
        }
        return false;
    }

    // steamPressure is null when nothing reads it, an unloaded boiler has no pressure of its own
    public static void check(SableTrain train, List<SableTrain> group, BoilerSummary summary, @Nullable Float steamPressure) {
        String problem = summary.problem();
        if (problem == null) return;
        if (steamPressure != null && steamPressure > 0f) return;
        if (Math.abs(train.speed()) > STOPPED_SPEED) return;
        if (!hasWorkingSchedule(group)) return;
        TrainStatusChat.cannotContinueOnce(train, LoconauticsLang.translate("boiler.stopped." + problem).component());
    }
}
