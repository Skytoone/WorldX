package fr.skynex.worldx.region;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class RegionAnalytics {

    private final String regionId;
    private final AtomicLong totalTimeSpentSeconds = new AtomicLong(0);
    private final AtomicLong blocksPlaced = new AtomicLong(0);
    private final AtomicLong blocksBroken = new AtomicLong(0);
    private final AtomicLong pvpKills = new AtomicLong(0);
    private final AtomicLong intrusions = new AtomicLong(0);

    // Heatmap data: Chunk Coordinate String -> Activity Count
    private final ConcurrentHashMap<String, AtomicLong> chunkActivityMap = new ConcurrentHashMap<>();

    public RegionAnalytics(String regionId) {
        this.regionId = regionId;
    }

    public String getRegionId() {
        return regionId;
    }

    public long getTotalTimeSpentSeconds() {
        return totalTimeSpentSeconds.get();
    }

    public void addTimeSpent(long seconds) {
        totalTimeSpentSeconds.addAndGet(seconds);
    }

    public long getBlocksPlaced() {
        return blocksPlaced.get();
    }

    public void incrementBlocksPlaced() {
        blocksPlaced.incrementAndGet();
    }

    public long getBlocksBroken() {
        return blocksBroken.get();
    }

    public void incrementBlocksBroken() {
        blocksBroken.incrementAndGet();
    }

    public long getPvpKills() {
        return pvpKills.get();
    }

    public void incrementPvpKills() {
        pvpKills.incrementAndGet();
    }

    public long getIntrusions() {
        return intrusions.get();
    }

    public void incrementIntrusions() {
        intrusions.incrementAndGet();
    }

    public void recordChunkActivity(int chunkX, int chunkZ) {
        String key = chunkX + "," + chunkZ;
        chunkActivityMap.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    }

    public ConcurrentHashMap<String, AtomicLong> getChunkActivityMap() {
        return chunkActivityMap;
    }
}
