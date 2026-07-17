package me.usainsrht.backupyourserver.bossbar;

import io.github.miniplaceholders.api.MiniPlaceholders;
import me.usainsrht.backupyourserver.config.PluginConfig;
import me.usainsrht.backupyourserver.placeholder.BackupPlaceholders;
import me.usainsrht.backupyourserver.util.SchedulerUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;

public final class BackupBossBarService {

    private final JavaPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Set<Player> viewers = new HashSet<>();
    private BossBar bossBar;
    private PluginConfig.BossBarSettings settings;
    private volatile boolean active;

    public BackupBossBarService(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void setActive(final boolean active) {
        this.active = active;
        if (!active) {
            hideAll();
        } else {
            refreshDisplay();
        }
    }

    public boolean isActive() {
        return active;
    }

    public void reload(final PluginConfig.BossBarSettings settings) {
        this.settings = settings;
        setActive(false);
        bossBar = BossBar.bossBar(
                Component.empty(),
                0.0F,
                settings.color(),
                settings.style()
        );
    }

    public void showProgress(final double progress) {
        BackupPlaceholders.updateProgress(progress);
        refreshDisplay();
    }

    public void hideAll() {
        active = false;
        if (bossBar == null) {
            viewers.clear();
            return;
        }
        for (final Player player : viewers) {
            player.hideBossBar(bossBar);
        }
        viewers.clear();
    }

    public void startUpdater() {
        SchedulerUtil.runGlobalRepeating(plugin, this::syncViewers, 20L, 20L);
    }

    private void syncViewers() {
        if (!active || settings == null || !settings.enabled() || bossBar == null) {
            return;
        }

        refreshDisplay();

        for (final Player player : Bukkit.getOnlinePlayers()) {
            final boolean allowed = player.hasPermission(settings.permission());
            final boolean viewing = viewers.contains(player);
            if (allowed && !viewing) {
                player.showBossBar(bossBar);
                viewers.add(player);
            } else if (!allowed && viewing) {
                player.hideBossBar(bossBar);
                viewers.remove(player);
            }
        }
    }

    private void refreshDisplay() {
        if (!active || settings == null || !settings.enabled() || bossBar == null) {
            return;
        }

        bossBar.progress((float) Math.clamp(BackupPlaceholders.progressFraction(), 0.0D, 1.0D));
        updateTitle();

        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission(settings.permission())) {
                continue;
            }
            if (!viewers.contains(player)) {
                player.showBossBar(bossBar);
                viewers.add(player);
            }
        }
    }

    private void updateTitle() {
        if (bossBar == null || settings == null) {
            return;
        }
        final TagResolver resolver = TagResolver.builder()
                .resolvers(BackupPlaceholders.resolver())
                .resolvers(org.bukkit.Bukkit.getPluginManager().isPluginEnabled("MiniPlaceholders")
                        ? MiniPlaceholders.globalPlaceholders()
                        : TagResolver.empty())
                .build();
        final Component title = miniMessage.deserialize(settings.text(), resolver);
        bossBar.name(title);
    }
}
