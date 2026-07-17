package me.usainsrht.backupyourserver.backup;

import java.nio.file.Path;
import java.time.Instant;

public record BackupInfo(
        String fileName,
        Path path,
        CompressionMethod method,
        long sizeBytes,
        Instant createdAt
) {
}
