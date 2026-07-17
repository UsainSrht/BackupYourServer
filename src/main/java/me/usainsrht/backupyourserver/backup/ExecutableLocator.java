package me.usainsrht.backupyourserver.backup;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

final class ExecutableLocator {

    private ExecutableLocator() {
    }

    static boolean isAvailable(final String executable) {
        if (executable == null || executable.isBlank()) {
            return false;
        }

        if (Files.isExecutable(Path.of(executable))) {
            return true;
        }

        final String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return probeCommand(executable);
        }

        for (final String directory : pathEnv.split(File.pathSeparator)) {
            final Path candidate = Path.of(directory, executable);
            if (Files.isExecutable(candidate)) {
                return true;
            }
            if (isWindows() && Files.isExecutable(Path.of(directory, executable + ".exe"))) {
                return true;
            }
        }

        return probeCommand(executable);
    }

    private static boolean probeCommand(final String executable) {
        try {
            final ProcessBuilder builder = isWindows()
                    ? new ProcessBuilder("where", executable)
                    : new ProcessBuilder("which", executable);
            builder.redirectErrorStream(true);
            final Process process = builder.start();
            final boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (final IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
