package fr.skynex.worldx.region;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.integration.DiscordWebhookLogger;

import java.util.Arrays;
import java.util.List;

/**
 * Encapsulates Region Flag management operations.
 */
public class RegionFlagService {

    private final WorldX plugin;
    private static final List<String> KNOWN_FLAGS = Arrays.asList(
            "build", "pvp", "use", "mob-spawn", "entry", "greeting", "farewell",
            "heal-delay", "feed-delay", "gamemode", "crop-trample", "item-frame-rotate",
            "hanging-destroy", "fall-damage", "hunger", "keep-inventory", "mob-target",
            "command-blacklist", "command-whitelist", "fire-spread", "explosion", "leaf-decay",
            "god-mode", "respawn-location", "projectile", "money-multiplier", "money-loot-on-kill",
            "liquid-flow", "piston-interact", "fire-ignite", "auto-replant", "villager-trade",
            "armor-stand-interact", "exit", "gravity-modifier", "particles-ambient",
            "auto-rollback-interval", "temporary-blocks", "schematic-regen-on-entry",
            "max-players", "mob-damage-multiplier", "deny-elytra-boost", "absorption-shield",
            "deny-ender-chest", "blocked-crafting", "regional-chat", "announcement-interval",
            "void-teleport", "region-chat-format", "no-potion-drink", "fog-color",
            "keep-effects-on-death", "prevent-teleport", "intrusion-command"
    );

    public RegionFlagService(WorldX plugin) {
        this.plugin = plugin;
    }

    public boolean isKnownFlag(String flagName) {
        return flagName != null && KNOWN_FLAGS.contains(flagName.toLowerCase());
    }

    public boolean setFlag(String regionId, String flagName, String value, String modifiedBy) {
        Region region = plugin.getRegionManager().getRegion(regionId);
        if (region == null || flagName == null) return false;

        String key = flagName.toLowerCase();
        if (value == null || value.equalsIgnoreCase("none")) {
            region.getFlags().remove(key);
            plugin.getRegionManager().addRegion(region);
            DiscordWebhookLogger.logRegionAction(plugin, "RETRAIT_FLAG", modifiedBy, regionId, "Flag: " + key);
        } else {
            region.getFlags().put(key, value);
            plugin.getRegionManager().addRegion(region);
            DiscordWebhookLogger.logRegionAction(plugin, "MODIF_FLAG", modifiedBy, regionId, "Flag: " + key + " = " + value);
        }
        return true;
    }
}
