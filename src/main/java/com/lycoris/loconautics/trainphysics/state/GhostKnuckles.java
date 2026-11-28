package com.lycoris.loconautics.trainphysics.state;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.content.knuckle.KnuckleBlockEntity;
import com.lycoris.loconautics.content.knuckle.KnuckleTuning;
import com.lycoris.loconautics.trainphysics.SableTrain;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import java.util.Locale;

import static com.lycoris.loconautics.foundation.LogFormat.shortId;

// server only
final class GhostKnuckles {

    private GhostKnuckles() {
    }

    // a coupler that engages from a bad pose pulls the cars hard, so it only restarts from a pose that is already coupled
    static void reengage(SableTrain train, ServerSubLevel sub) {
        for (KnuckleBlockEntity knuckle : SubLevelBlocks.findAll(sub, KnuckleBlockEntity.class)) {
            if (!knuckle.isConnected() || knuckle.getPartnerLocalPos() == null) {
                continue;
            }
            ServerSubLevel partner = SubLevelData.resolve(train.level(), knuckle.getPartnerSubLevelId());
            if (partner == null || !(partner.getLevel().getBlockEntity(knuckle.getPartnerLocalPos())
                    instanceof KnuckleBlockEntity mate)) {
                continue;
            }
            SableTrain partnerTrain = SableTrainRegistry.bySubLevel(partner.getUniqueId());
            // a partner still parked as a ghost sits at its old pose, its own turn reengages the pair
            if (partnerTrain == null || partnerTrain.isGhost()) {
                continue;
            }
            double gap = knuckle.signedHingeGap(sub, partner, mate);
            if (Math.abs(gap - KnuckleTuning.REST_LENGTH) > LoconauticsConstants.GHOSTCREATE_HINGE_TOLERANCE_BLOCKS) {
                LoconauticsConstants.TRACE.warn("{} reengage skipped car={} partner={} signedGap={} rest={}",
                        LoconauticsConstants.GHOSTCREATE_TAG, shortId(train.id()), shortId(partner.getUniqueId()),
                        String.format(Locale.ROOT, "%.3f", gap), KnuckleTuning.REST_LENGTH);
                continue;
            }
            knuckle.releaseCoupling();
            mate.releaseCoupling();
        }
    }
}
