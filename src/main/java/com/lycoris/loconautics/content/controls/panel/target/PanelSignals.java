package com.lycoris.loconautics.content.controls.panel.target;

import com.lycoris.loconautics.LoconauticsConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
public final class PanelSignals {

    private static final int LINGER_TICKS = 3;

    private record Key(ResourceKey<Level> dimension, long pos) {
    }

    private static final class Entry {
        int value;
        long tick;
    }

    private static final Map<Key, Entry> ENTRIES = new HashMap<>();

    private PanelSignals() {
    }

    public static boolean any() {
        return !ENTRIES.isEmpty();
    }

    public static void set(ServerLevel level, BlockPos target, int value) {
        Key key = new Key(level.dimension(), target.asLong());
        long now = level.getGameTime();
        Entry entry = ENTRIES.get(key);
        if (entry == null) {
            if (value == 0) return;
            entry = new Entry();
            ENTRIES.put(key, entry);
        }
        int before = live(entry, now);
        entry.value = value;
        entry.tick = now;
        if (before != value) notifyTarget(level, target);
    }

    public static void release(Level level, BlockPos target) {
        if (!(level instanceof ServerLevel server)) return;
        Entry entry = ENTRIES.remove(new Key(server.dimension(), target.asLong()));
        if (entry != null && entry.value > 0) notifyTarget(server, target);
    }

    public static int at(ServerLevel level, BlockPos target) {
        Entry entry = ENTRIES.get(new Key(level.dimension(), target.asLong()));
        return entry == null ? 0 : live(entry, level.getGameTime());
    }

    // the neighbor at pos is asked in direction, the reader is the block on the other side
    public static int signalFrom(ServerLevel level, BlockPos neighbor, Direction direction) {
        Entry entry = ENTRIES.get(new Key(level.dimension(), neighbor.relative(direction.getOpposite()).asLong()));
        return entry == null ? 0 : live(entry, level.getGameTime());
    }

    private static int live(Entry entry, long now) {
        return now - entry.tick > LINGER_TICKS ? 0 : entry.value;
    }

    private static void notifyTarget(ServerLevel level, BlockPos target) {
        if (!level.isLoaded(target)) return;
        BlockState state = level.getBlockState(target);
        // blocks that read one side only transmission, bearing axle check where the update came from
        for (Direction direction : Direction.values()) {
            BlockPos from = target.relative(direction);
            state.handleNeighborChanged(level, target, level.getBlockState(from).getBlock(), from, false);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ENTRIES.isEmpty()) return;
        Iterator<Map.Entry<Key, Entry>> it = ENTRIES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Key, Entry> mapped = it.next();
            ServerLevel level = event.getServer().getLevel(mapped.getKey().dimension());
            if (level == null) {
                it.remove();
                continue;
            }
            Entry entry = mapped.getValue();
            if (level.getGameTime() - entry.tick <= LINGER_TICKS) continue;
            it.remove();
            if (entry.value > 0) notifyTarget(level, BlockPos.of(mapped.getKey().pos()));
        }
    }

    public static void clear() {
        ENTRIES.clear();
    }
}
