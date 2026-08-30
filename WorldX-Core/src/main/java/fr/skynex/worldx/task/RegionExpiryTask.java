package fr.skynex.worldx.task;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.region.Region;
import org.bukkit.Bukkit;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RegionExpiryTask extends BukkitRunnable {

    private final WorldX plugin;

    public RegionExpiryTask(WorldX plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        List<Region> expiredRegions = new ArrayList<>();
        List<Region> inactiveRegions = new ArrayList<>();

        for (Region region : plugin.getRegionManager().getRegions().values()) {
            // 1. Check rent expiration
            String expiryStr = region.getFlags().get("rent-expiry");
            if (expiryStr != null && !expiryStr.isEmpty() && !expiryStr.equalsIgnoreCase("expired")) {
                try {
                    long expiryTime = Long.parseLong(expiryStr);
                    if (now > expiryTime) {
                        expiredRegions.add(region);
                    }
                } catch (NumberFormatException ignored) {}
            }

            // 2. Check inactivity expiration
            String inactivityStr = region.getFlags().get("inactivity-expiry");
            if (inactivityStr != null && !inactivityStr.isEmpty()) {
                long inactivityThreshold = parseDurationMs(inactivityStr);
                if (inactivityThreshold > 0) {
                    String lastActiveStr = region.getFlags().get("last-active");
                    long lastActive = 0;
                    if (lastActiveStr != null && !lastActiveStr.isEmpty()) {
                        try {
                            lastActive = Long.parseLong(lastActiveStr);
                        } catch (NumberFormatException ignored) {}
                    }
                    // If lastActive is 0, we assume it's creation time or default to current time to avoid instant expiration
                    if (lastActive == 0) {
                        region.getFlags().put("last-active", String.valueOf(now));
                        plugin.getRegionManager().addRegion(region); // Save immediately
                    } else if (now - lastActive > inactivityThreshold) {
                        inactiveRegions.add(region);
                    }
                }
            }
        }

        // Process expired rents
        for (Region region : expiredRegions) {
            boolean paid = false;
            double price = 0;
            try {
                price = Double.parseDouble(region.getFlags().getOrDefault("rent-price", "0"));
            } catch (NumberFormatException ignored) {}

            String durationStr = region.getFlags().getOrDefault("rent-duration", "7d");
            long durationMs = parseDurationMs(durationStr);

            if (!region.getOwners().isEmpty() && price > 0 && durationMs > 0) {
                UUID ownerId = region.getOwners().iterator().next(); // First owner
                org.bukkit.OfflinePlayer offlineOwner = Bukkit.getOfflinePlayer(ownerId);
                
                // If Vault is active, try to withdraw
                if (fr.skynex.worldx.integration.EconomyIntegration.setupEconomy()) {
                    double balance = fr.skynex.worldx.integration.EconomyIntegration.getBalance(offlineOwner);
                    if (balance >= price) {
                        if (fr.skynex.worldx.integration.EconomyIntegration.withdrawPlayer(offlineOwner, price)) {
                            paid = true;
                            long currentExpiry = 0;
                            String expiryStr = region.getFlags().get("rent-expiry");
                            if (expiryStr != null && !expiryStr.isEmpty() && !expiryStr.equalsIgnoreCase("expired")) {
                                try {
                                    currentExpiry = Long.parseLong(expiryStr);
                                } catch (NumberFormatException ignored) {}
                            }
                            long newExpiry = (currentExpiry > now ? currentExpiry : now) + durationMs;
                            region.getFlags().put("rent-expiry", String.valueOf(newExpiry));
                            plugin.getRegionManager().addRegion(region); // Save changes
                            
                            plugin.getLogger().info("[WorldX Rent] Location renouvelée automatiquement pour la région \"" + region.getId() + "\" (Propriétaire: " + offlineOwner.getName() + ").");
                            Player p = Bukkit.getPlayer(ownerId);
                            if (p != null && p.isOnline()) {
                                p.sendMessage(MiniMessage.miniMessage().deserialize("<green>[WorldX] Votre location pour la région \"" + region.getId() + "\" a été renouvelée automatiquement pour " + price + "."));
                            }
                        }
                    }
                } else {
                    // If Vault is not active, simulate free renewal
                    paid = true;
                    long newExpiry = now + durationMs;
                    region.getFlags().put("rent-expiry", String.valueOf(newExpiry));
                    plugin.getRegionManager().addRegion(region);
                    plugin.getLogger().info("[WorldX Rent] Location renouvelée automatiquement (simulation) pour la région \"" + region.getId() + "\".");
                }
            }

            if (paid) {
                continue; // Skip eviction since they paid!
            }

            plugin.getLogger().info("[WorldX Rent] La location de la région \"" + region.getId() + "\" a expiré.");
            
            // Notify owners/members before clearing
            for (UUID uuid : region.getOwners()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    p.sendMessage(MiniMessage.miniMessage().deserialize("<red>[WorldX] Votre location pour la région \"" + region.getId() + "\" a expiré. La zone va être libérée et restaurée."));
                }
            }

            // Clean owners and members
            region.clearOwners();
            region.clearMembers();
            region.getFlags().put("rent-expiry", "expired");
            
            // Trigger automatic rollback to restore physical blocks
            plugin.getRegionManager().triggerAutomaticRollback(region);

            // Save region changes to DB and publish Redis sync
            plugin.getRegionManager().addRegion(region);
        }

        // Process inactive regions
        for (Region region : inactiveRegions) {
            plugin.getLogger().info("[WorldX Inactivité] La région \"" + region.getId() + "\" a été libérée pour inactivité.");
            
            for (UUID uuid : region.getOwners()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    p.sendMessage(MiniMessage.miniMessage().deserialize("<red>[WorldX] Votre claim \"" + region.getId() + "\" a été libéré suite à une inactivité prolongée."));
                }
            }

            // Clean owners and members
            region.clearOwners();
            region.clearMembers();
            region.getFlags().remove("last-active");
            region.getFlags().remove("inactivity-expiry");

            // Trigger automatic rollback
            plugin.getRegionManager().triggerAutomaticRollback(region);

            // Save region changes to DB and publish Redis sync
            plugin.getRegionManager().addRegion(region);
        }
    }

    private long parseDurationMs(String spec) {
        spec = spec.toLowerCase();
        try {
            if (spec.endsWith("m")) {
                return Long.parseLong(spec.replace("m", "")) * 60 * 1000L;
            }
            if (spec.endsWith("h")) {
                return Long.parseLong(spec.replace("h", "")) * 60 * 60 * 1000L;
            }
            if (spec.endsWith("d")) {
                return Long.parseLong(spec.replace("d", "")) * 24 * 60 * 60 * 1000L;
            }
        } catch (NumberFormatException ignored) {}
        return 0L;
    }
}
