package fr.skynex.worldx.visual;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.region.Region;
import fr.skynex.worldx.session.Session;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

public class SelectionVisualizer extends BukkitRunnable {

    private final WorldX plugin;
    private final Map<UUID, ShowRegionInfo> activeRegionShows = new HashMap<>();
    private final Map<UUID, Boolean> forceParticles = new HashMap<>();

    // Block displays for mode: "display"
    private final Map<UUID, List<BlockDisplay>> activeSelectionDisplays = new HashMap<>();
    private final Map<UUID, List<BlockDisplay>> activeRegionDisplays = new HashMap<>();
    private final Map<UUID, List<org.bukkit.entity.Entity>> activeHandles = new HashMap<>();

    // Cache of displayed bounds to avoid redundant teleportation and packet spam
    private final Map<UUID, DisplayBounds> lastSelectionBounds = new HashMap<>();
    private final Map<UUID, DisplayBounds> lastRegionBounds = new HashMap<>();

    private final Particle.DustOptions selectionDust = new Particle.DustOptions(Color.fromRGB(255, 85, 255), 1.0f); // Light Purple
    private final Particle.DustOptions regionDust = new Particle.DustOptions(Color.fromRGB(85, 255, 85), 1.0f);    // Light Green

    public SelectionVisualizer(WorldX plugin) {
        this.plugin = plugin;
    }

    public synchronized void showRegion(UUID playerUUID, String regionId, int durationSeconds) {
        showRegion(playerUUID, regionId, durationSeconds, false);
    }

    public synchronized void showRegion(UUID playerUUID, String regionId, int durationSeconds, boolean forceParticleMode) {
        activeRegionShows.put(playerUUID, new ShowRegionInfo(regionId, System.currentTimeMillis() + (durationSeconds * 1000L)));
        forceParticles.put(playerUUID, forceParticleMode);
    }

    public synchronized void hideRegion(UUID playerUUID) {
        activeRegionShows.remove(playerUUID);
        forceParticles.remove(playerUUID);
        lastRegionBounds.remove(playerUUID);
        lastSelectionBounds.remove(playerUUID);
        
        List<BlockDisplay> bdList = activeRegionDisplays.remove(playerUUID);
        if (bdList != null) {
            for (BlockDisplay bd : bdList) {
                if (bd != null && bd.isValid()) {
                    bd.remove();
                }
            }
        }
        List<BlockDisplay> sdList = activeSelectionDisplays.remove(playerUUID);
        if (sdList != null) {
            for (BlockDisplay sd : sdList) {
                if (sd != null && sd.isValid()) {
                    sd.remove();
                }
            }
        }
        clearSelectionHandles(playerUUID);
    }

    public void cleanup() {
        for (List<BlockDisplay> list : activeSelectionDisplays.values()) {
            for (BlockDisplay bd : list) {
                if (bd != null && bd.isValid()) {
                    bd.remove();
                }
            }
        }
        activeSelectionDisplays.clear();
        lastSelectionBounds.clear();

        for (List<BlockDisplay> list : activeRegionDisplays.values()) {
            for (BlockDisplay bd : list) {
                if (bd != null && bd.isValid()) {
                    bd.remove();
                }
            }
        }
        activeRegionDisplays.clear();
        lastRegionBounds.clear();

        for (List<org.bukkit.entity.Entity> list : activeHandles.values()) {
            for (org.bukkit.entity.Entity e : list) {
                if (e != null && e.isValid()) {
                    e.remove();
                }
            }
        }
        activeHandles.clear();

        activeRegionShows.clear();
        forceParticles.clear();
    }

