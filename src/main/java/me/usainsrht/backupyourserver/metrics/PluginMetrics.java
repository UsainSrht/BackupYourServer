package me.usainsrht.backupyourserver.metrics;

import me.usainsrht.backupyourserver.BackupYourServerPlugin;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;

public final class PluginMetrics {

    private PluginMetrics() {
    }

    public static void register(final BackupYourServerPlugin plugin) {
        final Metrics metrics = new Metrics(plugin, 32689);

        metrics.addCustomChart(new SimplePie("folia_supported", () -> "true"));
    }
}
