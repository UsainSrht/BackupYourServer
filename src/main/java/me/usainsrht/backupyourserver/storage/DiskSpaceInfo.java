package me.usainsrht.backupyourserver.storage;

import me.usainsrht.backupyourserver.backup.BackupManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public record DiskSpaceInfo(
        long totalBytes,
        long usedBytes,
        long usableBytes,
        String driveName,
        String fileSystemType
) {

    public static DiskSpaceInfo fromPath(final Path path) {
        final File file = path.toFile();
        final long total = file.getTotalSpace();
        final long usable = file.getUsableSpace();
        final long used = Math.max(0L, total - usable);

        String drive = "";
        String fsType = "";

        if (path.getRoot() != null) {
            drive = path.getRoot().toString();
        }

        try {
            final FileStore store = Files.getFileStore(path);
            fsType = store.type();
            final String storeName = store.name();
            if (storeName != null && !storeName.isBlank()) {
                if (drive.isBlank()) {
                    drive = storeName;
                } else if (!drive.contains(storeName) && !storeName.contains(drive)) {
                    drive = drive + " (" + storeName + ")";
                }
            }
        } catch (final IOException ignored) {
        }

        if (drive.isBlank()) {
            drive = "Root";
        }
        if (fsType.isBlank()) {
            fsType = "Default";
        }

        return new DiskSpaceInfo(total, used, usable, drive, fsType);
    }

    public double usedPercent() {
        if (totalBytes <= 0L) {
            return 0.0D;
        }
        return (usedBytes * 100.0D) / (double) totalBytes;
    }

    public double freePercent() {
        if (totalBytes <= 0L) {
            return 0.0D;
        }
        return (usableBytes * 100.0D) / (double) totalBytes;
    }

    public String formatTotal() {
        return BackupManager.formatSize(totalBytes);
    }

    public String formatUsed() {
        return BackupManager.formatSize(usedBytes);
    }

    public String formatUsable() {
        return BackupManager.formatSize(usableBytes);
    }

    /**
     * Builds a colored MiniMessage progress bar representing disk usage.
     *
     * @param totalBlocks Number of bar segments
     * @return MiniMessage formatted bar string
     */
    public String renderProgressBar(final int totalBlocks) {
        final double ratio = totalBytes > 0 ? Math.clamp((double) usedBytes / (double) totalBytes, 0.0D, 1.0D) : 0.0D;
        final int filledBlocks = (int) Math.round(ratio * totalBlocks);
        final int emptyBlocks = Math.max(0, totalBlocks - filledBlocks);

        final String colorTag;
        final double percent = ratio * 100.0D;
        if (percent >= 85.0D) {
            colorTag = "<red>";
        } else if (percent >= 70.0D) {
            colorTag = "<yellow>";
        } else {
            colorTag = "<green>";
        }

        final StringBuilder builder = new StringBuilder();
        builder.append("<dark_gray>[");
        if (filledBlocks > 0) {
            builder.append(colorTag).append("■".repeat(filledBlocks));
        }
        if (emptyBlocks > 0) {
            builder.append("<dark_gray>").append("■".repeat(emptyBlocks));
        }
        builder.append("<dark_gray>]");
        return builder.toString();
    }
}
