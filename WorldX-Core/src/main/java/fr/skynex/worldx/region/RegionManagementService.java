package fr.skynex.worldx.region;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.integration.DiscordWebhookLogger;
import fr.skynex.worldx.session.Session;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Encapsulates core region business operations (creation, deletion, membership, priority, claiming).
 */
public class RegionManagementService {

    private final WorldX plugin;

    public RegionManagementService(WorldX plugin) {
        this.plugin = plugin;
    }

    public boolean createRegion(Player player, String id, String shape) {
        if (plugin.getRegionManager().getRegion(id) != null) {
            return false;
        }

        Region region;
        if (id.equalsIgnoreCase("__global__") || shape.equalsIgnoreCase("global")) {
            region = new Region(id, player.getWorld().getName());
        } else {
            Session session = plugin.getSessionManager().getSession(player);
            if (!session.hasCompleteSelection()) {
                return false;
            }

            Location p1 = session.getPos1();
            Location p2 = session.getPos2();

            if (shape.isEmpty()) {
                shape = session.getSelectionType().name().toLowerCase();
            }

            if (shape.equalsIgnoreCase("sphere")) {
                if (p1 == null || p2 == null) return false;
                int radius = Math.max(1, (int) p1.distance(p2));
                region = new Region(id, p1.getWorld().getName(), p1.getBlockX(), p1.getBlockY(), p1.getBlockZ(), radius);
            } else if (shape.equalsIgnoreCase("cylinder")) {
                if (p1 == null || p2 == null) return false;
                int radius = Math.max(1, (int) Math.sqrt(Math.pow(p1.getBlockX() - p2.getBlockX(), 2) + Math.pow(p1.getBlockZ() - p2.getBlockZ(), 2)));
                int minY = Math.min(p1.getBlockY(), p2.getBlockY());
                int maxY = Math.max(p1.getBlockY(), p2.getBlockY());
                region = new Region(id, p1.getWorld().getName(), p1.getBlockX(), minY, p1.getBlockZ(), radius, maxY, 0, ShapeType.CYLINDER);
            } else if (shape.equalsIgnoreCase("polygon") || shape.equalsIgnoreCase("poly")) {
                List<Location> points = session.getSelectionPoints();
                if (points.size() < 3) return false;

                int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
                int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;

                List<int[]> polyPoints = new ArrayList<>();
                for (Location loc : points) {
                    int px = loc.getBlockX(), py = loc.getBlockY(), pz = loc.getBlockZ();
                    minX = Math.min(minX, px); minZ = Math.min(minZ, pz);
                    maxX = Math.max(maxX, px); maxZ = Math.max(maxZ, pz);
                    minY = Math.min(minY, py); maxY = Math.max(maxY, py);
                    polyPoints.add(new int[]{px, pz});
                }

                region = new Region(id, points.get(0).getWorld().getName(), minX, minY, minZ, maxX, maxY, maxZ, ShapeType.POLYGON);
                region.setPolyPoints(polyPoints);
            } else {
                if (p1 == null || p2 == null) return false;
                region = new Region(id, p1.getWorld().getName(), p1.getBlockX(), p1.getBlockY(), p1.getBlockZ(), p2.getBlockX(), p2.getBlockY(), p2.getBlockZ());
            }
        }

        region.addOwner(player.getUniqueId());
        Region cloned = RegionAction.cloneRegion(region);
        Session sessionObj = plugin.getSessionManager().getSession(player);
        if (sessionObj != null) {
            sessionObj.addRegionUndo(new RegionAction(RegionAction.Type.CREATE, null, cloned));
        }

        plugin.getRegionManager().addRegion(region);
        DiscordWebhookLogger.logRegionAction(plugin, "CRÉATION", player.getName(), id,
                "Forme: " + (region.getShapeType() != null ? region.getShapeType().name() : "CUBOID") + ", Monde: " + region.getWorldName());

        return true;
    }

    public boolean deleteRegion(String id, String deletedBy) {
        Region region = plugin.getRegionManager().getRegion(id);
        if (region == null) return false;

        plugin.getRegionManager().removeRegion(id);
        DiscordWebhookLogger.logRegionAction(plugin, "SUPPRESSION", deletedBy, id, "Monde: " + region.getWorldName());
        return true;
    }

    public boolean addMemberOrOwner(String regionId, UUID uuid, boolean isOwner) {
        Region region = plugin.getRegionManager().getRegion(regionId);
        if (region == null) return false;

        if (isOwner) {
            region.addOwner(uuid);
        } else {
            region.addMember(uuid);
        }
        plugin.getRegionManager().addRegion(region);
        return true;
    }

    public boolean removeMemberOrOwner(String regionId, UUID uuid, boolean isOwner) {
        Region region = plugin.getRegionManager().getRegion(regionId);
        if (region == null) return false;

        if (isOwner) {
            region.removeOwner(uuid);
        } else {
            region.removeMember(uuid);
        }
        plugin.getRegionManager().addRegion(region);
        return true;
    }
}
