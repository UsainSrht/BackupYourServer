package me.usainsrht.backupyourserver.backup;

import java.nio.file.Path;
import java.util.List;

final class CompressionPlan {

    enum StageKind {
        ARCHIVE,
        COMPRESS
    }

    record Stage(List<String> command, StageKind kind) {
    }

    private final List<Stage> stages;
    private final Path tempArchive;

    private CompressionPlan(final List<Stage> stages, final Path tempArchive) {
        this.stages = List.copyOf(stages);
        this.tempArchive = tempArchive;
    }

    List<Stage> stages() {
        return stages;
    }

    Path tempArchive() {
        return tempArchive;
    }

    boolean multiStage() {
        return stages.size() > 1;
    }

    static CompressionPlan single(final List<String> command) {
        return new CompressionPlan(List.of(new Stage(command, StageKind.ARCHIVE)), null);
    }

    static CompressionPlan twoStage(
            final List<String> archiveCommand,
            final List<String> compressCommand,
            final Path tempArchive
    ) {
        return new CompressionPlan(
                List.of(
                        new Stage(archiveCommand, StageKind.ARCHIVE),
                        new Stage(compressCommand, StageKind.COMPRESS)
                ),
                tempArchive
        );
    }
}
