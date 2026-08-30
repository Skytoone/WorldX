package fr.skynex.worldx.task;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.session.Session;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class PastePreviewTask extends BukkitRunnable {

    private final WorldX plugin;
    private final NamespacedKey relXKey;
    private final NamespacedKey relYKey;
    private final NamespacedKey relZKey;

    public PastePreviewTask(WorldX plugin) {
        this.plugin = plugin;
        this.relXKey = new NamespacedKey(plugin, "rel-x");
        this.relYKey = new NamespacedKey(plugin, "rel-y");
        this.relZKey = new NamespacedKey(plugin, "rel-z");
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Session session = plugin.getSessionManager().getSession(player);
            if (session == null) continue;

            List<BlockDisplay> displays = session.getPastePreviewDisplays();
            if (displays == null || displays.isEmpty()) continue;

            // Check if any display is invalid, if so clean up
            if (displays.stream().anyMatch(d -> !d.isValid())) {
                cleanupPreview(session);
                continue;
            }

            Block targetBlock = player.getTargetBlockExact(50);
            Location targetLoc = targetBlock != null ? targetBlock.getLocation().add(0, 1, 0) : player.getLocation().getBlock().getLocation();

            Location lastLoc = session.getPastePreviewLocation();
            if (lastLoc != null && lastLoc.getWorld().equals(targetLoc.getWorld()) &&
                lastLoc.getBlockX() == targetLoc.getBlockX() &&
                lastLoc.getBlockY() == targetLoc.getBlockY() &&
                lastLoc.getBlockZ() == targetLoc.getBlockZ()) {
                continue; // Location hasn't changed, skip teleporting to prevent packet spam
            }

            session.setPastePreviewLocation(targetLoc);

            for (BlockDisplay bd : displays) {
                Integer rx = bd.getPersistentDataContainer().get(relXKey, PersistentDataType.INTEGER);
                Integer ry = bd.getPersistentDataContainer().get(relYKey, PersistentDataType.INTEGER);
                Integer rz = bd.getPersistentDataContainer().get(relZKey, PersistentDataType.INTEGER);

                if (rx != null && ry != null && rz != null) {
                    Location loc = targetLoc.clone().add(rx, ry, rz);
                    bd.teleport(loc);
                }
            }
        }
    }

    private void cleanupPreview(Session session) {
        List<BlockDisplay> displays = session.getPastePreviewDisplays();
        if (displays != null) {
            for (BlockDisplay bd : displays) {
                if (bd != null && bd.isValid()) {
                    bd.remove();
                }
            }
            session.setPastePreviewDisplays(null);
            session.setPastePreviewLocation(null);
        }
    }
}