    public synchronized void clearWorld(World world) {
        if (world == null) return;
        String worldName = world.getName();

        lastSelectionBounds.entrySet().removeIf(e -> e.getValue().worldName().equals(worldName));
        lastRegionBounds.entrySet().removeIf(e -> e.getValue().worldName().equals(worldName));

        activeSelectionDisplays.values().forEach(list -> {
            if (list != null) {
                for (BlockDisplay bd : list) {
                    if (bd != null && bd.isValid() && bd.getWorld().getName().equals(worldName)) {
                        bd.remove();
                    }
                }
            }
        });
        activeRegionDisplays.values().forEach(list -> {
            if (list != null) {
                for (BlockDisplay bd : list) {
                    if (bd != null && bd.isValid() && bd.getWorld().getName().equals(worldName)) {
                        bd.remove();
                    }
                }
            }
        });
        activeHandles.values().forEach(list -> {
            if (list != null) {
                for (org.bukkit.entity.Entity e : list) {
                    if (e != null && e.isValid() && e.getWorld().getName().equals(worldName)) {
                        e.remove();
                    }
                }
            }
        });
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        String mode = plugin.getConfig().getString("visual.mode", "display").toLowerCase();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOnline()) continue;

            UUID uuid = player.getUniqueId();
            World world = player.getWorld();
            Session session = plugin.getSessionManager().getSession(uuid);

            boolean hasSelection = session != null && session.hasCompleteSelection() && 
                                  session.getPos1().getWorld().getName().equals(world.getName());

            ShowRegionInfo showInfo = getActiveShowRegion(uuid, now);
            Region region = showInfo != null ? plugin.getRegionManager().getRegion(showInfo.regionId) : null;
            boolean hasRegionShow = region != null && region.getWorldName().equals(world.getName());

            boolean isParticleForced = forceParticles.getOrDefault(uuid, false);
            String currentMode = isParticleForced ? "particles" : mode;

