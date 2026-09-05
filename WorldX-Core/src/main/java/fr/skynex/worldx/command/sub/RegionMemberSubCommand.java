package fr.skynex.worldx.command.sub;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.region.RegionManagementService;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

public class RegionMemberSubCommand implements WorldXSubCommand {

    private final WorldX plugin;
    private final RegionManagementService regionService;
    private final String action;
    private final boolean isOwner;

    public RegionMemberSubCommand(WorldX plugin, RegionManagementService regionService, String action, boolean isOwner) {
        this.plugin = plugin;
        this.regionService = regionService;
        this.action = action;
        this.isOwner = isOwner;
    }

    @Override
    public String getName() {
        return action;
    }

    @Override
    public String getDescription() {
        return (action.startsWith("add") ? "Ajoute" : "Retire") + " un " + (isOwner ? "propriétaire" : "membre");
    }

    @Override
    public String getSyntax() {
        return "/rg " + action + " <id> <joueur>";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("worldx.region.admin")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: " + getSyntax());
            return true;
        }

        String regionId = args[1];
        String targetName = args[2];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            sender.sendMessage(ChatColor.RED + "Joueur inconnu ou n'ayant jamais joué.");
            return true;
        }

        boolean success;
        if (action.startsWith("add")) {
            success = regionService.addMemberOrOwner(regionId, target.getUniqueId(), isOwner);
            if (success) {
                sender.sendMessage(ChatColor.GREEN + targetName + " a été ajouté comme " + (isOwner ? "propriétaire" : "membre") + " de la région.");
            }
        } else {
            success = regionService.removeMemberOrOwner(regionId, target.getUniqueId(), isOwner);
            if (success) {
                sender.sendMessage(ChatColor.GREEN + targetName + " a été retiré des " + (isOwner ? "propriétaires" : "membres") + " de la région.");
            }
        }

        if (!success) {
            sender.sendMessage(ChatColor.RED + "Région introuvable.");
        }

        return true;
    }
}
