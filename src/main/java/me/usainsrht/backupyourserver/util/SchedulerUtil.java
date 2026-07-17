package me.usainsrht.backupyourserver.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;

public final class SchedulerUtil {

    private SchedulerUtil() {
    }

    public static void runGlobal(final JavaPlugin plugin, final Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
    }

    public static void runAsync(final JavaPlugin plugin, final Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
    }

    public static void runAsyncDelayed(final JavaPlugin plugin, final Runnable task, final long delayMillis) {
        Bukkit.getAsyncScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayMillis, TimeUnit.MILLISECONDS);
    }

    public static void runGlobalRepeating(
            final JavaPlugin plugin,
            final Runnable task,
            final long initialDelayTicks,
            final long periodTicks
    ) {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                scheduledTask -> task.run(),
                initialDelayTicks,
                periodTicks
        );
    }
}