            if (currentMode.equals("display")) {
                // Handle Selection Display
                if (hasSelection && session != null) {
                    if (session.getSelectionType() == fr.skynex.worldx.region.ShapeType.POLYGON) {
                        double minY = Double.MAX_VALUE;
                        double maxY = Double.MIN_VALUE;
                        for (Location loc : session.getSelectionPoints()) {
                            minY = Math.min(minY, loc.getY());
                            maxY = Math.max(maxY, loc.getY());
                        }
                        updatePolygonDisplay(player, session.getSelectionPoints(), minY, maxY, true);
                    } else {
                        updateSelectionDisplay(player, session.getPos1(), session.getPos2());
                    }
                } else {
                    List<BlockDisplay> sdList = activeSelectionDisplays.remove(uuid);
                    if (sdList != null) {
                        for (BlockDisplay sd : sdList) {
                            if (sd != null && sd.isValid()) sd.remove();
                        }
                    }
                    lastSelectionBounds.remove(uuid);
                }

                // Handle Region Show Display
                if (hasRegionShow && region != null) {
                    if (region.getShapeType() == fr.skynex.worldx.region.ShapeType.POLYGON) {
                        List<Location> regionPoints = new ArrayList<>();
                        for (int[] p : region.getPolyPoints()) {
                            regionPoints.add(new Location(world, p[0], region.getMinY(), p[1]));
                        }
                        updatePolygonDisplay(player, regionPoints, region.getMinY(), region.getMaxY(), false);
                    } else {
                        updateRegionDisplay(player, region);
                    }
                } else {
                    List<BlockDisplay> bdList = activeRegionDisplays.remove(uuid);
                    if (bdList != null) {
                        for (BlockDisplay bd : bdList) {
                            if (bd != null && bd.isValid()) bd.remove();
                        }
                    }
                    lastRegionBounds.remove(uuid);
                }
            } else {
                // Fallback to particle visualization
                List<BlockDisplay> sdList = activeSelectionDisplays.remove(uuid);
                if (sdList != null) {
                    for (BlockDisplay sd : sdList) if (sd != null && sd.isValid()) sd.remove();
                }
                lastSelectionBounds.remove(uuid);

                List<BlockDisplay> bdList = activeRegionDisplays.remove(uuid);
                if (bdList != null) {
                    for (BlockDisplay bd : bdList) if (bd != null && bd.isValid()) bd.remove();
                }
                lastRegionBounds.remove(uuid);

                if (hasSelection && session != null) {
                    if (session.getSelectionType() == fr.skynex.worldx.region.ShapeType.LASSO) {
                        List<Location> points = session.getSelectionPoints();
                        for (int i = 0; i < points.size(); i++) {
                            Location start = points.get(i);
                            Location end = points.get((i + 1) % points.size());
                            drawLine(player, start.getX(), start.getY(), start.getZ(), end.getX(), end.getY(), end.getZ(), selectionDust);
                            for (int j = i + 2; j < points.size(); j++) {
                                Location other = points.get(j);
                                drawLine(player, start.getX(), start.getY(), start.getZ(), other.getX(), other.getY(), other.getZ(), selectionDust);
                            }
                        }
                    } else if (session.getSelectionType() == fr.skynex.worldx.region.ShapeType.POLYGON) {
                        List<Location> points = session.getSelectionPoints();
                        double minY = Double.MAX_VALUE;
                        double maxY = Double.MIN_VALUE;
                        for (Location loc : points) {
                            minY = Math.min(minY, loc.getY());
                            maxY = Math.max(maxY, loc.getY());
                        }
                        for (int i = 0; i < points.size(); i++) {
                            Location p = points.get(i);
                            Location next = points.get((i + 1) % points.size());
                            
                            Location botStart = new Location(world, p.getX(), minY, p.getZ());
                            Location botEnd = new Location(world, next.getX(), minY, next.getZ());
                            drawLine(player, botStart.getX(), botStart.getY(), botStart.getZ(), botEnd.getX(), botEnd.getY(), botEnd.getZ(), selectionDust);
                            
                            Location topStart = new Location(world, p.getX(), maxY + 1.0, p.getZ());
                            Location topEnd = new Location(world, next.getX(), maxY + 1.0, next.getZ());
                            drawLine(player, topStart.getX(), topStart.getY(), topStart.getZ(), topEnd.getX(), topEnd.getY(), topEnd.getZ(), selectionDust);
                            
                            drawLine(player, botStart.getX(), botStart.getY(), botStart.getZ(), topStart.getX(), topStart.getY(), topStart.getZ(), selectionDust);
                        }
                    } else {
                        Location p1 = session.getPos1();
                        Location p2 = session.getPos2();
                        double minX = Math.min(p1.getX(), p2.getX());
                        double maxX = Math.max(p1.getX(), p2.getX()) + 1.0;
                        double minY = Math.min(p1.getY(), p2.getY());
                        double maxY = Math.max(p1.getY(), p2.getY()) + 1.0;
                        double minZ = Math.min(p1.getZ(), p2.getZ());
                        double maxZ = Math.max(p1.getZ(), p2.getZ()) + 1.0;
                        drawCuboidWireframe(player, world, minX, minY, minZ, maxX, maxY, maxZ, selectionDust);
                    }
                }

                if (hasRegionShow && region != null) {
                    double minX = region.getMinX();
                    double maxX = region.getMaxX() + 1.0;
                    double minY = region.getMinY();
                    double maxY = region.getMaxY() + 1.0;
                    double minZ = region.getMinZ();
                    double maxZ = region.getMaxZ() + 1.0;

                    drawCuboidWireframe(player, world, minX, minY, minZ, maxX, maxY, maxZ, regionDust);
                }
            }

            // Update 3D resize handles
            String wandMatName = plugin.getConfig().getString("edit.wand-item", "WOODEN_AXE");
            org.bukkit.Material wandMat = org.bukkit.Material.matchMaterial(wandMatName);
            if (wandMat == null) wandMat = org.bukkit.Material.WOODEN_AXE;
            boolean holdsWand = player.getInventory().getItemInMainHand().getType() == wandMat;

