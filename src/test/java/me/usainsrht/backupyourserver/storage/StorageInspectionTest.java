package me.usainsrht.backupyourserver.storage;

import me.usainsrht.backupyourserver.backup.BackupManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageInspectionTest {

    @Test
    void testFormatSize() {
        assertEquals("500 B", BackupManager.formatSize(500L));
        assertEquals("1.5 KB", BackupManager.formatSize(1536L));
        assertEquals("20.0 MB", BackupManager.formatSize(20L * 1024L * 1024L));
        assertEquals("120.00 GB", BackupManager.formatSize(120L * 1024L * 1024L * 1024L));
        assertEquals("2.50 TB", BackupManager.formatSize((long) (2.5 * 1024L * 1024L * 1024L * 1024L)));
    }

    @Test
    void testDiskSpaceInfo() {
        final DiskSpaceInfo info = new DiskSpaceInfo(
                1_000_000_000L,
                400_000_000L,
                600_000_000L,
                "C:\\",
                "NTFS"
        );

        assertEquals(40.0D, info.usedPercent(), 0.01D);
        assertEquals(60.0D, info.freePercent(), 0.01D);

        final String bar = info.renderProgressBar(10);
        assertNotNull(bar);
        assertTrue(bar.contains("■"));
        assertTrue(bar.contains("<green>"));
    }

    @Test
    void testStorageNodeSorting() {
        final StorageNode root = new StorageNode("root", null, 0L);
        final StorageNode small = new StorageNode("small", null, 100L);
        final StorageNode large = new StorageNode("large", null, 1000L);
        final StorageNode medium = new StorageNode("medium", null, 500L);

        root.addChild(small);
        root.addChild(large);
        root.addChild(medium);

        root.sortChildrenBySizeDescending();

        assertEquals(3, root.children().size());
        assertEquals("large", root.children().get(0).name());
        assertEquals("medium", root.children().get(1).name());
        assertEquals("small", root.children().get(2).name());
    }

    @Test
    void testCalculateDirectorySize(@TempDir final Path tempDir) throws IOException {
        final Path subDir = tempDir.resolve("sub");
        Files.createDirectories(subDir);

        final Path file1 = tempDir.resolve("file1.txt");
        Files.writeString(file1, "12345"); // 5 bytes

        final Path file2 = subDir.resolve("file2.txt");
        Files.writeString(file2, "1234567890"); // 10 bytes

        final long total = StorageInspectionService.calculateDirectorySize(tempDir);
        assertEquals(15L, total);
    }
}
