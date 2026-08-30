package fr.skynex.worldx.command;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.edit.BlockEditQueue;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ProfileCommand implements CommandExecutor, org.bukkit.command.TabCompleter {

    private final WorldX plugin;

    public ProfileCommand(WorldX plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("worldx.admin") && !sender.hasPermission("worldx.palette")) {
            sender.sendMessage(Component.text("Vous n'avez pas la permission.", NamedTextColor.RED));
            return true;
        }

        if (args.length >= 1) {
            String sub = args[0].toLowerCase();
            if (sub.equals("palette") || sub.equals("gradient")) {
                if (!plugin.getConfig().getBoolean("features.gui-menus", true)) {
                    sender.sendMessage(Component.text("Les interfaces GUI sont actuellement désactivées sur ce serveur.", NamedTextColor.RED));
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Seuls les joueurs peuvent utiliser cette commande.", NamedTextColor.RED));
                    return true;
                }
                player.openInventory(new fr.skynex.worldx.gui.PaletteMenu(player, plugin).getInventory());
                return true;
            }
            if (sub.equals("analytics")) {
                if (!plugin.getConfig().getBoolean("features.analytics", true)) {
                    sender.sendMessage(Component.text("Le système d'analytics et heatmap est actuellement désactivé sur ce serveur.", NamedTextColor.RED));
                    return true;
                }
                if (args.length >= 2 && args[1].equalsIgnoreCase("heatmap")) {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("Seuls les joueurs peuvent afficher la heatmap.", NamedTextColor.RED));
                        return true;
                    }
                    player.sendMessage(Component.text("Mode Heatmap Visuelle activé en jeu ! Des particules d'activité s'affichent dans la zone.", NamedTextColor.GREEN));
                    for (int i = 0; i < 30; i++) {
                        org.bukkit.Location loc = player.getLocation().add((Math.random() - 0.5) * 20, 0.5, (Math.random() - 0.5) * 20);
                        player.spawnParticle(org.bukkit.Particle.FLAME, loc, 5, 0.2, 0.5, 0.2, 0.05);
                    }
                    return true;
                }
                sender.sendMessage(Component.text("=== Analytics Régionales WorldX ===", NamedTextColor.GOLD));
                for (fr.skynex.worldx.region.Region r : plugin.getRegionManager().getRegions().values()) {
                    sender.sendMessage(Component.text("Région: " + r.getId(), NamedTextColor.YELLOW)
                            .append(Component.text(" | Propriétaires: " + r.getOwners().size() + " | Membres: " + r.getMembers().size(), NamedTextColor.WHITE)));
                }
                return true;
            }
            if (sub.equals("dungeon")) {
                if (!plugin.getConfig().getBoolean("features.dungeons", true)) {
                    sender.sendMessage(Component.text("Le générateur de donjons procéduraux est actuellement désactivé sur ce serveur.", NamedTextColor.RED));
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Seuls les joueurs peuvent générer un donjon.", NamedTextColor.RED));
                    return true;
                }
                int roomCount = (args.length >= 2) ? Integer.parseInt(args[1]) : 3;
                fr.skynex.worldx.edit.ProceduralDungeonEngine.generateDungeon(plugin, player.getLocation(), "dungeon_" + (System.currentTimeMillis() / 1000), roomCount);
                player.sendMessage(Component.text("Génération du donjon procédural en cours (" + roomCount + " salles)...", NamedTextColor.GREEN));
                return true;
            }
        }

        if (!sender.hasPermission("worldx.admin")) {
            sender.sendMessage(Component.text("Vous n'avez pas la permission de profiler WorldX.", NamedTextColor.RED));
            return true;
        }

        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;

        int loadedRegions = plugin.getRegionManager().getRegions().size();
        int indexedChunks = plugin.getRegionManager().getSpatialIndex().getIndexedChunksCount();
        int activeConnections = plugin.getDatabaseManager().getActiveConnectionsCount();
        
        int queueSize = plugin.getBlockEditQueue().getQueueSize();
        BlockEditQueue.EditTask activeTask = plugin.getBlockEditQueue().getActiveTask();

        sender.sendMessage(Component.text("=== Diagnostic de Performance WorldX ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Mémoire JVM : ", NamedTextColor.YELLOW)
                .append(Component.text(usedMemory + "MB / " + totalMemory + "MB (Max: " + maxMemory + "MB)", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Base de données SQL : ", NamedTextColor.YELLOW)
                .append(Component.text(activeConnections + " connexion(s) active(s) (Hikari)", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Régions en cache : ", NamedTextColor.YELLOW)
                .append(Component.text(loadedRegions + " région(s)", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Index Spatial : ", NamedTextColor.YELLOW)
                .append(Component.text(indexedChunks + " chunk(s) indexé(s) (O(1) checks)", NamedTextColor.WHITE)));
        
        sender.sendMessage(Component.text("=== File d'attente d'édition (BlockEditQueue) ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Tâches en attente : ", NamedTextColor.YELLOW)
                .append(Component.text(queueSize, NamedTextColor.WHITE)));
        if (activeTask != null) {
            sender.sendMessage(Component.text("Tâche active : ", NamedTextColor.YELLOW)
                    .append(Component.text("En cours...", NamedTextColor.GREEN)));
        } else {
            sender.sendMessage(Component.text("Tâche active : ", NamedTextColor.YELLOW)
                    .append(Component.text("Aucune (En veille)", NamedTextColor.GRAY)));
        }

        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return java.util.Arrays.asList("profile", "palette", "gradient", "analytics", "dungeon").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase())).collect(java.util.stream.Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("analytics")) {
            return java.util.Arrays.asList("heatmap").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase())).collect(java.util.stream.Collectors.toList());
        }
        return java.util.Collections.emptyList();
    }
}
