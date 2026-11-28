package com.lycoris.loconautics.content.dispatcher;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** In-memory dispatcher event log for the current server session. */
public final class DispatcherJournal {

    public static final int CONFLICT = 1;
    public static final int DERAIL = 2;
    public static final int WAITING = 3;
    public static final int HELD = 4;
    public static final int RELEASED = 5;
    public static final int SIGNAL_CLOSED = 6;
    public static final int SIGNAL_OPENED = 7;
    public static final int SPAD = 8;

    public record Entry(int kind, String subject, long tick) {
    }

    private static final int MAX = 40;
    private static final ArrayDeque<Entry> LOG = new ArrayDeque<>();
    private static boolean seeded;
    private static final Set<UUID> derailed = new HashSet<>();
    private static final Set<UUID> waiting = new HashSet<>();
    private static final Set<UUID> conflicts = new HashSet<>();

    private DispatcherJournal() {
    }

    public static void record(int kind, String subject) {
        record(kind, subject, 0L);
    }

    public static void record(int kind, String subject, long gameTime) {
        LOG.addFirst(new Entry(kind, subject == null ? "" : subject, gameTime));
        while (LOG.size() > MAX) {
            LOG.removeLast();
        }
    }

    public static void observe(MinecraftServer server, List<DispatcherTrainEntry> trains,
                               List<DispatcherSectionEntry> sections) {
        long now = server.overworld() == null ? 0L : server.overworld().getGameTime();
        Set<UUID> nowDerailed = new HashSet<>();
        Set<UUID> nowWaiting = new HashSet<>();
        for (DispatcherTrainEntry train : trains) {
            if (train.state() == DispatcherTrainEntry.DERAILED) {
                nowDerailed.add(train.id());
            } else if (train.state() == DispatcherTrainEntry.WAITING) {
                nowWaiting.add(train.id());
            }
        }
        Set<UUID> nowConflict = new HashSet<>();
        for (DispatcherSectionEntry section : sections) {
            if (section.state() == DispatcherSectionEntry.CONFLICT) {
                nowConflict.add(section.id());
            }
        }
        if (seeded) {
            for (DispatcherTrainEntry train : trains) {
                if (nowDerailed.contains(train.id()) && !derailed.contains(train.id())) {
                    record(DERAIL, train.name(), now);
                }
                if (nowWaiting.contains(train.id()) && !waiting.contains(train.id())) {
                    record(WAITING, train.name(), now);
                }
            }
            for (UUID id : nowConflict) {
                if (!conflicts.contains(id)) {
                    record(CONFLICT, shortId(id), now);
                }
            }
        }
        seeded = true;
        derailed.clear();
        derailed.addAll(nowDerailed);
        waiting.clear();
        waiting.addAll(nowWaiting);
        conflicts.clear();
        conflicts.addAll(nowConflict);
    }

    public static List<DispatcherEventEntry> recent(MinecraftServer server) {
        long now = server.overworld() == null ? 0L : server.overworld().getGameTime();
        List<DispatcherEventEntry> out = new ArrayList<>(LOG.size());
        for (Entry entry : LOG) {
            int age = (int) Math.max(0L, now - entry.tick());
            out.add(new DispatcherEventEntry(entry.kind(), entry.subject(), age));
        }
        return List.copyOf(out);
    }

    public static void clear() {
        LOG.clear();
        seeded = false;
        derailed.clear();
        waiting.clear();
        conflicts.clear();
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }
}
