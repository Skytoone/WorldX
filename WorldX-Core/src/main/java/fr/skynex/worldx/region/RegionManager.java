package fr.skynex.worldx.region;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.database.DatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class RegionManager {

    private final WorldX plugin;
    private final DatabaseManager databaseManager;
    private final Map<String, Region> regions = new HashMap<>();
    private final SpatialIndex spatialIndex = new SpatialIndex();

    public RegionManager(WorldX plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    /**
     * Load all regions from database into memory and spatial index.
     */
    public void loadAllRegions() {
        try {
            List<Region> loaded = databaseManager.loadAllRegions();
            regions.clear();
            spatialIndex.clear();
            for (Region r : loaded) {
                regions.put(r.getId().toLowerCase(), r);
                spatialIndex.addRegion(r);
            }
            plugin.getLogger().info("Loaded " + loaded.size() + " regions from the database.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load regions from database!", e);
        }
    }

    public synchronized void addRegion(Region region) {
        regions.put(region.getId().toLowerCase(), region);
        spatialIndex.addRegion(region);
        databaseManager.saveRegion(region);
        if (plugin.getNetworkSyncManager() != null) {
            plugin.getNetworkSyncManager().sendRegionSave(region);
        }
        if (plugin.getRedisManager() != null) {
            plugin.getRedisManager().publishSync("UPDATE", region.getId());
        }
    }

    public synchronized void removeRegion(String id) {
        Region region = regions.remove(id.toLowerCase());
        if (region != null) {
            spatialIndex.removeRegion(region);
            databaseManager.deleteRegion(region.getId());
            if (plugin.getNetworkSyncManager() != null) {
                plugin.getNetworkSyncManager().sendRegionDelete(region.getId());
            }
            if (plugin.getRedisManager() != null) {
                plugin.getRedisManager().publishSync("DELETE", region.getId());
            }
        }
    }

    public synchronized void updateLocalCacheOnly(Region region) {
        Region old = regions.put(region.getId().toLowerCase(), region);
        if (old != null) {
            spatialIndex.removeRegion(old);
        }
        spatialIndex.addRegion(region);
        plugin.getLogger().info("Synchronisation réseau : région \"" + region.getId() + "\" mise à jour en cache.");
    }

    public synchronized void removeLocalCacheOnly(String id) {
        Region region = regions.remove(id.toLowerCase());
        if (region != null) {
            spatialIndex.removeRegion(region);
            plugin.getLogger().info("Synchronisation réseau : région \"" + id + "\" retirée du cache.");
        }
    }

    public synchronized void removeRegionInMemory(String id) {
        Region region = regions.remove(id.toLowerCase());
        if (region != null) {
            spatialIndex.removeRegion(region);
            plugin.getLogger().info("[Redis Sync] Région \"" + id + "\" retirée de la mémoire locale.");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (plugin.getSelectionVisualizer() != null) {
                    plugin.getSelectionVisualizer().hideRegion(p.getUniqueId());
                }
            }
        }
    }

    public void reloadRegionFromDatabase(String id) {
        databaseManager.loadRegion(id).thenAccept(region -> {
            if (region != null) {
                fr.skynex.worldx.scheduler.FoliaScheduler.runSync(plugin, () -> {
                    synchronized (RegionManager.this) {
                        Region old = regions.put(id.toLowerCase(), region);
                        if (old != null) {
                            spatialIndex.removeRegion(old);
                        }
                        spatialIndex.addRegion(region);
                        plugin.getLogger().info("[Redis Sync] Région \"" + id + "\" synchronisée en mémoire.");
                    }
                });
            } else {
                fr.skynex.worldx.scheduler.FoliaScheduler.runSync(plugin, () -> removeRegionInMemory(id));
            }
        });
    }

    public synchronized Region getRegion(String id) {
        return regions.get(id.toLowerCase());
    }

    public Map<String, Region> getRegions() {
        return Collections.unmodifiableMap(regions);
    }

    public SpatialIndex getSpatialIndex() {
        return spatialIndex;
    }

    public List<Region> getRegionsAt(Location loc) {
        long start = System.nanoTime();
        try {
            return spatialIndex.getRegionsAt(loc);
        } finally {
            long elapsed = System.nanoTime() - start;
            if (plugin.getRegionProfiler().isActive()) {
                plugin.getRegionProfiler().record("SpatialIndex.getRegionsAt", elapsed);
            }
        }
    }

    /**
     * Check if a player has permission to perform an action governed by a flag at a
     * location.
     */
    public boolean checkPermission(Player player, Location loc, Flag flag) {
        long start = System.nanoTime();
        try {
            return checkPermissionInternal(player, loc, flag);
        } finally {
            long elapsed = System.nanoTime() - start;
            if (plugin.getRegionProfiler().isActive()) {
                plugin.getRegionProfiler().record("checkPermission (Total)", elapsed);
            }
        }
    }

    private boolean checkPermissionInternal(Player player, Location loc, Flag flag) {
        if (player != null && (player.isOp() || player.hasPermission("worldx.bypass"))) {
            return true;
        }

        List<Region> regionsAt = getRegionsAt(loc);
        if (regionsAt.isEmpty()) {
            return true; // No protection here
        }

        // Sort by priority descending
        regionsAt.sort((r1, r2) -> Integer.compare(r2.getPriority(), r1.getPriority()));

        int highestPriority = regionsAt.get(0).getPriority();
        UUID playerUUID = player != null ? player.getUniqueId() : null;

        // Evaluate regions at highest priority
        for (Region r : regionsAt) {
            if (r.getPriority() < highestPriority) {
                break; // Only evaluate highest priority regions
            }

            // Flag value in this region (or inherited from parent)
            String value = getEffectiveFlagValue(r, flag.name());
            if (value != null && (value.contains(" if ") || value.contains(" unless "))) {
                boolean result = fr.skynex.worldx.integration.ConditionEvaluator.evaluate(player, value);
                value = result ? "allow" : "deny";
            }

            // Special evaluation for BUILD and USE flags (membership protection)
            if (flag == Flag.BUILD || flag == Flag.USE) {
                String siegeMode = getEffectiveFlagValue(r, "siege-mode");
                if ("allow".equalsIgnoreCase(siegeMode)) {
                    String siegeStart = getEffectiveFlagValue(r, "siege-start-time");
                    if (siegeStart != null && !siegeStart.isEmpty()) {
                        continue;
                    }
                }

                if ("allow".equalsIgnoreCase(value)) {
                    continue;
                }

                // If flag is explicitly DENY, no one can build/use (even owners/members)
                if ("deny".equalsIgnoreCase(value)) {
                    return false;
                }

                // If flag is not set, check if the region has owners/members defined
                if (playerUUID != null) {
                    boolean hasOwnersOrMembers = !r.getOwners().isEmpty() || !r.getMembers().isEmpty();
                    if (hasOwnersOrMembers) {
                        if (r.isMember(playerUUID)) {
                            continue; // Member is allowed to build/use unless blocked by another region
                        } else {
                            return false; // Not a member: blocked by default
                        }
                    }
                } else {
                    return false; // No player (e.g. environment): blocked if there are owners
                }
            } else {
                // General flags like PVP, MOB_SPAWN
                if ("deny".equalsIgnoreCase(value)) {
                    return false;
                }
                if ("allow".equalsIgnoreCase(value)) {
                    continue;
                }
            }
        }

        return true;
    }

    /**
     * Get the effective flag value of a region, traversing up to its parents if
     * necessary.
     */
    public synchronized String getEffectiveFlagValue(Region region, String flagName) {
        Region current = region;
        while (current != null) {
            String value = current.getFlags().get(flagName.toLowerCase().replace("_", "-"));
            if (value != null) {
                return value;
            }
            // Move to parent region
            if (current.getParentId() != null) {
                current = getRegion(current.getParentId());
            } else {
                current = null;
            }
        }
        return null;
    }

    public Region getHighestPriorityRegionOfBlock(Location loc) {
        List<Region> regionsAt = getRegionsAt(loc);
        if (regionsAt.isEmpty()) {
            return null;
        }

        Region highest = regionsAt.get(0);
        for (Region r : regionsAt) {
            if (r.getPriority() > highest.getPriority()) {
                highest = r;
            }
        }
        return highest;
    }

    /**
     * Trace the evaluation of a permission for a player at a location.
     */
    public List<String> tracePermission(Player player, Location loc, String flagName) {
        List<String> trace = new ArrayList<>();

        if (player != null) {
            if (player.isOp()) {
                trace.add("§a[Bypass] Le joueur est OP (opérateur). Permission accordée.");
                return trace;
            }
            if (player.hasPermission("worldx.bypass")) {
                trace.add("§a[Bypass] Le joueur a la permission 'worldx.bypass'. Permission accordée.");
                return trace;
            }
        }

        List<Region> regionsAt = getRegionsAt(loc);
        if (regionsAt.isEmpty()) {
            trace.add("§aAucune région présente à cette position. Action autorisée par défaut.");
            return trace;
        }

        // Sort by priority descending
        regionsAt.sort((r1, r2) -> Integer.compare(r2.getPriority(), r1.getPriority()));

        int highestPriority = regionsAt.get(0).getPriority();
        UUID playerUUID = player != null ? player.getUniqueId() : null;

        trace.add("§eRégions trouvées à cette position (" + regionsAt.size() + ") : Priorité max = " + highestPriority);

        boolean decisionMade = false;

        // Find matching flag if it is an enum or general string flag
        Flag flagEnum = null;
        try {
            flagEnum = Flag.valueOf(flagName.toUpperCase());
        } catch (IllegalArgumentException ignored) {
        }

        // Evaluate regions at highest priority
        for (Region r : regionsAt) {
            if (r.getPriority() < highestPriority) {
                trace.add("§7Région \"" + r.getId() + "\" ignorée (Priorité " + r.getPriority() + " < "
                        + highestPriority + ")");
                continue;
            }

            // Flag value in this region (or inherited from parent)
            String rawValue = getEffectiveFlagValue(r, flagName);
            String value = rawValue;

            trace.add("§bÉvaluation de la région \"" + r.getId() + "\" (Priorité " + r.getPriority() + ") :");

            if (rawValue != null) {
                if (rawValue.contains(" if ") || rawValue.contains(" unless ")) {
                    boolean condResult = fr.skynex.worldx.integration.ConditionEvaluator.evaluate(player, rawValue);
                    value = condResult ? "allow" : "deny";
                    trace.add("  - Condition détectée : \"" + rawValue + "\" -> Évaluée à : "
                            + (condResult ? "§aALLOW" : "§cDENY"));
                } else {
                    // Check if value was inherited
                    String directValue = r.getFlags().get(flagName.toLowerCase());
                    if (directValue == null) {
                        trace.add("  - Flag \"" + flagName + "\" hérité d'un parent -> Valeur : " + rawValue);
                    } else {
                        trace.add("  - Flag \"" + flagName + "\" défini directement -> Valeur : " + rawValue);
                    }
                }
            } else {
                trace.add("  - Flag \"" + flagName + "\" non défini (null) pour cette région ou ses parents.");
            }

            // Special evaluation for BUILD and USE flags (membership protection)
            if (flagEnum == Flag.BUILD || flagEnum == Flag.USE) {
                if ("allow".equalsIgnoreCase(value)) {
                    trace.add("  - Décision partielle : §aALLOW§b (flag explicite). Poursuite des vérifications.");
                    continue;
                }

                if ("deny".equalsIgnoreCase(value)) {
                    trace.add("  - Décision finale : §cDENY§b (flag explicite). Accès refusé.");
                    decisionMade = true;
                    break;
                }

                // If flag is not set, check if the region has owners/members defined
                if (playerUUID != null) {
                    boolean hasOwnersOrMembers = !r.getOwners().isEmpty() || !r.getMembers().isEmpty();
                    if (hasOwnersOrMembers) {
                        if (r.isMember(playerUUID)) {
                            trace.add(
                                    "  - Décision partielle : §aALLOW§b (le joueur est membre/propriétaire). Poursuite.");
                            continue;
                        } else {
                            trace.add(
                                    "  - Décision finale : §cDENY§b (le joueur n'est ni membre ni propriétaire). Accès refusé.");
                            decisionMade = true;
                            break;
                        }
                    } else {
                        trace.add("  - Aucun membre/propriétaire défini sur la région. Poursuite.");
                    }
                } else {
                    trace.add(
                            "  - Pas de joueur associé (entité/environnement) et des propriétaires sont définis sur la région -> §cDENY");
                    decisionMade = true;
                    break;
                }
            } else {
                // General flags like PVP, MOB_SPAWN or custom string flags
                if ("deny".equalsIgnoreCase(value)) {
                    trace.add("  - Décision finale : §cDENY§b (flag égal à \"deny\").");
                    decisionMade = true;
                    break;
                }
                if ("allow".equalsIgnoreCase(value)) {
                    trace.add("  - Décision partielle : §aALLOW§b (flag égal à \"allow\"). Poursuite.");
                    continue;
                }

                // If it's a custom string flag, show its value
                if (value != null) {
                    trace.add("  - Valeur personnalisée : \"" + value + "\". Poursuite.");
                }
            }
        }

        if (!decisionMade) {
            trace.add("§aAucune règle de refus trouvée. Action autorisée par défaut.");
        } else {
            trace.add("§cAction refusée en raison des règles ci-dessus.");
        }

        return trace;
    }

    public void triggerIntrusionAlert(Player intruder, Region region, String action) {
        String cmdStr = getEffectiveFlagValue(region, "intrusion-command");
        if (cmdStr != null && !cmdStr.trim().isEmpty()) {
            String commandToRun = cmdStr
                    .replace("{player}", intruder.getName())
                    .replace("{region}", region.getId())
                    .replace("{action}", action)
                    .replace("{world}", intruder.getWorld().getName())
                    .replace("{x}", String.valueOf(intruder.getLocation().getBlockX()))
                    .replace("{y}", String.valueOf(intruder.getLocation().getBlockY()))
                    .replace("{z}", String.valueOf(intruder.getLocation().getBlockZ()));
            
            fr.skynex.worldx.scheduler.FoliaScheduler.runSync(plugin, () -> {
                org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), commandToRun);
            });
        }

        String alertType = getEffectiveFlagValue(region, "intrusion-alert");
        if (alertType == null || alertType.equalsIgnoreCase("none")) {
            return;
        }

        Component message = Component.text("[ALERTE INTRUSION] Le joueur ", NamedTextColor.RED)
                .append(Component.text(intruder.getName(), NamedTextColor.YELLOW))
                .append(Component.text(" a tenté de ", NamedTextColor.RED))
                .append(Component.text(action, NamedTextColor.GOLD))
                .append(Component.text(" dans la région ", NamedTextColor.RED))
                .append(Component.text(region.getId(), NamedTextColor.YELLOW))
                .append(Component.text(" (" + intruder.getLocation().getBlockX() + ", "
                        + intruder.getLocation().getBlockY() + ", " + intruder.getLocation().getBlockZ() + ").",
                        NamedTextColor.RED));

        if (alertType.equalsIgnoreCase("owner")) {
            for (UUID ownerUUID : region.getOwners()) {
                Player owner = Bukkit.getPlayer(ownerUUID);
                if (owner != null && owner.isOnline()) {
                    owner.sendMessage(message);
                }
            }
        } else if (alertType.equalsIgnoreCase("sound")) {
            intruder.playSound(intruder.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 0.5f);
            for (UUID ownerUUID : region.getOwners()) {
                Player owner = Bukkit.getPlayer(ownerUUID);
                if (owner != null && owner.isOnline()) {
                    owner.sendMessage(message);
                    owner.playSound(owner.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 0.5f);
                }
            }
        } else if (alertType.equalsIgnoreCase("discord")) {
            fr.skynex.worldx.integration.DiscordWebhookLogger.logIntrusion(
                    plugin, intruder.getName(), region.getId(), action,
                    intruder.getWorld().getName(),
                    intruder.getLocation().getBlockX(),
                    intruder.getLocation().getBlockY(),
                    intruder.getLocation().getBlockZ()
            );
            for (UUID ownerUUID : region.getOwners()) {
                Player owner = Bukkit.getPlayer(ownerUUID);
                if (owner != null && owner.isOnline()) {
                    owner.sendMessage(message);
                }
            }
        }
    }

    public void triggerAutomaticRollback(Region region) {
        plugin.getLogger()
                .info("[WorldX Expire] Déclenchement du rollback automatique pour la région : " + region.getId());
        plugin.getDatabaseManager().getRollbackLogs(region.getId(), null, 0)
                .thenAccept(logs -> {
                    if (logs.isEmpty()) {
                        plugin.getLogger()
                                .info("[WorldX Expire] Aucun historique de modification trouvé pour la région : "
                                        + region.getId());
                        return;
                    }

                    List<fr.skynex.worldx.edit.BlockEditQueue.BlockChangeInfo> changes = new ArrayList<>();
                    for (fr.skynex.worldx.database.RollbackEntry entry : logs) {
                        try {
                            changes.add(new fr.skynex.worldx.edit.BlockEditQueue.BlockChangeInfo(
                                    entry.getX(), entry.getY(), entry.getZ(),
                                    Bukkit.createBlockData(entry.getPreviousBlock())));
                        } catch (Exception ignored) {
                        }
                    }

                    if (!changes.isEmpty()) {
                        fr.skynex.worldx.scheduler.FoliaScheduler.runSync(plugin, () -> {
                            plugin.getBlockEditQueue().queueTask(new fr.skynex.worldx.edit.BlockEditQueue.EditTask(
                                    new UUID(0L, 0L), region.getWorldName(), changes));
                            plugin.getLogger().info("[WorldX Expire] " + changes.size()
                                    + " blocs mis en file d'attente de restauration pour : " + region.getId());
                        });
                    }
                });
    }
}

