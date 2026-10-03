package me.usainsrht.backupyourserver.storage;

import me.usainsrht.backupyourserver.backup.BackupManager;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StorageNode {

    private final String name;
    private final @Nullable Path path;
    private long sizeBytes;
    private @Nullable String description;
    private final List<StorageNode> children = new ArrayList<>();
    private final Map<String, String> details = new LinkedHashMap<>();

    public StorageNode(final String name, final @Nullable Path path, final long sizeBytes) {
        this(name, path, sizeBytes, null);
    }

    public StorageNode(
            final String name,
            final @Nullable Path path,
            final long sizeBytes,
            final @Nullable String description
    ) {
        this.name = name;
        this.path = path;
        this.sizeBytes = sizeBytes;
        this.description = description;
    }

    public String name() {
        return name;
    }

    public @Nullable Path path() {
        return path;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(final long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public @Nullable String description() {
        return description;
    }

    public void setDescription(final @Nullable String description) {
        this.description = description;
    }

    public List<StorageNode> children() {
        return Collections.unmodifiableList(children);
    }

    public void addChild(final StorageNode child) {
        this.children.add(child);
    }

    public void addChildren(final List<StorageNode> children) {
        this.children.addAll(children);
    }

    public void sortChildrenBySizeDescending() {
        this.children.sort(Comparator.comparingLong(StorageNode::sizeBytes).reversed());
        for (final StorageNode child : this.children) {
            child.sortChildrenBySizeDescending();
        }
    }

    public Map<String, String> details() {
        return Collections.unmodifiableMap(details);
    }

    public void addDetail(final String key, final String value) {
        this.details.put(key, value);
    }

    public String formatSize() {
        return BackupManager.formatSize(sizeBytes);
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }
}
