package me.usainsrht.backupyourserver.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.backupyourserver.BackupYourServerPlugin;
import me.usainsrht.backupyourserver.backup.BackupInfo;
import me.usainsrht.backupyourserver.backup.BackupManager;
import me.usainsrht.backupyourserver.backup.BackupProgress;
import me.usainsrht.backupyourserver.backup.CompressionMethod;
import me.usainsrht.backupyourserver.backup.ExecutableInstallGuide;
import me.usainsrht.backupyourserver.backup.PlatformInfo;
import me.usainsrht.backupyourserver.config.PluginConfig;
import me.usainsrht.backupyourserver.message.Message;
import me.usainsrht.backupyourserver.message.MessageSender;
import me.usainsrht.backupyourserver.placeholder.BackupPlaceholders;
import me.usainsrht.backupyourserver.util.DurationFormatter;
import me.usainsrht.backupyourserver.util.SchedulerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class BackupCommand {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final SuggestionProvider<CommandSourceStack> METHOD_SUGGESTIONS = (context, builder) -> {
        final BackupYourServerPlugin plugin = BackupYourServerPlugin.getInstance();
        if (plugin == null) {
            return builder.buildFuture();
        }
        for (final CompressionMethod method : plugin.config().enabledMethods()) {
            builder.suggest(method.id());
        }
        return builder.buildFuture();
    };

    private BackupCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build(final BackupYourServerPlugin plugin) {
        return Commands.literal("backupyourserver")
                .requires(source -> source.getSender().hasPermission("backupyourserver.use"))
                .then(Commands.literal("start")
                        .executes(context -> startBackup(plugin, context, null))
                        .then(Commands.argument("backupmethod", StringArgumentType.word())
                                .suggests(METHOD_SUGGESTIONS)
                                .executes(context -> startBackup(
                                        plugin,
                                        context,
                                        StringArgumentType.getString(context, "backupmethod")
                                ))))
                .then(Commands.literal("stop")
                        .executes(context -> stopBackup(plugin, context)))
                .then(Commands.literal("list")
                        .executes(context -> listBackups(plugin, context)))
                .then(Commands.literal("time")
                        .executes(context -> showNextBackupTime(plugin, context)))
                .then(Commands.literal("status")
                        .executes(context -> showStatus(plugin, context)))
                .then(Commands.literal("storage")
                        .requires(source -> StorageCommand.hasPermission(source.getSender()))
                        .executes(context -> StorageCommand.executeStorage(plugin, context, null))
                        .then(Commands.argument("view", StringArgumentType.word())
                                .suggests(StorageCommand.STORAGE_SUGGESTIONS)
                                .executes(context -> StorageCommand.executeStorage(
                                        plugin,
                                        context,
                                        StringArgumentType.getString(context, "view")
                                ))))
                .then(Commands.literal("disk")
                        .requires(source -> StorageCommand.hasPermission(source.getSender()))
                        .executes(context -> StorageCommand.executeStorage(plugin, context, null))
                        .then(Commands.argument("view", StringArgumentType.word())
                                .suggests(StorageCommand.STORAGE_SUGGESTIONS)
                                .executes(context -> StorageCommand.executeStorage(
                                        plugin,
                                        context,
                                        StringArgumentType.getString(context, "view")
                                ))))
                .then(Commands.literal("space")
                        .requires(source -> StorageCommand.hasPermission(source.getSender()))
                        .executes(context -> StorageCommand.executeStorage(plugin, context, null))
                        .then(Commands.argument("view", StringArgumentType.word())
                                .suggests(StorageCommand.STORAGE_SUGGESTIONS)
                                .executes(context -> StorageCommand.executeStorage(
                                        plugin,
                                        context,
                                        StringArgumentType.getString(context, "view")
                                ))))
                .then(Commands.literal("reload")
                        .requires(source -> source.getSender().hasPermission("backupyourserver.reload"))
                        .executes(context -> reloadConfig(plugin, context)))
                .executes(context -> {
                    send(plugin.config().message("command-usage"), context.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                });
    }

    private static int startBackup(
            final BackupYourServerPlugin plugin,
            final CommandContext<CommandSourceStack> context,
            final String methodName
    ) {
        final CommandSender sender = context.getSource().getSender();
        final PluginConfig config = plugin.config();
        final BackupManager manager = plugin.backupManager();

        if (manager.isRunning()) {
            send(config.message("backup-already-running"), sender);
            return Command.SINGLE_SUCCESS;
        }

        final CompressionMethod method = config.resolveMethod(methodName);
        final PluginConfig.CompressionMethodSettings settings = config.compressionSettings(method);
        if (settings == null || !settings.enabled()) {
            send(config.message("backup-method-disabled"), sender);
            return Command.SINGLE_SUCCESS;
        }

        BackupPlaceholders.updateStatus("running");
        BackupPlaceholders.updateMethod(method);
        BackupPlaceholders.updateProgress(BackupProgress.EMPTY);
        send(config.message("backup-started"), sender);
        plugin.bossBarService().setActive(true);

        manager.start(config, method, null).whenComplete((session, throwable) -> SchedulerUtil.runGlobal(plugin, () -> {
            plugin.bossBarService().hideAll();
            if (throwable != null) {
                BackupPlaceholders.updateStatus("failed");
                send(config.message("backup-failed"), sender);
                plugin.getLogger().severe("Backup failed: " + throwable.getMessage());
                return;
            }

            if (session.successful()) {
                BackupPlaceholders.updateStatus("completed");
                BackupPlaceholders.updateProgress(new BackupProgress(
                        1.0D,
                        session.detailedProgress().totalFiles(),
                        session.detailedProgress().totalFiles(),
                        session.detailedProgress().totalBytes(),
                        session.detailedProgress().totalBytes(),
                        session.detailedProgress().currentFile(),
                        0L
                ));
                BackupPlaceholders.updateLastArchive(session.outputArchive().getFileName().toString());
                send(config.message("backup-completed"), sender);
                plugin.retentionService().cleanup(config);
            } else if (session.cancelled().get()) {
                BackupPlaceholders.updateStatus("cancelled");
                send(config.message("backup-cancelled"), sender);
            } else {
                BackupPlaceholders.updateStatus("failed");
                final String failureReason = session.failureReason();
                if (failureReason != null && failureReason.startsWith("Backup method ")) {
                    sendDetailedFailure(config, sender, failureReason);
                } else {
                    send(config.message("backup-failed"), sender);
                }
                if (failureReason != null) {
                    plugin.getLogger().severe(failureReason);
                }
            }
        }));

        return Command.SINGLE_SUCCESS;
    }

    private static int stopBackup(
            final BackupYourServerPlugin plugin,
            final CommandContext<CommandSourceStack> context
    ) {
        final CommandSender sender = context.getSource().getSender();
        final PluginConfig config = plugin.config();

        if (!plugin.backupManager().stop()) {
            send(config.message("backup-not-running"), sender);
            return Command.SINGLE_SUCCESS;
        }

        BackupPlaceholders.updateStatus("cancelling");
        send(config.message("backup-stop-requested"), sender);
        return Command.SINGLE_SUCCESS;
    }

    private static int listBackups(
            final BackupYourServerPlugin plugin,
            final CommandContext<CommandSourceStack> context
    ) {
        final CommandSender sender = context.getSource().getSender();
        final PluginConfig config = plugin.config();

        SchedulerUtil.runAsync(plugin, () -> {
            try {
                final List<BackupInfo> backups = plugin.backupManager().listBackups(config);
                SchedulerUtil.runGlobal(plugin, () -> renderBackupList(plugin, sender, config, backups));
            } catch (final IOException exception) {
                SchedulerUtil.runGlobal(plugin, () -> {
                    send(config.message("backup-list-failed"), sender);
                    plugin.getLogger().severe("Unable to list backups: " + exception.getMessage());
                });
            }
        });

        return Command.SINGLE_SUCCESS;
    }

    private static int showNextBackupTime(
            final BackupYourServerPlugin plugin,
            final CommandContext<CommandSourceStack> context
    ) {
        final CommandSender sender = context.getSource().getSender();
        final PluginConfig config = plugin.config();

        if (!config.scheduledBackupSettings().enabled()) {
            send(config.message("backup-time-disabled"), sender);
            return Command.SINGLE_SUCCESS;
        }

        final Instant nextRun = plugin.schedulerService().nextRunAt();
        if (nextRun == null) {
            send(config.message("backup-time-unavailable"), sender);
            return Command.SINGLE_SUCCESS;
        }

        final Duration remaining = Duration.between(Instant.now(), nextRun);
        sender.sendMessage(MINI_MESSAGE.deserialize(
                "<gray>Next automatic backup in <white>" + escapeMiniMessage(DurationFormatter.format(remaining))
                        + " <dark_gray>(" + escapeMiniMessage(BackupManager.formatTimestamp(nextRun)) + ")"
        ));
        return Command.SINGLE_SUCCESS;
    }

    private static int showStatus(
            final BackupYourServerPlugin plugin,
            final CommandContext<CommandSourceStack> context
    ) {
        final CommandSender sender = context.getSource().getSender();
        final PluginConfig config = plugin.config();
        final BackupManager manager = plugin.backupManager();

        send(config.message("backup-status-header"), sender);
        sender.sendMessage(MINI_MESSAGE.deserialize(
                "<gray>Platform: <white>" + escapeMiniMessage(PlatformInfo.description())
        ));
        sender.sendMessage(MINI_MESSAGE.deserialize(
                "<gray>Default method: <white>" + escapeMiniMessage(config.defaultMethod())
        ));

        final BackupManager.BackupSession session = manager.activeSession();
        if (session == null) {
            send(config.message("backup-status-idle"), sender);
        } else {
            final int progress = (int) Math.round(session.progress() * 100.0D);
            sender.sendMessage(MINI_MESSAGE.deserialize(
                    "<gray>Running: <green>Yes"
                            + " <dark_gray>(<white>" + escapeMiniMessage(session.method().id())
                            + "<dark_gray>, <white>" + progress + "%<dark_gray>)"
            ));
        }

        send(config.message("backup-status-methods-header"), sender);
        for (final CompressionMethod method : config.enabledMethods()) {
            renderMethodStatus(sender, config, method);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int reloadConfig(
            final BackupYourServerPlugin plugin,
            final CommandContext<CommandSourceStack> context
    ) {
        final CommandSender sender = context.getSource().getSender();
        plugin.reloadLocalConfig();
        send(plugin.config().message("reload-success"), sender);
        return Command.SINGLE_SUCCESS;
    }

    private static void renderMethodStatus(
            final CommandSender sender,
            final PluginConfig config,
            final CompressionMethod method
    ) {
        final PluginConfig.CompressionMethodSettings settings = config.compressionSettings(method);
        if (settings == null) {
            return;
        }

        final boolean ready = ExecutableInstallGuide.isReady(method, settings);

        final Component status = ready
                ? MINI_MESSAGE.deserialize("<green>ready")
                : Component.text("missing tools", NamedTextColor.RED)
                        .hoverEvent(HoverEvent.showText(buildMissingToolsHover(method, settings)));

        sender.sendMessage(MINI_MESSAGE.deserialize(
                "<gray>- <white>" + escapeMiniMessage(method.id())
                        + " <dark_gray>(" + escapeMiniMessage(settings.executable())
                        + (method != CompressionMethod.SEVEN_Z && method != CompressionMethod.SEVEN_ZA ? ", tar" : "")
                        + ") <dark_gray>- "
        ).append(status));
    }

    private static Component buildMissingToolsHover(
            final CompressionMethod method,
            final PluginConfig.CompressionMethodSettings settings
    ) {
        Component hover = Component.empty();
        boolean first = true;
        for (final String missing : ExecutableInstallGuide.missingRequirements(method, settings)) {
            if (!first) {
                hover = hover.append(Component.newline()).append(Component.newline());
            }
            first = false;
            hover = hover
                    .append(Component.text("Missing ", NamedTextColor.RED))
                    .append(Component.text(missing, NamedTextColor.WHITE))
                    .append(Component.text(":", NamedTextColor.GRAY))
                    .append(Component.newline())
                    .append(Component.text(
                            ExecutableInstallGuide.installInstructions(missing),
                            NamedTextColor.WHITE
                    ));
        }
        return hover;
    }

    private static void sendDetailedFailure(
            final PluginConfig config,
            final CommandSender sender,
            final String details
    ) {
        send(config.message("backup-failed"), sender);
        for (final String line : details.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            final String color = line.startsWith("How to install:") || line.startsWith("Install ")
                    || line.startsWith("Download ") || line.startsWith("sudo ")
                    ? "<yellow>"
                    : line.startsWith("Missing executable:") || line.startsWith("Backup method ")
                    ? "<red>"
                    : line.startsWith("Platform:") ? "<gray>"
                    : "<white>";
            sender.sendMessage(MINI_MESSAGE.deserialize(color + escapeMiniMessage(line)));
        }
    }

    private static String escapeMiniMessage(final String input) {
        return input.replace("\\", "\\\\").replace("<", "\\<");
    }

    private static void renderBackupList(
            final BackupYourServerPlugin plugin,
            final CommandSender sender,
            final PluginConfig config,
            final List<BackupInfo> backups
    ) {
        if (backups.isEmpty()) {
            send(config.message("backup-list-empty"), sender);
            return;
        }

        send(config.message("backup-list-header"), sender);
        final Instant now = Instant.now();
        for (int index = 0; index < backups.size(); index++) {
            final BackupInfo backup = backups.get(index);
            final Instant deletionAt = plugin.retentionService().computeDeletionAt(backup, index, config, now);
            final String deletionText = formatDeletionText(deletionAt, now);
            sender.sendMessage(MINI_MESSAGE.deserialize(
                    "<gray>- <white>" + backup.fileName()
                            + " <dark_gray>(" + backup.method().id()
                            + ", " + BackupManager.formatSize(backup.sizeBytes())
                            + ", " + BackupManager.formatTimestamp(backup.createdAt())
                            + ", delete in <yellow>" + escapeMiniMessage(deletionText) + "<dark_gray>)"
            ));
        }
    }

    private static String formatDeletionText(final Instant deletionAt, final Instant now) {
        if (deletionAt == null) {
            return "never";
        }
        return DurationFormatter.format(Duration.between(now, deletionAt));
    }

    private static void send(final Message message, final CommandSender sender) {
        MessageSender.send(sender, message);
    }
}
