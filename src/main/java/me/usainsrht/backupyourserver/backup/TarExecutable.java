package me.usainsrht.backupyourserver.backup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

final class TarExecutable {

    private static volatile Boolean bsdtar;

    private TarExecutable() {
    }

    static boolean isBsdtar() {
        if (bsdtar != null) {
            return bsdtar;
        }
        synchronized (TarExecutable.class) {
            if (bsdtar == null) {
                bsdtar = detectBsdtar();
            }
            return bsdtar;
        }
    }

    private static boolean detectBsdtar() {
        try {
            final ProcessBuilder builder = new ProcessBuilder("tar", "--version");
            builder.redirectErrorStream(true);
            final Process process = builder.start();
            final String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .toLowerCase(Locale.ROOT);
            process.waitFor(5, TimeUnit.SECONDS);
            return output.contains("bsdtar") || output.contains("libarchive");
        } catch (final IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return PlatformInfo.isWindows();
        }
    }
}
