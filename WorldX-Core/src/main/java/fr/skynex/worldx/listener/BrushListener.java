package fr.skynex.worldx.listener;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.edit.BlockEditQueue;
import fr.skynex.worldx.edit.BrushInfo;
import fr.skynex.worldx.edit.EditOperation;
import fr.skynex.worldx.region.Flag;
import fr.skynex.worldx.session.Session;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import java.util.ArrayList;
import java.util.List;

public class BrushListener implements Listener {

    private final WorldX plugin;

    public BrushListener(WorldX plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null) return;

        String brushMatName = plugin.getConfig().getString("edit.brush-item", "BLAZE_ROD");
        if (!item.getType().name().equalsIgnoreCase(brushMatName)) {
            return;
        }

        Session session = plugin.getSessionManager().getSession(player);
        BrushInfo brush = session.getBrushInfo();
        if (brush == null) {
            return;
        }

        event.setCancelled(true); // Prevent normal interact

        Block target = player.getTargetBlockExact(100);
        if (target == null) {
            player.sendMessage(Component.text("Aucun bloc ciblé dans votre champ de vision (max 100 blocs).", NamedTextColor.RED));
            return;
        }

        applyBrush(player, target, brush);
    }

    private void applyBrush(Player player, Block target, BrushInfo brush) {
        World world = target.getWorld();
        int radius = brush.getRadius();
        BrushInfo.BrushType type = brush.getType();
        BlockData blockData = brush.getBlockData();

        List<BlockEditQueue.BlockChangeInfo> changes = new ArrayList<>();
        int cx = target.getX();
        int cy = target.getY();
        int cz = target.getZ();

        if (type == BrushInfo.BrushType.SPHERE || type == BrushInfo.BrushType.ERASER) {
            BlockData targetData = type == BrushInfo.BrushType.ERASER ? Material.AIR.createBlockData() : blockData;

            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        if (x * x + y * y + z * z <= radius * radius) {
                            Location loc = new Location(world, cx + x, cy + y, cz + z);
                            
                            // Check region build permission
                            if (plugin.getRegionManager().checkPermission(player, loc, Flag.BUILD)) {
                                changes.add(new BlockEditQueue.BlockChangeInfo(cx + x, cy + y, cz + z, targetData));
                            }
                        }
                    }
                }
            }
        } else if (type == BrushInfo.BrushType.UNDO) {
            Session session = plugin.getSessionManager().getSession(player.getUniqueId());
            if (session != null) {
                java.util.Set<String> processedCoords = new java.util.HashSet<>();
                for (EditOperation op : session.getUndoHistory()) {
                    for (EditOperation.BlockChange bc : op.getChanges()) {
                        int bx = bc.getX();
                        int by = bc.getY();
                        int bz = bc.getZ();
                        
                        double distSq = Math.pow(bx - cx, 2) + Math.pow(by - cy, 2) + Math.pow(bz - cz, 2);
                        if (distSq <= radius * radius) {
                            String coordKey = bx + "," + by + "," + bz;
                            if (processedCoords.add(coordKey)) {
                                Location loc = new Location(world, bx, by, bz);
                                if (plugin.getRegionManager().checkPermission(player, loc, Flag.BUILD)) {
                                    changes.add(new BlockEditQueue.BlockChangeInfo(bx, by, bz, bc.getPreviousData()));
                                }
                            }
                        }
                    }
                }
            }
        } else if (type == BrushInfo.BrushType.SMOOTH) {
            // Terraforming smooth brush: averages height map inside circle
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz <= radius * radius) {
                        int bx = cx + dx;
                        int bz = cz + dz;
                        
                        // Compute average height in 3x3 neighborhood around this point
                        double sumHeight = 0;
                        int count = 0;
                        for (int nx = -1; nx <= 1; nx++) {
                            for (int nz = -1; nz <= 1; nz++) {
                                int ny = world.getHighestBlockYAt(bx + nx, bz + nz);
                                sumHeight += ny;
                                count++;
                            }
                        }
                        int avgY = (int) Math.round(sumHeight / count);
                        int currentY = world.getHighestBlockYAt(bx, bz);

                        if (currentY < avgY) {
                            // Raise terrain up to avgY
                            for (int y = currentY + 1; y <= avgY; y++) {
                                Location loc = new Location(world, bx, y, bz);
                                if (plugin.getRegionManager().checkPermission(player, loc, Flag.BUILD)) {
                                    // Use grass block for top block, dirt for below
                                    BlockData fill = (y == avgY) ? Material.GRASS_BLOCK.createBlockData() : Material.DIRT.createBlockData();
                                    changes.add(new BlockEditQueue.BlockChangeInfo(bx, y, bz, fill));
                                }
                            }
                        } else if (currentY > avgY) {
                            // Lower terrain down to avgY
                            for (int y = currentY; y > avgY; y--) {
                                Location loc = new Location(world, bx, y, bz);
                                if (plugin.getRegionManager().checkPermission(player, loc, Flag.BUILD)) {
                                    changes.add(new BlockEditQueue.BlockChangeInfo(bx, y, bz, Material.AIR.createBlockData()));
                                }
                            }
                        }
                    }
                }
            }
        } else if (type == BrushInfo.BrushType.ROAD) {
            // Paints road material on the highest block of target area XZ grid
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz <= radius * radius) {
                        int bx = cx + dx;
                        int bz = cz + dz;
                        int by = world.getHighestBlockYAt(bx, bz);
                        // Decrease if highest is air/foliage, though highest block is generally solid
                        Location loc = new Location(world, bx, by, bz);
                        if (plugin.getRegionManager().checkPermission(player, loc, Flag.BUILD)) {
                            changes.add(new BlockEditQueue.BlockChangeInfo(bx, by, bz, blockData));
                        }
                    }
                }
            }
        } else if (type == BrushInfo.BrushType.TREE) {
            // Procedurally spawns a custom tree
            Material logMat = blockData.getMaterial();
            Material leafMat = Material.OAK_LEAVES;
            if (logMat == Material.SPRUCE_LOG) leafMat = Material.SPRUCE_LEAVES;
            if (logMat == Material.BIRCH_LOG) leafMat = Material.BIRCH_LEAVES;

            int H = 5 + new java.util.Random().nextInt(3);

            // 1. Queue logs
            for (int y = 0; y < H; y++) {
                int ly = cy + y;
                Location loc = new Location(world, cx, ly, cz);
                if (plugin.getRegionManager().checkPermission(player, loc, Flag.BUILD)) {
                    changes.add(new BlockEditQueue.BlockChangeInfo(cx, ly, cz, logMat.createBlockData()));
                }
            }

            // 2. Queue leaves canopy
            int leavesCenterY = cy + H - 1;
            int leavesRadius = 3;
            for (int x = -leavesRadius; x <= leavesRadius; x++) {
                for (int y = -leavesRadius; y <= leavesRadius; y++) {
                    for (int z = -leavesRadius; z <= leavesRadius; z++) {
                        if (x * x + y * y + z * z <= leavesRadius * leavesRadius - 1) {
                            int lx = cx + x;
                            int ly = leavesCenterY + y;
                            int lz = cz + z;
                            
                            // Prevent overwriting the tree trunk
                            if (lx == cx && lz == cz && ly <= cy + H - 1) continue;

                            Location loc = new Location(world, lx, ly, lz);
                            if (plugin.getRegionManager().checkPermission(player, loc, Flag.BUILD)) {
                                changes.add(new BlockEditQueue.BlockChangeInfo(lx, ly, lz, leafMat.createBlockData()));
                            }
                        }
                    }
                }
            }
        } else if (type == BrushInfo.BrushType.BLEND) {
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        double dist = Math.sqrt(x * x + y * y + z * z);
                        if (dist <= radius) {
                            double prob = 1.0 - (dist / radius);
                            if (Math.random() < prob) {
                                Location loc = new Location(world, cx + x, cy + y, cz + z);
                                if (plugin.getRegionManager().checkPermission(player, loc, Flag.BUILD)) {
                                    changes.add(new BlockEditQueue.BlockChangeInfo(cx + x, cy + y, cz + z, blockData));
                                }
                            }
                        }
                    }
                }
            }
        } else if (type == BrushInfo.BrushType.SPLINE) {
            Session session = plugin.getSessionManager().getSession(player);
            List<Location> points = session.getSelectionPoints();
            if (points == null || points.size() < 2) {
                player.sendMessage(Component.text("Veuillez d'abord définir une sélection de points (ex: /sel polygon et clic droit avec la baguette).", NamedTextColor.RED));
                return;
            }

            List<Location> curve = calculateSplinePoints(points, 20);
            java.util.Set<String> processed = new java.util.HashSet<>();
            for (Location curveLoc : curve) {
                int ccx = curveLoc.getBlockX();
                int ccy = curveLoc.getBlockY();
                int ccz = curveLoc.getBlockZ();
                for (int x = -radius; x <= radius; x++) {
                    for (int y = -radius; y <= radius; y++) {
                        for (int z = -radius; z <= radius; z++) {
                            if (x * x + y * y + z * z <= radius * radius) {
                                int bx = ccx + x;
                                int by = ccy + y;
                                int bz = ccz + z;
                                String key = bx + "," + by + "," + bz;
                                if (processed.add(key)) {
                                    Location blockLoc = new Location(world, bx, by, bz);
                                    if (plugin.getRegionManager().checkPermission(player, blockLoc, Flag.BUILD)) {
                                        changes.add(new BlockEditQueue.BlockChangeInfo(bx, by, bz, blockData));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (type == BrushInfo.BrushType.CLIPBOARD) {
            Session session = plugin.getSessionManager().getSession(player);
            fr.skynex.worldx.edit.Clipboard clipboard = session.getClipboard();
            if (clipboard == null) {
                player.sendMessage(Component.text("Votre presse-papier est vide. Utilisez d'abord /copy.", NamedTextColor.RED));
                return;
            }

            for (fr.skynex.worldx.edit.Clipboard.ClipboardBlock cb : clipboard.getBlocks()) {
                int bx = cx + cb.getRelX();
                int by = cy + cb.getRelY();
                int bz = cz + cb.getRelZ();
                Location blockLoc = new Location(world, bx, by, bz);
                if (plugin.getRegionManager().checkPermission(player, blockLoc, Flag.BUILD)) {
                    changes.add(new BlockEditQueue.BlockChangeInfo(bx, by, bz, cb.getBlockData()));
                }
            }
        } else if (type == BrushInfo.BrushType.BIOME) {
            String biomeName = brush.getMetadata();
            if (biomeName == null) return;
            NamespacedKey key;
            if (biomeName.contains(":")) {
                key = NamespacedKey.fromString(biomeName.toLowerCase());
            } else {
                key = NamespacedKey.minecraft(biomeName.toLowerCase());
            }
            org.bukkit.block.Biome targetBiome = (key != null) ? RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).get(key) : null;
            if (targetBiome == null) return;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz <= radius * radius) {
                        int bx = cx + dx;
                        int bz = cz + dz;
                        int by = world.getHighestBlockYAt(bx, bz);
                        Location blockLoc = new Location(world, bx, by, bz);
                        if (plugin.getRegionManager().checkPermission(player, blockLoc, Flag.BUILD)) {
                            world.setBiome(bx, by, bz, targetBiome);
                        }
                    }
                }
            }
            player.sendMessage(Component.text("Biome mis à jour en " + biomeName + " dans la zone.", NamedTextColor.GREEN));
            return;
        } else if (type == BrushInfo.BrushType.EROSION) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz <= radius * radius) {
                        int bx = cx + dx;
                        int bz = cz + dz;
                        int by = world.getHighestBlockYAt(bx, bz);
                        Location blockLoc = new Location(world, bx, by, bz);
                        
                        if (!plugin.getRegionManager().checkPermission(player, blockLoc, Flag.BUILD)) {
                            continue;
                        }

                        int[] nx = {1, -1, 0, 0};
                        int[] nz = {0, 0, 1, -1};
                        int lowestX = bx, lowestZ = bz, lowestY = by;

                        for (int i = 0; i < 4; i++) {
                            int nbx = bx + nx[i];
                            int nbz = bz + nz[i];
                            int nby = world.getHighestBlockYAt(nbx, nbz);
                            if (nby < lowestY) {
                                lowestY = nby;
                                lowestX = nbx;
                                lowestZ = nbz;
                            }
                        }

                        if (by - lowestY > 1) {
                            Location destLoc = new Location(world, lowestX, lowestY + 1, lowestZ);
                            if (plugin.getRegionManager().checkPermission(player, destLoc, Flag.BUILD)) {
                                Block topBlock = world.getBlockAt(bx, by, bz);
                                BlockData topData = topBlock.getBlockData();
                                changes.add(new BlockEditQueue.BlockChangeInfo(bx, by, bz, Material.AIR.createBlockData()));
                                changes.add(new BlockEditQueue.BlockChangeInfo(lowestX, lowestY + 1, lowestZ, topData));
                            }
                        }
                    }
                }
            }
        } else if (type == BrushInfo.BrushType.RUINS) {
            java.util.Random random = new java.util.Random();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                            int bx = cx + dx;
                            int by = cy + dy;
                            int bz = cz + dz;
                            Location blockLoc = new Location(world, bx, by, bz);
                            if (plugin.getRegionManager().checkPermission(player, blockLoc, Flag.BUILD)) {
                                Block b = world.getBlockAt(blockLoc);
                                if (b.getType().isAir() || !b.getType().isSolid()) continue;

                                Material mat = b.getType();
                                if (mat == Material.STONE || mat == Material.COBBLESTONE || mat == Material.STONE_BRICKS ||
                                    mat == Material.MOSSY_COBBLESTONE || mat == Material.MOSSY_STONE_BRICKS || mat == Material.CRACKED_STONE_BRICKS) {
                                    
                                    double r = random.nextDouble();
                                    Material ruinedMat = mat;
                                    if (r < 0.20) ruinedMat = Material.COBBLESTONE;
                                    else if (r < 0.35) ruinedMat = Material.MOSSY_COBBLESTONE;
                                    else if (r < 0.50) ruinedMat = Material.CRACKED_STONE_BRICKS;
                                    else if (r < 0.65) ruinedMat = Material.MOSSY_STONE_BRICKS;
                                    else if (r < 0.73) ruinedMat = Material.AIR;
                                    else if (r < 0.78) ruinedMat = Material.COBWEB;

                                    if (ruinedMat != mat) {
                                        changes.add(new BlockEditQueue.BlockChangeInfo(bx, by, bz, ruinedMat.createBlockData()));
                                    }
                                } else {
                                    double r = random.nextDouble();
                                    if (r < 0.08) {
                                        changes.add(new BlockEditQueue.BlockChangeInfo(bx, by, bz, Material.AIR.createBlockData()));
                                    } else if (r < 0.10) {
                                        changes.add(new BlockEditQueue.BlockChangeInfo(bx, by, bz, Material.COBWEB.createBlockData()));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (type == BrushInfo.BrushType.GREEBLE) {
            java.util.Random random = new java.util.Random();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                            int bx = cx + dx;
                            int by = cy + dy;
                            int bz = cz + dz;
                            Location blockLoc = new Location(world, bx, by, bz);
                            if (plugin.getRegionManager().checkPermission(player, blockLoc, Flag.BUILD)) {
                                Block b = world.getBlockAt(blockLoc);
                                if (b.getType().isAir() || !b.getType().isSolid()) continue;

                                int[][] faces = {
                                    {0, 1, 0}, {0, -1, 0},
                                    {1, 0, 0}, {-1, 0, 0},
                                    {0, 0, 1}, {0, 0, -1}
                                };
                                
                                for (int[] face : faces) {
                                    int abx = bx + face[0];
                                    int aby = by + face[1];
                                    int abz = bz + face[2];
                                    
                                    Location adjLoc = new Location(world, abx, aby, abz);
                                    if (world.getBlockAt(adjLoc).getType().isAir()) {
                                        if (random.nextDouble() < 0.20) {
                                            Material baseMat = b.getType();
                                            Material detailMat = null;
                                            if (baseMat.name().contains("WOOD") || baseMat.name().contains("LOG") || baseMat.name().contains("PLANKS")) {
                                                double r = random.nextDouble();
                                                if (r < 0.3) detailMat = Material.OAK_SLAB;
                                                else if (r < 0.6) detailMat = Material.OAK_STAIRS;
                                                else if (r < 0.8) detailMat = Material.OAK_BUTTON;
                                                else detailMat = Material.OAK_FENCE;
                                            } else if (baseMat.name().contains("STONE") || baseMat.name().contains("DEEPSLATE") || baseMat.name().contains("BRICK") || baseMat == Material.COBBLESTONE) {
                                                double r = random.nextDouble();
                                                if (r < 0.3) detailMat = Material.STONE_BRICK_SLAB;
                                                else if (r < 0.6) detailMat = Material.STONE_BRICK_STAIRS;
                                                else if (r < 0.8) detailMat = Material.STONE_BUTTON;
                                                else detailMat = Material.COBBLESTONE_WALL;
                                            }
                                            
                                            if (detailMat != null && plugin.getRegionManager().checkPermission(player, adjLoc, Flag.BUILD)) {
                                                changes.add(new BlockEditQueue.BlockChangeInfo(abx, aby, abz, detailMat.createBlockData()));
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (type == BrushInfo.BrushType.PAINTER) {
            Session session = plugin.getSessionManager().getSession(player.getUniqueId());
            if (session != null && session.getActivePaintPalette() != null) {
                fr.skynex.worldx.edit.Palette palette = session.getActivePaintPalette();
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dy = -radius; dy <= radius; dy++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                                int bx = cx + dx;
                                int by = cy + dy;
                                int bz = cz + dz;
                                Location blockLoc = new Location(world, bx, by, bz);
                                if (plugin.getRegionManager().checkPermission(player, blockLoc, Flag.BUILD)) {
                                    Block b = world.getBlockAt(blockLoc);
                                    if (b.getType().isAir() || !b.getType().isSolid()) continue;

                                    BlockData sampled = palette.sampleBlock();
                                    if (sampled.getMaterial() != b.getType()) {
                                        changes.add(new BlockEditQueue.BlockChangeInfo(bx, by, bz, sampled));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!changes.isEmpty()) {
            Session s = plugin.getSessionManager().getSession(player);
            if (s != null && s.getActiveMask() != null) {
                fr.skynex.worldx.edit.Mask mask = fr.skynex.worldx.edit.MaskParser.parse(s.getActiveMask());
                changes.removeIf(change -> {
                    Block block = world.getBlockAt(change.x, change.y, change.z);
                    return !mask.matches(block);
                });
            }
        }

        if (!changes.isEmpty()) {
            plugin.getBlockEditQueue().queueTask(new BlockEditQueue.EditTask(
                    player.getUniqueId(), world.getName(), changes
            ));
        } else {
            player.sendMessage(Component.text("Aucun bloc modifié (protection de région ou masque actif).", NamedTextColor.RED));
        }
    }

    private List<Location> calculateSplinePoints(List<Location> controlPoints, int pointsPerSegment) {
        List<Location> spline = new ArrayList<>();
        if (controlPoints == null || controlPoints.isEmpty()) {
            return spline;
        }
        if (controlPoints.size() < 3) {
            for (int i = 0; i < controlPoints.size() - 1; i++) {
                Location start = controlPoints.get(i);
                Location end = controlPoints.get(i + 1);
                double distance = start.distance(end);
                int steps = (int) Math.max(2, distance * 2);
                for (int s = 0; s <= steps; s++) {
                    double t = (double) s / steps;
                    spline.add(new Location(
                        start.getWorld(),
                        start.getX() + t * (end.getX() - start.getX()),
                        start.getY() + t * (end.getY() - start.getY()),
                        start.getZ() + t * (end.getZ() - start.getZ())
                    ));
                }
            }
            return spline;
        }

        int n = controlPoints.size();
        for (int i = 0; i < n - 1; i++) {
            Location p0 = controlPoints.get(Math.max(0, i - 1));
            Location p1 = controlPoints.get(i);
            Location p2 = controlPoints.get(i + 1);
            Location p3 = controlPoints.get(Math.min(n - 1, i + 2));

            for (int s = 0; s <= pointsPerSegment; s++) {
                double t = (double) s / pointsPerSegment;
                double t2 = t * t;
                double t3 = t2 * t;

                double x = 0.5 * ((2 * p1.getX()) +
                           (-p0.getX() + p2.getX()) * t +
                           (2 * p0.getX() - 5 * p1.getX() + 4 * p2.getX() - p3.getX()) * t2 +
                           (-p0.getX() + 3 * p1.getX() - 3 * p2.getX() + p3.getX()) * t3);

                double y = 0.5 * ((2 * p1.getY()) +
                           (-p0.getY() + p2.getY()) * t +
                           (2 * p0.getY() - 5 * p1.getY() + 4 * p2.getY() - p3.getY()) * t2 +
                           (-p0.getY() + 3 * p1.getY() - 3 * p2.getY() + p3.getY()) * t3);

                double z = 0.5 * ((2 * p1.getZ()) +
                           (-p0.getZ() + p2.getZ()) * t +
                           (2 * p0.getZ() - 5 * p1.getZ() + 4 * p2.getZ() - p3.getZ()) * t2 +
                           (-p0.getZ() + 3 * p1.getZ() - 3 * p2.getZ() + p3.getZ()) * t3);

                spline.add(new Location(p1.getWorld(), x, y, z));
            }
        }
        return spline;
    }
}
