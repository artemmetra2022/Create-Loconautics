package com.lycoris.loconautics.trainphysics.debug;

import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.lycoris.loconautics.trainphysics.state.CartLifecycle;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class CartStateCommand {

    private CartStateCommand() {
    }

    public static void addBranch(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("cartstate")
                .executes(CartStateCommand::describe)
                .then(Commands.literal("watch")
                        .then(Commands.literal("on").executes(ctx -> watch(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> watch(ctx, false)))));
    }

    private static int describe(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        SableTrain train = SableTrainSpawner.pickLookedAtTrain(ctx.getSource().getPlayerOrException());
        if (train == null) {
            say(ctx.getSource(), "no cart in view");
            return 0;
        }
        List<String> lines = CartLifecycle.describe(train);
        say(ctx.getSource(), "cart " + train.id().toString().substring(0, 8) + " holds " + lines.size() + " store(s)");
        for (String line : lines) {
            say(ctx.getSource(), line);
        }
        return lines.size();
    }

    private static int watch(CommandContext<CommandSourceStack> ctx, boolean on) {
        CartLifecycle.setWatching(on);
        say(ctx.getSource(), "stale check " + (on ? "on" : "off"));
        return 1;
    }

    private static void say(CommandSourceStack source, String line) {
        source.sendSuccess(() -> Component.literal("[cartstate] " + line), false);
    }
}
