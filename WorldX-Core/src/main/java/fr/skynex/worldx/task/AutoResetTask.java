package fr.skynex.worldx.task;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.edit.BlockEditQueue;
import fr.skynex.worldx.region.Region;
import fr.skynex.worldx.region.ShapeType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AutoResetTask extends BukkitRunnable {

    private final WorldX plugin;

    public AutoResetTask(WorldX plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        for (Region region : plugin.getRegionManager().getRegions().values()) {
            String schemName = region.getBoundSchematic();
            if (schemName != null && !schemName.trim().isEmpty()) {
                String intervalStr = plugin.getRegionManager().getEffectiveFlagValue(region, "auto-rollback-interval");
                boolean shouldReset = false;
                if (intervalStr != null && !intervalStr.trim().isEmpty()) {
                    long interval = parseIntervalMillis(intervalStr);
                    if (interval > 0) {
                        String lastResetStr = region.getFlags().get("last-reset");
                        long lastReset = 0;
                        if (lastResetStr != null) {
                            try {
                                lastReset = Long.parseLong(lastResetStr);
                            } catch (NumberFormatException ignored) {}
                        }
                        if (now - lastReset >= interval) {
                            shouldReset = true;
                        }
                    }
                }

                if (shouldReset) {
                    resetRegionAsync(region, schemName);
                    region.getFlags().put("last-reset", String.valueOf(now));
                    plugin.getRegionManager().addRegion(region); // Save immediately
                }
            }
        }
    }

    private long parseIntervalMillis(String val) {
        if (val == null || val.trim().isEmpty()) return 0;
        val = val.trim().toLowerCase();
        try {
            if (val.endsWith("h")) {
                double hours = Double.parseDouble(val.substring(0, val.length() - 1));
                return (long) (hours * 3600000L);
            } else if (val.endsWith("m")) {
                double minutes = Double.parseDouble(val.substring(0, val.length() - 1));
                return (long) (minutes * 60000L);
            } else if (val.endsWith("s")) {
                double seconds = Double.parseDouble(val.substring(0, val.length() - 1));
                return (long) (seconds * 1000L);
            } else {
                double minutes = Double.parseDouble(val);
                return (long) (minutes * 60000L);
            }
        } catch (NumberFormatException ignored) {}
        return 0;
    }

    private void resetRegionAsync(Region region, String schemName) {
        resetRegionAsyncStatic(plugin, region, schemName);
    }

    public static void resetRegionAsyncStatic(WorldX plugin, Region region, String schemName) {
        plugin.getDatabaseManager().loadSchematic(schemName).thenAccept(dbBytes -> {
            byte[] bytes = dbBytes;
            if (bytes == null) {
                File file = new File(new File(plugin.getDataFolder(), "schematics"), schemName + ".wxschem");
                if (file.exists()) {
                    try {
                        bytes = Files.readAllBytes(file.toPath());
                    } catch (IOException ignored) {}
                }
            }

            if (bytes == null) {
                return;
            }

            try {
                fr.skynex.worldx.edit.Clipboard clipboard = fr.skynex.worldx.edit.SchematicEngine.deserialize(bytes);
                List<BlockEditQueue.BlockChangeInfo> changes = new ArrayList<>();

                int minX = region.getMinX();
                int minY = region.getMinY();
                int minZ = region.getMinZ();
                int maxX = region.getMaxX();
                int maxY = region.getMaxY();
                int maxZ = region.getMaxZ();

                if (region.getShapeType() == ShapeType.SPHERE) {
                    minX = region.getMinX() - region.getMaxX();
                    minY = region.getMinY() - region.getMaxX();
                    minZ = region.getMinZ() - region.getMaxX();
                    maxX = region.getMinX() + region.getMaxX();
                    maxY = region.getMinY() + region.getMaxX();
                    maxZ = region.getMinZ() + region.getMaxX();
                } else if (region.getShapeType() == ShapeType.CYLINDER) {
                    minX = region.getMinX() - region.getMaxX();
                    minZ = region.getMinZ() - region.getMaxX();
                    maxX = region.getMinX() + region.getMaxX();
                    maxZ = region.getMinZ() + region.getMaxX();
                }

                World world = Bukkit.getWorld(region.getWorldName());
                if (world == null) return;

                // 1. Clear region to AIR
                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            if (region.contains(x, y, z)) {
                                changes.add(new BlockEditQueue.BlockChangeInfo(x, y, z, Material.AIR.createBlockData()));
                            }
                        }
                    }
                }

                // 2. Paste schematic blocks relative to minX, minY, minZ
                for (fr.skynex.worldx.edit.Clipboard.ClipboardBlock block : clipboard.getBlocks()) {
                    int targetX = minX + block.getRelX();
                    int targetY = minY + block.getRelY();
                    int targetZ = minZ + block.getRelZ();

                    if (region.contains(targetX, targetY, targetZ)) {
                        changes.add(new BlockEditQueue.BlockChangeInfo(targetX, targetY, targetZ, block.getBlockData()));
                    }
                }

                if (!changes.isEmpty()) {
                    UUID consoleUUID = UUID.nameUUIDFromBytes("WORLDX-CONSOLE".getBytes());
                    plugin.getBlockEditQueue().queueTask(new BlockEditQueue.EditTask(
                            consoleUUID, region.getWorldName(), changes
                    ));
                }
            } catch (Exception ignored) {}
        });
    }
}
