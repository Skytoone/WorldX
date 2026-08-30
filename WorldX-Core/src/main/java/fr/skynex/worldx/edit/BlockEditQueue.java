package fr.skynex.worldx.edit;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.session.Session;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

public class BlockEditQueue {

    private final WorldX plugin;
    private final Queue<EditTask> taskQueue = new ArrayDeque<>();
    private boolean isProcessing = false;
    private EditTask activeTask = null;

    public BlockEditQueue(WorldX plugin) {
        this.plugin = plugin;
    }

    private long lastTickTime = 0;

    public synchronized void startProcessing() {
        if (isProcessing)
            return;
        isProcessing = true;

        int blocksPerTick = plugin.getConfig().getInt("edit.async.blocks-per-tick", 5000);

        fr.skynex.worldx.scheduler.FoliaScheduler.runTaskTimer(plugin, () -> {
            if (isProcessing) {
                long now = System.currentTimeMillis();
                int targetLimit = blocksPerTick;
                if (lastTickTime != 0) {
                    long elapsed = now - lastTickTime;
                    if (elapsed > 55) { // Server is lagging (ideal tick is 50ms)
                        double ratio = 50.0 / elapsed;
                        targetLimit = Math.max(500, (int) (blocksPerTick * ratio));
                    }
                }
                lastTickTime = now;
                processNextBatch(targetLimit);
            }
        }, 1L, 1L);
    }

    public synchronized void stopProcessing() {
        isProcessing = false;
    }

