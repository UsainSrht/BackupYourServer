package me.usainsrht.backupyourserver.storage;

import me.usainsrht.backupyourserver.backup.BackupManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class StorageRenderer {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private StorageRenderer() {
    }

    public static void renderOverview(
            final CommandSender sender,
            final StorageReport report,
            final boolean expanded
    ) {
        final DiskSpaceInfo disk = report.diskSpace();

        sender.sendMessage(MINI_MESSAGE.deserialize(
                "<gold><bold>Disk & Server Storage</bold></gold>"
        ));

        sender.sendMessage(MINI_MESSAGE.deserialize(
                "<gray>Storage Drive: <white>" + escapeMiniMessage(disk.driveName())
                        + " <dark_gray>(<white>" + escapeMiniMessage(disk.fileSystemType()) + "<dark_gray>)</dark_gray>"
        ));

        sender.sendMessage(MINI_MESSAGE.deserialize(
                "<gray>Disk Space: <white>" + disk.formatUsed()
                        + " <gray>/ <white>" + disk.formatTotal()
                        + " <dark_gray>(<yellow>" + String.format(Locale.ROOT, "%.1f", disk.usedPercent()) + "% used<dark_gray>)</dark_gray>"
        ));

        sender.sendMessage(MINI_MESSAGE.deserialize(
                "<gray>Available: <green>" + disk.formatUsable()
                        + " <dark_gray>(<green>" + String.format(Locale.ROOT, "%.1f", disk.freePercent()) + "% free<dark_gray>)</dark_gray>"
        ));

        sender.sendMessage(MINI_MESSAGE.deserialize(
                disk.renderProgressBar(16)
                        + " <dark_gray>| <gray>Server root: <white>" + report.formatTotalServerSize() + "</dark_gray>"
        ));

        sender.sendMessage(Component.empty());
        sender.sendMessage(MINI_MESSAGE.deserialize("<gold><bold>Server Directory Tree:</bold></gold>"));

        final List<StorageNode> topNodes = new ArrayList<>();
        topNodes.add(report.worldsNode());
        topNodes.add(report.pluginsNode());
        topNodes.addAll(report.otherDirectories());
        if (report.rootFilesNode() != null) {
            topNodes.add(report.rootFilesNode());
        }

        for (int i = 0; i < topNodes.size(); i++) {
            final StorageNode node = topNodes.get(i);
            final boolean isLast = (i == topNodes.size() - 1);
            final String branchPrefix = isLast ? "└── " : "├── ";
            final String childIndent = isLast ? "    " : "│   ";

            renderTopLevelNode(sender, node, branchPrefix, childIndent, expanded);
        }
    }

    private static void renderTopLevelNode(
            final CommandSender sender,
            final StorageNode node,
            final String branchPrefix,
            final String childIndent,
            final boolean expanded
    ) {
        final StringBuilder line = new StringBuilder();
        line.append("<gray>").append(branchPrefix).append("</gray>");
        line.append("<white><bold>").append(escapeMiniMessage(node.name())).append("</bold></white>");
        line.append(" <dark_gray>(<yellow>").append(node.formatSize()).append("</yellow>");
        if (node.description() != null && !node.description().isBlank()) {
            line.append("<dark_gray>, <white>").append(escapeMiniMessage(node.description())).append("</white>");
        }
        line.append("<dark_gray>)</dark_gray>");

        Component component = MINI_MESSAGE.deserialize(line.toString());
        final Component hover = buildNodeHover(node);
        if (hover != null) {
            component = component.hoverEvent(HoverEvent.showText(hover));
        }
        sender.sendMessage(component);

        if (!node.hasChildren()) {
            return;
        }

        final List<StorageNode> children = node.children();
        final int maxDisplay = (expanded || node.name().equalsIgnoreCase("worlds")) ? children.size() : Math.min(children.size(), 10);
        final boolean hasMore = !expanded && children.size() > maxDisplay;

        for (int j = 0; j < maxDisplay; j++) {
            final StorageNode child = children.get(j);
            final boolean isLastChild = (j == maxDisplay - 1 && !hasMore);
            final String childBranch = childIndent + (isLastChild ? "└── " : "├── ");

            renderChildNode(sender, child, childBranch, childIndent + (isLastChild ? "    " : "│   "));
        }

        if (hasMore) {
            final int remaining = children.size() - maxDisplay;
            final String viewAllCommand = "/backup storage " + node.name().toLowerCase(Locale.ROOT);
            final Component moreComponent = MINI_MESSAGE.deserialize(
                    "<gray>" + childIndent + "└── </gray><aqua><underlined>[+"
                            + remaining + " more " + escapeMiniMessage(node.name())
                            + ", click to view all]</underlined></aqua>"
            ).clickEvent(ClickEvent.runCommand(viewAllCommand))
             .hoverEvent(HoverEvent.showText(MINI_MESSAGE.deserialize(
                     "<gray>Click to run <white>" + escapeMiniMessage(viewAllCommand) + "</white>"
             )));
            sender.sendMessage(moreComponent);
        }
    }

    private static void renderChildNode(
            final CommandSender sender,
            final StorageNode node,
            final String branchPrefix,
            final String subChildIndent
    ) {
        final StringBuilder line = new StringBuilder();
        line.append("<gray>").append(branchPrefix).append("</gray>");
        line.append("<white>").append(escapeMiniMessage(node.name())).append("</white>");
        if (node.description() != null && !node.description().isBlank()) {
            line.append(" <dark_gray>(<gray>").append(escapeMiniMessage(node.description())).append("</gray>)</dark_gray>");
        }
        line.append(" <dark_gray>- </dark_gray><yellow>").append(node.formatSize()).append("</yellow>");

        Component component = MINI_MESSAGE.deserialize(line.toString());
        final Component hover = buildNodeHover(node);
        if (hover != null) {
            component = component.hoverEvent(HoverEvent.showText(hover));
        }
        sender.sendMessage(component);

        if (node.hasChildren()) {
            final List<StorageNode> subChildren = node.children();
            for (int k = 0; k < subChildren.size(); k++) {
                final StorageNode sub = subChildren.get(k);
                final boolean isLastSub = (k == subChildren.size() - 1);
                renderChildNode(
                        sender,
                        sub,
                        subChildIndent + (isLastSub ? "└── " : "├── "),
                        subChildIndent + (isLastSub ? "    " : "│   ")
                );
            }
        }
    }

    public static void renderPluginsBreakdown(final CommandSender sender, final StorageReport report) {
        final StorageNode pluginsNode = report.pluginsNode();
        final List<StorageNode> children = pluginsNode.children();

        sender.sendMessage(MINI_MESSAGE.deserialize(
                "<gold><bold>Plugins Storage Breakdown</bold></gold> <dark_gray>(Total: <yellow>"
                        + pluginsNode.formatSize() + "<dark_gray>, <white>"
                        + children.size() + " items<dark_gray>)</dark_gray>"
        ));

        if (children.isEmpty()) {
            sender.sendMessage(MINI_MESSAGE.deserialize("<gray>No plugins found.</gray>"));
            return;
        }

        for (final StorageNode pluginNode : children) {
            final double percent = pluginsNode.sizeBytes() > 0
                    ? (pluginNode.sizeBytes() * 100.0D / pluginsNode.sizeBytes())
                    : 0.0D;

            final StringBuilder line = new StringBuilder();
            line.append("<gray>- <white>").append(escapeMiniMessage(pluginNode.name())).append("</white>");
            line.append(" <dark_gray>- <yellow>").append(pluginNode.formatSize()).append("</yellow>");
            line.append(" <dark_gray>(<gray>")
                    .append(String.format(Locale.ROOT, "%.1f", percent))
                    .append("%</gray>");

            final String dataFolder = pluginNode.details().get("Data folder");
            final String jarFile = pluginNode.details().get("Jar file");
            if (dataFolder != null && jarFile != null) {
                line.append("<dark_gray>, <gray>data: <white>").append(dataFolder)
                        .append("</white>, jar: <white>").append(jarFile).append("</white>");
            }
            line.append("<dark_gray>)</dark_gray>");

            Component component = MINI_MESSAGE.deserialize(line.toString());
            final Component hover = buildNodeHover(pluginNode);
            if (hover != null) {
                component = component.hoverEvent(HoverEvent.showText(hover));
            }
            sender.sendMessage(component);
        }
    }

    public static void renderWorldsBreakdown(final CommandSender sender, final StorageReport report) {
        final StorageNode worldsNode = report.worldsNode();
        final List<StorageNode> worlds = worldsNode.children();

        sender.sendMessage(MINI_MESSAGE.deserialize(
                "<gold><bold>Worlds Storage Breakdown</bold></gold> <dark_gray>(Total: <yellow>"
                        + worldsNode.formatSize() + "<dark_gray>, <white>"
                        + worlds.size() + " worlds<dark_gray>)</dark_gray>"
        ));

        if (worlds.isEmpty()) {
            sender.sendMessage(MINI_MESSAGE.deserialize("<gray>No worlds found.</gray>"));
            return;
        }

        for (final StorageNode world : worlds) {
            final double percent = worldsNode.sizeBytes() > 0
                    ? (world.sizeBytes() * 100.0D / worldsNode.sizeBytes())
                    : 0.0D;

            final StringBuilder line = new StringBuilder();
            line.append("<gray>- <white>").append(escapeMiniMessage(world.name())).append("</white>");
            if (world.description() != null && !world.description().isBlank()) {
                line.append(" <dark_gray>(<gray>").append(escapeMiniMessage(world.description())).append("</gray>)</dark_gray>");
            }
            line.append(" <dark_gray>- <yellow>").append(world.formatSize()).append("</yellow>");
            line.append(" <dark_gray>(<gray>")
                    .append(String.format(Locale.ROOT, "%.1f", percent))
                    .append("% of worlds</gray><dark_gray>)</dark_gray>");

            Component component = MINI_MESSAGE.deserialize(line.toString());
            final Component hover = buildNodeHover(world);
            if (hover != null) {
                component = component.hoverEvent(HoverEvent.showText(hover));
            }
            sender.sendMessage(component);

            if (world.hasChildren()) {
                for (final StorageNode child : world.children()) {
                    final Component sub = MINI_MESSAGE.deserialize(
                            "<gray>  ├── <white>" + escapeMiniMessage(child.name())
                                    + " <dark_gray>- <yellow>" + child.formatSize()
                                    + "</yellow> <dark_gray>(" + escapeMiniMessage(child.description() != null ? child.description() : "") + ")</dark_gray>"
                    );
                    sender.sendMessage(sub);
                }
            }
        }
    }

    private static Component buildNodeHover(final StorageNode node) {
        if (node.details().isEmpty() && node.path() == null) {
            return null;
        }

        Component hover = Component.text(node.name(), NamedTextColor.GOLD);
        if (node.description() != null && !node.description().isBlank()) {
            hover = hover.append(Component.text(" (" + node.description() + ")", NamedTextColor.GRAY));
        }

        hover = hover.append(Component.newline())
                .append(Component.text("Size: ", NamedTextColor.GRAY))
                .append(Component.text(node.formatSize(), NamedTextColor.YELLOW));

        if (node.path() != null) {
            hover = hover.append(Component.newline())
                    .append(Component.text("Path: ", NamedTextColor.GRAY))
                    .append(Component.text(node.path().toString().replace('\\', '/'), NamedTextColor.WHITE));
        }

        for (final Map.Entry<String, String> entry : node.details().entrySet()) {
            if ("Path".equalsIgnoreCase(entry.getKey())) {
                continue;
            }
            hover = hover.append(Component.newline())
                    .append(Component.text(entry.getKey() + ": ", NamedTextColor.GRAY))
                    .append(Component.text(entry.getValue(), NamedTextColor.WHITE));
        }

        return hover;
    }

    private static String escapeMiniMessage(final String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\").replace("<", "\\<");
    }
}
