package me.usainsrht.backupyourserver.storage;

import me.usainsrht.backupyourserver.backup.BackupManager;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

public record StorageReport(
        Instant timestamp,
        DiskSpaceInfo diskSpace,
        StorageNode worldsNode,
        StorageNode pluginsNode,
        List<StorageNode> otherDirectories,
        @Nullable StorageNode rootFilesNode,
        long totalServerBytes
) {

    public List<StorageNode> otherDirectories() {
        return Collections.unmodifiableList(otherDirectories);
    }

    public String formatTotalServerSize() {
        return BackupManager.formatSize(totalServerBytes);
    }
}
