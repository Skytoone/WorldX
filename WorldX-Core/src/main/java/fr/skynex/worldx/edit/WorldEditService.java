package fr.skynex.worldx.edit;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.session.Session;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates WorldEdit block operations (set, replace, count).
 */
public class WorldEditService {

    private final WorldX plugin;

    public WorldEditService(WorldX plugin) {
        this.plugin = plugin;
    }

    public boolean queueSetTask(Player player, BlockData blockData, Palette palette) {
        Session session = plugin.getSessionManager().getSession(player);
        if (!session.hasCompleteSelection()) return false;

        Location p1 = session.getPos1();
        Location p2 = session.getPos2();

        List<Location> points = session.getSelectionPoints();
        if (points.isEmpty()) {
            points = List.of(p1, p2);
        }

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        for (Location loc : points) {
            if (loc != null) {
                int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
            }
        }

        plugin.getBlockEditQueue().queueTask(new BlockEditQueue.EditTask(
                player.getUniqueId(),
                p1.getWorld().getName(),
                session.getSelectionType(),
                session.getSelectionPoints(),
                palette,
                blockData,
                null,
                false,
                minX, maxX, minY, maxY, minZ, maxZ,
                session.getActiveMask()
        ));
        return true;
    }

    public Map<Material, Integer> executeCount(Player player, Material targetMaterial) {
        Session session = plugin.getSessionManager().getSession(player);
        if (!session.hasCompleteSelection()) return Map.of();

        Location p1 = session.getPos1();
        Location p2 = session.getPos2();

        int minX = Math.min(p1.getBlockX(), p2.getBlockX());
        int maxX = Math.max(p1.getBlockX(), p2.getBlockX());
        int minY = Math.min(p1.getBlockY(), p2.getBlockY());
        int maxY = Math.max(p1.getBlockY(), p2.getBlockY());
        int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
        int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());

        Map<Material, Integer> counts = new HashMap<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Material type = p1.getWorld().getBlockAt(x, y, z).getType();
                    if (targetMaterial == null || type == targetMaterial) {
                        counts.put(type, counts.getOrDefault(type, 0) + 1);
                    }
                }
            }
        }
        return counts;
    }
}
