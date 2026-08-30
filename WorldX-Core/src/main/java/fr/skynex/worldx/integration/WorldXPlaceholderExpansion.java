package fr.skynex.worldx.integration;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.region.Region;
import fr.skynex.worldx.util.PermissionQuotaManager;
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
        String lowerParams = params.toLowerCase();

        // 1. %worldx_current_region% & %worldx_region_name%
        if (lowerParams.equals("current_region") || lowerParams.equals("region_name")) {
            if (player == null) return "Wilderness";
            Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(player.getLocation());
            return region != null ? region.getId() : "Wilderness";
        }

        // 2. %worldx_region_owner% (for current region player is standing in)
        if (lowerParams.equals("region_owner")) {
            if (player == null) return "none";
            Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(player.getLocation());
            if (region == null || region.getOwners().isEmpty()) {
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

        // 3. %worldx_region_owner_<region_id>%
        if (lowerParams.startsWith("region_owner_")) {
            String regionId = params.substring("region_owner_".length());
            Region region = plugin.getRegionManager().getRegion(regionId);
            if (region == null || region.getOwners().isEmpty()) {
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

        // 4. %worldx_in_pvp_zone%
        if (lowerParams.equals("in_pvp_zone")) {
            if (player == null) return "Oui";
            Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(player.getLocation());
            if (region != null) {
                String pvpVal = plugin.getRegionManager().getEffectiveFlagValue(region, "pvp");
                return "allow".equalsIgnoreCase(pvpVal) ? "Oui" : "Non";
            }
            return "Oui";
        }

        // 5. %worldx_claims_used%
        if (lowerParams.equals("claims_used")) {
            long count = plugin.getRegionManager().getRegions().values().stream()
                    .filter(r -> r.getOwners().contains(offlinePlayer.getUniqueId()))
                    .count();
            return String.valueOf(count);
        }

        // 6. %worldx_claims_max%
        if (lowerParams.equals("claims_max")) {
            if (player == null) return "3";
            int max = PermissionQuotaManager.getMaxClaims(plugin, player);
            return max == Integer.MAX_VALUE ? "∞" : String.valueOf(max);
        }

        // 7. %worldx_claims_remaining%
        if (lowerParams.equals("claims_remaining")) {
            if (player == null) return "0";
            int max = PermissionQuotaManager.getMaxClaims(plugin, player);
            if (max == Integer.MAX_VALUE) return "∞";
            long count = plugin.getRegionManager().getRegions().values().stream()
                    .filter(r -> r.getOwners().contains(offlinePlayer.getUniqueId()))
                    .count();
            return String.valueOf(Math.max(0, max - count));
        }

        // 8. %worldx_edit_quota%
        if (lowerParams.equals("edit_quota")) {
            if (player == null) return "10000";
            int quota = PermissionQuotaManager.getEditQuota(plugin, player);
            return quota == Integer.MAX_VALUE ? "∞" : String.valueOf(quota);
        }

        // 9. %worldx_history_size%
        if (lowerParams.equals("history_size")) {
            if (player == null) return "20";
            return String.valueOf(PermissionQuotaManager.getMaxHistorySize(plugin, player));
        }

        return null;
    }
}
