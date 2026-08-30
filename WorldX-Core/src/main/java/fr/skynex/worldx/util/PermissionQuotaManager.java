package fr.skynex.worldx.util;

import fr.skynex.worldx.WorldX;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

public class PermissionQuotaManager {

    public static int getMaxClaims(WorldX plugin, Player player) {
        int defaultVal = (plugin != null && plugin.getConfig() != null) ? plugin.getConfig().getInt("claim.default-max-count", 3) : 3;
        if (player == null) return defaultVal;
        if (player.isOp() || player.hasPermission("worldx.admin")) {
            return Integer.MAX_VALUE;
        }

        int max = defaultVal;
        for (PermissionAttachmentInfo attachment : player.getEffectivePermissions()) {
            String perm = attachment.getPermission().toLowerCase();
            if (perm.startsWith("worldx.claim.max.")) {
                try {
                    int val = Integer.parseInt(perm.substring("worldx.claim.max.".length()));
                    max = Math.max(max, val);
                } catch (NumberFormatException ignored) {}
            }
        }
        return max;
    }

    public static int getMaxBlocksPerClaim(WorldX plugin, Player player) {
        int defaultVal = (plugin != null && plugin.getConfig() != null) ? plugin.getConfig().getInt("claim.default-max-blocks", 50000) : 50000;
        if (player == null) return defaultVal;
        if (player.isOp() || player.hasPermission("worldx.admin")) {
            return Integer.MAX_VALUE;
        }

        int max = defaultVal;
        for (PermissionAttachmentInfo attachment : player.getEffectivePermissions()) {
            String perm = attachment.getPermission().toLowerCase();
            if (perm.startsWith("worldx.claim.maxblocks.")) {
                try {
                    int val = Integer.parseInt(perm.substring("worldx.claim.maxblocks.".length()));
                    max = Math.max(max, val);
                } catch (NumberFormatException ignored) {}
            }
        }
        return max;
    }

    public static int getEditQuota(WorldX plugin, Player player) {
        int defaultVal = (plugin != null && plugin.getConfig() != null) ? plugin.getConfig().getInt("edit.default-quota", 10000) : 10000;
        if (player == null) return defaultVal;
        if (player.isOp() || player.hasPermission("worldx.admin")) {
            return Integer.MAX_VALUE;
        }

        int max = defaultVal;
        for (PermissionAttachmentInfo attachment : player.getEffectivePermissions()) {
            String perm = attachment.getPermission().toLowerCase();
            if (perm.startsWith("worldx.edit.quota.")) {
                try {
                    int val = Integer.parseInt(perm.substring("worldx.edit.quota.".length()));
                    max = Math.max(max, val);
                } catch (NumberFormatException ignored) {}
            }
        }
        return max;
    }

    public static int getMaxHistorySize(WorldX plugin, Player player) {
        int defaultVal = (plugin != null && plugin.getConfig() != null) ? plugin.getConfig().getInt("edit.max-undo-history-per-player", 20) : 20;
        if (player == null) return defaultVal;

        int max = defaultVal;
        for (PermissionAttachmentInfo attachment : player.getEffectivePermissions()) {
            String perm = attachment.getPermission().toLowerCase();
            if (perm.startsWith("worldx.history.size.")) {
                try {
                    int val = Integer.parseInt(perm.substring("worldx.history.size.".length()));
                    max = Math.max(max, val);
                } catch (NumberFormatException ignored) {}
            }
        }
        return max;
    }
}
