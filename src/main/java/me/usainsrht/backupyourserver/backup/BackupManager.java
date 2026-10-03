package me.usainsrht.backupyourserver.backup;

import me.usainsrht.backupyourserver.config.PluginConfig;
import me.usainsrht.backupyourserver.placeholder.BackupPlaceholders;
import me.usainsrht.backupyourserver.util.SchedulerUtil;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Stream;

public final class BackupManager {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final AtomicReference<BackupSession> activeSession = new AtomicReference<>();

    public BackupManager(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public boolean isRunning() {
        return activeSession.get() != null;
    }

    public BackupSession activeSession() {
        return activeSession.get();
    }

    public CompletableFuture<BackupSession> start(
            final PluginConfig config,
            final CompressionMethod method,
            final Consumer<BackupProgress> progressListener
    ) {
        if (!activeSession.compareAndSet(null, new BackupSession(method))) {
            return CompletableFuture.failedFuture(new IllegalStateException("A backup is already running."));
        }

        final BackupSession session = activeSession.get();
        final AtomicBoolean cancelled = session.cancelled();
        final BackupTask task = new BackupTask(config, method, logger, progress -> {
            session.progress(progress);
            BackupPlaceholders.updateProgress(progress);
            if (progressListener != null) {
                progressListener.accept(progress);
            }
        }, cancelled);
        session.bindTask(task);

        final CompletableFuture<BackupSession> future = new CompletableFuture<>();

        SchedulerUtil.runAsync(plugin, () -> {
            try {
                task.run();
                session.complete(task.outputArchive(), task.failureReason());
                future.complete(session);
            } catch (final Exception exception) {
                session.complete(null, exception.getMessage());
                future.completeExceptionally(exception);
            } finally {
                activeSession.compareAndSet(session, null);
            }
        });

        return future;
    }

    public boolean stop() {
        final BackupSession session = activeSession.get();
        if (session == null) {
            return false;
        }
        session.cancelled().set(true);
        final BackupTask task = session.task();
        if (task != null) {
            task.cancel();
        }
        return true;
    }

    public List<BackupInfo> listBackups(final PluginConfig config) throws IOException {
        final Path backupDirectory = config.backupDirectory();
        if (!Files.isDirectory(backupDirectory)) {
            return List.of();
        }

        final List<BackupInfo> backups = new ArrayList<>();
        try (Stream<Path> stream = Files.list(backupDirectory)) {
            stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing((Path path) -> {
                        try {
                            return Files.getLastModifiedTime(path);
                        } catch (final IOException exception) {
                            return null;
                        }
                    }, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                    .forEach(path -> backups.add(toInfo(path)));
        }
        return backups;
    }

    private BackupInfo toInfo(final Path path) {
        final CompressionMethod method = detectMethod(path);
        long size = 0L;
        Instant createdAt = Instant.EPOCH;
        try {
            size = Files.size(path);
            createdAt = Files.getLastModifiedTime(path).toInstant();
        } catch (final IOException exception) {
            logger.fine("Unable to read backup metadata for " + path);
        }
        return new BackupInfo(path.getFileName().toString(), path, method, size, createdAt);
    }

    private CompressionMethod detectMethod(final Path path) {
        final String name = path.getFileName().toString().toLowerCase();
        for (final CompressionMethod method : CompressionMethod.values()) {
            if (name.endsWith(method.fileExtension())) {
                return method;
            }
        }
        return CompressionMethod.TAR_ZSTD;
    }

    public static String formatTimestamp(final Instant instant) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(instant);
    }

    public static String formatSize(final long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        final double kilobytes = bytes / 1024.0D;
        if (kilobytes < 1024) {
            return String.format(java.util.Locale.ROOT, "%.1f KB", kilobytes);
        }
        final double megabytes = kilobytes / 1024.0D;
        if (megabytes < 1024) {
            return String.format(java.util.Locale.ROOT, "%.1f MB", megabytes);
        }
        final double gigabytes = megabytes / 1024.0D;
        if (gigabytes < 1024) {
            return String.format(java.util.Locale.ROOT, "%.2f GB", gigabytes);
        }
        return String.format(java.util.Locale.ROOT, "%.2f TB", gigabytes / 1024.0D);
    }

    public static final class BackupSession {
        private final CompressionMethod method;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile BackupTask task;
        private volatile BackupProgress progress = BackupProgress.EMPTY;
        private volatile Path outputArchive;
        private volatile String failureReason;
        private volatile boolean completed;

        private BackupSession(final CompressionMethod method) {
            this.method = method;
        }

        void bindTask(final BackupTask task) {
            this.task = task;
        }

        public BackupTask task() {
            return task;
        }

        public CompressionMethod method() {
            return method;
        }

        public AtomicBoolean cancelled() {
            return cancelled;
        }

        public double progress() {
            return progress.fraction();
        }

        public BackupProgress detailedProgress() {
            return progress;
        }

        void progress(final BackupProgress progress) {
            this.progress = progress == null ? BackupProgress.EMPTY : progress;
        }

        void complete(final Path outputArchive, final String failureReason) {
            this.outputArchive = outputArchive;
            this.failureReason = failureReason;
            this.completed = true;
        }

        public Path outputArchive() {
            return outputArchive;
        }

        public String failureReason() {
            return failureReason;
        }

        public boolean successful() {
            return completed && failureReason == null && outputArchive != null;
        }

        public boolean completed() {
            return completed;
        }
    }
}
