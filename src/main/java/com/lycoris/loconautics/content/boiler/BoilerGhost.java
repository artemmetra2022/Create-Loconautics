package com.lycoris.loconautics.content.boiler;

import com.lycoris.loconautics.trainphysics.SableTrain;

import java.util.List;

// server only, an unloaded boiler burns its fuel and water
// server only, the unloaded step of a boiler, called from the ghost consist step with the leading car
public final class BoilerGhost {

    private BoilerGhost() {
    }

    // the boiler can be on any car of the rake, the leader is the one that gets stopped
    public static void step(SableTrain leader, List<SableTrain> group) {
        BoilerSummary summary = null;
        for (SableTrain car : group) {
            if (car.boilerSummary() != null) {
                summary = car.boilerSummary();
                break;
            }
        }
        boolean scheduled = BoilerStopAlert.hasWorkingSchedule(group);
        if (summary == null || !scheduled) {
            return;
        }
        summary.spendTick();
        String problem = summary.problem();
        if (problem == null) return;
        leader.setTargetSpeed(0.0);
        BoilerStopAlert.check(leader, group, summary, null);
    }
}
