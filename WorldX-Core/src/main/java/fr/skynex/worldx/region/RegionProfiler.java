package fr.skynex.worldx.region;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class RegionProfiler {

    private boolean active = false;

    public static class ProfilerStats {
        public final LongAdder count = new LongAdder();
        public final LongAdder totalNanos = new LongAdder();

        public void record(long nanos) {
            count.increment();
            totalNanos.add(nanos);
        }
    }

    private final Map<String, ProfilerStats> stats = new ConcurrentHashMap<>();

    public boolean isActive() {
        return active;
    }

    public void start() {
        active = true;
        stats.clear();
    }

    public void stop() {
        active = false;
    }

    public void record(String category, long nanos) {
        if (!active) return;
        stats.computeIfAbsent(category, k -> new ProfilerStats()).record(nanos);
    }

    public Map<String, ProfilerStats> getStats() {
        return stats;
    }
}
