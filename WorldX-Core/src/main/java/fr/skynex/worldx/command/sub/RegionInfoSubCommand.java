package fr.skynex.worldx.command.sub;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.region.Region;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class RegionInfoSubCommand implements WorldXSubCommand {

    private final WorldX plugin;

    public RegionInfoSubCommand(WorldX plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "info";
    }

    @Override
    public String getDescription() {
        return "Affiche des informations sur la région";
    }

    @Override
    public String getSyntax() {
        return "/rg info [id]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        Region region = null;

        if (args.length >= 2) {
            region = plugin.getRegionManager().getRegion(args[1]);
        } else if (sender instanceof Player player) {
            List<Region> list = plugin.getRegionManager().getRegionsAt(player.getLocation());
            if (!list.isEmpty()) {
                list.sort((r1, r2) -> Integer.compare(r2.getPriority(), r1.getPriority()));
                region = list.get(0);
            }
        }

        if (region == null) {
            sender.sendMessage(ChatColor.RED + "Aucune région trouvée ici, ou ID non spécifié.");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "=== Région : " + region.getId() + " ===");
        sender.sendMessage(ChatColor.YELLOW + "Monde : " + ChatColor.WHITE + region.getWorldName());
        sender.sendMessage(ChatColor.YELLOW + "Priorité : " + ChatColor.WHITE + region.getPriority());
        sender.sendMessage(ChatColor.YELLOW + "Parent : " + ChatColor.WHITE + (region.getParentId() != null ? region.getParentId() : "Aucun"));
        sender.sendMessage(ChatColor.YELLOW + "Bornes : " + ChatColor.WHITE +
                String.format("(%d, %d, %d) à (%d, %d, %d)",
                        region.getMinX(), region.getMinY(), region.getMinZ(),
                        region.getMaxX(), region.getMaxY(), region.getMaxZ()));

        String owners = region.getOwners().stream()
                .map(uuid -> Bukkit.getOfflinePlayer(uuid).getName())
                .collect(Collectors.joining(", "));
        sender.sendMessage(ChatColor.YELLOW + "Propriétaires : " + ChatColor.WHITE + (owners.isEmpty() ? "Aucun" : owners));

        String members = region.getMembers().stream()
                .map(uuid -> Bukkit.getOfflinePlayer(uuid).getName())
                .collect(Collectors.joining(", "));
        sender.sendMessage(ChatColor.YELLOW + "Membres : " + ChatColor.WHITE + (members.isEmpty() ? "Aucun" : members));

        StringBuilder flagsSb = new StringBuilder();
        region.getFlags().forEach((key, val) -> flagsSb.append(key).append(": ").append(val).append(", "));
        String flagsStr = flagsSb.toString();
        if (!flagsStr.isEmpty()) {
            flagsStr = flagsStr.substring(0, flagsStr.length() - 2);
        } else {
            flagsStr = "Aucun";
        }
        sender.sendMessage(ChatColor.YELLOW + "Flags : " + ChatColor.WHITE + flagsStr);

        return true;
    }
}
