package fr.skynex.worldx.command;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.database.RollbackEntry;
import fr.skynex.worldx.edit.BlockEditQueue;
import fr.skynex.worldx.region.Region;
import fr.skynex.worldx.session.Session;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class RollbackCommand implements CommandExecutor, TabCompleter {

    private final WorldX plugin;

    public RollbackCommand(WorldX plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Seuls les joueurs peuvent effectuer des rollbacks.", NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("worldx.region.rollback")) {
            player.sendMessage(Component.text("Vous n'avez pas la permission de rollback.", NamedTextColor.RED));
            return true;
        }

        // Subcommand: /rg rollback <id> [time] [player]
        // But since this is registered as its own command `/rg rollback`, wait!
        // We will execute it as a subcommand of /rg, OR as a separate command /rgrollback?
        // Let's route it inside RegionCommand.java under `case "rollback"`, which is extremely clean!
        // So we can make RollbackCommand a helper or run it directly here.
        // Let's check: yes, we can have a helper method called from RegionCommand, OR register `/rg rollback` subcommand directly in RegionCommand.java and delegate to this command class!
        // Let's do that: Route "rollback" in RegionCommand to this executor!
        // Let's implement the routing in RegionCommand.java:
        // `case "rollback": return new RollbackCommand(plugin).onCommand(...)`
        // This is extremely simple and clean!

        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /rg rollback <id> [temps: 30m/1h/1d] [joueur] [preview]", NamedTextColor.RED));
            return true;
        }

        String regionId = args[1];
        Session session = plugin.getSessionManager().getSession(player);

        if (regionId.equalsIgnoreCase("confirm")) {
            List<BlockEditQueue.BlockChangeInfo> pending = session.getPendingRollback();
            if (pending == null || pending.isEmpty()) {
                player.sendMessage(Component.text("Aucun rollback en attente de confirmation.", NamedTextColor.RED));
                return true;
            }
            String worldName = session.getPendingRollbackWorldName();
            if (worldName == null) worldName = player.getWorld().getName();
            
            plugin.getBlockEditQueue().queueTask(new BlockEditQueue.EditTask(
                    player.getUniqueId(), worldName, pending
            ));
            player.sendMessage(Component.text("Rollback de " + pending.size() + " blocs confirmé et appliqué !", NamedTextColor.GREEN));
            
            session.setPendingRollback(null);
            session.setPendingRollbackRegionId(null);
            session.setPendingRollbackWorldName(null);
            return true;
        }

        if (regionId.equalsIgnoreCase("cancel")) {
            List<BlockEditQueue.BlockChangeInfo> pending = session.getPendingRollback();
            if (pending == null || pending.isEmpty()) {
                player.sendMessage(Component.text("Aucun rollback en attente à annuler.", NamedTextColor.RED));
                return true;
            }
            player.sendMessage(Component.text("Restauration visuelle de " + pending.size() + " blocs...", NamedTextColor.YELLOW));
            org.bukkit.World world = player.getWorld();
            for (BlockEditQueue.BlockChangeInfo change : pending) {
                org.bukkit.Location blockLoc = new org.bukkit.Location(world, change.x, change.y, change.z);
                player.sendBlockChange(blockLoc, blockLoc.getBlock().getBlockData());
            }
            session.setPendingRollback(null);
            session.setPendingRollbackRegionId(null);
            session.setPendingRollbackWorldName(null);
            player.sendMessage(Component.text("Prévisualisation du rollback annulée.", NamedTextColor.GREEN));
            return true;
        }

        Region region = plugin.getRegionManager().getRegion(regionId);
        if (region == null) {
            player.sendMessage(Component.text("Région \"" + regionId + "\" introuvable.", NamedTextColor.RED));
            return true;
        }

        long maxAgeMs = 0;
        UUID filterPlayerUUID = null;

        if (args.length >= 3) {
            String timeArg = args[2];
            maxAgeMs = parseTimeSpec(timeArg);
            if (maxAgeMs == 0) {
                // Not a time, maybe a player name?
                OfflinePlayer op = Bukkit.getOfflinePlayer(timeArg);
                if (op.hasPlayedBefore() || op.isOnline()) {
                    filterPlayerUUID = op.getUniqueId();
                } else {
                    player.sendMessage(Component.text("Spécification de temps invalide (ex: 30m, 1h, 1d) ou joueur inconnu.", NamedTextColor.RED));
                    return true;
                }
            }
        }

        if (args.length >= 4 && filterPlayerUUID == null) {
            String playerArg = args[3];
            OfflinePlayer op = Bukkit.getOfflinePlayer(playerArg);
            filterPlayerUUID = op.getUniqueId();
        }

        boolean isPreviewTemp = false;
        for (String arg : args) {
            if (arg.equalsIgnoreCase("preview")) {
                isPreviewTemp = true;
                break;
            }
        }
        final boolean isPreview = isPreviewTemp;

        final UUID finalFilterUUID = filterPlayerUUID;
        final long finalMaxAge = maxAgeMs;

        player.sendMessage(Component.text("Recherche de l'historique pour la région \"" + regionId + "\"...", NamedTextColor.YELLOW));

        plugin.getDatabaseManager().getRollbackLogs(region.getId(), finalFilterUUID, finalMaxAge)
                .thenAccept(logs -> {
                    if (logs.isEmpty()) {
                        player.sendMessage(Component.text("Aucune modification trouvée dans l'historique avec ces critères.", NamedTextColor.RED));
                        return;
                    }

                    List<BlockEditQueue.BlockChangeInfo> changes = new ArrayList<>();
                    for (RollbackEntry entry : logs) {
                        try {
                            changes.add(new BlockEditQueue.BlockChangeInfo(
                                     entry.getX(), entry.getY(), entry.getZ(),
                                     Bukkit.createBlockData(entry.getPreviousBlock())
                            ));
                        } catch (Exception ignored) {}
                    }

                    if (!changes.isEmpty()) {
                        if (isPreview) {
                            session.setPendingRollback(changes);
                            session.setPendingRollbackRegionId(regionId);
                            session.setPendingRollbackWorldName(region.getWorldName());

                            org.bukkit.World world = player.getWorld();
                            for (BlockEditQueue.BlockChangeInfo change : changes) {
                                org.bukkit.Location blockLoc = new org.bukkit.Location(world, change.x, change.y, change.z);
                                player.sendBlockChange(blockLoc, change.newData);
                            }
                            player.sendMessage(Component.text("Prévisualisation de " + changes.size() + " blocs affichée !", NamedTextColor.GREEN));
                            player.sendMessage(Component.text("Confirmez avec /rg rollback confirm ou annulez avec /rg rollback cancel.", NamedTextColor.GREEN));
                        } else {
                            player.sendMessage(Component.text("Annulation de " + logs.size() + " modifications en cours...", NamedTextColor.YELLOW));
                            plugin.getBlockEditQueue().queueTask(new BlockEditQueue.EditTask(
                                    player.getUniqueId(), region.getWorldName(), changes
                            ));
                            fr.skynex.worldx.integration.DiscordWebhookLogger.logRegionAction(
                                    plugin, "ROLLBACK", player.getName(), regionId,
                                    "Modifications annulées: " + changes.size() + ", Filtre Joueur: " + (finalFilterUUID != null ? finalFilterUUID.toString() : "Aucun")
                            );
                        }
                    }
                });

        return true;
    }

    private long parseTimeSpec(String spec) {
        spec = spec.toLowerCase();
        try {
            if (spec.endsWith("m")) {
                return Long.parseLong(spec.replace("m", "")) * 60 * 1000;
            }
            if (spec.endsWith("h")) {
                return Long.parseLong(spec.replace("h", "")) * 60 * 60 * 1000;
            }
            if (spec.endsWith("d")) {
                return Long.parseLong(spec.replace("d", "")) * 24 * 60 * 60 * 1000;
            }
        } catch (NumberFormatException ignored) {}
        return 0;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 2) {
            List<String> list = new ArrayList<>(plugin.getRegionManager().getRegions().keySet());
            list.add("confirm");
            list.add("cancel");
            return list.stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 3) {
            return Arrays.asList("15m", "30m", "1h", "12h", "1d", "7d", "preview").stream()
                    .filter(s -> s.startsWith(args[2])).collect(Collectors.toList());
        }
        if (args.length == 4) {
            List<String> list = Bukkit.getOnlinePlayers().stream().map((Player player) -> player.getName()).collect(Collectors.toList());
            list.add("preview");
            return list.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase())).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
