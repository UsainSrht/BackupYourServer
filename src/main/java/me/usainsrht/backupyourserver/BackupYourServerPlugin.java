package me.usainsrht.backupyourserver;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.usainsrht.backupyourserver.backup.BackupManager;
import me.usainsrht.backupyourserver.bossbar.BackupBossBarService;
import me.usainsrht.backupyourserver.command.BackupCommand;
import me.usainsrht.backupyourserver.config.PluginConfig;
import me.usainsrht.backupyourserver.metrics.PluginMetrics;
import me.usainsrht.backupyourserver.placeholder.BackupPlaceholders;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.List;

public final class BackupYourServerPlugin extends JavaPlugin {

    private static BackupYourServerPlugin instance;

    private PluginConfig pluginConfig;
    private BackupManager backupManager;
    private BackupBossBarService bossBarService;

    public static BackupYourServerPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        reloadLocalConfig();

        backupManager = new BackupManager(this);
        BackupPlaceholders.bind(backupManager);

        bossBarService = new BackupBossBarService(this);
        bossBarService.reload(pluginConfig.bossBarSettings());
        bossBarService.startUpdater();

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(
                    BackupCommand.build(this).build(),
                    "Manage asynchronous server backups",
                    List.of("bys", "backup")
            );
        });

        PluginMetrics.register(this);
        getLogger().info("BackupYourServer enabled.");
    }

    @Override
    public void onDisable() {
        if (backupManager != null && backupManager.isRunning()) {
            backupManager.stop();
        }
        if (bossBarService != null) {
            bossBarService.hideAll();
        }
        instance = null;
    }

    public void reloadLocalConfig() {
        reloadConfig();
        final FileConfiguration configuration = getConfig();
        final Path serverRoot = getServer().getWorldContainer().toPath();
        pluginConfig = PluginConfig.load(configuration, serverRoot, getLogger());
        if (bossBarService != null) {
            bossBarService.reload(pluginConfig.bossBarSettings());
        }
    }

    public PluginConfig config() {
        return pluginConfig;
    }

    public BackupManager backupManager() {
        return backupManager;
    }

    public BackupBossBarService bossBarService() {
        return bossBarService;
    }
}
