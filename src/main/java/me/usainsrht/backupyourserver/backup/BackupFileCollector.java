package me.usainsrht.backupyourserver.backup;

import me.usainsrht.backupyourserver.config.PluginConfig;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

final class BackupFileCollector {

    private BackupFileCollector() {
    }

    static CollectionResult collect(
            final Path sourceRoot,
            final Path backupDirectory,
            final PluginConfig config,
            final Logger logger,
            final BackupProgressTracker tracker,
            final Consumer<BackupProgress> progressListener
    ) throws IOException {
        Files.createDirectories(backupDirectory);

        final Set<String> normalizedBlacklistPaths = new HashSet<>();
        for (final String path : config.blacklistedPaths()) {
            normalizedBlacklistPaths.add(normalizeRelative(path));
        }

        final List<Pattern> regexPatterns = new ArrayList<>();
        for (final String regex : config.blacklistedRegexes()) {
            try {
                regexPatterns.add(Pattern.compile(regex));
            } catch (final PatternSyntaxException exception) {
                logger.log(Level.WARNING, "Invalid blacklist regex: " + regex, exception);
            }
        }

        final List<String> extensions = config.blacklistedExtensions().stream()
                .map(value -> value.startsWith(".") ? value.toLowerCase() : "." + value.toLowerCase())
                .toList();

        final Path fileListPath = Files.createTempFile(backupDirectory, "backup-list-", ".txt");
        final Path manifestPath = sourceRoot.resolve(JarFilePolicy.MANIFEST_RELATIVE_PATH);
        final List<JarManifestWriter.JarEntry> jarEntries = new ArrayList<>();
        final AtomicLong totalBytes = new AtomicLong();
        final AtomicLong totalFiles = new AtomicLong();
        final AtomicLong lastProgressReport = new AtomicLong(System.nanoTime());
        tracker.beginCollection();

        final Path normalizedBackupDirectory = backupDirectory.toAbsolutePath().normalize();

        try (BufferedWriter writer = Files.newBufferedWriter(fileListPath)) {
            Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) {
                    final Path normalizedDir = dir.toAbsolutePath().normalize();
                    if (normalizedDir.startsWith(normalizedBackupDirectory)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    final String relative = normalizeRelative(sourceRoot.relativize(dir).toString());
                    if (relative.equals(".backupyourserver")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (shouldSkipPath(relative, normalizedBlacklistPaths, regexPatterns)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    final String relative = normalizeRelative(sourceRoot.relativize(file).toString());
                    if (shouldSkipPath(relative, normalizedBlacklistPaths, regexPatterns)) {
                        return FileVisitResult.CONTINUE;
                    }

                    final String fileName = file.getFileName().toString();
                    if (JarFilePolicy.isJarFile(fileName)) {
                        if (JarFilePolicy.shouldDocumentInManifest(relative)) {
                            jarEntries.add(JarManifestWriter.fromFile(file, sourceRoot));
                        }
                        logger.fine("Skipping blacklisted jar: " + relative);
                        return FileVisitResult.CONTINUE;
                    }

                    if (hasBlacklistedExtension(file, extensions)) {
                        return FileVisitResult.CONTINUE;
                    }

                    if (!isReadableForBackup(file)) {
                        logger.fine("Skipping unreadable file during scan: " + relative);
                        return FileVisitResult.CONTINUE;
                    }

                    writer.write(relative);
                    writer.newLine();
                    totalBytes.addAndGet(attrs.size());
                    totalFiles.incrementAndGet();
                    tracker.onFileScanned(relative, attrs.size());
                    reportProgressThrottled(progressListener, tracker, lastProgressReport);
                    return FileVisitResult.CONTINUE;
                }
            });

            if (!jarEntries.isEmpty()) {
                JarManifestWriter.write(manifestPath, jarEntries);
                writer.write(JarFilePolicy.MANIFEST_RELATIVE_PATH);
                writer.newLine();
                totalBytes.addAndGet(Files.size(manifestPath));
                totalFiles.incrementAndGet();
                tracker.onFileScanned(JarFilePolicy.MANIFEST_RELATIVE_PATH, Files.size(manifestPath));
            }
        }

        final long files = totalFiles.get();
        final long bytes = totalBytes.get();
        tracker.finishCollection(files, bytes);
        if (progressListener != null) {
            progressListener.accept(tracker.snapshot());
        }

        return new CollectionResult(
                fileListPath,
                bytes,
                files,
                jarEntries.isEmpty() ? null : manifestPath
        );
    }

    private static void reportProgressThrottled(
            final Consumer<BackupProgress> progressListener,
            final BackupProgressTracker tracker,
            final AtomicLong lastProgressReport
    ) {
        if (progressListener == null) {
            return;
        }

        final long now = System.nanoTime();
        if (now - lastProgressReport.get() < 250_000_000L) {
            return;
        }
        lastProgressReport.set(now);
        progressListener.accept(tracker.snapshot());
    }

    private static boolean shouldSkipPath(
            final String relativePath,
            final Set<String> blacklistedPaths,
            final List<Pattern> regexPatterns
    ) {
        if (blacklistedPaths.contains(relativePath)) {
            return true;
        }
        for (final Pattern pattern : regexPatterns) {
            if (pattern.matcher(relativePath).find()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasBlacklistedExtension(final Path file, final List<String> extensions) {
        final String fileName = file.getFileName().toString().toLowerCase();
        for (final String extension : extensions) {
            if (fileName.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeRelative(final String path) {
        return path.replace('\\', '/');
    }

    private static boolean isReadableForBackup(final Path file) {
        if (!Files.isRegularFile(file)) {
            return false;
        }

        try (var input = Files.newInputStream(file)) {
            input.read();
            return true;
        } catch (final IOException exception) {
            return false;
        }
    }

    record CollectionResult(Path fileListPath, long totalBytes, long totalFiles, Path manifestPath) {
    }
}
