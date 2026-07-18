package me.usainsrht.backupyourserver.backup;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.usainsrht.backupyourserver.BackupYourServerPlugin;
import me.usainsrht.backupyourserver.config.PluginConfig;
import me.usainsrht.backupyourserver.placeholder.BackupPlaceholders;
import me.usainsrht.backupyourserver.util.DurationFormatter;
import me.usainsrht.backupyourserver.util.SchedulerUtil;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public final class BackupSchedulerService {

    private final BackupYourServerPlugin plugin;
    private final BackupManager backupManager;
    private final BackupRetentionService retentionService;
    private final Logger logger;
    private final AtomicReference<Instant> nextRunAt = new AtomicReference<>();
    private final AtomicReference<ScheduledTask> pendingTask = new AtomicReference<>();

    public BackupSchedulerService(
            final BackupYourServerPlugin plugin,
            final BackupManager backupManager,
            final BackupRetentionService retentionService
    ) {
        this.plugin = plugin;
        this.backupManager = backupManager;
        this.retentionService = retentionService;
        this.logger = plugin.getLogger();
    }

    public void start() {
        reload(plugin.config());
    }

    public void reload(final PluginConfig config) {
        cancelPending();
        nextRunAt.set(null);

        final PluginConfig.ScheduledBackupSettings schedule = config.scheduledBackupSettings();
        if (!schedule.enabled()) {
            return;
        }

        scheduleNext(config);
    }

    public void shutdown() {
        cancelPending();
        nextRunAt.set(null);
    }

    public Instant nextRunAt() {
        return nextRunAt.get();
    }

    public Duration timeUntilNextBackup() {
        final Instant next = nextRunAt.get();
        if (next == null) {
            return null;
        }
        return Duration.between(Instant.now(), next);
    }

    public boolean isEnabled() {
        return plugin.config().scheduledBackupSettings().enabled();
    }

    private void scheduleNext(final PluginConfig config) {
        final Instant next = computeNextRunAt(config, Instant.now());
        nextRunAt.set(next);

        final long delayMillis = Math.max(0L, Duration.between(Instant.now(), next).toMillis());
        logger.info("Next scheduled backup in " + DurationFormatter.format(Duration.ofMillis(delayMillis))
                + " (" + BackupManager.formatTimestamp(next) + ").");

        final ScheduledTask task = SchedulerUtil.runAsyncDelayed(plugin, () -> runScheduledBackup(config), delayMillis);
        pendingTask.set(task);
    }

    private void runScheduledBackup(final PluginConfig config) {
        if (!config.scheduledBackupSettings().enabled()) {
            return;
        }

        if (backupManager.isRunning()) {
            logger.info("Scheduled backup skipped because a backup is already running.");
            scheduleNext(config);
            return;
        }

        final CompressionMethod method = config.resolveMethod(config.scheduledBackupSettings().method());
        final PluginConfig.CompressionMethodSettings settings = config.compressionSettings(method);
        if (settings == null || !settings.enabled()) {
            logger.warning("Scheduled backup skipped: method " + method.id() + " is disabled.");
            scheduleNext(config);
            return;
        }

        logger.info("Starting scheduled backup using " + method.id() + ".");
        BackupPlaceholders.updateStatus("running");
        BackupPlaceholders.updateMethod(method);
        BackupPlaceholders.updateProgress(BackupProgress.EMPTY);
        plugin.bossBarService().setActive(true);

        backupManager.start(config, method, null).whenComplete((session, throwable) -> SchedulerUtil.runGlobal(plugin, () -> {
            plugin.bossBarService().hideAll();
            if (throwable != null) {
                BackupPlaceholders.updateStatus("failed");
                logger.severe("Scheduled backup failed: " + throwable.getMessage());
                scheduleNext(plugin.config());
                return;
            }

            if (session.successful()) {
                BackupPlaceholders.updateStatus("completed");
                BackupPlaceholders.updateLastArchive(session.outputArchive().getFileName().toString());
                logger.info("Scheduled backup completed: " + session.outputArchive().getFileName());
                retentionService.cleanup(plugin.config());
            } else if (session.cancelled().get()) {
                BackupPlaceholders.updateStatus("cancelled");
                logger.info("Scheduled backup was cancelled.");
            } else {
                BackupPlaceholders.updateStatus("failed");
                if (session.failureReason() != null) {
                    logger.severe(session.failureReason());
                }
            }

            scheduleNext(plugin.config());
        }));
    }

    static Instant computeNextRunAt(final PluginConfig config, final Instant from) {
        final PluginConfig.ScheduledBackupSettings schedule = config.scheduledBackupSettings();
        final ZoneId zone = ZoneId.systemDefault();

        return switch (schedule.mode()) {
            case INTERVAL -> from.plus(schedule.interval().duration());
            case DAILY -> nextDailyRun(from, schedule.time(), zone);
            case WEEKLY -> nextWeeklyRun(from, schedule.time(), schedule.dayOfWeek(), zone);
        };
    }

    private static Instant nextDailyRun(
            final Instant from,
            final PluginConfig.ScheduleTime time,
            final ZoneId zone
    ) {
        final LocalDateTime fromDateTime = LocalDateTime.ofInstant(from, zone);
        LocalDateTime candidate = fromDateTime.with(time.toLocalTime());
        if (!candidate.isAfter(fromDateTime)) {
            candidate = candidate.plusDays(1L);
        }
        return candidate.atZone(zone).toInstant();
    }

    private static Instant nextWeeklyRun(
            final Instant from,
            final PluginConfig.ScheduleTime time,
            final DayOfWeek dayOfWeek,
            final ZoneId zone
    ) {
        final LocalDate fromDate = LocalDate.ofInstant(from, zone);
        LocalDate candidateDate = fromDate.with(TemporalAdjusters.nextOrSame(dayOfWeek));
        LocalDateTime candidate = LocalDateTime.of(candidateDate, time.toLocalTime());
        if (!candidate.isAfter(LocalDateTime.ofInstant(from, zone))) {
            candidateDate = candidateDate.with(TemporalAdjusters.next(dayOfWeek));
            candidate = LocalDateTime.of(candidateDate, time.toLocalTime());
        }
        return candidate.atZone(zone).toInstant();
    }

    private void cancelPending() {
        final ScheduledTask task = pendingTask.getAndSet(null);
        if (task != null) {
            task.cancel();
        }
    }
}
