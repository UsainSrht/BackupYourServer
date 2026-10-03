package me.usainsrht.backupyourserver.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.backupyourserver.BackupYourServerPlugin;
import me.usainsrht.backupyourserver.message.Message;
import me.usainsrht.backupyourserver.message.MessageSender;
import me.usainsrht.backupyourserver.storage.StorageRenderer;
import me.usainsrht.backupyourserver.util.SchedulerUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;

public final class StorageCommand {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final List<String> SUBCOMMAND_OPTIONS = List.of("all", "plugins", "worlds", "refresh");

    public static final SuggestionProvider<CommandSourceStack> STORAGE_SUGGESTIONS = (context, builder) -> {
        for (final String option : SUBCOMMAND_OPTIONS) {
            builder.suggest(option);
        }
        return builder.buildFuture();
    };

    private StorageCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build(final BackupYourServerPlugin plugin) {
        return Commands.literal("serverstorage")
                .requires(source -> hasPermission(source.getSender()))
                .executes(context -> executeStorage(plugin, context, null))
                .then(Commands.argument("view", StringArgumentType.word())
                        .suggests(STORAGE_SUGGESTIONS)
                        .executes(context -> executeStorage(
                                plugin,
                                context,
                                StringArgumentType.getString(context, "view")
                        )));
    }

    public static boolean hasPermission(final CommandSender sender) {
        return sender.hasPermission("backupyourserver.storage") || sender.hasPermission("backupyourserver.use");
    }

    public static int executeStorage(
            final BackupYourServerPlugin plugin,
            final CommandContext<CommandSourceStack> context,
            final String viewArg
    ) {
        final CommandSender sender = context.getSource().getSender();
        final String view = viewArg == null ? "" : viewArg.trim().toLowerCase(Locale.ROOT);
        final boolean forceRefresh = "refresh".equals(view) || "--refresh".equals(view) || "-r".equals(view);

        final Message calculatingMsg = plugin.config().message("storage-calculating");
        if (calculatingMsg != null && !calculatingMsg.isEmpty()) {
            MessageSender.send(sender, calculatingMsg);
        } else {
            sender.sendMessage(MINI_MESSAGE.deserialize("<gray>Analyzing server storage, please wait...</gray>"));
        }

        plugin.storageService().inspectStorage(forceRefresh).whenComplete((report, throwable) -> {
            SchedulerUtil.runGlobal(plugin, () -> {
                if (throwable != null) {
                    final Message failedMsg = plugin.config().message("storage-failed");
                    if (failedMsg != null && !failedMsg.isEmpty()) {
                        MessageSender.send(sender, failedMsg);
                    } else {
                        sender.sendMessage(MINI_MESSAGE.deserialize(
                                "<red>Failed to inspect server storage: <white>"
                                        + throwable.getMessage() + "</white></red>"
                        ));
                    }
                    plugin.getLogger().severe("Storage inspection failed: " + throwable.getMessage());
                    return;
                }

                switch (view) {
                    case "plugins" -> StorageRenderer.renderPluginsBreakdown(sender, report);
                    case "worlds" -> StorageRenderer.renderWorldsBreakdown(sender, report);
                    case "all" -> StorageRenderer.renderOverview(sender, report, true);
                    default -> StorageRenderer.renderOverview(sender, report, false);
                }
            });
        });

        return Command.SINGLE_SUCCESS;
    }
}
