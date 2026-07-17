package me.usainsrht.backupyourserver.backup;

import org.bukkit.World;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Resolves world-related paths for Minecraft 26.1+ and legacy layouts.
 */
public final class WorldPathResolver {

    private WorldPathResolver() {
    }

    public static List<Path> resolveWorldRoots(final Path serverRoot, final Logger logger) {
        final List<Path> roots = new ArrayList<>();
        final Path primaryWorld = serverRoot.resolve("world");

        if (Files.isDirectory(primaryWorld)) {
            roots.add(primaryWorld);

            final Path dimensionsRoot = primaryWorld.resolve("dimensions");
            if (Files.isDirectory(dimensionsRoot)) {
                logger.info("Detected Minecraft 26.1+ world layout under " + dimensionsRoot);
            } else {
                logger.info("Detected legacy world layout under " + primaryWorld);
            }
        }

        addIfDirectory(roots, serverRoot.resolve("world_nether"));
        addIfDirectory(roots, serverRoot.resolve("world_the_end"));

        return roots;
    }

    public static Path resolveWorldFolder(final World world) {
        return world.getWorldFolder().toPath();
    }

    public static boolean isDimensionPath(final Path worldRoot, final Path candidate) {
        final Path normalizedWorld = worldRoot.normalize();
        final Path normalizedCandidate = candidate.normalize();
        return normalizedCandidate.startsWith(normalizedWorld.resolve("dimensions"));
    }

    public static boolean isPlayerDataPath(final Path worldRoot, final Path candidate) {
        final Path normalizedWorld = worldRoot.normalize();
        final Path normalizedCandidate = candidate.normalize();
        if (normalizedCandidate.startsWith(normalizedWorld.resolve("players"))) {
            return true;
        }
        return normalizedCandidate.getFileName() != null
                && normalizedCandidate.toString().replace('\\', '/').contains("/playerdata/");
    }

    private static void addIfDirectory(final List<Path> roots, final Path path) {
        if (Files.isDirectory(path)) {
            roots.add(path);
        }
    }
}
