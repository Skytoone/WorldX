package fr.skynex.worldx.region;

import org.bukkit.Location;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Region {

    private final String id;
    private final String worldName;
    
    // For CUBOID: min/max coordinates
    // For SPHERE: minX, minY, minZ is center; maxX is radius
    private int minX, minY, minZ;
    private int maxX, maxY, maxZ;
    
    private ShapeType shapeType = ShapeType.CUBOID;
    
    private int priority;
    private String parentId;
    
    private final List<UUID> owners;
    private final List<UUID> members;
    private final Map<String, String> flags;

    private List<int[]> polyPoints = new ArrayList<>();
    private String boundSchematic;

    public List<int[]> getPolyPoints() {
        return polyPoints;
    }

    public void setPolyPoints(List<int[]> polyPoints) {
        this.polyPoints = polyPoints;
    }

    public String getBoundSchematic() {
        return boundSchematic;
    }

    public void setBoundSchematic(String boundSchematic) {
        this.boundSchematic = boundSchematic;
    }

    public Region(String id, String worldName, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.id = id;
        this.worldName = worldName;
        this.owners = new ArrayList<>();
        this.members = new ArrayList<>();
        this.flags = new HashMap<>();
        this.priority = 0;
        
        recalculateBoundaries(x1, y1, z1, x2, y2, z2);
    }
    // Constructor for database loading
    public Region(String id, String worldName, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, ShapeType shapeType) {
        this.id = id;
        this.worldName = worldName;
        this.owners = new ArrayList<>();
        this.members = new ArrayList<>();
        this.flags = new HashMap<>();
        this.priority = 0;
        this.shapeType = shapeType;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }
    // Constructor for sphere
    public Region(String id, String worldName, int centerX, int centerY, int centerZ, int radius) {
        this.id = id;
        this.worldName = worldName;
        this.owners = new ArrayList<>();
        this.members = new ArrayList<>();
        this.flags = new HashMap<>();
        this.priority = 0;
        this.shapeType = ShapeType.SPHERE;
        
        this.minX = centerX;
        this.minY = centerY;
        this.minZ = centerZ;
        this.maxX = radius; // maxX stores radius
        // Bounding box logic for chunk indexing & visualizers
        this.maxY = centerY + radius;
        this.maxZ = centerZ + radius;
    }

    // Constructor for global region
    public Region(String id, String worldName) {
        this.id = id;
        this.worldName = worldName;
        this.owners = new ArrayList<>();
        this.members = new ArrayList<>();
        this.flags = new HashMap<>();
        this.priority = -1;
        this.shapeType = ShapeType.GLOBAL;
        this.minX = Integer.MIN_VALUE;
        this.minY = Integer.MIN_VALUE;
        this.minZ = Integer.MIN_VALUE;
        this.maxX = Integer.MAX_VALUE;
        this.maxY = Integer.MAX_VALUE;
        this.maxZ = Integer.MAX_VALUE;
    }

    public void recalculateBoundaries(int x1, int y1, int z1, int x2, int y2, int z2) {
        if (shapeType == ShapeType.CUBOID) {
            this.minX = Math.min(x1, x2);
            this.minY = Math.min(y1, y2);
            this.minZ = Math.min(z1, z2);
            this.maxX = Math.max(x1, x2);
            this.maxY = Math.max(y1, y2);
            this.maxZ = Math.max(z1, z2);
        }
    }

    public boolean contains(int x, int y, int z) {
        if (shapeType == ShapeType.GLOBAL) {
            return true;
        }
        if (shapeType == ShapeType.SPHERE) {
            double distSq = Math.pow(x - minX, 2) + Math.pow(y - minY, 2) + Math.pow(z - minZ, 2);
            return distSq <= Math.pow(maxX, 2); // maxX is radius
        } else if (shapeType == ShapeType.CYLINDER) {
            double distSq = Math.pow(x - minX, 2) + Math.pow(z - minZ, 2);
            return distSq <= Math.pow(maxX, 2) && y >= minY && y <= maxY; // minX/minZ is center, maxX is radius
        } else if (shapeType == ShapeType.POLYGON) {
            if (y < minY || y > maxY) {
                return false;
            }
            return isPointInPolygon(x, z);
        } else {
            return x >= minX && x <= maxX &&
                   y >= minY && y <= maxY &&
                   z >= minZ && z <= maxZ;
        }
    }

    private boolean isPointInPolygon(int x, int z) {
        if (polyPoints == null || polyPoints.size() < 3) {
            return false;
        }
        boolean inside = false;
        int numPoints = polyPoints.size();
        for (int i = 0, j = numPoints - 1; i < numPoints; j = i++) {
            int[] pi = polyPoints.get(i);
            int[] pj = polyPoints.get(j);
            if (((pi[1] > z) != (pj[1] > z)) &&
                (x < (pj[0] - pi[0]) * (z - pi[1]) / (pj[1] - pi[1]) + pi[0])) {
                inside = !inside;
            }
        }
        return inside;
    }

    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        if (!loc.getWorld().getName().equals(worldName)) {
            return false;
        }
        return contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public boolean intersects(Region other) {
        if (!this.worldName.equals(other.worldName)) {
            return false;
        }

        if (this.shapeType == ShapeType.GLOBAL || other.shapeType == ShapeType.GLOBAL) {
            return true;
        }

        // CUBOID ↔ CUBOID
        if (this.shapeType == ShapeType.CUBOID && other.shapeType == ShapeType.CUBOID) {
            return this.minX <= other.maxX && this.maxX >= other.minX &&
                   this.minY <= other.maxY && this.maxY >= other.minY &&
                   this.minZ <= other.maxZ && this.maxZ >= other.minZ;
        }

        // SPHERE ↔ SPHERE
        if (this.shapeType == ShapeType.SPHERE && other.shapeType == ShapeType.SPHERE) {
            double distSq = Math.pow(this.minX - other.minX, 2) + Math.pow(this.minY - other.minY, 2) + Math.pow(this.minZ - other.minZ, 2);
            return distSq <= Math.pow(this.maxX + other.maxX, 2);
        }

        // CYLINDER ↔ CYLINDER  (minX/minZ = center, maxX = radius, minY..maxY = height)
        if (this.shapeType == ShapeType.CYLINDER && other.shapeType == ShapeType.CYLINDER) {
            double horizDistSq = Math.pow(this.minX - other.minX, 2) + Math.pow(this.minZ - other.minZ, 2);
            boolean horizOverlap = horizDistSq <= Math.pow((double) this.maxX + other.maxX, 2);
            boolean vertOverlap  = this.minY <= other.maxY && this.maxY >= other.minY;
            return horizOverlap && vertOverlap;
        }

        // CYLINDER ↔ CUBOID (and reverse)
        if ((this.shapeType == ShapeType.CYLINDER && other.shapeType == ShapeType.CUBOID) ||
            (this.shapeType == ShapeType.CUBOID && other.shapeType == ShapeType.CYLINDER)) {
            Region cyl = this.shapeType == ShapeType.CYLINDER ? this : other;
            Region box = this.shapeType == ShapeType.CUBOID    ? this : other;
            // Closest point on cuboid XZ to cylinder center
            double closestX = Math.max(box.minX, Math.min(cyl.minX, box.maxX));
            double closestZ = Math.max(box.minZ, Math.min(cyl.minZ, box.maxZ));
            double horizDistSq = Math.pow(cyl.minX - closestX, 2) + Math.pow(cyl.minZ - closestZ, 2);
            boolean horizOverlap = horizDistSq <= Math.pow(cyl.maxX, 2);
            boolean vertOverlap  = cyl.minY <= box.maxY && cyl.maxY >= box.minY;
            return horizOverlap && vertOverlap;
        }

        // SPHERE ↔ CUBOID (and reverse)
        if ((this.shapeType == ShapeType.SPHERE && other.shapeType == ShapeType.CUBOID) ||
            (this.shapeType == ShapeType.CUBOID && other.shapeType == ShapeType.SPHERE)) {
            Region sphere = this.shapeType == ShapeType.SPHERE ? this : other;
            Region cuboid = this.shapeType == ShapeType.CUBOID ? this : other;
            double closestX = Math.max(cuboid.minX, Math.min(sphere.minX, cuboid.maxX));
            double closestY = Math.max(cuboid.minY, Math.min(sphere.minY, cuboid.maxY));
            double closestZ = Math.max(cuboid.minZ, Math.min(sphere.minZ, cuboid.maxZ));
            double distSq = Math.pow(sphere.minX - closestX, 2) + Math.pow(sphere.minY - closestY, 2) + Math.pow(sphere.minZ - closestZ, 2);
            return distSq <= Math.pow(sphere.maxX, 2);
        }

        // POLYGON or any other combination: conservative bounding-box fallback
        return this.minX <= other.maxX && this.maxX >= other.minX &&
               this.minY <= other.maxY && this.maxY >= other.minY &&
               this.minZ <= other.maxZ && this.maxZ >= other.minZ;
    }

    public long getVolume() {
        switch (shapeType) {
            case SPHERE:
                return (long) (4.0 / 3.0 * Math.PI * Math.pow(maxX, 3)); // maxX is radius
            case CYLINDER:
                // maxX = radius, minY..maxY = height
                return (long) (Math.PI * Math.pow(maxX, 2) * (maxY - minY + 1));
            case POLYGON:
                // Bounding-box approximation (polygon points vary; exact area would need shoelace formula)
                return (long) (maxX - minX + 1) * (long) (maxY - minY + 1) * (long) (maxZ - minZ + 1);
            default: // CUBOID
                return (long) (maxX - minX + 1) * (long) (maxY - minY + 1) * (long) (maxZ - minZ + 1);
        }
    }

    public boolean isOwner(UUID uuid) {
        return owners.contains(uuid);
    }

    public boolean isMember(UUID uuid) {
        return owners.contains(uuid) || members.contains(uuid);
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public ShapeType getShapeType() {
        return shapeType;
    }

    public void setShapeType(ShapeType shapeType) {
        this.shapeType = shapeType;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public List<UUID> getOwners() {
        return Collections.unmodifiableList(owners);
    }

    /**
     * Add an owner UUID directly (use instead of getOwners().add(...)).
     */
    public void addOwner(UUID uuid) {
        if (!owners.contains(uuid)) owners.add(uuid);
    }

    /**
     * Remove an owner UUID. Returns true if the list changed.
     */
    public boolean removeOwner(UUID uuid) {
        return owners.remove(uuid);
    }

    /**
     * Clear all owners (used during rent transfers).
     */
    public void clearOwners() {
        owners.clear();
    }

    public List<UUID> getMembers() {
        return Collections.unmodifiableList(members);
    }

    /**
     * Add a member UUID directly (use instead of getMembers().add(...)).
     */
    public void addMember(UUID uuid) {
        if (!members.contains(uuid)) members.add(uuid);
    }

    /**
     * Remove a member UUID. Returns true if the list changed.
     */
    public boolean removeMember(UUID uuid) {
        return members.remove(uuid);
    }

    /**
     * Clear all members (used during rent transfers).
     */
    public void clearMembers() {
        members.clear();
    }

    public Map<String, String> getFlags() {
        return flags;
    }

    public String getFlagValue(String key) {
        return flags.get(key);
    }

    public void setFlagValue(String key, String value) {
        if (value == null) {
            flags.remove(key);
        } else {
            flags.put(key, value);
        }
    }
}
