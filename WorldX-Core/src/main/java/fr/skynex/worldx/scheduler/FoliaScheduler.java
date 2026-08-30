package fr.skynex.worldx.scheduler;

import fr.skynex.worldx.WorldX;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.TimeUnit;

public class FoliaScheduler {

    private static boolean isFolia = false;

    static {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
        } catch (ClassNotFoundException ignored) {
            isFolia = false;
        }
    }

    public static boolean isFolia() {
        return isFolia;
    }

    public static void runTaskTimer(WorldX plugin, Runnable runnable, long delayTicks, long periodTicks) {
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                    plugin, 
                    task -> runnable.run(), 
                    delayTicks, 
                    periodTicks
            );
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    runnable.run();
                }
            }.runTaskTimer(plugin, delayTicks, periodTicks);
        }
    }

    public static void runAtLocation(WorldX plugin, Location loc, Runnable runnable) {
        if (isFolia) {
            Bukkit.getRegionScheduler().run(plugin, loc, task -> runnable.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    public static void runAsync(WorldX plugin, Runnable runnable) {
        if (isFolia) {
            Bukkit.getAsyncScheduler().runNow(plugin, task -> runnable.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        }
    }

    public static void runSync(WorldX plugin, Runnable runnable) {
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> runnable.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    public static void runLater(WorldX plugin, Runnable runnable, long ticks) {
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> runnable.run(), ticks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, ticks);
        }
    }
}
