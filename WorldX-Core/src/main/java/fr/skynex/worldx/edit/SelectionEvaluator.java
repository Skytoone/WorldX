package fr.skynex.worldx.edit;

import fr.skynex.worldx.session.Session;
import fr.skynex.worldx.region.ShapeType;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SelectionEvaluator {

    public static List<BlockEditQueue.BlockChangeInfo> getBlocksInSelection(Session session, Palette palette, BlockData blockData) {
        List<Location> points = session.getSelectionPoints();
        if (points.isEmpty()) {
            Location p1 = session.getPos1();
            Location p2 = session.getPos2();
            if (p1 == null || p2 == null) {
                return Collections.emptyList();
            }
            points = List.of(p1, p2);
        }
        int minX = 0, maxX = 0, minY = 0, maxY = 0, minZ = 0, maxZ = 0;
        boolean first = true;
        for (Location loc : points) {
            if (loc != null) {
                int x = loc.getBlockX();
                int y = loc.getBlockY();
                int z = loc.getBlockZ();
                if (first) {
                    minX = x;
                    maxX = x;
                    minY = y;
                    maxY = y;
                    minZ = z;
                    maxZ = z;
                    first = false;
                } else {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                    if (z < minZ) minZ = z;
                    if (z > maxZ) maxZ = z;
                }
            }
        }

        List<BlockEditQueue.BlockChangeInfo> changes = new ArrayList<>();
        ShapeType type = session.getSelectionType();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean inside = false;
                    if (type == ShapeType.CUBOID) {
                        inside = true;
                    } else if (type == ShapeType.POLYGON) {
                        inside = isPointIn2DPolygon(x, z, points) && y >= minY && y <= maxY;
                    } else if (type == ShapeType.LASSO) {
                        inside = ConvexHull3D.contains(x, y, z, points);
                    }

                    if (inside) {
                        BlockData toSet = (palette != null) ? palette.sampleBlock() : blockData;
                        changes.add(new BlockEditQueue.BlockChangeInfo(x, y, z, toSet));
                    }
                }
            }
        }
        return changes;
    }

    private static boolean isPointIn2DPolygon(double px, double pz, List<Location> points) {
        boolean inside = false;
        int numPoints = points.size();
        for (int i = 0, j = numPoints - 1; i < numPoints; j = i++) {
            Location pi = points.get(i);
            Location pj = points.get(j);
            if (((pi.getZ() > pz) != (pj.getZ() > pz)) &&
                (px < (pj.getX() - pi.getX()) * (pz - pi.getZ()) / (pj.getZ() - pi.getZ()) + pi.getX())) {
                inside = !inside;
            }
        }
        return inside;
    }
}
