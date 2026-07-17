package me.usainsrht.backupyourserver.config;

import me.usainsrht.backupyourserver.backup.CompressionMethod;
import me.usainsrht.backupyourserver.message.Message;
import me.usainsrht.backupyourserver.message.MessageParser;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

public final class PluginConfig {

    private final Path backupDirectory;
    private final Path sourceDirectory;
    private final String defaultMethod;
    private final List<String> blacklistedExtensions;
    private final List<String> blacklistedPaths;
    private final List<String> blacklistedRegexes;
    private final Map<String, Message> messages;
    private final BossBarSettings bossBarSettings;
    private final Map<CompressionMethod, CompressionMethodSettings> compressionMethods;

    private PluginConfig(
            final Path backupDirectory,
            final Path sourceDirectory,
            final String defaultMethod,
            final List<String> blacklistedExtensions,
            final List<String> blacklistedPaths,
            final List<String> blacklistedRegexes,
            final Map<String, Message> messages,
            final BossBarSettings bossBarSettings,
            final Map<CompressionMethod, CompressionMethodSettings> compressionMethods
    ) {
        this.backupDirectory = backupDirectory;
        this.sourceDirectory = sourceDirectory;
        this.defaultMethod = defaultMethod;
        this.blacklistedExtensions = List.copyOf(blacklistedExtensions);
        this.blacklistedPaths = List.copyOf(blacklistedPaths);
        this.blacklistedRegexes = List.copyOf(blacklistedRegexes);
        this.messages = Map.copyOf(messages);
        this.bossBarSettings = bossBarSettings;
        this.compressionMethods = Map.copyOf(compressionMethods);
    }

    public static PluginConfig load(final FileConfiguration config, final Path serverRoot, final Logger logger) {
        final Path backupDirectory = serverRoot.resolve(config.getString("backup-directory", "backups"));
        final Path sourceDirectory = serverRoot.resolve(config.getString("source-directory", "."));

        final Map<String, Message> messages = new HashMap<>();
        final ConfigurationSection messagesSection = config.getConfigurationSection("messages");
        if (messagesSection != null) {
            for (final String key : messagesSection.getKeys(false)) {
                final Message message = MessageParser.parse(messagesSection.getConfigurationSection(key));
                if (message != null) {
                    messages.put(key, message);
                }
            }
        }

        final BossBarSettings bossBarSettings = BossBarSettings.from(config.getConfigurationSection("bossbar"));
        final Map<CompressionMethod, CompressionMethodSettings> compressionMethods = loadCompressionMethods(
                config.getConfigurationSection("backup-methods"),
                logger
        );

        return new PluginConfig(
                backupDirectory,
                sourceDirectory,
                config.getString("default-backup-method", "tar+zstd"),
                config.getStringList("blacklisted-extensions"),
                config.getStringList("blacklisted-paths"),
                config.getStringList("blacklisted-regexes"),
                messages,
                bossBarSettings,
                compressionMethods
        );
    }

    private static Map<CompressionMethod, CompressionMethodSettings> loadCompressionMethods(
            final ConfigurationSection section,
            final Logger logger
    ) {
        final Map<CompressionMethod, CompressionMethodSettings> methods = new EnumMap<>(CompressionMethod.class);
        if (section == null) {
            return methods;
        }

        for (final CompressionMethod method : CompressionMethod.values()) {
            final ConfigurationSection methodSection = section.getConfigurationSection(method.configKey());
            if (methodSection == null) {
                continue;
            }
            methods.put(method, CompressionMethodSettings.from(method, methodSection, logger));
        }

        return methods;
    }

    public Path backupDirectory() {
        return backupDirectory;
    }

    public Path sourceDirectory() {
        return sourceDirectory;
    }

    public String defaultMethod() {
        return defaultMethod;
    }

    public List<String> blacklistedExtensions() {
        return blacklistedExtensions;
    }

    public List<String> blacklistedPaths() {
        return blacklistedPaths;
    }

