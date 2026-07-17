package me.usainsrht.backupyourserver.backup;

final class JarFilePolicy {

    static final String MANIFEST_RELATIVE_PATH = ".backupyourserver/jars-manifest.json";

    private JarFilePolicy() {
    }

    static boolean isJarFile(final String fileName) {
        return fileName.toLowerCase().endsWith(".jar");
    }

    static boolean shouldDocumentInManifest(final String relativePath) {
        final String normalized = normalizeRelative(relativePath);

        if (isRootLevelJar(normalized)) {
            return true;
        }

        return normalized.startsWith("plugins/") && normalized.endsWith(".jar");
    }

    static String jarCategory(final String relativePath) {
        final String normalized = normalizeRelative(relativePath);
        if (isRootLevelJar(normalized)) {
            return "server";
        }
        return "plugin";
    }

    private static boolean isRootLevelJar(final String normalizedRelativePath) {
        return !normalizedRelativePath.contains("/") && normalizedRelativePath.endsWith(".jar");
    }

    static String normalizeRelative(final String path) {
        return path.replace('\\', '/');
    }
}
