package me.usainsrht.backupyourserver.backup;

import me.usainsrht.backupyourserver.config.PluginConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class CompressionCommandBuilder {

    private CompressionCommandBuilder() {
    }

    static CompressionPlan buildPlan(
            final CompressionMethod method,
            final PluginConfig.CompressionMethodSettings settings,
            final Path sourceRoot,
            final Path outputArchive,
            final Path fileListPath
    ) {
        if (usesTarExternalCompressor(method) && PlatformInfo.isWindows() && TarExecutable.isBsdtar()) {
            return buildWindowsTwoStagePlan(method, settings, sourceRoot, outputArchive, fileListPath);
        }
        return CompressionPlan.single(build(method, settings, sourceRoot, outputArchive, fileListPath));
    }

    static List<String> build(
            final CompressionMethod method,
            final PluginConfig.CompressionMethodSettings settings,
            final Path sourceRoot,
            final Path outputArchive,
            final Path fileListPath
    ) {
        return switch (method) {
            case TAR_ZSTD -> tarWithFilter(sourceRoot, outputArchive, fileListPath, List.of(
                    settings.executable(), "-T" + settings.threadCount(), "-" + settings.compressionLevel()
            ), "zst");
            case TAR_GZIP, PIGZ -> tarWithFilter(sourceRoot, outputArchive, fileListPath, List.of(
                    method == CompressionMethod.PIGZ ? settings.executable() : "gzip",
                    "-" + settings.compressionLevel()
            ), "gz");
            case LZ4 -> tarWithFilter(sourceRoot, outputArchive, fileListPath, List.of(
                    settings.executable(), "-" + settings.compressionLevel()
            ), "lz4");
            case XZ -> tarWithFilter(sourceRoot, outputArchive, fileListPath, List.of(
                    settings.executable(), "-T" + settings.threadCount(), "-" + settings.compressionLevel()
            ), "xz");
            case PIXZ -> tarWithFilter(sourceRoot, outputArchive, fileListPath, List.of(
                    settings.executable(), "-p", String.valueOf(settings.threadCount()), "-" + settings.compressionLevel()
            ), "xz");
            case SEVEN_Z, SEVEN_ZA -> sevenZip(settings, outputArchive, fileListPath);
        };
    }

    private static CompressionPlan buildWindowsTwoStagePlan(
            final CompressionMethod method,
            final PluginConfig.CompressionMethodSettings settings,
            final Path sourceRoot,
            final Path outputArchive,
            final Path fileListPath
    ) {
        final Path tempArchive = tempArchivePath(outputArchive);
        final List<String> archiveCommand = tarArchiveOnly(sourceRoot, tempArchive, fileListPath);
        final List<String> compressCommand = buildCompressCommand(method, settings, tempArchive, outputArchive);
        return CompressionPlan.twoStage(archiveCommand, compressCommand, tempArchive);
    }

    private static List<String> tarArchiveOnly(
            final Path sourceRoot,
            final Path outputArchive,
            final Path fileListPath
    ) {
        final List<String> command = new ArrayList<>();
        command.add("tar");
        command.add("--create");
        command.add("--verbose");
        command.add("-C");
        command.add(sourceRoot.toString());
        command.add("--file");
        command.add(outputArchive.toString());
        command.add("--files-from");
        command.add(fileListPath.toString());
        return command;
    }

    private static List<String> buildCompressCommand(
            final CompressionMethod method,
            final PluginConfig.CompressionMethodSettings settings,
            final Path tempArchive,
            final Path outputArchive
    ) {
        return switch (method) {
            case TAR_ZSTD -> List.of(
                    settings.executable(),
                    "-T" + settings.threadCount(),
                    "-" + settings.compressionLevel(),
                    "-f",
                    "-o", outputArchive.toString(),
                    tempArchive.toString()
            );
            case TAR_GZIP -> List.of(
                    "gzip",
                    "-" + settings.compressionLevel(),
                    "-f",
                    tempArchive.toString()
            );
            case PIGZ -> List.of(
                    settings.executable(),
                    "-p", String.valueOf(settings.threadCount()),
                    "-" + settings.compressionLevel(),
                    "-f",
                    tempArchive.toString()
            );
            case LZ4 -> List.of(
                    settings.executable(),
                    "-" + settings.compressionLevel(),
                    "-f",
                    tempArchive.toString(),
                    outputArchive.toString()
            );
            case XZ -> List.of(
                    settings.executable(),
                    "-T" + settings.threadCount(),
                    "-" + settings.compressionLevel(),
                    "-f",
                    tempArchive.toString()
            );
            case PIXZ -> List.of(
                    settings.executable(),
                    "-p", String.valueOf(settings.threadCount()),
                    "-" + settings.compressionLevel(),
                    tempArchive.toString(),
                    outputArchive.toString()
            );
            default -> throw new IllegalArgumentException("Unsupported two-stage method: " + method.id());
        };
    }

    private static Path tempArchivePath(final Path outputArchive) {
        final String fileName = outputArchive.getFileName().toString();
        final int extensionIndex = fileName.indexOf('.');
        final String baseName = extensionIndex >= 0 ? fileName.substring(0, extensionIndex) : fileName;
        return outputArchive.resolveSibling(baseName + ".tar");
    }

    private static boolean usesTarExternalCompressor(final CompressionMethod method) {
        return switch (method) {
            case TAR_ZSTD, TAR_GZIP, PIGZ, LZ4, XZ, PIXZ -> true;
            default -> false;
        };
    }

    private static List<String> tarWithFilter(
            final Path sourceRoot,
            final Path outputArchive,
            final Path fileListPath,
            final List<String> compressorArgs,
            final String tarAutoCompressFlag
    ) {
        final List<String> command = new ArrayList<>();
        command.add("tar");
        command.add("--create");
        command.add("--verbose");
        command.add("-C");
        command.add(sourceRoot.toString());
        command.add("--file");
        command.add(outputArchive.toString());
        command.add("--files-from");
        command.add(fileListPath.toString());
        command.add("--use-compress-program");
        command.add(String.join(" ", compressorArgs));
        return command;
    }

    private static List<String> sevenZip(
            final PluginConfig.CompressionMethodSettings settings,
            final Path outputArchive,
            final Path fileListPath
    ) {
        final List<String> command = new ArrayList<>();
        command.add(settings.executable());
        command.add("a");
        command.add("-bb1");
        command.add("-t7z");
        command.add("-mx=" + settings.compressionLevel());
        if (settings.threadCount() > 0) {
            command.add("-mmt=" + settings.threadCount());
        }
        if (settings.extraArgs() != null && !settings.extraArgs().isBlank()) {
            command.addAll(List.of(settings.extraArgs().split("\\s+")));
        }
        command.add(outputArchive.toString());
        command.add("@" + fileListPath.toAbsolutePath());
        return command;
    }

    static String archiveFileName(final CompressionMethod method) {
        return "backup-" + System.currentTimeMillis() + method.fileExtension();
    }
}
