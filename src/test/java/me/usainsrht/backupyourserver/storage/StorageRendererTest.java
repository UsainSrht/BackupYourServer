package me.usainsrht.backupyourserver.storage;

import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageRendererTest {

    static class TestSender implements CommandSender {
        final List<Component> messages = new ArrayList<>();

        @Override
        public void sendMessage(final @NotNull Component message) {
            messages.add(message);
        }

        @Override public void sendMessage(@NotNull String message) {}
        @Override public void sendMessage(@NotNull String... messages) {}
        @Override public void sendMessage(@Nullable UUID sender, @NotNull String message) {}
        @Override public void sendMessage(@Nullable UUID sender, @NotNull String... messages) {}
        @Override public @NotNull Server getServer() { throw new UnsupportedOperationException(); }
        @Override public @NotNull String getName() { return "TestSender"; }
        @Override public @NotNull Spigot spigot() { throw new UnsupportedOperationException(); }
        @Override public @NotNull Component name() { return Component.text("TestSender"); }
        @Override public boolean isPermissionSet(@NotNull String name) { return true; }
        @Override public boolean isPermissionSet(@NotNull Permission perm) { return true; }
        @Override public boolean hasPermission(@NotNull String name) { return true; }
        @Override public boolean hasPermission(@NotNull Permission perm) { return true; }
        @Override public @NotNull PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String name, boolean value) { throw new UnsupportedOperationException(); }
        @Override public @NotNull PermissionAttachment addAttachment(@NotNull Plugin plugin) { throw new UnsupportedOperationException(); }
        @Override public @Nullable PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String name, boolean value, int ticks) { return null; }
        @Override public @Nullable PermissionAttachment addAttachment(@NotNull Plugin plugin, int ticks) { return null; }
        @Override public void removeAttachment(@NotNull PermissionAttachment attachment) {}
        @Override public void recalculatePermissions() {}
        @Override public @NotNull Set<PermissionAttachmentInfo> getEffectivePermissions() { return Set.of(); }
        @Override public boolean isOp() { return true; }
        @Override public void setOp(boolean value) {}
    }

    @Test
    void testRenderOverview() {
        final DiskSpaceInfo disk = new DiskSpaceInfo(
                500L * 1024L * 1024L * 1024L,
                200L * 1024L * 1024L * 1024L,
                300L * 1024L * 1024L * 1024L,
                "H:\\",
                "NTFS"
        );

        final StorageNode worldsNode = new StorageNode("worlds", null, 168L * 1024L * 1024L * 1024L);
        final StorageNode overworld = new StorageNode("world", null, 120L * 1024L * 1024L * 1024L, "overworld");
        final StorageNode nether = new StorageNode("world_nether", null, 35L * 1024L * 1024L * 1024L, "nether");
        final StorageNode end = new StorageNode("world_the_end", null, 13L * 1024L * 1024L * 1024L, "the_end");
        worldsNode.addChild(overworld);
        worldsNode.addChild(nether);
        worldsNode.addChild(end);

        final StorageNode pluginsNode = new StorageNode("plugins", null, 1024L * 1024L * 1024L);
        final StorageNode worldEdit = new StorageNode("WorldEdit", null, 20L * 1024L * 1024L);
        worldEdit.addDetail("Data folder", "18.0 MB");
        worldEdit.addDetail("Jar file", "2.0 MB");
        pluginsNode.addChild(worldEdit);

        final StorageNode backups = new StorageNode("backups", null, 45L * 1024L * 1024L * 1024L, "10 archives");
        final StorageNode logs = new StorageNode("logs", null, 2L * 1024L * 1024L * 1024L);

        final StorageReport report = new StorageReport(
                Instant.now(),
                disk,
                worldsNode,
                pluginsNode,
                List.of(backups, logs),
                null,
                216L * 1024L * 1024L * 1024L
        );

        final TestSender sender = new TestSender();
        StorageRenderer.renderOverview(sender, report, false);

        assertFalse(sender.messages.isEmpty());
        assertTrue(sender.messages.size() >= 7);

        final TestSender pluginsSender = new TestSender();
        StorageRenderer.renderPluginsBreakdown(pluginsSender, report);
        assertFalse(pluginsSender.messages.isEmpty());

        final TestSender worldsSender = new TestSender();
        StorageRenderer.renderWorldsBreakdown(worldsSender, report);
        assertFalse(worldsSender.messages.isEmpty());
    }
}
