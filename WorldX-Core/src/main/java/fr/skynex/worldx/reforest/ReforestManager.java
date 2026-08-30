package fr.skynex.worldx.reforest;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fr.skynex.worldx.WorldX;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

public class ReforestManager extends BukkitRunnable {

    private final WorldX plugin;
    private final List<ReforestBlock> queue = new ArrayList<>();
    private final java.util.Set<String> queuedKeys = new java.util.HashSet<>();
    private final File file;
    private final Gson gson = new Gson();

    public ReforestManager(WorldX plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "reforestation.json");
    }

    private static String toKey(String worldName, int x, int y, int z) {
        return worldName + ":" + x + ":" + y + ":" + z;
    }

    public synchronized void addBlock(Block block) {
        if (!plugin.getConfig().getBoolean("features.reforestation", true))
            return;

        // Double check permissions and flag
        Location loc = block.getLocation();
        fr.skynex.worldx.region.Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(loc);
        if (region == null)
            return;

        String val = plugin.getRegionManager().getEffectiveFlagValue(region, "natural-reforestation");
        if (!"allow".equalsIgnoreCase(val))
            return;

        // Limit maximum queue size to prevent memory exhaustion
        int maxQueueSize = plugin.getConfig().getInt("ecological.reforest-max-queue", 5000);
        if (queue.size() >= maxQueueSize) {
            return;
        }

        // Verify if it's already queued in O(1)
        String key = toKey(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if (queuedKeys.contains(key)) {
            return;
        }

        // Add to queue.
        long delay = plugin.getConfig().getLong("ecological.reforest-delay-seconds", 10) * 1000L;
        queue.add(new ReforestBlock(
                loc.getWorld().getName(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ(),
                block.getBlockData().getAsString(),
                System.currentTimeMillis() + delay));
        queuedKeys.add(key);
    }

    public void load() {
        if (!file.exists())
            return;
        try (FileReader reader = new FileReader(file)) {
            Type listType = new TypeToken<ArrayList<ReforestBlock>>() {
            }.getType();
            List<ReforestBlock> loaded = gson.fromJson(reader, listType);
            if (loaded != null) {
                synchronized (this) {
                    queue.addAll(loaded);
                    for (ReforestBlock rb : loaded) {
                        queuedKeys.add(toKey(rb.worldName, rb.x, rb.y, rb.z));
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load reforestation.json", e);
        }
    }

    public void save() {
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(file)) {
                synchronized (this) {
                    gson.toJson(queue, writer);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save reforestation.json", e);
        }
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        synchronized (this) {
            Iterator<ReforestBlock> it = queue.iterator();
            while (it.hasNext()) {
                ReforestBlock rb = it.next();
                if (now >= rb.restoreTime) {
                    World w = Bukkit.getWorld(rb.worldName);
                    if (w != null) {
                        Block b = w.getBlockAt(rb.x, rb.y, rb.z);
                        // Only restore if current block is air, leaves, or matching material (to
                        // prevent overwriting player builds)
                        if (b.getType() == Material.AIR || b.getType().name().contains("LEAVES")) {
                            try {
                                BlockData bd = Bukkit.createBlockData(rb.blockDataStr);
                                b.setBlockData(bd, true);
                                // Play growing particles & sound
                                Location loc = b.getLocation().add(0.5, 0.5, 0.5);
                                w.spawnParticle(Particle.HAPPY_VILLAGER, loc, 5, 0.3, 0.3, 0.3, 0.05);
                                w.playSound(loc, Sound.BLOCK_GRASS_PLACE, 0.6f, 1.2f);
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                    }
                    queuedKeys.remove(toKey(rb.worldName, rb.x, rb.y, rb.z));
                    it.remove();
                }
            }
        }
    }

    public static class ReforestBlock {
        public String worldName;
        public int x, y, z;
        public String blockDataStr;
        public long restoreTime;

        public ReforestBlock(String worldName, int x, int y, int z, String blockDataStr, long restoreTime) {
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockDataStr = blockDataStr;
            this.restoreTime = restoreTime;
        }
    }
}
