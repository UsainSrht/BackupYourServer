package me.usainsrht.backupyourserver;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.usainsrht.backupyourserver.backup.BackupManager;
import me.usainsrht.backupyourserver.backup.BackupRetentionService;
import me.usainsrht.backupyourserver.backup.BackupSchedulerService;
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
    private BackupRetentionService retentionService;
    private BackupSchedulerService schedulerService;
    private BackupBossBarService bossBarService;
    private me.usainsrht.backupyourserver.storage.StorageInspectionService storageService;

    public static BackupYourServerPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        reloadLocalConfig();

        backupManager = new BackupManager(this);
        retentionService = new BackupRetentionService(this, backupManager);
        schedulerService = new BackupSchedulerService(this, backupManager, retentionService);
        storageService = new me.usainsrht.backupyourserver.storage.StorageInspectionService(this);
        BackupPlaceholders.bind(backupManager);

        bossBarService = new BackupBossBarService(this);
        bossBarService.reload(pluginConfig.bossBarSettings());
        bossBarService.startUpdater();

        schedulerService.start();
        retentionService.cleanup(pluginConfig);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(
                    BackupCommand.build(this).build(),
                    "Manage asynchronous server backups",
                    List.of("bys", "backup")
            );
            event.registrar().register(
                    me.usainsrht.backupyourserver.command.StorageCommand.build(this).build(),
                    "Inspect machine and server storage tree breakdown",
                    List.of("storage", "diskspace")
            );
        });

        PluginMetrics.register(this);
        getLogger().info("BackupYourServer enabled.");
    }

    @Override
    public void onDisable() {
        if (schedulerService != null) {
            schedulerService.shutdown();
        }
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
        if (schedulerService != null) {
            schedulerService.reload(pluginConfig);
        }
        if (retentionService != null) {
            retentionService.cleanup(pluginConfig);
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

    public BackupSchedulerService schedulerService() {
        return schedulerService;
    }

    public BackupRetentionService retentionService() {
        return retentionService;
    }

    public me.usainsrht.backupyourserver.storage.StorageInspectionService storageService() {
        return storageService;
    }
}
