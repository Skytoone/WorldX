package fr.skynex.worldx.command.sub;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.region.RegionManagementService;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.CommandSender;

public class RegionDeleteSubCommand implements WorldXSubCommand {

    private final WorldX plugin;
    private final RegionManagementService regionService;

    public RegionDeleteSubCommand(WorldX plugin, RegionManagementService regionService) {
        this.plugin = plugin;
        this.regionService = regionService;
    }

    @Override
    public String getName() {
        return "delete";
    }

    @Override
    public String getDescription() {
        return "Supprime une région";
    }

    @Override
    public String getSyntax() {
        return "/rg delete <id>";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("worldx.region.admin")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: " + getSyntax());
            return true;
        }

        String id = args[1];
        boolean success = regionService.deleteRegion(id, sender.getName());
        if (success) {
            sender.sendMessage(ChatColor.GREEN + "La région \"" + id + "\" a été supprimée avec succès.");
        } else {
            sender.sendMessage(ChatColor.RED + "La région \"" + id + "\" n'existe pas.");
        }
        return true;
    }
}
