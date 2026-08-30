package fr.skynex.worldx.siege;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.integration.EconomyIntegration;
import fr.skynex.worldx.region.Region;
import fr.skynex.worldx.scheduler.FoliaScheduler;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SiegeManager {

    private final WorldX plugin;
    private final Map<String, ActiveSiege> activeSieges = new ConcurrentHashMap<>();

    public SiegeManager(WorldX plugin) {
        this.plugin = plugin;
        startSiegeTask();
    }

    public boolean startSiege(Region region, Player initiator) {
        if (region == null || initiator == null) return false;

        if (activeSieges.containsKey(region.getId())) {
            return false;
        }

        BossBar bossBar = Bukkit.createBossBar(
                "⚔️ Siège de " + region.getId() + " - Capture: 0%",
                BarColor.RED,
                BarStyle.SOLID
        );

        ActiveSiege siege = new ActiveSiege(region, initiator.getUniqueId(), bossBar);
        activeSieges.put(region.getId(), siege);

        region.setFlagValue("siege-start-time", String.valueOf(System.currentTimeMillis()));
        plugin.getDatabaseManager().saveRegion(region);

        return true;
    }

    public boolean stopSiege(String regionId) {
        ActiveSiege siege = activeSieges.remove(regionId);
        if (siege != null) {
            siege.bossBar.removeAll();
            Region region = siege.region;
            region.setFlagValue("siege-start-time", null);
            plugin.getDatabaseManager().saveRegion(region);
            return true;
        }
        return false;
    }

    public ActiveSiege getActiveSiege(String regionId) {
        return activeSieges.get(regionId);
    }

    public boolean isUnderSiege(String regionId) {
        return activeSieges.containsKey(regionId);
    }

    private void startSiegeTask() {
        FoliaScheduler.runTaskTimer(plugin, () -> {
            if (activeSieges.isEmpty()) return;

            for (ActiveSiege siege : new ArrayList<>(activeSieges.values())) {
                Region region = siege.region;
                org.bukkit.World world = Bukkit.getWorld(region.getWorldName());
                if (world == null) continue;

                List<Player> playersInRegion = new ArrayList<>();
                int attackers = 0;
                int defenders = 0;

                for (Player player : world.getPlayers()) {
                    if (region.contains(player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ())) {
                        playersInRegion.add(player);
                        if (region.isOwner(player.getUniqueId()) || region.isMember(player.getUniqueId())) {
                            defenders++;
                        } else {
                            attackers++;
                        }
                    }
                }

                // Update BossBar visibility
                siege.bossBar.removeAll();
                for (Player p : playersInRegion) {
                    siege.bossBar.addPlayer(p);
                }

                // Calculate progress
                if (attackers > defenders) {
                    siege.progress += 2.0; // +2% per second
                } else if (defenders > attackers) {
                    siege.progress = Math.max(0.0, siege.progress - 1.0);
                }

                siege.progress = Math.min(100.0, siege.progress);
                siege.bossBar.setProgress(siege.progress / 100.0);
                siege.bossBar.setTitle("⚔️ Siège de " + region.getId() + " - Capture: " + (int) siege.progress + "%");

                if (siege.progress >= 100.0) {
                    // Victory for attackers!
                    completeSiegeCapture(siege);
                }
            }
        }, 20L, 20L); // Every second
    }

    private void completeSiegeCapture(ActiveSiege siege) {
        Region region = siege.region;
        stopSiege(region.getId());

        Player initiator = Bukkit.getPlayer(siege.initiatorUUID);
        if (initiator != null && initiator.isOnline()) {
            region.clearOwners();
            region.addOwner(initiator.getUniqueId());
            plugin.getRegionManager().addRegion(region); // Async save & sync

            // Give monetary reward
            if (EconomyIntegration.setupEconomy()) {
                EconomyIntegration.depositPlayer(initiator, 500.0);
            }

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld().getName().equals(region.getWorldName())) {
                    player.showTitle(Title.title(
                            MiniMessage.miniMessage().deserialize("<gold><b>👑 VICTOIRE DE SIÈGE !</b></gold>"),
                            MiniMessage.miniMessage().deserialize("<yellow>Le territoire " + region.getId() + " a été conquis par " + initiator.getName() + " !</yellow>")
                    ));
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                }
            }
        }
    }

    public static class ActiveSiege {
        public final Region region;
        public final UUID initiatorUUID;
        public final BossBar bossBar;
        public double progress = 0.0;

        public ActiveSiege(Region region, UUID initiatorUUID, BossBar bossBar) {
            this.region = region;
            this.initiatorUUID = initiatorUUID;
            this.bossBar = bossBar;
        }
    }
}