            if (hasSelection && session != null && holdsWand) {
                updateSelectionHandles(player, session);
            } else {
                clearSelectionHandles(uuid);
            }
        }

        // Cleanup offline players from active visual tracking maps
        java.util.Set<UUID> allTracked = new java.util.HashSet<>();
        allTracked.addAll(activeHandles.keySet());
        allTracked.addAll(activeSelectionDisplays.keySet());
        allTracked.addAll(activeRegionDisplays.keySet());
        allTracked.addAll(activeRegionShows.keySet());

        List<UUID> offlineTracked = new ArrayList<>();
        for (UUID u : allTracked) {
            Player p = Bukkit.getPlayer(u);
            if (p == null || !p.isOnline()) {
                offlineTracked.add(u);
            }
        }
        for (UUID u : offlineTracked) {
            hideRegion(u);
        }
    }

    private void updateSelectionDisplay(Player player, Location p1, Location p2) {
        UUID uuid = player.getUniqueId();
        List<BlockDisplay> existingList = activeSelectionDisplays.get(uuid);
        BlockDisplay existing = (existingList != null && !existingList.isEmpty()) ? existingList.get(0) : null;

        double minX = Math.min(p1.getBlockX(), p2.getBlockX());
        double minY = Math.min(p1.getBlockY(), p2.getBlockY());
        double minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
        double dx = Math.abs(p1.getBlockX() - p2.getBlockX()) + 1.0;
        double dy = Math.abs(p1.getBlockY() - p2.getBlockY()) + 1.0;
        double dz = Math.abs(p1.getBlockZ() - p2.getBlockZ()) + 1.0;

        DisplayBounds current = new DisplayBounds(player.getWorld().getName(), minX, minY, minZ, dx, dy, dz);
        DisplayBounds last = lastSelectionBounds.get(uuid);

        Location loc = new Location(player.getWorld(), minX - 0.01, minY - 0.01, minZ - 0.01);
        Transformation trans = new Transformation(
            new Vector3f(0, 0, 0),
            new Quaternionf(),
            new Vector3f((float) dx + 0.02f, (float) dy + 0.02f, (float) dz + 0.02f),
            new Quaternionf()
        );

        if (existing != null && existing.isValid() && existing.getWorld().getName().equals(player.getWorld().getName())) {
            if (current.equals(last)) {
                return;
            }
            existing.teleport(loc);
            existing.setTransformation(trans);
            lastSelectionBounds.put(uuid, current);
        } else {
            if (existingList != null) {
                for (BlockDisplay bd : existingList) if (bd != null && bd.isValid()) bd.remove();
            }
            BlockDisplay display = player.getWorld().spawn(loc, BlockDisplay.class, entity -> {
                entity.setBlock(Bukkit.createBlockData(org.bukkit.Material.LIGHT_BLUE_STAINED_GLASS));
                entity.setTransformation(trans);
                entity.setGlowing(true);
                entity.setPersistent(false);
                entity.setVisibleByDefault(false);
            });
            player.showEntity(plugin, display);
            List<BlockDisplay> newList = new ArrayList<>();
            newList.add(display);
            activeSelectionDisplays.put(uuid, newList);
            lastSelectionBounds.put(uuid, current);
        }
    }

    private void updateRegionDisplay(Player player, Region region) {
        UUID uuid = player.getUniqueId();
        List<BlockDisplay> existingList = activeRegionDisplays.get(uuid);
        BlockDisplay existing = (existingList != null && !existingList.isEmpty()) ? existingList.get(0) : null;

        double minX = region.getMinX();
        double minY = region.getMinY();
        double minZ = region.getMinZ();
        double dx = region.getMaxX() - region.getMinX() + 1.0;
        double dy = region.getMaxY() - region.getMinY() + 1.0;
        double dz = region.getMaxZ() - region.getMinZ() + 1.0;

        DisplayBounds current = new DisplayBounds(player.getWorld().getName(), minX, minY, minZ, dx, dy, dz);
        DisplayBounds last = lastRegionBounds.get(uuid);

        Location loc = new Location(player.getWorld(), minX - 0.01, minY - 0.01, minZ - 0.01);
        Transformation trans = new Transformation(
            new Vector3f(0, 0, 0),
            new Quaternionf(),
            new Vector3f((float) dx + 0.02f, (float) dy + 0.02f, (float) dz + 0.02f),
            new Quaternionf()
        );

        if (existing != null && existing.isValid() && existing.getWorld().getName().equals(player.getWorld().getName())) {
            if (current.equals(last)) {
                return;
            }
            existing.teleport(loc);
            existing.setTransformation(trans);
            lastRegionBounds.put(uuid, current);
        } else {
            if (existingList != null) {
                for (BlockDisplay bd : existingList) if (bd != null && bd.isValid()) bd.remove();
            }
            BlockDisplay display = player.getWorld().spawn(loc, BlockDisplay.class, entity -> {
                entity.setBlock(Bukkit.createBlockData(org.bukkit.Material.LIME_STAINED_GLASS));
                entity.setTransformation(trans);
                entity.setGlowing(true);
                entity.setPersistent(false);
                entity.setVisibleByDefault(false);
            });
            player.showEntity(plugin, display);
            List<BlockDisplay> newList = new ArrayList<>();
            newList.add(display);
            activeRegionDisplays.put(uuid, newList);
            lastRegionBounds.put(uuid, current);
        }
    }

    private void updatePolygonDisplay(Player player, List<Location> points, double minY, double maxY, boolean isSelection) {
        UUID uuid = player.getUniqueId();
        Map<UUID, List<BlockDisplay>> activeMap = isSelection ? activeSelectionDisplays : activeRegionDisplays;

        if (points.size() < 2) {
            List<BlockDisplay> existing = activeMap.remove(uuid);
            if (existing != null) {
                for (BlockDisplay bd : existing) {
                    if (bd != null && bd.isValid()) bd.remove();
                }
            }
            return;
        }

        List<BlockDisplay> displays = activeMap.get(uuid);
        if (displays == null) {
            displays = new ArrayList<>();
        } else {
            displays.removeIf(bd -> bd == null || !bd.isValid());
        }

        org.bukkit.World world = player.getWorld();
        org.bukkit.Material glassMat = isSelection ? org.bukkit.Material.LIGHT_BLUE_STAINED_GLASS : org.bukkit.Material.LIME_STAINED_GLASS;

        int numSegments = points.size();
        int validSegments = (numSegments < 3) ? 1 : numSegments;

        // Trim excess displays if segment count shrank
        while (displays.size() > validSegments) {
            BlockDisplay bd = displays.remove(displays.size() - 1);
            if (bd != null && bd.isValid()) {
                bd.remove();
            }
        }

        // Spawn extra displays if segment count grew
        while (displays.size() < validSegments) {
            BlockDisplay newDisplay = world.spawn(new Location(world, 0, 0, 0), BlockDisplay.class, entity -> {
                entity.setBlock(Bukkit.createBlockData(glassMat));
                entity.setPersistent(false);
                entity.setVisibleByDefault(false);
            });
            player.showEntity(plugin, newDisplay);
            displays.add(newDisplay);
        }

        // Update positions & transformations on existing displays
        double height = maxY - minY + 1.0;
        int displayIdx = 0;

        for (int i = 0; i < numSegments; i++) {
            if (numSegments < 3 && i == numSegments - 1) break;

            Location p1 = points.get(i);
            Location p2 = points.get((i + 1) % numSegments);

            double x1 = p1.getX();
            double z1 = p1.getZ();
            double x2 = p2.getX();
            double z2 = p2.getZ();

            double dx = x2 - x1;
            double dz = z2 - z1;
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 0.001) continue;

            float yaw = (float) Math.toDegrees(Math.atan2(dz, dx));
            Location loc = new Location(world, x1, minY, z1, yaw, 0.0f);

            Transformation trans = new Transformation(
                new Vector3f(0, 0, 0),
                new Quaternionf(),
                new Vector3f((float) length, (float) height, 0.05f),
                new Quaternionf()
            );

            if (displayIdx < displays.size()) {
                BlockDisplay bd = displays.get(displayIdx++);
                bd.teleport(loc);
                bd.setTransformation(trans);
            }
        }

        activeMap.put(uuid, displays);
    }

    private synchronized ShowRegionInfo getActiveShowRegion(UUID playerUUID, long now) {
        ShowRegionInfo showInfo = activeRegionShows.get(playerUUID);
        if (showInfo != null) {
            if (now > showInfo.expireTime) {
                activeRegionShows.remove(playerUUID);
                List<BlockDisplay> bdList = activeRegionDisplays.remove(playerUUID);
                if (bdList != null) {
                    for (BlockDisplay bd : bdList) {
                        if (bd != null && bd.isValid()) bd.remove();
                    }
                }
                lastRegionBounds.remove(playerUUID);
                return null;
            }
            return showInfo;
        }
        return null;
    }

    private void drawCuboidWireframe(Player player, World world, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Particle.DustOptions dust) {
        // Bottom square
        drawLine(player, minX, minY, minZ, maxX, minY, minZ, dust);
        drawLine(player, minX, minY, minZ, minX, minY, maxZ, dust);
        drawLine(player, maxX, minY, minZ, maxX, minY, maxZ, dust);
        drawLine(player, minX, minY, maxZ, maxX, minY, maxZ, dust);

        // Top square
        drawLine(player, minX, maxY, minZ, maxX, maxY, minZ, dust);
        drawLine(player, minX, maxY, minZ, minX, maxY, maxZ, dust);
        drawLine(player, maxX, maxY, minZ, maxX, maxY, maxZ, dust);
        drawLine(player, minX, maxY, maxZ, maxX, maxY, maxZ, dust);

        // Pillars
        drawLine(player, minX, minY, minZ, minX, maxY, minZ, dust);
        drawLine(player, maxX, minY, minZ, maxX, maxY, minZ, dust);
        drawLine(player, minX, minY, maxZ, minX, maxY, maxZ, dust);
        drawLine(player, maxX, minY, maxZ, maxX, maxY, maxZ, dust);
    }

    private void drawLine(Player player, double x1, double y1, double z1, double x2, double y2, double z2, Particle.DustOptions dust) {
        double distance = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2) + Math.pow(z2 - z1, 2));
        double steps = distance * 2.0; // Step every 0.5 block
        if (steps < 1) steps = 1;

        double stepX = (x2 - x1) / steps;
        double stepY = (y2 - y1) / steps;
        double stepZ = (z2 - z1) / steps;

        for (int i = 0; i <= steps; i++) {
            double px = x1 + (stepX * i);
            double py = y1 + (stepY * i);
            double pz = z1 + (stepZ * i);
            
            player.spawnParticle(Particle.DUST, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0, dust);
        }
    }

    private static class ShowRegionInfo {
        private final String regionId;
        private final long expireTime;

        public ShowRegionInfo(String regionId, long expireTime) {
            this.regionId = regionId;
            this.expireTime = expireTime;
        }
    }

    // Helper bounds record for selection state caching
    private record DisplayBounds(String worldName, double minX, double minY, double minZ, double dx, double dy, double dz) {}

    private void updateSelectionHandles(Player player, Session session) {
        UUID uuid = player.getUniqueId();
        if (session.getSelectionType() != fr.skynex.worldx.region.ShapeType.CUBOID) {
            clearSelectionHandles(uuid);
            return;
        }

        Location p1 = session.getPos1();
        Location p2 = session.getPos2();
        if (p1 == null || p2 == null || p1.getWorld() == null) {
            clearSelectionHandles(uuid);
            return;
        }

        World world = p1.getWorld();

        // Calculate boundaries
        double minX = Math.min(p1.getX(), p2.getX());
        double maxX = Math.max(p1.getX(), p2.getX()) + 1.0;
        double minY = Math.min(p1.getY(), p2.getY());
        double maxY = Math.max(p1.getY(), p2.getY()) + 1.0;
        double minZ = Math.min(p1.getZ(), p2.getZ());
        double maxZ = Math.max(p1.getZ(), p2.getZ()) + 1.0;

        double midX = minX + (maxX - minX) / 2.0;
        double midY = minY + (maxY - minY) / 2.0;
        double midZ = minZ + (maxZ - minZ) / 2.0;

        // Position of each handle: (Direction -> Location)
        Map<String, Location> targets = new java.util.LinkedHashMap<>();
        targets.put("UP", new Location(world, midX, maxY + 0.1, midZ));
        targets.put("DOWN", new Location(world, midX, minY - 1.1, midZ));
        targets.put("NORTH", new Location(world, midX, midY, minZ - 1.1));
        targets.put("SOUTH", new Location(world, midX, midY, maxZ + 0.1));
        targets.put("EAST", new Location(world, maxX + 0.1, midY, midZ));
        targets.put("WEST", new Location(world, minX - 1.1, midY, midZ));

        // Display characters
        Map<String, String> chars = new HashMap<>();
        chars.put("UP", "▲ U");
        chars.put("DOWN", "▼ D");
        chars.put("NORTH", "◀ N");
        chars.put("SOUTH", "▶ S");
        chars.put("EAST", "▶ E");
        chars.put("WEST", "◀ W");

        List<org.bukkit.entity.Entity> entities = activeHandles.get(uuid);
        if (entities == null || entities.isEmpty() || entities.stream().anyMatch(e -> !e.isValid())) {
            // Recreate all
            if (entities != null) {
                for (org.bukkit.entity.Entity e : entities) e.remove();
            }
            entities = new ArrayList<>();
            org.bukkit.NamespacedKey dirKey = new org.bukkit.NamespacedKey(plugin, "handle-direction");
            org.bukkit.NamespacedKey ownerKey = new org.bukkit.NamespacedKey(plugin, "handle-owner");

            for (Map.Entry<String, Location> entry : targets.entrySet()) {
                String dir = entry.getKey();
                Location loc = entry.getValue();

                // Spawn Interaction Entity
                org.bukkit.entity.Interaction interaction = world.spawn(loc, org.bukkit.entity.Interaction.class, ent -> {
                    ent.setInteractionWidth(1.2f);
                    ent.setInteractionHeight(1.2f);
                    ent.setPersistent(false);
                    ent.getPersistentDataContainer().set(dirKey, org.bukkit.persistence.PersistentDataType.STRING, dir);
                    ent.getPersistentDataContainer().set(ownerKey, org.bukkit.persistence.PersistentDataType.STRING, uuid.toString());
                });

                // Spawn TextDisplay Entity
                org.bukkit.entity.TextDisplay textDisplay = world.spawn(loc.clone().add(0, 0.5, 0), org.bukkit.entity.TextDisplay.class, ent -> {
                    ent.text(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<gold><b>" + chars.get(dir) + "</b></gold>"));
                    ent.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                    ent.setPersistent(false);
                    ent.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                });

                entities.add(interaction);
                entities.add(textDisplay);
            }
            activeHandles.put(uuid, entities);
        } else {
            // Teleport existing
            int idx = 0;
            for (Map.Entry<String, Location> entry : targets.entrySet()) {
                Location loc = entry.getValue();
                if (idx < entities.size()) {
                    org.bukkit.entity.Entity interaction = entities.get(idx++);
                    interaction.teleport(loc);
                }
                if (idx < entities.size()) {
                    org.bukkit.entity.Entity textDisplay = entities.get(idx++);
                    textDisplay.teleport(loc.clone().add(0, 0.5, 0));
                }
            }
        }
    }

    public void clearSelectionHandles(UUID uuid) {
        List<org.bukkit.entity.Entity> list = activeHandles.remove(uuid);
        if (list != null) {
            for (org.bukkit.entity.Entity e : list) {
                if (e != null && e.isValid()) {
                    e.remove();
                }
            }
        }
    }
}
