package me.usainsrht.backupyourserver.storage;

import me.usainsrht.backupyourserver.BackupYourServerPlugin;
import me.usainsrht.backupyourserver.backup.BackupManager;
import me.usainsrht.backupyourserver.config.PluginConfig;
import me.usainsrht.backupyourserver.util.SchedulerUtil;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.CodeSource;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.stream.Stream;

public final class StorageInspectionService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final BackupYourServerPlugin plugin;
    private final AtomicReference<StorageReport> cachedReport = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<StorageReport>> inFlightScan = new AtomicReference<>();

    public StorageInspectionService(final BackupYourServerPlugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<StorageReport> inspectStorage(final boolean forceRefresh) {
        final StorageReport currentCache = cachedReport.get();
        if (!forceRefresh && currentCache != null) {
            final Duration age = Duration.between(currentCache.timestamp(), Instant.now());
            if (age.compareTo(CACHE_TTL) < 0) {
                return CompletableFuture.completedFuture(currentCache);
            }
        }

        final CompletableFuture<StorageReport> existing = inFlightScan.get();
        if (existing != null && !existing.isDone()) {
            return existing;
        }

        final CompletableFuture<StorageReport> future = new CompletableFuture<>();
        if (!inFlightScan.compareAndSet(existing, future)) {
            final CompletableFuture<StorageReport> concurrent = inFlightScan.get();
            if (concurrent != null) {
                return concurrent;
            }
        }

        SchedulerUtil.runAsync(plugin, () -> {
            try {
                final StorageReport report = performScan();
                cachedReport.set(report);
                future.complete(report);
            } catch (final Throwable throwable) {
                plugin.getLogger().log(Level.SEVERE, "Failed to analyze server storage", throwable);
                future.completeExceptionally(throwable);
            } finally {
                inFlightScan.set(null);
            }
        });

        return future;
    }

    public StorageReport performScan() {
        final Path serverRoot = plugin.getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
        final PluginConfig config = plugin.config();
        final DiskSpaceInfo diskSpace = DiskSpaceInfo.fromPath(serverRoot);

        final Set<Path> accountedPaths = new HashSet<>();

        // 1. Scan worlds
        final StorageNode worldsNode = scanWorlds(serverRoot, accountedPaths);

        // 2. Scan plugins
        final StorageNode pluginsNode = scanPlugins(serverRoot, accountedPaths);

        // 3. Scan backups directory
        final StorageNode backupsNode = scanBackups(config.backupDirectory(), accountedPaths);

        // 4. Scan other directories and root files
        final List<StorageNode> otherDirs = new ArrayList<>();
        if (backupsNode != null) {
            otherDirs.add(backupsNode);
        }

        StorageNode rootFilesNode = null;
        try (Stream<Path> stream = Files.list(serverRoot)) {
            final List<Path> entries = stream.toList();

            long rootFilesTotalBytes = 0L;
            long rootFilesCount = 0L;

            for (final Path entry : entries) {
                final Path normalized = entry.toAbsolutePath().normalize();
                if (accountedPaths.contains(normalized)) {
                    continue;
                }

                if (Files.isDirectory(normalized)) {
                    final String dirName = normalized.getFileName().toString();
                    // Skip hidden / system dirs like .git if present
                    if (dirName.startsWith(".git") || dirName.equals(".idea") || dirName.equals(".gradle")) {
                        continue;
                    }
                    final long size = calculateDirectorySize(normalized);
                    final StorageNode dirNode = new StorageNode(dirName, normalized, size);
                    otherDirs.add(dirNode);
                    accountedPaths.add(normalized);
                } else if (Files.isRegularFile(normalized)) {
                    try {
                        rootFilesTotalBytes += Files.size(normalized);
                        rootFilesCount++;
                    } catch (final IOException ignored) {
                    }
                }
            }

            if (rootFilesCount > 0) {
                rootFilesNode = new StorageNode(
                        "root files",
                        serverRoot,
                        rootFilesTotalBytes,
                        rootFilesCount + " files"
                );
                rootFilesNode.addDetail("File count", String.valueOf(rootFilesCount));
            }
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Unable to list server root entries", exception);
        }

        otherDirs.sort((a, b) -> Long.compare(b.sizeBytes(), a.sizeBytes()));

        long totalServerBytes = worldsNode.sizeBytes() + pluginsNode.sizeBytes();
        for (final StorageNode node : otherDirs) {
            totalServerBytes += node.sizeBytes();
        }
        if (rootFilesNode != null) {
            totalServerBytes += rootFilesNode.sizeBytes();
        }

        return new StorageReport(
                Instant.now(),
                diskSpace,
                worldsNode,
                pluginsNode,
                otherDirs,
                rootFilesNode,
                totalServerBytes
        );
    }

    private StorageNode scanWorlds(final Path serverRoot, final Set<Path> accountedPaths) {
        final StorageNode worldsNode = new StorageNode("worlds", null, 0L);
        final Set<Path> worldPaths = new HashSet<>();

        // Add loaded worlds
        for (final World world : plugin.getServer().getWorlds()) {
            final Path worldPath = world.getWorldFolder().toPath().toAbsolutePath().normalize();
            worldPaths.add(worldPath);
            accountedPaths.add(worldPath);

            final long size = calculateDirectorySize(worldPath);
            final String envName = formatEnvironment(world.getEnvironment());
            final StorageNode worldNode = new StorageNode(world.getName(), worldPath, size, envName);
            worldNode.addDetail("Environment", envName);
            worldNode.addDetail("Path", serverRoot.relativize(worldPath).toString().replace('\\', '/'));

            // Check for dimensions directory inside world (modern 26.1+ layout)
            final Path dimensionsDir = worldPath.resolve("dimensions");
            if (Files.isDirectory(dimensionsDir)) {
                scanDimensions(dimensionsDir, worldNode, worldPath);
            }

            worldsNode.addChild(worldNode);
        }

        // Check for unloaded worlds in serverRoot or worlds container
        final Path container = plugin.getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
        scanUnloadedWorldsIn(container, worldPaths, worldsNode, accountedPaths, serverRoot);
        if (!container.equals(serverRoot)) {
            scanUnloadedWorldsIn(serverRoot, worldPaths, worldsNode, accountedPaths, serverRoot);
        }

        final Path worldsSubdir = serverRoot.resolve("worlds");
        if (Files.isDirectory(worldsSubdir) && !container.equals(worldsSubdir)) {
            accountedPaths.add(worldsSubdir);
            scanUnloadedWorldsIn(worldsSubdir, worldPaths, worldsNode, accountedPaths, serverRoot);
        }

        long totalWorldsBytes = 0L;
        for (final StorageNode child : worldsNode.children()) {
            totalWorldsBytes += child.sizeBytes();
        }
        worldsNode.setSizeBytes(totalWorldsBytes);
        worldsNode.sortChildrenBySizeDescending();

        return worldsNode;
    }

    private void scanUnloadedWorldsIn(
            final Path directory,
            final Set<Path> worldPaths,
            final StorageNode worldsNode,
            final Set<Path> accountedPaths,
            final Path serverRoot
    ) {
        if (!Files.isDirectory(directory)) {
            return;
        }

        try (Stream<Path> stream = Files.list(directory)) {
            for (final Path candidate : stream.filter(Files::isDirectory).toList()) {
                final Path normalized = candidate.toAbsolutePath().normalize();
                if (worldPaths.contains(normalized)) {
                    continue;
                }

                if (Files.exists(normalized.resolve("level.dat"))) {
                    worldPaths.add(normalized);
                    accountedPaths.add(normalized);

                    final long size = calculateDirectorySize(normalized);
                    final String name = normalized.getFileName().toString();
                    final StorageNode worldNode = new StorageNode(name, normalized, size, "unloaded");
                    worldNode.addDetail("Status", "Unloaded");
                    worldNode.addDetail("Path", serverRoot.relativize(normalized).toString().replace('\\', '/'));
                    worldsNode.addChild(worldNode);
                }
            }
        } catch (final IOException ignored) {
        }
    }

    private void scanDimensions(final Path dimensionsDir, final StorageNode parentWorldNode, final Path worldRoot) {
        try (Stream<Path> stream = Files.walk(dimensionsDir, 3)) {
            final List<Path> dimensionDirs = stream
                    .filter(Files::isDirectory)
                    .filter(dir -> Files.isDirectory(dir.resolve("region")))
                    .toList();

            for (final Path dimDir : dimensionDirs) {
                final long dimSize = calculateDirectorySize(dimDir);
                final String rel = worldRoot.relativize(dimDir).toString().replace('\\', '/');
                final StorageNode dimNode = new StorageNode(dimDir.getFileName().toString(), dimDir, dimSize, rel);
                parentWorldNode.addChild(dimNode);
            }
        } catch (final IOException ignored) {
        }
    }

    private StorageNode scanPlugins(final Path serverRoot, final Set<Path> accountedPaths) {
        final Path pluginsDir = serverRoot.resolve("plugins").toAbsolutePath().normalize();
        accountedPaths.add(pluginsDir);

        if (!Files.isDirectory(pluginsDir)) {
            return new StorageNode("plugins", pluginsDir, 0L);
        }

        final long totalPluginsBytes = calculateDirectorySize(pluginsDir);
        final StorageNode pluginsNode = new StorageNode("plugins", pluginsDir, totalPluginsBytes);

        final Set<Path> claimedInPlugins = new HashSet<>();

        // 1. Scan loaded plugins
        for (final Plugin p : plugin.getServer().getPluginManager().getPlugins()) {
            final String pluginName = p.getName();
            final Path dataFolder = p.getDataFolder().toPath().toAbsolutePath().normalize();

            long dataSize = 0L;
            if (Files.isDirectory(dataFolder)) {
                dataSize = calculateDirectorySize(dataFolder);
                claimedInPlugins.add(dataFolder);
            }

            long jarSize = 0L;
            Path jarPath = findPluginJar(p, pluginsDir);
            if (jarPath != null && Files.isRegularFile(jarPath)) {
                jarPath = jarPath.toAbsolutePath().normalize();
                try {
                    jarSize = Files.size(jarPath);
                    claimedInPlugins.add(jarPath);
                } catch (final IOException ignored) {
                }
            }

            final long pluginTotal = dataSize + jarSize;
            final StorageNode pluginNode = new StorageNode(pluginName, dataFolder, pluginTotal);

            if (dataSize > 0) {
                pluginNode.addDetail("Data folder", BackupManager.formatSize(dataSize));
            }
            if (jarSize > 0) {
                pluginNode.addDetail("Jar file", BackupManager.formatSize(jarSize));
            }
            try {
                final String version = p.getPluginMeta().getVersion();
                if (version != null && !version.isBlank()) {
                    pluginNode.addDetail("Version", version);
                }
            } catch (final Throwable ignored) {
            }

            pluginsNode.addChild(pluginNode);
        }

        // 2. Scan unclaimed directories and jars in plugins/
        try (Stream<Path> stream = Files.list(pluginsDir)) {
            for (final Path entry : stream.toList()) {
                final Path normalized = entry.toAbsolutePath().normalize();
                if (claimedInPlugins.contains(normalized)) {
                    continue;
                }

                final String fileName = normalized.getFileName().toString();
                if (Files.isDirectory(normalized)) {
                    final long size = calculateDirectorySize(normalized);
                    final StorageNode unclaimedNode = new StorageNode(fileName, normalized, size, "unloaded data");
                    unclaimedNode.addDetail("Type", "Directory (unloaded / leftover)");
                    pluginsNode.addChild(unclaimedNode);
                } else if (Files.isRegularFile(normalized) && fileName.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    try {
                        final long size = Files.size(normalized);
                        final StorageNode jarNode = new StorageNode(fileName, normalized, size, "unloaded jar");
                        jarNode.addDetail("Type", "Jar file (unloaded / disabled)");
                        pluginsNode.addChild(jarNode);
                    } catch (final IOException ignored) {
                    }
                }
            }
        } catch (final IOException ignored) {
        }

        pluginsNode.sortChildrenBySizeDescending();
        return pluginsNode;
    }

    private Path findPluginJar(final Plugin p, final Path pluginsDir) {
        try {
            final CodeSource codeSource = p.getClass().getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                final URI uri = codeSource.getLocation().toURI();
                final Path path = Path.of(uri).toAbsolutePath().normalize();
                if (Files.isRegularFile(path) && path.startsWith(pluginsDir)) {
                    return path;
                }
            }
        } catch (final Throwable ignored) {
        }

        // Fallback: look for jar starting with plugin name in plugins dir
        final String nameLower = p.getName().toLowerCase(Locale.ROOT);
        try (Stream<Path> stream = Files.list(pluginsDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        final String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return fileName.endsWith(".jar") && (fileName.equals(nameLower + ".jar") || fileName.startsWith(nameLower + "-"));
                    })
                    .findFirst()
                    .orElse(null);
        } catch (final IOException ignored) {
            return null;
        }
    }

    private StorageNode scanBackups(final Path backupDir, final Set<Path> accountedPaths) {
        final Path normalized = backupDir.toAbsolutePath().normalize();
        accountedPaths.add(normalized);

        if (!Files.isDirectory(normalized)) {
            return null;
        }

        long totalBytes = 0L;
        long backupCount = 0L;

        try (Stream<Path> stream = Files.list(normalized)) {
            for (final Path file : stream.filter(Files::isRegularFile).toList()) {
                try {
                    totalBytes += Files.size(file);
                    backupCount++;
                } catch (final IOException ignored) {
                }
            }
        } catch (final IOException ignored) {
        }

        final StorageNode backupsNode = new StorageNode(
                normalized.getFileName().toString(),
                normalized,
                totalBytes,
                backupCount + " archives"
        );
        backupsNode.addDetail("Total backups", String.valueOf(backupCount));
        backupsNode.addDetail("Path", normalized.toString().replace('\\', '/'));
        return backupsNode;
    }

    public static long calculateDirectorySize(final Path path) {
        if (!Files.exists(path)) {
            return 0L;
        }
        if (Files.isRegularFile(path)) {
            try {
                return Files.size(path);
            } catch (final IOException exception) {
                return 0L;
            }
        }

        final AtomicLong size = new AtomicLong(0L);
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                    size.addAndGet(attrs.size());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(final Path file, final IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (final IOException ignored) {
        }

        return size.get();
    }

    private static String formatEnvironment(final World.Environment environment) {
        if (environment == null) {
            return "world";
        }
        return switch (environment) {
            case NORMAL -> "overworld";
            case NETHER -> "nether";
            case THE_END -> "the_end";
            case CUSTOM -> "custom";
        };
    }
}
