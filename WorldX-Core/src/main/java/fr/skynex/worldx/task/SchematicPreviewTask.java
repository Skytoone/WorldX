package fr.skynex.worldx.task;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.edit.Clipboard;
import fr.skynex.worldx.session.Session;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.scheduler.BukkitRunnable;

public class SchematicPreviewTask extends BukkitRunnable {

    private final WorldX plugin;
    private final java.util.Map<java.util.UUID, BlockDisplay> activePreviews = new java.util.HashMap<>();

    public SchematicPreviewTask(WorldX plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            java.util.UUID uuid = player.getUniqueId();
            boolean hasPreview = false;

            Session session = plugin.getSessionManager().getSession(player);
            if (session != null && session.isSchematicPreviewActive()) {
                Clipboard clipboard = session.getClipboard();
                if (clipboard != null) {
                    Block target = player.getTargetBlockExact(50);
                    if (target != null) {
                        hasPreview = true;
                        updateSchematicPreview(player, target.getLocation().add(0, 1, 0), clipboard);
                    }
                }
            }

            if (!hasPreview) {
                removePreview(uuid);
            }
        }

        java.util.Iterator<java.util.UUID> it = activePreviews.keySet().iterator();
        while (it.hasNext()) {
            java.util.UUID uuid = it.next();
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                BlockDisplay display = activePreviews.get(uuid);
                if (display != null && display.isValid()) {
                    display.remove();
                }
                it.remove();
            }
        }
    }

    private void updateSchematicPreview(Player player, Location targetLoc, Clipboard clipboard) {
        java.util.UUID uuid = player.getUniqueId();
        BlockDisplay display = activePreviews.get(uuid);

        double w = clipboard.getWidth();
        double h = clipboard.getHeight();
        double l = clipboard.getLength();

        Location loc = targetLoc.clone().add(-0.01, -0.01, -0.01);
        org.bukkit.util.Transformation trans = new org.bukkit.util.Transformation(
            new org.joml.Vector3f(0, 0, 0),
            new org.joml.Quaternionf(),
            new org.joml.Vector3f((float) w + 0.02f, (float) h + 0.02f, (float) l + 0.02f),
            new org.joml.Quaternionf()
        );

        if (display != null && display.isValid() && display.getWorld().getName().equals(player.getWorld().getName())) {
            display.teleport(loc);
            display.setTransformation(trans);
        } else {
            if (display != null) display.remove();

            BlockDisplay newDisplay = player.getWorld().spawn(loc, BlockDisplay.class, entity -> {
                entity.setBlock(Bukkit.createBlockData(org.bukkit.Material.LIGHT_GRAY_STAINED_GLASS));
                entity.setTransformation(trans);
                entity.setPersistent(false);
                entity.setVisibleByDefault(false);
            });
            player.showEntity(plugin, newDisplay);
            activePreviews.put(uuid, newDisplay);
        }
    }

    private void removePreview(java.util.UUID uuid) {
        BlockDisplay display = activePreviews.remove(uuid);
        if (display != null && display.isValid()) {
            display.remove();
        }
    }

    public void cleanup() {
        for (BlockDisplay display : activePreviews.values()) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        activePreviews.clear();
    }
}
