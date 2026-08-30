package fr.skynex.worldx.command;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.auction.ClaimAuctionManager;
import fr.skynex.worldx.region.Region;
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
import java.util.Map;
import java.util.stream.Collectors;

public class AuctionCommand implements CommandExecutor, TabCompleter {

    private final WorldX plugin;

    public AuctionCommand(WorldX plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length < 2) {
            sendAuctionHelp(sender);
            return true;
        }

        String sub = args[1].toLowerCase();
        ClaimAuctionManager manager = plugin.getClaimAuctionManager();

        if (sub.equals("list")) {
            Map<String, ClaimAuctionManager.AuctionEntry> auctions = manager.getActiveAuctions();
            if (auctions.isEmpty()) {
                sender.sendMessage(Component.text("Aucune enchère de région n'est en cours actuellement.", NamedTextColor.YELLOW));
                return true;
            }

            sender.sendMessage(Component.text("=== Enchères Régionales en Cours ===", NamedTextColor.GOLD));
            for (ClaimAuctionManager.AuctionEntry entry : auctions.values()) {
                String highestBidderName = "Aucun";
                if (entry.highestBidder != null) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(entry.highestBidder);
                    highestBidderName = op.getName() != null ? op.getName() : entry.highestBidder.toString();
                }
                long timeLeftSec = Math.max(0, (entry.endTime - System.currentTimeMillis()) / 1000);
                sender.sendMessage(Component.text("- Région: " + entry.regionId + " | Offre actuelle: " + entry.currentBid + "$ (" + highestBidderName + ") | Temps restant: " + timeLeftSec + "s", NamedTextColor.YELLOW));
            }
            return true;
        }

        if (sub.equals("bid")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Seuls les joueurs peuvent surenchérir.", NamedTextColor.RED));
                return true;
            }
            if (args.length < 4) {
                player.sendMessage(Component.text("Usage: /rg auction bid <region_id> <montant>", NamedTextColor.RED));
                return true;
            }
            String regionId = args[2];
            double amount;
            try {
                amount = Double.parseDouble(args[3]);
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("Montant d'enchère invalide.", NamedTextColor.RED));
                return true;
            }

            if (manager.placeBid(player, regionId, amount)) {
                player.sendMessage(Component.text("Enchère de " + amount + "$ placée avec succès pour la région \"" + regionId + "\" !", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("Échec du placement de l'enchère. Vérifiez votre solde et que l'offre est supérieure à l'actuelle.", NamedTextColor.RED));
            }
            return true;
        }

        if (sub.equals("start")) {
            if (!sender.hasPermission("worldx.region.admin")) {
                sender.sendMessage(Component.text("Vous n'avez pas la permission de démarrer une enchère.", NamedTextColor.RED));
                return true;
            }
            if (args.length < 4) {
                sender.sendMessage(Component.text("Usage: /rg auction start <region_id> <prix_depart> [durée_h]", NamedTextColor.RED));
                return true;
            }
            String regionId = args[2];
            Region region = plugin.getRegionManager().getRegion(regionId);
            if (region == null) {
                sender.sendMessage(Component.text("Région introuvable.", NamedTextColor.RED));
                return true;
            }
            double startPrice;
            try {
                startPrice = Double.parseDouble(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Prix de départ invalide.", NamedTextColor.RED));
                return true;
            }
            long durationMs = 24 * 60 * 60 * 1000L; // 24 hours default
            if (args.length >= 5) {
                try {
                    durationMs = Long.parseLong(args[4]) * 60 * 60 * 1000L;
                } catch (NumberFormatException ignored) {}
            }

            if (manager.createAuction(region, startPrice, durationMs)) {
                sender.sendMessage(Component.text("Enchère démarrée pour la région \"" + regionId + "\" au prix de " + startPrice + "$ !", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("Cette région est déjà aux enchères.", NamedTextColor.RED));
            }
            return true;
        }

        sendAuctionHelp(sender);
        return true;
    }

    private void sendAuctionHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== Commandes Enchères WorldX ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/rg auction list - Affiche les enchères en cours", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/rg auction bid <id> <montant> - Place une surenchère sur un claim", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/rg auction start <id> <prix> [heures] - Démarrer une enchère (Admin)", NamedTextColor.YELLOW));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 2) {
            return Arrays.asList("list", "bid", "start").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 3 && (args[1].equalsIgnoreCase("bid") || args[1].equalsIgnoreCase("start"))) {
            return plugin.getRegionManager().getRegions().keySet().stream()
                    .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
