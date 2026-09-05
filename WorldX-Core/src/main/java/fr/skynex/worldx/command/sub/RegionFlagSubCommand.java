package fr.skynex.worldx.command.sub;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.region.RegionFlagService;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.CommandSender;

public class RegionFlagSubCommand implements WorldXSubCommand {

    private final WorldX plugin;
    private final RegionFlagService flagService;

    public RegionFlagSubCommand(WorldX plugin, RegionFlagService flagService) {
        this.plugin = plugin;
        this.flagService = flagService;
    }

    @Override
    public String getName() {
        return "flag";
    }

    @Override
    public String getDescription() {
        return "Modifie les flags d'une région";
    }

    @Override
    public String getSyntax() {
        return "/rg flag <id> <flag> [valeur|none]";
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
        String flagName = args[2].toLowerCase();

        if (!flagService.isKnownFlag(flagName)) {
            sender.sendMessage(ChatColor.YELLOW + "Attention : \"" + flagName + "\" n'est pas un flag standard.");
        }

        String value = null;
        if (args.length >= 4 && !args[3].equalsIgnoreCase("none")) {
            StringBuilder sb = new StringBuilder();
            for (int i = 3; i < args.length; i++) {
                sb.append(args[i]).append(" ");
            }
            value = sb.toString().trim();
        }

        boolean success = flagService.setFlag(regionId, flagName, value, sender.getName());
        if (success) {
            if (value == null) {
                sender.sendMessage(ChatColor.GREEN + "Flag \"" + flagName + "\" supprimé de la région.");
            } else {
                sender.sendMessage(ChatColor.GREEN + "Flag \"" + flagName + "\" défini sur \"" + value + "\".");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Région introuvable.");
        }

        return true;
    }
}
