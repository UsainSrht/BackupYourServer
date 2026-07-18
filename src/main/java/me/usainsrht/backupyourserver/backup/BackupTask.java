package me.usainsrht.backupyourserver.backup;

import me.usainsrht.backupyourserver.config.PluginConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BackupTask implements Runnable {

    private final PluginConfig config;
    private final CompressionMethod method;
    private final Logger logger;
    private final Consumer<BackupProgress> progressListener;
    private final AtomicBoolean cancelled;

    private volatile Process activeProcess;
    private volatile Path outputArchive;
    private volatile String failureReason;

    public BackupTask(
            final PluginConfig config,
            final CompressionMethod method,
            final Logger logger,
            final Consumer<BackupProgress> progressListener,
            final AtomicBoolean cancelled
    ) {
        this.config = config;
        this.method = method;
        this.logger = logger;
        this.progressListener = progressListener;
        this.cancelled = cancelled;
    }

    @Override
    public void run() {
        Path fileList = null;
        Path tempArchive = null;
        Path jarManifest = null;
        final BackupProgressTracker tracker = new BackupProgressTracker();
        try {
            final PluginConfig.CompressionMethodSettings settings = config.compressionSettings(method);
            if (settings == null || !settings.enabled()) {
                failureReason = "Backup method " + method.id() + " is disabled.";
                return;
            }

            final String missingRequirements = ExecutableInstallGuide.missingRequirementsMessage(method, settings);
            if (!missingRequirements.isBlank()) {
                failureReason = missingRequirements;
                logger.severe(failureReason);
                return;
            }

            reportProgress(tracker.snapshot());

            final Path sourceRoot = config.sourceDirectory().toAbsolutePath().normalize();
            final Path backupDirectory = config.backupDirectory().toAbsolutePath().normalize();
            Files.createDirectories(backupDirectory);

            final BackupFileCollector.CollectionResult collection = BackupFileCollector.collect(
                    sourceRoot,
                    backupDirectory,
                    config,
                    logger,
                    tracker,
                    this::reportProgress
            );
            fileList = collection.fileListPath();
            jarManifest = collection.manifestPath();

            if (cancelled.get()) {
                failureReason = "Backup cancelled.";
                return;
            }

            reportProgress(tracker.snapshot());

            BackupFileListSanitizer.sanitize(sourceRoot, fileList, tracker, logger);

            if (cancelled.get()) {
                failureReason = "Backup cancelled.";
                return;
            }

            outputArchive = backupDirectory.resolve(CompressionCommandBuilder.archiveFileName(method));
            final CompressionPlan plan = CompressionCommandBuilder.buildPlan(
                    method,
                    settings,
                    sourceRoot,
                    outputArchive,
                    fileList
            );
            tempArchive = plan.tempArchive();

            for (final CompressionPlan.Stage stage : plan.stages()) {
                if (cancelled.get()) {
                    failureReason = "Backup cancelled.";
                    return;
                }

                logger.info("Starting backup using " + method.id() + ": " + String.join(" ", stage.command()));

                if (stage.kind() == CompressionPlan.StageKind.ARCHIVE) {
                    final Path archiveTarget = tempArchive != null ? tempArchive : outputArchive;
                    runArchiveStage(stage.command(), sourceRoot, archiveTarget, tracker);
                } else {
                    runCompressStage(stage.command(), tempArchive, tracker);
                    finalizeCompressedOutput(tempArchive, outputArchive);
                }
            }

            final BackupProgress completed = tracker.snapshot();
            reportProgress(new BackupProgress(
                    1.0D,
                    completed.totalFiles(),
                    completed.totalFiles(),
                    completed.totalBytes(),
                    completed.totalBytes(),
                    completed.currentFile(),
                    0L
            ));
            logger.info("Backup completed: " + outputArchive);
        } catch (final IOException exception) {
            failureReason = exception.getMessage();
            logger.log(Level.SEVERE, "Backup failed due to I/O error", exception);
            cleanupIncompleteArchive(outputArchive);
            outputArchive = null;
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            failureReason = "Backup interrupted.";
            if (activeProcess != null) {
                activeProcess.destroyForcibly();
            }
        } finally {
            if (fileList != null) {
                try {
                    Files.deleteIfExists(fileList);
                } catch (final IOException exception) {
                    logger.log(Level.WARNING, "Unable to delete temporary file list", exception);
                }
            }
            cleanupJarManifest(jarManifest);
            if (tempArchive != null) {
                deleteIfExistsQuietly(tempArchive);
                deleteIfExistsQuietly(Path.of(tempArchive + ".gz"));
                deleteIfExistsQuietly(Path.of(tempArchive + ".xz"));
            }
            activeProcess = null;
        }
    }

    private void runArchiveStage(
            final java.util.List<String> command,
            final Path sourceRoot,
            final Path archiveTarget,
            final BackupProgressTracker tracker
    ) throws IOException, InterruptedException {
        final ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(sourceRoot.toFile());
        processBuilder.redirectErrorStream(true);

        activeProcess = processBuilder.start();
        reportProgress(tracker.snapshot());

        final AtomicLong lastProgressReport = new AtomicLong(System.nanoTime());
        final ArchiveWarningParser warningParser = new ArchiveWarningParser(
                sourceRoot,
                "[" + method.id() + "] ",
                logger
        );
        final java.util.List<String> archiveMessages = new java.util.ArrayList<>();
        try (var reader = activeProcess.inputReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (cancelled.get()) {
                    activeProcess.destroyForcibly();
                    failureReason = "Backup cancelled.";
                    return;
                }

                if (ArchiveWarningParser.isArchiveWarningLine(line)) {
                    warningParser.onLine(line);
                    archiveMessages.add(line.trim());
                } else if (!warningParser.onLine(line)) {
                    final String processedFile = parseProcessedFile(line);
                    if (processedFile != null) {
                        warningParser.onProcessedFile(processedFile);
                        tracker.onFileProcessed(processedFile);
                        reportProgressThrottled(tracker, lastProgressReport);
                    }
                }

                logger.fine(line);
            }
        }
        warningParser.flush();

        final int exitCode = activeProcess.waitFor();
        final boolean archiveCreated = Files.isRegularFile(archiveTarget) && Files.size(archiveTarget) > 0L;
        if (exitCode != 0) {
            if (exitCode == 1 && archiveCreated) {
                final int skippedCount = warningParser.inaccessibleFiles().size();
                if (skippedCount > 0) {
                    logger.warning(method.id() + " archive stage completed with warnings (exit code 1). "
                            + skippedCount + " file(s) could not be accessed.");
                } else {
                    logger.warning(method.id() + " archive stage completed with warnings (exit code 1). "
                            + "Some files may have been skipped.");
                }
                return;
            }

            if (!archiveMessages.isEmpty()) {
                logger.severe(method.id() + " archive output: " + String.join(" | ", archiveMessages));
            }
            failureReason = method.id() + " archive stage exited with code " + exitCode;
            logger.severe(failureReason);
            cleanupIncompleteArchive(archiveTarget);
            if (archiveTarget.equals(outputArchive)) {
                outputArchive = null;
            }
            throw new IOException(failureReason);
        }
    }

    private void runCompressStage(
            final java.util.List<String> command,
            final Path tempArchive,
            final BackupProgressTracker tracker
    ) throws IOException, InterruptedException {
        final long sourceBytes = Files.size(tempArchive);
        tracker.beginCompressStage("Compressing archive...");

        final ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        activeProcess = processBuilder.start();

        final AtomicLong lastProgressReport = new AtomicLong(System.nanoTime());
        while (activeProcess.isAlive()) {
            if (cancelled.get()) {
                activeProcess.destroyForcibly();
                failureReason = "Backup cancelled.";
                return;
            }

            updateCompressProgress(tracker, sourceBytes, tempArchive, lastProgressReport);
            Thread.sleep(500L);
        }

        updateCompressProgress(tracker, sourceBytes, tempArchive, lastProgressReport);

        final int exitCode = activeProcess.waitFor();
        if (exitCode != 0) {
            failureReason = method.id() + " compression stage exited with code " + exitCode;
            logger.severe(failureReason);
            cleanupIncompleteArchive(outputArchive);
            outputArchive = null;
            throw new IOException(failureReason);
        }
    }

    private void updateCompressProgress(
            final BackupProgressTracker tracker,
            final long sourceBytes,
            final Path tempArchive,
            final AtomicLong lastProgressReport
    ) {
        try {
            final long compressedBytes = readCompressedOutputSize(tempArchive);
            if (compressedBytes > 0L) {
                tracker.onCompressProgress(sourceBytes, compressedBytes);
            }
            reportProgressThrottled(tracker, lastProgressReport);
        } catch (final IOException exception) {
            logger.fine("Unable to read compressed archive size.");
        }
    }

    private long readCompressedOutputSize(final Path tempArchive) throws IOException {
        if (outputArchive != null && Files.exists(outputArchive)) {
            return Files.size(outputArchive);
        }

        final Path gzipOutput = Path.of(tempArchive + ".gz");
        if (Files.exists(gzipOutput)) {
            return Files.size(gzipOutput);
        }

        final Path xzOutput = Path.of(tempArchive + ".xz");
        if (Files.exists(xzOutput)) {
            return Files.size(xzOutput);
        }

        return 0L;
    }

    private void finalizeCompressedOutput(final Path tempArchive, final Path outputArchive) throws IOException {
        if (Files.exists(outputArchive)) {
            return;
        }

        final Path gzipOutput = Path.of(tempArchive + ".gz");
        if (Files.exists(gzipOutput)) {
            Files.move(gzipOutput, outputArchive, StandardCopyOption.REPLACE_EXISTING);
            return;
        }

        final Path xzOutput = Path.of(tempArchive + ".xz");
        if (Files.exists(xzOutput)) {
            Files.move(xzOutput, outputArchive, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String parseProcessedFile(final String line) {
        if (line == null) {
            return null;
        }

        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("tar:")) {
            return null;
        }

        trimmed = stripTarVerbosePrefix(trimmed);

        return switch (method) {
            case SEVEN_Z, SEVEN_ZA -> {
                if (!trimmed.startsWith("+ ")) {
                    yield null;
                }
                yield normalizeRelative(trimmed.substring(2).trim());
            }
            default -> {
                if (trimmed.startsWith("+ ") || trimmed.contains("  ")) {
                    yield null;
                }
                yield normalizeRelative(trimmed);
            }
        };
    }

    private static String stripTarVerbosePrefix(final String line) {
        if (line.length() >= 2 && (line.charAt(0) == 'a' || line.charAt(0) == 'x') && line.charAt(1) == ' ') {
            return line.substring(2).trim();
        }
        return line;
    }

    private void reportProgressThrottled(final BackupProgressTracker tracker, final AtomicLong lastProgressReport) {
        final long now = System.nanoTime();
        if (now - lastProgressReport.get() < 250_000_000L) {
            return;
        }
        lastProgressReport.set(now);
        reportProgress(tracker.snapshot());
    }

    private void reportProgress(final BackupProgress progress) {
        if (progressListener != null) {
            progressListener.accept(progress);
        }
    }

    private static String normalizeRelative(final String path) {
        String normalized = path.replace('\\', '/').trim();
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }

    private void cleanupJarManifest(final Path manifestPath) {
        if (manifestPath == null) {
            return;
        }

        try {
            Files.deleteIfExists(manifestPath);
            final Path parent = manifestPath.getParent();
            if (parent != null && Files.isDirectory(parent)) {
                try (var entries = Files.list(parent)) {
                    if (entries.findAny().isEmpty()) {
                        Files.deleteIfExists(parent);
                    }
                }
            }
        } catch (final IOException exception) {
            logger.fine("Unable to delete temporary jar manifest.");
        }
    }

    private void cleanupIncompleteArchive(final Path archive) {
        if (archive == null) {
            return;
        }
        try {
            Files.deleteIfExists(archive);
        } catch (final IOException ignored) {
            logger.fine("Unable to delete incomplete archive.");
        }
    }

    private void deleteIfExistsQuietly(final Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (final IOException exception) {
            logger.fine("Unable to delete temporary file: " + path);
        }
    }

    public void cancel() {
        cancelled.set(true);
        final Process process = activeProcess;
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }

    public Path outputArchive() {
        return outputArchive;
    }

    public String failureReason() {
        return failureReason;
    }

    public CompressionMethod method() {
        return method;
    }
}
