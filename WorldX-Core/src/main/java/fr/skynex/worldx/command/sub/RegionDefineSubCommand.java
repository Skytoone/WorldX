package fr.skynex.worldx.command.sub;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.region.RegionManagementService;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RegionDefineSubCommand implements WorldXSubCommand {

    private final WorldX plugin;
    private final RegionManagementService regionService;

    public RegionDefineSubCommand(WorldX plugin, RegionManagementService regionService) {
        this.plugin = plugin;
        this.regionService = regionService;
    }

    @Override
    public String getName() {
        return "define";
    }

    @Override
    public String getDescription() {
        return "Crée une région à partir de la sélection";
    }

    @Override
    public String getSyntax() {
        return "/rg define <id> [cuboid|sphere|cylinder|polygon|global]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Seuls les joueurs peuvent créer des régions.");
            return true;
        }

        if (!player.hasPermission("worldx.region.admin")) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission de gérer les régions.");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: " + getSyntax());
            return true;
        }

        String id = args[1];
        String shape = args.length >= 3 ? args[2] : "";

        boolean success = regionService.createRegion(player, id, shape);
        if (success) {
            player.sendMessage(ChatColor.GREEN + "Région \"" + id + "\" créée avec succès !");
        } else {
            player.sendMessage(ChatColor.RED + "Impossible de créer la région \"" + id + "\". Vérifiez la sélection ou que l'ID n'existe pas déjà.");
        }
        return true;
    }
}
