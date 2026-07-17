package me.usainsrht.backupyourserver.backup;

public record BackupProgress(
        double fraction,
        long backedUpFiles,
        long totalFiles,
        long backedUpBytes,
        long totalBytes,
        String currentFile,
        long etaSeconds
) {
    public static final BackupProgress EMPTY = new BackupProgress(0.0D, 0L, 0L, 0L, 0L, "-", -1L);

    public long remainingBytes() {
        return Math.max(0L, totalBytes - backedUpBytes);
    }
}
