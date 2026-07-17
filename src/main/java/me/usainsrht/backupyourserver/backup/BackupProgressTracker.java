package me.usainsrht.backupyourserver.backup;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

final class BackupProgressTracker {

    private static final double COLLECTION_WEIGHT = 0.10D;
    private static final double COMPRESSION_WEIGHT = 0.90D;

    private final Map<String, Long> fileSizes;

    private long totalFiles;
    private long totalBytes;
    private long backedUpFiles;
    private long backedUpBytes;
    private String currentFile = "-";
    private boolean collecting = true;
    private boolean compressingArchive;
    private long compressSourceBytes;
    private long compressOutputBytes;
    private long compressionStartNanos;

    BackupProgressTracker() {
        this.fileSizes = new HashMap<>();
        this.compressionStartNanos = System.nanoTime();
    }

    Map<String, Long> fileSizes() {
        return fileSizes;
    }

    void beginCollection() {
        collecting = true;
        totalFiles = 0L;
        totalBytes = 0L;
        backedUpFiles = 0L;
        backedUpBytes = 0L;
        currentFile = "-";
    }

    void onFileScanned(final String relativePath, final long size) {
        collecting = true;
        fileSizes.put(relativePath, size);
        backedUpFiles++;
        backedUpBytes += size;
        currentFile = relativePath;
    }

    void finishCollection(final long totalFiles, final long totalBytes) {
        this.totalFiles = totalFiles;
        this.totalBytes = totalBytes;
        beginCompression();
    }

    void beginCompression() {
        collecting = false;
        compressingArchive = false;
        compressSourceBytes = 0L;
        compressOutputBytes = 0L;
        backedUpFiles = 0L;
        backedUpBytes = 0L;
        currentFile = "-";
        compressionStartNanos = System.nanoTime();
    }

    void beginCompressStage(final String label) {
        compressingArchive = true;
        compressSourceBytes = 0L;
        compressOutputBytes = 0L;
        currentFile = label;
        compressionStartNanos = System.nanoTime();
    }

    void onCompressProgress(final long sourceBytes, final long outputBytes) {
        compressingArchive = true;
        compressSourceBytes = sourceBytes;
        compressOutputBytes = outputBytes;
    }

    void onFileProcessed(final String relativePath) {
        collecting = false;
        backedUpFiles++;
        backedUpBytes += fileSizes.getOrDefault(relativePath, 0L);
        currentFile = relativePath;
    }

    void removeFile(final String relativePath) {
        final Long removedSize = fileSizes.remove(relativePath);
        if (removedSize == null) {
            return;
        }
        totalFiles = Math.max(0L, totalFiles - 1L);
        totalBytes = Math.max(0L, totalBytes - removedSize);
    }

    BackupProgress snapshot() {
        return new BackupProgress(
                calculateFraction(),
                backedUpFiles,
                totalFiles,
                backedUpBytes,
                totalBytes,
                currentFile,
                calculateEtaSeconds()
        );
    }

    private double calculateFraction() {
        if (collecting) {
            if (totalFiles > 0L) {
                final double scannedRatio = Math.min(1.0D, backedUpFiles / (double) totalFiles);
                return COLLECTION_WEIGHT * scannedRatio;
            }
            return backedUpFiles > 0L ? 0.01D : 0.0D;
        }

        if (compressingArchive && compressSourceBytes > 0L) {
            final double compressRatio = Math.min(1.0D, compressOutputBytes / (double) compressSourceBytes);
            return 0.90D + 0.10D * compressRatio;
        }

        if (totalBytes > 0L) {
            final double compressedRatio = Math.min(1.0D, backedUpBytes / (double) totalBytes);
            return COLLECTION_WEIGHT + COMPRESSION_WEIGHT * compressedRatio;
        }

        if (totalFiles > 0L) {
            final double compressedRatio = Math.min(1.0D, backedUpFiles / (double) totalFiles);
            return COLLECTION_WEIGHT + COMPRESSION_WEIGHT * compressedRatio;
        }

        return COLLECTION_WEIGHT;
    }

    private long calculateEtaSeconds() {
        if (collecting) {
            return -1L;
        }

        if (compressingArchive) {
            if (compressSourceBytes <= 0L || compressOutputBytes <= 0L || compressOutputBytes >= compressSourceBytes) {
                return -1L;
            }
            final long elapsedNanos = System.nanoTime() - compressionStartNanos;
            if (elapsedNanos <= 0L) {
                return -1L;
            }
            final double bytesPerSecond = compressOutputBytes / (elapsedNanos / 1_000_000_000.0D);
            if (bytesPerSecond <= 0.0D) {
                return -1L;
            }
            return Math.max(0L, Math.round((compressSourceBytes - compressOutputBytes) / bytesPerSecond));
        }

        if (backedUpBytes <= 0L || totalBytes <= backedUpBytes) {
            return -1L;
        }

        final long elapsedNanos = System.nanoTime() - compressionStartNanos;
        if (elapsedNanos <= 0L) {
            return -1L;
        }

        final double bytesPerSecond = backedUpBytes / (elapsedNanos / 1_000_000_000.0D);
        if (bytesPerSecond <= 0.0D) {
            return -1L;
        }

        return Math.max(0L, Math.round((totalBytes - backedUpBytes) / bytesPerSecond));
    }
}
