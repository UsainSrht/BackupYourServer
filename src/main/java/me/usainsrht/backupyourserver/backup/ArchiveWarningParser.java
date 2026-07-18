package me.usainsrht.backupyourserver.backup;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ArchiveWarningParser {

    private static final Pattern CANNOT_OPEN_FILE = Pattern.compile(
            "(?i)cannot open file\\s+(.+)$"
    );
    private static final Pattern TAR_FILE_ERROR = Pattern.compile(
            "^tar:\\s*(.+?):\\s*.+$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SUMMARY_CANNOT_OPEN = Pattern.compile(
            "(?i)cannot open\\s+\\d+\\s+files?"
    );

    private final Path sourceRoot;
    private final String logPrefix;
    private final Logger logger;
    private final List<String> inaccessibleFiles = new ArrayList<>();
    private String pendingWarning;
    private String lastAttemptedRelativePath;

    ArchiveWarningParser(final Path sourceRoot, final String logPrefix, final Logger logger) {
        this.sourceRoot = sourceRoot;
        this.logPrefix = logPrefix;
        this.logger = logger;
    }

    boolean onLine(final String line) {
        if (line == null) {
            return false;
        }

        final String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return false;
        }

        if (pendingWarning != null && isBarePathLine(trimmed)) {
            logWarningWithPath(pendingWarning, trimmed);
            pendingWarning = null;
            return true;
        }

        if (pendingWarning != null) {
            logger.warning(logPrefix + pendingWarning);
            pendingWarning = null;
        }

        if (!isArchiveWarningLine(trimmed)) {
            return false;
        }

        if (isSummaryWarning(trimmed)) {
            logger.warning(logPrefix + trimmed);
            return true;
        }

        if ("WARNINGS for files:".equalsIgnoreCase(trimmed)) {
            logger.warning(logPrefix + trimmed);
            return true;
        }

        final String inlinePath = extractPathFromWarning(trimmed);
        if (inlinePath != null) {
            logWarningWithPath(trimmed, inlinePath);
            return true;
        }

        if (lastAttemptedRelativePath != null) {
            logWarningWithPath(trimmed, lastAttemptedRelativePath);
            return true;
        }

        pendingWarning = trimmed;
        return true;
    }

    void flush() {
        if (pendingWarning != null) {
            logger.warning(logPrefix + pendingWarning);
            pendingWarning = null;
        }
    }

    List<String> inaccessibleFiles() {
        return List.copyOf(inaccessibleFiles);
    }

    static boolean isArchiveWarningLine(final String line) {
        if (line == null) {
            return false;
        }

        final String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return false;
        }

        final String lower = trimmed.toLowerCase(Locale.ROOT);
        return trimmed.startsWith("tar:")
                || lower.contains("couldn't")
                || lower.contains("permission denied")
                || lower.contains("error exit")
                || lower.contains("warning");
    }

    private void logWarningWithPath(final String warning, final String pathText) {
        final String fullPath = resolveFullPath(pathText);
        logger.warning(logPrefix + warning + " -> " + fullPath);
        if (!inaccessibleFiles.contains(fullPath)) {
            inaccessibleFiles.add(fullPath);
        }
    }

    void onProcessedFile(final String relativePath) {
        lastAttemptedRelativePath = relativePath;
    }

    private String resolveFullPath(final String pathText) {
        String cleaned = pathText.replace('\\', '/').trim();
        while (cleaned.startsWith("./")) {
            cleaned = cleaned.substring(2);
        }

        final Path path = Path.of(cleaned);
        if (path.isAbsolute()) {
            return path.toAbsolutePath().normalize().toString();
        }
        return sourceRoot.resolve(cleaned).toAbsolutePath().normalize().toString();
    }

    private static String extractPathFromWarning(final String line) {
        Matcher matcher = CANNOT_OPEN_FILE.matcher(line);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        matcher = TAR_FILE_ERROR.matcher(line);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return null;
    }

    private static boolean isSummaryWarning(final String line) {
        return SUMMARY_CANNOT_OPEN.matcher(line).find();
    }

    private static boolean isBarePathLine(final String line) {
        if (line.isEmpty()) {
            return false;
        }

        final String lower = line.toLowerCase(Locale.ROOT);
        if (lower.startsWith("warning")
                || lower.startsWith("error")
                || lower.startsWith("open archive")
                || lower.startsWith("scanning")
                || lower.startsWith("creating archive")
                || lower.startsWith("compressing")
                || lower.startsWith("everything is ok")
                || lower.startsWith("7-zip")
                || lower.startsWith("+ ")
                || lower.startsWith("items to compress")
                || lower.startsWith("archives with")
                || "warnings for files:".equals(lower)) {
            return false;
        }

        return line.indexOf('/') >= 0
                || line.indexOf('\\') >= 0
                || line.startsWith(".\\")
                || line.startsWith("./");
    }
}
