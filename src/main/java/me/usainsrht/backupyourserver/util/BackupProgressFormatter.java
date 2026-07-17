package me.usainsrht.backupyourserver.util;

import java.util.Locale;

public final class BackupProgressFormatter {

    private BackupProgressFormatter() {
    }

    public static String formatEtaClock(final long etaSeconds) {
        if (etaSeconds < 0L) {
            return "(?:??)";
        }
        final long minutes = etaSeconds / 60L;
        final long seconds = etaSeconds % 60L;
        return String.format(Locale.ROOT, "(%d:%02d)", minutes, seconds);
    }

    public static String formatEtaLong(final long etaSeconds) {
        if (etaSeconds < 0L) {
            return "?mins ?seconds";
        }
        final long minutes = etaSeconds / 60L;
        final long seconds = etaSeconds % 60L;
        return minutes + "mins " + seconds + "seconds";
    }

    public static String formatFiles(final long backedUpFiles, final long totalFiles) {
        if (totalFiles <= 0L) {
            return backedUpFiles + "/-";
        }
        return backedUpFiles + "/" + totalFiles;
    }

    public static String formatSizes(final long remainingBytes, final long backedUpBytes) {
        return formatSizeGb(remainingBytes) + "/" + formatSizeGb(backedUpBytes);
    }

    public static String formatSizeGb(final long bytes) {
        if (bytes <= 0L) {
            return "0Gb";
        }
        final double gigabytes = bytes / (1024.0D * 1024.0D * 1024.0D);
        if (gigabytes >= 100.0D) {
            return String.format(Locale.ROOT, "%.0fGb", gigabytes);
        }
        if (gigabytes >= 10.0D) {
            return String.format(Locale.ROOT, "%.1fGb", gigabytes);
        }
        return String.format(Locale.ROOT, "%.2fGb", gigabytes);
    }

    public static String shortPath(final String path) {
        if (path == null || path.isBlank() || "-".equals(path)) {
            return "-";
        }

        final String normalized = path.replace('\\', '/');
        final int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash <= 0) {
            return normalized;
        }

        final int previousSlash = normalized.lastIndexOf('/', lastSlash - 1);
        if (previousSlash < 0) {
            return normalized;
        }

        return normalized.substring(previousSlash + 1);
    }
}
