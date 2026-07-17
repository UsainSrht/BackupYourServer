package me.usainsrht.backupyourserver.backup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class PlatformInfo {

    public enum Family {
        WINDOWS,
        DEBIAN,
        RHEL,
        ARCH,
        MACOS,
        OTHER_LINUX,
        UNKNOWN
    }

    private PlatformInfo() {
    }

    public static String description() {
        if (isWindows()) {
            return detectWindowsDescription();
        }
        if (isMac()) {
            return "macOS " + System.getProperty("os.version", "");
        }
        return detectLinuxDescription();
    }

    public static Family family() {
        if (isWindows()) {
            return Family.WINDOWS;
        }
        if (isMac()) {
            return Family.MACOS;
        }

        final Map<String, String> osRelease = readOsRelease();
        final String id = osRelease.getOrDefault("ID", "").toLowerCase(Locale.ROOT);
        return switch (id) {
            case "ubuntu", "debian", "linuxmint", "pop" -> Family.DEBIAN;
            case "fedora", "rhel", "centos", "rocky", "almalinux" -> Family.RHEL;
            case "arch", "manjaro" -> Family.ARCH;
            case "" -> Family.UNKNOWN;
            default -> Family.OTHER_LINUX;
        };
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    private static String detectWindowsDescription() {
        final String osName = System.getProperty("os.name", "Windows");
        if (osName.toLowerCase(Locale.ROOT).contains("windows 11")) {
            return "Windows 11";
        }
        if (osName.toLowerCase(Locale.ROOT).contains("windows 10")) {
            return "Windows 10";
        }

        final String version = System.getProperty("os.version", "");
        if (version.startsWith("10.0")) {
            final int build = parseWindowsBuild(version);
            if (build >= 22000) {
                return "Windows 11 (build " + build + ")";
            }
            if (build > 0) {
                return "Windows 10 (build " + build + ")";
            }
        }

        return osName + (version.isBlank() ? "" : " " + version);
    }

    private static int parseWindowsBuild(final String version) {
        final String[] parts = version.split("\\.");
        if (parts.length >= 3) {
            try {
                return Integer.parseInt(parts[2]);
            } catch (final NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private static String detectLinuxDescription() {
        final Map<String, String> osRelease = readOsRelease();
        final String prettyName = osRelease.get("PRETTY_NAME");
        if (prettyName != null && !prettyName.isBlank()) {
            return prettyName;
        }

        final String name = osRelease.getOrDefault("NAME", System.getProperty("os.name", "Linux"));
        final String version = osRelease.getOrDefault("VERSION_ID", System.getProperty("os.version", ""));
        if (version.isBlank()) {
            return name;
        }
        return name + " " + version;
    }

    private static Map<String, String> readOsRelease() {
        try {
            final Path path = Path.of("/etc/os-release");
            if (!Files.isRegularFile(path)) {
                return Map.of();
            }
            return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                    .filter(line -> line.contains("=") && !line.startsWith("#"))
                    .map(line -> line.split("=", 2))
                    .filter(parts -> parts.length == 2)
                    .collect(Collectors.toMap(
                            parts -> parts[0],
                            parts -> stripQuotes(parts[1]),
                            (left, right) -> right
                    ));
        } catch (final IOException ignored) {
            return Map.of();
        }
    }

    private static String stripQuotes(final String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
