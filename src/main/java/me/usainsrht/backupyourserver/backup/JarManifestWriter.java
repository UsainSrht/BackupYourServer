package me.usainsrht.backupyourserver.backup;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.List;

final class JarManifestWriter {

    private JarManifestWriter() {
    }

    static void write(final Path manifestPath, final List<JarEntry> entries) throws IOException {
        Files.createDirectories(manifestPath.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(manifestPath)) {
            writer.write("{\n");
            writer.write("  \"generatedAt\": \"" + escapeJson(Instant.now().toString()) + "\",\n");
            writer.write("  \"jars\": [\n");

            for (int index = 0; index < entries.size(); index++) {
                final JarEntry entry = entries.get(index);
                writer.write("    {\n");
                writer.write("      \"path\": \"" + escapeJson(entry.relativePath()) + "\",\n");
                writer.write("      \"filename\": \"" + escapeJson(entry.fileName()) + "\",\n");
                writer.write("      \"sizeBytes\": " + entry.sizeBytes() + ",\n");
                writer.write("      \"lastModified\": \"" + escapeJson(entry.lastModified()) + "\",\n");
                writer.write("      \"category\": \"" + escapeJson(entry.category()) + "\"\n");
                writer.write("    }");
                if (index < entries.size() - 1) {
                    writer.write(",");
                }
                writer.newLine();
            }

            writer.write("  ]\n");
            writer.write("}\n");
        }
    }

    static JarEntry fromFile(final Path file, final Path sourceRoot) throws IOException {
        final String relativePath = JarFilePolicy.normalizeRelative(sourceRoot.relativize(file).toString());
        final BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
        return new JarEntry(
                relativePath,
                file.getFileName().toString(),
                attributes.size(),
                Instant.ofEpochMilli(attributes.lastModifiedTime().toMillis()).toString(),
                JarFilePolicy.jarCategory(relativePath)
        );
    }

    private static String escapeJson(final String value) {
        final StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(character);
            }
        }
        return escaped.toString();
    }

    record JarEntry(
            String relativePath,
            String fileName,
            long sizeBytes,
            String lastModified,
            String category
    ) {
    }
}