    public synchronized void queueTask(EditTask task) {
        Player player = Bukkit.getPlayer(task.playerUUID);
        if (player != null && !player.isOp() && !player.hasPermission("worldx.admin")) {
            int maxBlocks = plugin.getConfig().getInt("edit.default-quota", 10000);
            for (org.bukkit.permissions.PermissionAttachmentInfo attachment : player.getEffectivePermissions()) {
                String perm = attachment.getPermission();
                if (perm.startsWith("worldx.edit.quota.")) {
                    try {
                        maxBlocks = Math.max(maxBlocks, Integer.parseInt(perm.replace("worldx.edit.quota.", "")));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            if (task.totalVolume > maxBlocks) {
                player.sendMessage(Component.text("Action rejetée ! Cette modification contient " + task.totalVolume
                        + " blocs, ce qui dépasse votre quota autorisé de " + maxBlocks + " blocs.",
                        NamedTextColor.RED));
                return; // Cancel queuing
            }
        }

        taskQueue.offer(task);
    }

    private synchronized void processNextBatch(int limit) {
        if (activeTask == null) {
            activeTask = taskQueue.poll();
            if (activeTask == null) {
                return; // Nothing to process
            }
        }

        World world = Bukkit.getWorld(activeTask.worldName);
        if (world == null) {
            activeTask = null;
            return;
        }

        if (activeTask.changes != null) {
            // Pre-allocated mode: group block changes by their chunk coordinates
            List<BlockChangeInfo> changes = activeTask.changes;
            int size = changes.size();
            if (activeTask.currentIndex >= size) {
                completeActiveTask();
                return;
            }

            int startIdx = activeTask.currentIndex;
            int endIdx = Math.min(startIdx + limit, size);
            BlockChangeInfo firstBlock = changes.get(startIdx);
            int chunkX = firstBlock.x >> 4;
            int chunkZ = firstBlock.z >> 4;

            int actualEnd = startIdx;
            while (actualEnd < endIdx && (changes.get(actualEnd).x >> 4) == chunkX && (changes.get(actualEnd).z >> 4) == chunkZ) {
                actualEnd++;
            }

            final int finalEnd = actualEnd;
            Location loc = new Location(world, chunkX << 4, 100, chunkZ << 4);
            fr.skynex.worldx.scheduler.FoliaScheduler.runAtLocation(plugin, loc, () -> {
                applyBatchEdits(world, startIdx, finalEnd);
            });
        } else {
            // Streaming mode: retrieve the active chunk coordinate to run on its specific regional thread
            if (activeTask.currentChunkListIndex >= activeTask.chunkKeys.size()) {
                completeActiveTask();
                return;
            }
            long key = activeTask.chunkKeys.get(activeTask.currentChunkListIndex);
            int cx = (int) (key >> 32);
            int cz = (int) key;

            Location loc = new Location(world, cx << 4, 100, cz << 4);
            fr.skynex.worldx.scheduler.FoliaScheduler.runAtLocation(plugin, loc, () -> {
                applyBatchEditsStreaming(world, limit);
            });
        }
    }

    private synchronized void applyBatchEdits(World world, int start, int end) {
        if (activeTask == null)
            return;

        List<BlockChangeInfo> changes = activeTask.changes;
        int size = changes.size();

        for (int i = start; i < end; i++) {
            BlockChangeInfo change = changes.get(i);
            Block block = world.getBlockAt(change.x, change.y, change.z);

            Mask mask = activeTask.getParsedMask();
            if (mask != null && !mask.matches(block)) {
                activeTask.currentIndex++;
                continue;
            }

            BlockData originalData = block.getBlockData();

            boolean success = NmsChunkWriter.setBlockDirectly(world, change.x, change.y, change.z, change.newData);
            if (!success) {
                block.setBlockData(change.newData, false);
            }

            long chunkKey = (((long) (change.x >> 4)) << 32) | ((change.z >> 4) & 0xFFFFFFFFL);
            activeTask.modifiedChunks.add(chunkKey);

            activeTask.historyChanges.add(new EditOperation.BlockChange(
                    change.x, change.y, change.z, originalData, change.newData));

            activeTask.currentIndex++;
        }

        Player player = Bukkit.getPlayer(activeTask.playerUUID);
        if (player != null && player.isOnline()) {
            sendProgressBar(player, activeTask.currentIndex, size);
        }

        if (activeTask.currentIndex >= size) {
            completeActiveTask();
        }
    }

    private synchronized void applyBatchEditsStreaming(World world, int limit) {
        if (activeTask == null)
            return;

        int processed = 0;
        long boxVolume = activeTask.totalVolume;

        while (processed < limit && activeTask.currentChunkListIndex < activeTask.chunkKeys.size()) {
            long key = activeTask.chunkKeys.get(activeTask.currentChunkListIndex);
            int cx = (int) (key >> 32);
            int cz = (int) key;

            int chunkMinX = cx << 4;
            int chunkMaxX = chunkMinX + 15;
            int chunkMinZ = cz << 4;
            int chunkMaxZ = chunkMinZ + 15;

            int boundMaxX = Math.min(activeTask.maxX, chunkMaxX);
            int boundMaxY = activeTask.maxY;
            int boundMaxZ = Math.min(activeTask.maxZ, chunkMaxZ);

            if (activeTask.currentX > boundMaxX) {
                // Done with this chunk column! Move to the next chunk
                activeTask.currentChunkListIndex++;
                if (activeTask.currentChunkListIndex >= activeTask.chunkKeys.size()) {
                    break;
                }
                activeTask.initializePointersForCurrentChunk();
                continue;
            }

            boolean inside = false;
            if (activeTask.selectionType == fr.skynex.worldx.region.ShapeType.CUBOID) {
                inside = true;
            } else if (activeTask.selectionType == fr.skynex.worldx.region.ShapeType.POLYGON) {
                inside = isPointIn2DPolygon(activeTask.currentX, activeTask.currentZ, activeTask.selectionPoints) && activeTask.currentY >= activeTask.minY && activeTask.currentY <= activeTask.maxY;
            } else if (activeTask.selectionType == fr.skynex.worldx.region.ShapeType.LASSO) {
                inside = ConvexHull3D.contains(activeTask.currentX, activeTask.currentY, activeTask.currentZ, activeTask.selectionPoints);
            }

            if (inside) {
                Block block = world.getBlockAt(activeTask.currentX, activeTask.currentY, activeTask.currentZ);
                Mask mask = activeTask.getParsedMask();
                if (mask == null || mask.matches(block)) {
                    BlockData currentData = block.getBlockData();

                    boolean shouldModify = false;
                    if (activeTask.isReplace) {
                        if (activeTask.fromBlock != null) {
                            shouldModify = (currentData.getMaterial() == activeTask.fromBlock.getMaterial());
                        } else {
                            shouldModify = (currentData.getMaterial() != org.bukkit.Material.AIR);
                        }
                    } else {
                        shouldModify = true;
                    }

                    if (shouldModify) {
                        BlockData replacement = (activeTask.activePalette != null) ? activeTask.activePalette.sampleBlock() : activeTask.activeBlockData;
                        BlockData originalData = block.getBlockData();

                        boolean success = NmsChunkWriter.setBlockDirectly(world, activeTask.currentX, activeTask.currentY, activeTask.currentZ, replacement);
                        if (!success) {
                            block.setBlockData(replacement, false);
                        }

                        long chunkKey = (((long) (activeTask.currentX >> 4)) << 32) | ((activeTask.currentZ >> 4) & 0xFFFFFFFFL);
                        activeTask.modifiedChunks.add(chunkKey);

                        // Track history up to 20k to conserve RAM
                        if (activeTask.historyChanges.size() < 20000) {
                            activeTask.historyChanges.add(new EditOperation.BlockChange(
                                    activeTask.currentX, activeTask.currentY, activeTask.currentZ, originalData, replacement));
                        }

                        processed++;
                    }
                }
            }

            // Advance pointers within this chunk column
            activeTask.currentIndex++;
            activeTask.currentZ++;
            if (activeTask.currentZ > boundMaxZ) {
                activeTask.currentZ = Math.max(activeTask.minZ, chunkMinZ);
                activeTask.currentY++;
                if (activeTask.currentY > boundMaxY) {
                    activeTask.currentY = activeTask.minY;
                    activeTask.currentX++;
                }
            }
        }

        Player player = Bukkit.getPlayer(activeTask.playerUUID);
        if (player != null && player.isOnline()) {
            sendProgressBar(player, activeTask.currentIndex, (int) boxVolume);
        }

        if (activeTask.currentChunkListIndex >= activeTask.chunkKeys.size()) {
            completeActiveTask();
        }
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

    private void completeActiveTask() {
        EditOperation op = new EditOperation(activeTask.worldName, activeTask.historyChanges);

        Player player = Bukkit.getPlayer(activeTask.playerUUID);
        Session session = plugin.getSessionManager().getSession(activeTask.playerUUID);

        if (session != null) {
            if (activeTask.isUndoRedo) {
                if (activeTask.isUndoAction) {
                    session.addRedoOperation(op);
                } else {
                    session.addUndoOperation(op);
                }
            } else {
                session.addUndoOperation(op);
            }
        }

        World world = Bukkit.getWorld(activeTask.worldName);
        if (world != null && NmsChunkWriter.isReflectionActive()) {
            for (long key : activeTask.modifiedChunks) {
                int cx = (int) (key >> 32);
                int cz = (int) key;
                world.refreshChunk(cx, cz);
            }
        }

        if (player != null && player.isOnline()) {
            player.sendActionBar(Component.text(
                    "Modification terminée ! (" + activeTask.historyChanges.size() + " blocs)", NamedTextColor.GREEN));
            boolean notify = plugin.getConfig().getBoolean("edit.async.notify-on-complete", true);
            if (notify) {
                player.sendMessage(Component.text()
                        .append(Component.text("Modification terminée ! ", NamedTextColor.GREEN))
                        .append(Component.text("(" + activeTask.historyChanges.size() + " blocs affectés)",
                                NamedTextColor.GRAY))
                        .build());
            }
        }

        NmsChunkWriter.clearCache();
        activeTask = null;
    }

    public synchronized int getQueueSize() {
        return taskQueue.size();
    }

    public synchronized EditTask getActiveTask() {
        return activeTask;
    }

    private void sendProgressBar(Player player, int current, int total) {
        if (total == 0)
            return;
        int pct = (int) (100.0 * current / total);
        if (pct > 100) pct = 100;
        int greenBlocks = pct / 5;
        int grayBlocks = 20 - greenBlocks;

        Component barComponent = Component.text()
                .append(Component.text("Modification : ", NamedTextColor.LIGHT_PURPLE))
                .append(Component.text("[", NamedTextColor.WHITE))
                .append(Component.text("█".repeat(greenBlocks), NamedTextColor.GREEN))
                .append(Component.text("█".repeat(grayBlocks), NamedTextColor.GRAY))
                .append(Component.text("] ", NamedTextColor.WHITE))
                .append(Component.text(pct + "%", NamedTextColor.YELLOW))
                .build();

        player.sendActionBar(barComponent);
    }

    public static class EditTask {
        private final UUID playerUUID;
        private final String worldName;
        private final List<BlockChangeInfo> changes;
        private final boolean isUndoRedo;
        private final boolean isUndoAction;

        private int currentIndex = 0;
        private final List<EditOperation.BlockChange> historyChanges = new ArrayList<>();
        private final java.util.Set<Long> modifiedChunks = new java.util.HashSet<>();

        // Streaming mode properties
        private final fr.skynex.worldx.region.ShapeType selectionType;
        private final List<Location> selectionPoints;
        private final Palette activePalette;
        private final BlockData activeBlockData;
        private final BlockData fromBlock;
        private final boolean isReplace;
        private final int minX, maxX, minY, maxY, minZ, maxZ;
        
        // Chunk columns streaming keys
        private final List<Long> chunkKeys = new ArrayList<>();
        private int currentChunkListIndex = 0;
        private int currentX, currentY, currentZ;
        
        private final long totalVolume;
        private final String maskString;
        private transient Mask parsedMask;
        private transient boolean maskParsed;

        public Mask getParsedMask() {
            if (!maskParsed) {
                parsedMask = MaskParser.parse(maskString);
                maskParsed = true;
            }
            return parsedMask;
        }

        public EditTask(UUID playerUUID, String worldName, List<BlockChangeInfo> changes) {
            this(playerUUID, worldName, changes, false, false);
        }

        public EditTask(UUID playerUUID, String worldName, List<BlockChangeInfo> changes, boolean isUndoRedo,
                boolean isUndoAction) {
            this.playerUUID = playerUUID;
            this.worldName = worldName;
            this.changes = new ArrayList<>(changes);
            this.changes.sort((b1, b2) -> {
                int cx1 = b1.x >> 4;
                int cz1 = b1.z >> 4;
                int cx2 = b2.x >> 4;
                int cz2 = b2.z >> 4;
                if (cx1 != cx2)
                    return Integer.compare(cx1, cx2);
                if (cz1 != cz2)
                    return Integer.compare(cz1, cz2);
                return Integer.compare(b1.y, b2.y);
            });

            this.isUndoRedo = isUndoRedo;
            this.isUndoAction = isUndoAction;

            this.selectionType = null;
            this.selectionPoints = null;
            this.activePalette = null;
            this.activeBlockData = null;
            this.fromBlock = null;
            this.isReplace = false;
            this.minX = 0; this.maxX = 0; this.minY = 0; this.maxY = 0; this.minZ = 0; this.maxZ = 0;
            this.currentX = 0; this.currentY = 0; this.currentZ = 0;
            this.totalVolume = this.changes.size();
            this.maskString = null;
        }

        public EditTask(UUID playerUUID, String worldName, fr.skynex.worldx.region.ShapeType selectionType, List<Location> selectionPoints, Palette palette, BlockData blockData, BlockData fromBlock, boolean isReplace, int minX, int maxX, int minY, int maxY, int minZ, int maxZ, String maskString) {
            this.playerUUID = playerUUID;
            this.worldName = worldName;
            this.changes = null;
            this.isUndoRedo = false;
            this.isUndoAction = false;

            this.selectionType = selectionType;
            this.selectionPoints = selectionPoints != null ? new ArrayList<>(selectionPoints) : null;
            this.activePalette = palette;
            this.activeBlockData = blockData;
            this.fromBlock = fromBlock;
            this.isReplace = isReplace;
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.minZ = minZ;
            this.maxZ = maxZ;

            // Collect all intersecting chunk columns (16x16 column grids)
            int startCX = minX >> 4;
            int endCX = maxX >> 4;
            int startCZ = minZ >> 4;
            int endCZ = maxZ >> 4;
            for (int cx = startCX; cx <= endCX; cx++) {
                for (int cz = startCZ; cz <= endCZ; cz++) {
                    long key = (((long) cx) << 32) | (cz & 0xFFFFFFFFL);
                    chunkKeys.add(key);
                }
            }

            this.currentChunkListIndex = 0;
            initializePointersForCurrentChunk();

            this.totalVolume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
            this.maskString = maskString;
        }

        private void initializePointersForCurrentChunk() {
            if (currentChunkListIndex >= chunkKeys.size()) {
                return;
            }
            long key = chunkKeys.get(currentChunkListIndex);
            int cx = (int) (key >> 32);
            int cz = (int) key;

            this.currentX = Math.max(minX, cx << 4);
            this.currentY = minY;
            this.currentZ = Math.max(minZ, cz << 4);
        }
    }

    public static class BlockChangeInfo {
        public final int x, y, z;
        public final BlockData newData;

        public BlockChangeInfo(int x, int y, int z, BlockData newData) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.newData = newData;
        }
    }
}
