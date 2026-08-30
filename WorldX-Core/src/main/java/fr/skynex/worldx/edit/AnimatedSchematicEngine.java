package fr.skynex.worldx.edit;

import fr.skynex.worldx.WorldX;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class AnimatedSchematicEngine {

    public static void animateClipboard(WorldX plugin, Location startLoc, Clipboard clipboard, double speed) {
        if (clipboard == null || clipboard.getBlocks().isEmpty()) return;

        List<BlockDisplay> displays = new ArrayList<>();
        for (Clipboard.ClipboardBlock cb : clipboard.getBlocks()) {
            Location loc = startLoc.clone().add(cb.getRelX(), cb.getRelY(), cb.getRelZ());
            BlockDisplay bd = (BlockDisplay) startLoc.getWorld().spawnEntity(loc, EntityType.BLOCK_DISPLAY);
            bd.setBlock(cb.getBlockData());
            displays.add(bd);
        }

        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (step >= 100 || !plugin.isEnabled()) {
                    for (BlockDisplay bd : displays) {
                        if (bd.isValid()) bd.remove();
                    }
                    cancel();
                    return;
                }

                for (BlockDisplay bd : displays) {
                    Location loc = bd.getLocation();
                    bd.teleport(loc.add(0, 0.05 * speed, 0));
                }
                step++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
