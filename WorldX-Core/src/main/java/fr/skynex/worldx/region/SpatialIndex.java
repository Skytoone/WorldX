package fr.skynex.worldx.region;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class SpatialIndex {

    // Map: WorldName -> Long ChunkKey -> List of overlapping regions
    // Using primitive Long keys and synchronized ArrayList for thread safety and low GC overhead
    private final Map<String, Map<Long, List<Region>>> index = new ConcurrentHashMap<>();
    private final Map<String, List<Region>> largeRegions = new ConcurrentHashMap<>();

    private static final int MAX_CHUNK_INDEX_LIMIT = 2500; // Regions larger than 2500 chunks are stored in largeRegions

    private static long toChunkKey(int cx, int cz) {
        return (((long) cx) << 32) | (cz & 0xFFFFFFFFL);
    }

    public void addRegion(Region region) {
        String world = region.getWorldName();

        if (region.getShapeType() == ShapeType.GLOBAL) {
            List<Region> list = largeRegions.computeIfAbsent(world, k -> Collections.synchronizedList(new ArrayList<>()));
            synchronized (list) {
                if (!list.contains(region)) {
                    list.add(region);
                }
            }
            return;
        }

        int minChunkX, maxChunkX, minChunkZ, maxChunkZ;
        if (region.getShapeType() == ShapeType.SPHERE) {
            int radius = region.getMaxX();
            minChunkX = (region.getMinX() - radius) >> 4;
            maxChunkX = (region.getMinX() + radius) >> 4;
            minChunkZ = (region.getMinZ() - radius) >> 4;
            maxChunkZ = (region.getMinZ() + radius) >> 4;
        } else {
            minChunkX = region.getMinX() >> 4;
            maxChunkX = region.getMaxX() >> 4;
            minChunkZ = region.getMinZ() >> 4;
            maxChunkZ = region.getMaxZ() >> 4;
        }

        long chunkCount = (long) (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
        if (chunkCount > MAX_CHUNK_INDEX_LIMIT) {
            List<Region> list = largeRegions.computeIfAbsent(world, k -> Collections.synchronizedList(new ArrayList<>()));
            synchronized (list) {
                if (!list.contains(region)) {
                    list.add(region);
                }
            }
            return;
        }

        Map<Long, List<Region>> worldMap = index.computeIfAbsent(world, k -> new ConcurrentHashMap<>());
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                long key = toChunkKey(cx, cz);
                List<Region> list = worldMap.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()));
                synchronized (list) {
                    if (!list.contains(region)) {
                        list.add(region);
                    }
                }
            }
        }
    }

    public void removeRegion(Region region) {
        String world = region.getWorldName();
        List<Region> largeList = largeRegions.get(world);
        if (largeList != null) {
            largeList.remove(region);
        }

        if (region.getShapeType() == ShapeType.GLOBAL) {
            return;
        }

        Map<Long, List<Region>> worldMap = index.get(world);
        if (worldMap == null) return;

        int minChunkX, maxChunkX, minChunkZ, maxChunkZ;
        if (region.getShapeType() == ShapeType.SPHERE) {
            int radius = region.getMaxX();
            minChunkX = (region.getMinX() - radius) >> 4;
            maxChunkX = (region.getMinX() + radius) >> 4;
            minChunkZ = (region.getMinZ() - radius) >> 4;
            maxChunkZ = (region.getMinZ() + radius) >> 4;
        } else {
            minChunkX = region.getMinX() >> 4;
            maxChunkX = region.getMaxX() >> 4;
            minChunkZ = region.getMinZ() >> 4;
            maxChunkZ = region.getMaxZ() >> 4;
        }

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                long key = toChunkKey(cx, cz);
                List<Region> list = worldMap.get(key);
                if (list != null) {
                    synchronized (list) {
                        list.remove(region);
                        if (list.isEmpty()) {
                            worldMap.remove(key);
                        }
                    }
                }
            }
        }
    }

    public void updateRegion(Region region, int oldMinX, int oldMinZ, int oldMaxX, int oldMaxZ) {
        removeRegion(region);
        addRegion(region);
    }

    public List<Region> getRegionsAt(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return Collections.emptyList();
        }

        String world = loc.getWorld().getName();
        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;

        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        List<Region> result = new ArrayList<>();

        List<Region> candidates = getRegionsInChunk(world, chunkX, chunkZ);
        for (Region r : candidates) {
            if (r.contains(x, y, z)) {
                result.add(r);
            }
        }

        List<Region> largeList = largeRegions.get(world);
        if (largeList != null) {
            synchronized (largeList) {
                for (Region r : largeList) {
                    if (r.contains(x, y, z)) {
                        result.add(r);
                    }
                }
            }
        }

        return result;
    }

    public List<Region> getRegionsInChunk(String worldName, int chunkX, int chunkZ) {
        Map<Long, List<Region>> worldMap = index.get(worldName);
        if (worldMap == null) {
            return Collections.emptyList();
        }
        List<Region> list = worldMap.get(toChunkKey(chunkX, chunkZ));
        if (list == null) {
            return Collections.emptyList();
        }
        synchronized (list) {
            return new ArrayList<>(list);
        }
    }

    public void clear() {
        index.clear();
        largeRegions.clear();
    }

    public int getIndexedChunksCount() {
        int count = 0;
        for (Map<Long, List<Region>> worldIndex : index.values()) {
            count += worldIndex.size();
        }
        return count;
    }
}
