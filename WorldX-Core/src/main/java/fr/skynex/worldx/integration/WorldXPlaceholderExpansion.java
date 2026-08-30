package fr.skynex.worldx.integration;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.region.Region;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Collectors;

public class WorldXPlaceholderExpansion extends PlaceholderExpansion {

    private final WorldX plugin;

    public WorldXPlaceholderExpansion(WorldX plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getAuthor() {
        return "Skynex";
    }

    @Override
    public @NotNull String getIdentifier() {
        return "worldx";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null) {
            return "";
        }

        Player player = offlinePlayer.getPlayer();
        if (player == null) {
            return "";
        }

        String lowerParams = params.toLowerCase();

        // 1. %worldx_current_region%
        if (lowerParams.equals("current_region")) {
            Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(player.getLocation());
            return region != null ? region.getId() : "none";
        }

        // 2. %worldx_region_owner_<region_id>%
        if (lowerParams.startsWith("region_owner_")) {
            String regionId = params.substring("region_owner_".length());
            Region region = plugin.getRegionManager().getRegion(regionId);
            if (region == null) {
                return "none";
            }
            if (region.getOwners().isEmpty()) {
                return "none";
            }
            return region.getOwners().stream()
                    .map(uuid -> {
                        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                        String name = op.getName();
                        return name != null ? name : uuid.toString();
                    })
                    .collect(Collectors.joining(", "));
        }

        // 3. %worldx_in_pvp_zone%
        if (lowerParams.equals("in_pvp_zone")) {
            Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(player.getLocation());
            if (region != null) {
                String pvpVal = plugin.getRegionManager().getEffectiveFlagValue(region, "pvp");
                return "allow".equalsIgnoreCase(pvpVal) ? "Oui" : "Non";
            }
            return "Oui"; // PvP is allowed by default in wild / no-region
        }

        return null;
    }
}
