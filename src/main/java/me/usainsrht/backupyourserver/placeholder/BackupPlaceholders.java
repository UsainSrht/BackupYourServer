package me.usainsrht.backupyourserver.placeholder;

import io.github.miniplaceholders.api.Expansion;
import me.usainsrht.backupyourserver.backup.BackupManager;
import me.usainsrht.backupyourserver.backup.BackupProgress;
import me.usainsrht.backupyourserver.backup.CompressionMethod;
import me.usainsrht.backupyourserver.util.BackupProgressFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

public final class BackupPlaceholders {

    private static final AtomicReference<BackupManager> MANAGER = new AtomicReference<>();
    private static final AtomicReference<String> STATUS = new AtomicReference<>("idle");
    private static final AtomicReference<BackupProgress> PROGRESS = new AtomicReference<>(BackupProgress.EMPTY);
    private static final AtomicReference<String> METHOD = new AtomicReference<>("-");
    private static final AtomicReference<String> LAST_ARCHIVE = new AtomicReference<>("-");

    private BackupPlaceholders() {
    }

    public static void bind(final BackupManager manager) {
        MANAGER.set(manager);
    }

    public static void reset() {
        STATUS.set("idle");
        PROGRESS.set(BackupProgress.EMPTY);
        METHOD.set("-");
    }

    public static void updateStatus(final String status) {
        STATUS.set(status);
    }

    public static void updateProgress(final double progress) {
        final BackupProgress current = PROGRESS.get();
        PROGRESS.set(new BackupProgress(
                Math.clamp(progress, 0.0D, 1.0D),
                current.backedUpFiles(),
                current.totalFiles(),
                current.backedUpBytes(),
                current.totalBytes(),
                current.currentFile(),
                current.etaSeconds()
        ));
    }

    public static void updateProgress(final BackupProgress progress) {
        if (progress == null) {
            PROGRESS.set(BackupProgress.EMPTY);
            return;
        }
        PROGRESS.set(progress);
    }

    public static void updateMethod(final CompressionMethod method) {
        METHOD.set(method == null ? "-" : method.id());
    }

    public static void updateLastArchive(final String archiveName) {
        LAST_ARCHIVE.set(archiveName == null ? "-" : archiveName);
    }

    public static double progressFraction() {
        return PROGRESS.get().fraction();
    }

    public static TagResolver resolver() {
        return Expansion.builder("backupyourserver")
                .globalPlaceholder("status", (argumentQueue, context) ->
                        Tag.selfClosingInserting(Component.text(STATUS.get())))
                .globalPlaceholder("progress", (argumentQueue, context) -> {
                    final BackupProgress progress = PROGRESS.get();
                    return Tag.selfClosingInserting(Component.text(
                            String.format(Locale.ROOT, "%.0f", progress.fraction() * 100.0D)
                    ));
                })
                .globalPlaceholder("method", (argumentQueue, context) ->
                        Tag.selfClosingInserting(Component.text(METHOD.get())))
                .globalPlaceholder("running", (argumentQueue, context) -> {
                    final BackupManager manager = MANAGER.get();
                    return Tag.selfClosingInserting(Component.text(String.valueOf(manager != null && manager.isRunning())));
                })
                .globalPlaceholder("last_archive", (argumentQueue, context) ->
                        Tag.selfClosingInserting(Component.text(LAST_ARCHIVE.get())))
                .globalPlaceholder("eta", (argumentQueue, context) -> {
                    final BackupProgress progress = PROGRESS.get();
                    return Tag.selfClosingInserting(Component.text(
                            BackupProgressFormatter.formatEtaClock(progress.etaSeconds())
                    ));
                })
                .globalPlaceholder("eta_long", (argumentQueue, context) -> {
                    final BackupProgress progress = PROGRESS.get();
                    return Tag.selfClosingInserting(Component.text(
                            BackupProgressFormatter.formatEtaLong(progress.etaSeconds())
                    ));
                })
                .globalPlaceholder("files", (argumentQueue, context) -> {
                    final BackupProgress progress = PROGRESS.get();
                    return Tag.selfClosingInserting(Component.text(
                            BackupProgressFormatter.formatFiles(progress.backedUpFiles(), progress.totalFiles())
                    ));
                })
                .globalPlaceholder("sizes", (argumentQueue, context) -> {
                    final BackupProgress progress = PROGRESS.get();
                    return Tag.selfClosingInserting(Component.text(
                            BackupProgressFormatter.formatSizes(progress.remainingBytes(), progress.backedUpBytes())
                    ));
                })
                .globalPlaceholder("current_file", (argumentQueue, context) -> {
                    final BackupProgress progress = PROGRESS.get();
                    return Tag.selfClosingInserting(Component.text(
                            BackupProgressFormatter.shortPath(progress.currentFile())
                    ));
                })
                .build()
                .globalPlaceholders();
    }
}
