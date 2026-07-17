package me.usainsrht.backupyourserver.backup;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum CompressionMethod {
    TAR_ZSTD("tar+zstd", "Balanced", "zstd", 3, 4, ".tar.zst"),
    SEVEN_Z("7z", "Smaller/Disk Space", "7z", 5, 4, ".7z"),
    XZ("xz", "Smaller/Disk Space", "xz", 6, 4, ".tar.xz"),
    PIXZ("pixz", "Smaller/Disk Space", "pixz", 6, 4, ".tar.xz"),
    LZ4("lz4", "Fastest", "lz4", 1, 1, ".tar.lz4"),
    PIGZ("pigz", "Fastest", "pigz", 6, 4, ".tar.gz"),
    SEVEN_ZA("7za", "Smaller/Disk Space", "7za", 5, 4, ".7z"),
    TAR_GZIP("tar+gzip", "Fastest", "gzip", 6, 1, ".tar.gz");

    private final String id;
    private final String category;
    private final String defaultExecutable;
    private final int defaultCompressionLevel;
    private final int defaultThreadCount;
    private final String fileExtension;

    CompressionMethod(
            final String id,
            final String category,
            final String defaultExecutable,
            final int defaultCompressionLevel,
            final int defaultThreadCount,
            final String fileExtension
    ) {
        this.id = id;
        this.category = category;
        this.defaultExecutable = defaultExecutable;
        this.defaultCompressionLevel = defaultCompressionLevel;
        this.defaultThreadCount = defaultThreadCount;
        this.fileExtension = fileExtension;
    }

    public String id() {
        return id;
    }

    public String configKey() {
        return id;
    }

    public String category() {
        return category;
    }

    public String defaultExecutable() {
        return defaultExecutable;
    }

    public int defaultCompressionLevel() {
        return defaultCompressionLevel;
    }

    public int defaultThreadCount() {
        return defaultThreadCount;
    }

    public String fileExtension() {
        return fileExtension;
    }

    public static CompressionMethod byId(final String raw) {
        final String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(method -> method.id.equals(normalized))
                .findFirst()
                .orElse(TAR_ZSTD);
    }

    public static Optional<CompressionMethod> optionalById(final String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(byId(raw));
    }
}
