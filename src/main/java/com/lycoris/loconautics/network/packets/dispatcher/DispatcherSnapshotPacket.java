package com.lycoris.loconautics.network.packets.dispatcher;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.dispatcher.DispatcherEventEntry;
import com.lycoris.loconautics.content.dispatcher.DispatcherScreen;
import com.lycoris.loconautics.content.dispatcher.DispatcherSectionEntry;
import com.lycoris.loconautics.content.dispatcher.DispatcherService;
import com.lycoris.loconautics.content.dispatcher.DispatcherTrainEntry;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record DispatcherSnapshotPacket(BlockPos pos, List<DispatcherTrainEntry> trains,
                                       List<DispatcherSectionEntry> sections,
                                       List<DispatcherEventEntry> events,
                                       int occupiedSections, int reservedSections,
                                       int conflicts, int totalSections) implements CustomPacketPayload {

    public static final int MAX_TRAINS = 256;
    public static final int MAX_SECTIONS = 64;
    public static final int MAX_EVENTS = 48;
    public static final CustomPacketPayload.Type<DispatcherSnapshotPacket> TYPE =
            new CustomPacketPayload.Type<>(LoconauticsConstants.id("dispatcher_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DispatcherSnapshotPacket> STREAM_CODEC =
            StreamCodec.of(DispatcherSnapshotPacket::write, DispatcherSnapshotPacket::read);

    private static void write(RegistryFriendlyByteBuf buf, DispatcherSnapshotPacket packet) {
        BlockPos.STREAM_CODEC.encode(buf, packet.pos);
        buf.writeVarInt(packet.trains.size());
        for (DispatcherTrainEntry entry : packet.trains) {
            buf.writeUUID(entry.id());
            buf.writeUtf(entry.type(), 24);
            buf.writeUtf(entry.name(), 96);
            buf.writeUtf(entry.dimension(), 128);
            buf.writeInt(entry.x());
            buf.writeInt(entry.y());
            buf.writeInt(entry.z());
            buf.writeDouble(entry.speed());
            buf.writeVarInt(entry.state());
            buf.writeVarInt(entry.occupiedSections());
            buf.writeVarInt(entry.reservedSections());
            buf.writeBoolean(entry.dispatcherHeld());
        }
        buf.writeVarInt(packet.sections.size());
        for (DispatcherSectionEntry section : packet.sections) {
            buf.writeUUID(section.id());
            buf.writeVarInt(section.state());
            buf.writeUtf(section.trains(), 96);
            buf.writeUtf(section.dimension(), 128);
            buf.writeInt(section.x());
            buf.writeInt(section.y());
            buf.writeInt(section.z());
            buf.writeBoolean(section.held());
        }
        buf.writeVarInt(packet.events.size());
        for (DispatcherEventEntry event : packet.events) {
            buf.writeVarInt(event.kind());
            buf.writeUtf(event.subject(), 96);
            buf.writeVarInt(event.ageTicks());
        }
        buf.writeVarInt(packet.occupiedSections);
        buf.writeVarInt(packet.reservedSections);
        buf.writeVarInt(packet.conflicts);
        buf.writeVarInt(packet.totalSections);
    }

    private static DispatcherSnapshotPacket read(RegistryFriendlyByteBuf buf) {
        BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_TRAINS) throw new DecoderException("dispatcher train count " + size);
        List<DispatcherTrainEntry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            UUID id = buf.readUUID();
            String type = buf.readUtf(24);
            String name = buf.readUtf(96);
            String dimension = buf.readUtf(128);
            int x = buf.readInt(), y = buf.readInt(), z = buf.readInt();
            double speed = buf.readDouble();
            int state = buf.readVarInt();
            int occupied = buf.readVarInt();
            int reserved = buf.readVarInt();
            boolean held = buf.readBoolean();
            if (!Double.isFinite(speed)) speed = 0.0;
            entries.add(new DispatcherTrainEntry(id, type, name, dimension, x, y, z, speed,
                    state, occupied, reserved, held));
        }
        int sectionCount = buf.readVarInt();
        if (sectionCount < 0 || sectionCount > MAX_SECTIONS) {
            throw new DecoderException("dispatcher section count " + sectionCount);
        }
        List<DispatcherSectionEntry> sections = new ArrayList<>(sectionCount);
        for (int i = 0; i < sectionCount; i++) {
            sections.add(new DispatcherSectionEntry(buf.readUUID(), buf.readVarInt(), buf.readUtf(96),
                    buf.readUtf(128), buf.readInt(), buf.readInt(), buf.readInt(), buf.readBoolean()));
        }
        int eventCount = buf.readVarInt();
        if (eventCount < 0 || eventCount > MAX_EVENTS) {
            throw new DecoderException("dispatcher event count " + eventCount);
        }
        List<DispatcherEventEntry> events = new ArrayList<>(eventCount);
        for (int i = 0; i < eventCount; i++) {
            events.add(new DispatcherEventEntry(nonNegative(buf.readVarInt()), buf.readUtf(96),
                    nonNegative(buf.readVarInt())));
        }
        return new DispatcherSnapshotPacket(pos, List.copyOf(entries), List.copyOf(sections),
                List.copyOf(events),
                nonNegative(buf.readVarInt()), nonNegative(buf.readVarInt()),
                nonNegative(buf.readVarInt()), nonNegative(buf.readVarInt()));
    }

    private static int nonNegative(int value) {
        if (value < 0) throw new DecoderException("negative dispatcher statistic");
        return value;
    }

    public static DispatcherSnapshotPacket of(BlockPos pos, DispatcherService.Snapshot snapshot) {
        List<DispatcherTrainEntry> entries = snapshot.trains().size() > MAX_TRAINS
                ? snapshot.trains().subList(0, MAX_TRAINS) : snapshot.trains();
        List<DispatcherSectionEntry> sections = snapshot.sections().size() > MAX_SECTIONS
                ? snapshot.sections().subList(0, MAX_SECTIONS) : snapshot.sections();
        List<DispatcherEventEntry> events = snapshot.events().size() > MAX_EVENTS
                ? snapshot.events().subList(0, MAX_EVENTS) : snapshot.events();
        return new DispatcherSnapshotPacket(pos, entries, sections, events, snapshot.occupiedSections(),
                snapshot.reservedSections(), snapshot.conflicts(), snapshot.totalSections());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DispatcherSnapshotPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> DispatcherScreen.open(packet));
    }
}
