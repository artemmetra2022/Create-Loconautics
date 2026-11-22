package com.lycoris.loconautics.client;

import com.lycoris.loconautics.content.trackswitch.TrackSwitchBlock;
import com.lycoris.loconautics.content.trackswitch.TrackSwitchBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import java.util.ArrayList;

/** Read-only status: looking at a stand never changes a route. */
public final class TrackSwitchHUD {
    private TrackSwitchHUD() {}

    public static final LayeredDraw.Layer OVERLAY = (graphics, delta) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null || mc.options.hideGui
                || !(mc.hitResult instanceof BlockHitResult hit)
                || !(mc.level.getBlockEntity(hit.getBlockPos()) instanceof TrackSwitchBlockEntity stand)) return;
        var lines = new ArrayList<Component>();
        lines.add(Component.translatable("block.loconautics.track_switch"));
        String mode = !stand.isBound() ? "unbound" : stand.isForced() ? "forced" : "auto";
        lines.add(Component.translatable("loconautics.track_switch.status." + mode));
        if (stand.isForced()) {
            // Old saves have no cached branch count until the next interaction.
            if (stand.branchNumber() > 0 && stand.branchCount() > 0)
                lines.add(Component.translatable("loconautics.track_switch.branch", stand.branchNumber(), stand.branchCount()));
            lines.add(Component.translatable("loconautics.track_switch.direction",
                    Component.translatable("loconautics.track_switch.direction." + stand.getBlockState()
                            .getValue(TrackSwitchBlock.FACING).getSerializedName())));
        }
        lines.add(Component.translatable("loconautics.track_switch.controls"));
        lines.add(Component.translatable("loconautics.track_switch.rebind_hint"));
        int width = Math.min(260, graphics.guiWidth() - 24);
        var wrapped = new ArrayList<net.minecraft.util.FormattedCharSequence>();
        for (Component line : lines) wrapped.addAll(mc.font.split(line, width - 16));
        int height = wrapped.size() * 11 + 16;
        int x = Math.max(4, (graphics.guiWidth() - width) / 2);
        int y = Math.max(4, graphics.guiHeight() / 2 - height - 18);
        int accent = !stand.isBound() ? 0xFFFF7777 : stand.isForced() ? 0xFFFFC567 : 0xFF72D5AC;
        graphics.fill(x, y, x + width, y + height, 0xE0182028);
        graphics.fill(x, y, x + 3, y + height, accent);
        for (int i = 0; i < wrapped.size(); i++)
            graphics.drawString(mc.font, wrapped.get(i), x + 8, y + 8 + i * 11,
                    i == 1 ? accent : 0xFFE8EDF2, false);
    };
}
