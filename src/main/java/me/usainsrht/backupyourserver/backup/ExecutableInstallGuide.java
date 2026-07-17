package me.usainsrht.backupyourserver.backup;

import me.usainsrht.backupyourserver.config.PluginConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ExecutableInstallGuide {

    private ExecutableInstallGuide() {
    }

    public static boolean isReady(
            final CompressionMethod method,
            final PluginConfig.CompressionMethodSettings settings
    ) {
        return missingRequirements(method, settings).isEmpty();
    }

    public static List<String> missingRequirements(
            final CompressionMethod method,
            final PluginConfig.CompressionMethodSettings settings
    ) {
        final List<String> missing = new ArrayList<>();
        if (settings == null) {
            return missing;
        }

        if (!ExecutableLocator.isAvailable(settings.executable())) {
            missing.add(settings.executable());
        }

        if (method != CompressionMethod.SEVEN_Z && method != CompressionMethod.SEVEN_ZA
                && !ExecutableLocator.isAvailable("tar")) {
            missing.add("tar");
        }

        return missing;
    }

    public static String missingExecutableMessage(final String executable) {
        return "Missing executable: " + executable
                + "\nPlatform: " + PlatformInfo.description()
                + "\nHow to install:\n  " + installInstructions(executable).replace("\n", "\n  ");
    }

    public static String missingRequirementsMessage(
            final CompressionMethod method,
            final PluginConfig.CompressionMethodSettings settings
    ) {
        final List<String> missing = missingRequirements(method, settings);
        if (missing.isEmpty()) {
            return "";
        }

        final StringBuilder builder = new StringBuilder();
        builder.append("Backup method ").append(method.id()).append(" cannot run on this server.")
                .append("\nPlatform: ").append(PlatformInfo.description());

        for (final String executable : missing) {
            builder.append("\n\n").append(missingExecutableMessage(executable));
        }

        return builder.toString();
    }

    public static String installInstructions(final String executable) {
        final String normalized = executable.toLowerCase(Locale.ROOT);
        return switch (PlatformInfo.family()) {
            case WINDOWS -> windowsInstructions(normalized);
            case DEBIAN -> debianInstructions(normalized);
            case RHEL -> rhelInstructions(normalized);
            case ARCH -> archInstructions(normalized);
            case MACOS -> macInstructions(normalized);
            case OTHER_LINUX, UNKNOWN -> genericInstructions(normalized);
        };
    }

    private static String windowsInstructions(final String executable) {
        return switch (executable) {
            case "7z", "7za" -> """
                    Download 7-Zip from https://www.7-zip.org/
                    Install it, then add the install folder to PATH (usually C:\\Program Files\\7-Zip).
                    Restart the server after updating PATH.""";
            case "tar" -> """
                    Windows 10 build 17063+ and Windows 11 include tar by default.
                    If it is missing, install Git for Windows (https://git-scm.com/download/win) or enable the Windows optional tar feature.""";
            case "zstd" -> """
                    Install with Chocolatey: choco install zstd
                    Or download a release from https://github.com/facebook/zstd/releases and add it to PATH.""";
            case "gzip", "pigz" -> """
                    Install Git for Windows (https://git-scm.com/download/win) or use WSL.
                    pigz can be installed with Chocolatey: choco install pigz""";
            case "lz4" -> """
                    Download from https://github.com/lz4/lz4/releases or install with Chocolatey: choco install lz4""";
            case "xz", "pixz" -> """
                    Install Git for Windows or WSL.
                    pixz can be installed from MSYS2: pacman -S pixz""";
            default -> """
                    Download the Windows build of """ + executable + """
                     and add its folder to PATH, then restart the server.""";
        };
    }

    private static String debianInstructions(final String executable) {
        return switch (executable) {
            case "7z", "7za" -> "sudo apt update && sudo apt install p7zip-full";
            case "tar" -> "sudo apt update && sudo apt install tar";
            case "zstd" -> "sudo apt update && sudo apt install zstd";
            case "gzip", "pigz" -> "sudo apt update && sudo apt install gzip pigz";
            case "lz4" -> "sudo apt update && sudo apt install lz4";
            case "xz", "pixz" -> "sudo apt update && sudo apt install xz-utils pixz";
            default -> "sudo apt update && sudo apt install " + executable;
        };
    }

    private static String rhelInstructions(final String executable) {
        return switch (executable) {
            case "7z", "7za" -> "sudo dnf install p7zip p7zip-plugins";
            case "tar" -> "sudo dnf install tar";
            case "zstd" -> "sudo dnf install zstd";
            case "gzip", "pigz" -> "sudo dnf install gzip pigz";
            case "lz4" -> "sudo dnf install lz4";
            case "xz", "pixz" -> "sudo dnf install xz pixz";
            default -> "sudo dnf install " + executable;
        };
    }

    private static String archInstructions(final String executable) {
        return switch (executable) {
            case "7z", "7za" -> "sudo pacman -S p7zip";
            case "tar" -> "sudo pacman -S tar";
            case "zstd" -> "sudo pacman -S zstd";
            case "gzip", "pigz" -> "sudo pacman -S gzip pigz";
            case "lz4" -> "sudo pacman -S lz4";
            case "xz", "pixz" -> "sudo pacman -S xz pixz";
            default -> "sudo pacman -S " + executable;
        };
    }

    private static String macInstructions(final String executable) {
        return switch (executable) {
            case "7z", "7za" -> "brew install p7zip";
            case "tar" -> "tar is included with macOS. If missing, reinstall Xcode Command Line Tools: xcode-select --install";
            case "zstd" -> "brew install zstd";
            case "gzip", "pigz" -> "brew install pigz";
            case "lz4" -> "brew install lz4";
            case "xz", "pixz" -> "brew install xz pixz";
            default -> "brew install " + executable;
        };
    }

    private static String genericInstructions(final String executable) {
        return switch (executable) {
            case "7z", "7za" -> """
                    Install p7zip using your distro package manager.
                    Example: sudo apt install p7zip-full (Ubuntu/Debian) or sudo dnf install p7zip (Fedora).""";
            case "tar" -> "Install tar with your distro package manager (usually pre-installed on Linux).";
            case "zstd" -> "Install zstd with your distro package manager.";
            default -> "Install " + executable + " with your system package manager and ensure it is on PATH.";
        };
    }
}
