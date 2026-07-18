package me.usainsrht.backupyourserver.backup;

import me.usainsrht.backupyourserver.config.PluginConfig;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

public final class BackupRetentionService {

    private final BackupManager backupManager;
    private final Logger logger;

    public BackupRetentionService(final JavaPlugin plugin, final BackupManager backupManager) {
        this.backupManager = backupManager;
        this.logger = plugin.getLogger();
    }

    public void cleanup(final PluginConfig config) {
        final PluginConfig.BackupRetentionSettings retention = config.retentionSettings();
        if (!retention.enabled()) {
            return;
        }

        try {
            final List<BackupInfo> backups = new ArrayList<>(backupManager.listBackups(config));
            if (backups.isEmpty()) {
                return;
            }

            backups.sort(Comparator.comparing(BackupInfo::createdAt).reversed());
            final Instant now = Instant.now();
            int deleted = 0;

            for (int index = 0; index < backups.size(); index++) {
                final BackupInfo backup = backups.get(index);
                if (!shouldDelete(backup, index, retention, now)) {
                    continue;
                }
                try {
                    Files.deleteIfExists(backup.path());
                    deleted++;
                    logger.info("Deleted backup " + backup.fileName() + " (retention policy).");
                } catch (final IOException exception) {
                    logger.warning("Unable to delete backup " + backup.fileName() + ": " + exception.getMessage());
                }
            }

            if (deleted > 0) {
                logger.info("Retention cleanup removed " + deleted + " backup(s).");
            }
        } catch (final IOException exception) {
            logger.warning("Retention cleanup failed: " + exception.getMessage());
        }
    }

    public Instant computeDeletionAt(
            final BackupInfo backup,
            final int indexNewestFirst,
            final PluginConfig config,
            final Instant now
    ) {
        final PluginConfig.BackupRetentionSettings retention = config.retentionSettings();
        if (!retention.enabled()) {
            return null;
        }

        Instant deletionAt = null;

        if (retention.maxAge().enabled()) {
            final Duration maxAge = retention.maxAge().duration();
            if (!maxAge.isZero()) {
                final Instant ageDeletion = backup.createdAt().plus(maxAge);
                deletionAt = earliest(deletionAt, ageDeletion);
            }
        }

        if (retention.keepLast().enabled()) {
            final int keepCount = retention.keepLast().count();
            if (keepCount > 0 && indexNewestFirst >= keepCount) {
                deletionAt = earliest(deletionAt, now);
            }
        }

        return deletionAt;
    }

    private static boolean shouldDelete(
            final BackupInfo backup,
            final int indexNewestFirst,
            final PluginConfig.BackupRetentionSettings retention,
            final Instant now
    ) {
        boolean delete = false;

        if (retention.maxAge().enabled()) {
            final Duration maxAge = retention.maxAge().duration();
            if (!maxAge.isZero() && backup.createdAt().plus(maxAge).compareTo(now) <= 0) {
                delete = true;
            }
        }

        if (retention.keepLast().enabled()) {
            final int keepCount = retention.keepLast().count();
            if (keepCount > 0 && indexNewestFirst >= keepCount) {
                delete = true;
            }
        }

        return delete;
    }

    private static Instant earliest(final Instant first, final Instant second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isBefore(second) ? first : second;
    }
}
