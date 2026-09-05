package fr.skynex.worldx.region;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.edit.TimeMachineEngine;
import org.bukkit.entity.Player;

/**
 * Encapsulates TimeMachine operations.
 */
public class TimeMachineService {

    private final WorldX plugin;

    public TimeMachineService(WorldX plugin) {
        this.plugin = plugin;
    }

    public boolean travelTime(Player player, String regionId, String offset) {
        if (!plugin.getConfig().getBoolean("features.timemachine", true)) {
            return false;
        }

        Region region = plugin.getRegionManager().getRegion(regionId);
        if (region == null || player == null) return false;

        TimeMachineEngine.travelTime(plugin, player, region, offset);
        return true;
    }
}
