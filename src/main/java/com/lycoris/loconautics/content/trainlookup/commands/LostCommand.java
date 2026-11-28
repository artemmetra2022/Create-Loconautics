package com.lycoris.loconautics.content.trainlookup.commands;

import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.state.TrainResidency;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.joml.Vector3d;

import java.util.List;
import java.util.Locale;

import static com.lycoris.loconautics.foundation.LogFormat.shortId;

public final class LostCommand {

    private LostCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("lost")
                .executes(LostCommand::list)
                .then(Commands.literal("find")
                        .then(Commands.argument("car", StringArgumentType.word())
                                .executes(LostCommand::find)));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<SableTrain> lost = SableTrainRegistry.all().stream().filter(TrainResidency::bodyLost).toList();
        if (lost.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No lost trains"), false);
            return 0;
        }
        for (SableTrain train : lost) {
            Vector3d at = train.car() == null ? null : TrainResidency.railCursor(train.car());
            String where = at == null ? "no rail point" : String.format(Locale.ROOT, "%.0f %.0f %.0f", at.x, at.y, at.z);
            String line = shortId(train.id()) + " " + train.name() + " in " + train.level().dimension().location() + " at " + where;
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return lost.size();
    }

    private static int find(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String raw = StringArgumentType.getString(context, "car");
        for (SableTrain train : SableTrainRegistry.all()) {
            if (TrainResidency.bodyLost(train) && train.id().toString().startsWith(raw)) {
                String result = TrainResidency.findLost(train);
                source.sendSuccess(() -> Component.literal(result), false);
                return 1;
            }
        }
        source.sendFailure(Component.literal("no lost train id starts with " + raw));
        return 0;
    }
}
