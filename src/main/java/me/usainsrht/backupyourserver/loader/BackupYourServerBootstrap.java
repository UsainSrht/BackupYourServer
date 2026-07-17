package me.usainsrht.backupyourserver.loader;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import org.bukkit.plugin.java.JavaPlugin;

public final class BackupYourServerBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(final BootstrapContext context) {
        // Reserved for early bootstrap initialization.
    }

    @Override
    public JavaPlugin createPlugin(final PluginProviderContext context) {
        return new me.usainsrht.backupyourserver.BackupYourServerPlugin();
    }
}