    public List<String> blacklistedRegexes() {
        return blacklistedRegexes;
    }

    public Message message(final String key) {
        return messages.get(key);
    }

    public BossBarSettings bossBarSettings() {
        return bossBarSettings;
    }

    public CompressionMethodSettings compressionSettings(final CompressionMethod method) {
        return compressionMethods.get(method);
    }

    public List<CompressionMethod> enabledMethods() {
        final List<CompressionMethod> enabled = new ArrayList<>();
        for (final CompressionMethod method : CompressionMethod.values()) {
            final CompressionMethodSettings settings = compressionMethods.get(method);
            if (settings != null && settings.enabled()) {
                enabled.add(method);
            }
        }
        return enabled;
    }

    public CompressionMethod resolveMethod(final String input) {
        if (input == null || input.isBlank()) {
            return CompressionMethod.byId(defaultMethod);
        }
        return CompressionMethod.byId(input);
    }

    public record BossBarSettings(
            boolean enabled,
            String permission,
            BossBar.Color color,
            BossBar.Overlay style,
            String text
    ) {
        static BossBarSettings from(final ConfigurationSection section) {
            if (section == null) {
                return new BossBarSettings(false, "backupyourserver.bossbar", BossBar.Color.BLUE, BossBar.Overlay.PROGRESS, "<aqua>Backup <backupyourserver_progress>% <dark_gray>| <yellow><backupyourserver_eta> <backupyourserver_eta_long> <dark_gray>| <white><backupyourserver_files> <dark_gray>| <gray><backupyourserver_current_file>");
            }

            final BossBar.Color color;
            try {
                color = BossBar.Color.valueOf(section.getString("color", "BLUE").toUpperCase(Locale.ROOT));
            } catch (final IllegalArgumentException ignored) {
                return new BossBarSettings(false, "backupyourserver.bossbar", BossBar.Color.BLUE, BossBar.Overlay.PROGRESS, "<aqua>Backup <backupyourserver_progress>% <dark_gray>| <yellow><backupyourserver_eta> <backupyourserver_eta_long> <dark_gray>| <white><backupyourserver_files> <dark_gray>| <gray><backupyourserver_current_file>");
            }

            final BossBar.Overlay style;
            try {
                style = BossBar.Overlay.valueOf(section.getString("style", "PROGRESS").toUpperCase(Locale.ROOT));
            } catch (final IllegalArgumentException ignored) {
                return new BossBarSettings(false, "backupyourserver.bossbar", BossBar.Color.BLUE, BossBar.Overlay.PROGRESS, "<aqua>Backup <backupyourserver_progress>% <dark_gray>| <yellow><backupyourserver_eta> <backupyourserver_eta_long> <dark_gray>| <white><backupyourserver_files> <dark_gray>| <gray><backupyourserver_current_file>");
            }

            return new BossBarSettings(
                    section.getBoolean("enabled", true),
                    section.getString("permission", "backupyourserver.bossbar"),
                    color,
                    style,
                    section.getString("text", "<aqua>Backup <backupyourserver_progress>% <dark_gray>| <yellow><backupyourserver_eta> <backupyourserver_eta_long> <dark_gray>| <white><backupyourserver_files> <dark_gray>| <gray><backupyourserver_current_file>")
            );
        }
    }

    public record CompressionMethodSettings(
            CompressionMethod method,
            boolean enabled,
            String executable,
            int compressionLevel,
            int threadCount,
            String extraArgs
    ) {
        static CompressionMethodSettings from(
                final CompressionMethod method,
                final ConfigurationSection section,
                final Logger logger
        ) {
            return new CompressionMethodSettings(
                    method,
                    section.getBoolean("enabled", true),
                    section.getString("executable", method.defaultExecutable()),
                    section.getInt("compression-level", method.defaultCompressionLevel()),
                    section.getInt("thread-count", method.defaultThreadCount()),
                    section.getString("extra-args", "")
            );
        }
    }
}
