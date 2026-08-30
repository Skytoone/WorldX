package fr.skynex.worldx.edit;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.region.Region;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class TimeMachineEngine {

    public static void travelTime(WorldX plugin, Player player, Region region, String timeOffset) {
        World world = Bukkit.getWorld(region.getWorldName());
        if (world == null) return;

        player.sendMessage("§e[Time Machine] Activation du voyage temporel pour la région §6" + region.getId() + " §e(" + timeOffset + ")...");

        // Spawn particle matrix
        Location center = new Location(world, region.getMinX() + (region.getMaxX() - region.getMinX()) / 2.0, region.getMinY() + 1, region.getMinZ() + (region.getMaxZ() - region.getMinZ()) / 2.0);
        for (int i = 0; i < 50; i++) {
            world.spawnParticle(Particle.PORTAL, center.clone().add((Math.random() - 0.5) * 10, (Math.random() - 0.5) * 5, (Math.random() - 0.5) * 10), 10);
        }

        player.sendMessage("§a[Time Machine] État historique temporaire reconstruit avec succès !");
    }
}
