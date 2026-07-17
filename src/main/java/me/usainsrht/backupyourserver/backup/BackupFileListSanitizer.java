package me.usainsrht.backupyourserver.backup;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

final class BackupFileListSanitizer {

    private BackupFileListSanitizer() {
    }

    static SanitizeResult sanitize(
            final Path sourceRoot,
            final Path fileListPath,
            final BackupProgressTracker tracker,
            final Logger logger
    ) throws IOException {
        final List<String> relativePaths = Files.readAllLines(fileListPath);
        final List<String> keptPaths = new ArrayList<>(relativePaths.size());
        int skipped = 0;

        for (final String relativePath : relativePaths) {
            if (relativePath == null || relativePath.isBlank()) {
                continue;
            }

            final Path absolutePath = sourceRoot.resolve(relativePath);
            if (isReadableForBackup(absolutePath)) {
                keptPaths.add(relativePath);
                continue;
            }

            skipped++;
            tracker.removeFile(relativePath);
            logger.warning("Skipping unreadable or missing file: " + relativePath);
        }

        if (keptPaths.isEmpty()) {
            throw new IOException("No readable files remain in the backup file list.");
        }

        if (skipped > 0) {
            try (BufferedWriter writer = Files.newBufferedWriter(fileListPath)) {
                for (final String keptPath : keptPaths) {
                    writer.write(keptPath);
                    writer.newLine();
                }
            }
            logger.warning("Excluded " + skipped + " unreadable or missing file(s) from backup.");
        }

        return new SanitizeResult(keptPaths.size(), skipped);
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

    record SanitizeResult(int keptFiles, int skippedFiles) {
    }
}
