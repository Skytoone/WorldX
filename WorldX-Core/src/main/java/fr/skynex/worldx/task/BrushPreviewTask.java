package fr.skynex.worldx.task;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.edit.BrushInfo;
import fr.skynex.worldx.session.Session;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.scheduler.BukkitRunnable;

public class BrushPreviewTask extends BukkitRunnable {

    private final WorldX plugin;
    private final java.util.Map<java.util.UUID, BlockDisplay> activePreviews = new java.util.HashMap<>();

    public BrushPreviewTask(WorldX plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        String brushMatName = plugin.getConfig().getString("edit.brush-item", "BLAZE_ROD");

        for (Player player : Bukkit.getOnlinePlayers()) {
            java.util.UUID uuid = player.getUniqueId();
            boolean hasBrush = false;

            if (player.isOnline()
                    && player.getInventory().getItemInMainHand().getType().name().equalsIgnoreCase(brushMatName)) {
                Session session = plugin.getSessionManager().getSession(uuid);
                if (session != null) {
                    BrushInfo brush = session.getBrushInfo();
                    if (brush != null) {
                        Block target = player.getTargetBlockExact(100);
                        if (target != null) {
                            hasBrush = true;
                            updateBrushPreview(player, target.getLocation(), brush);
                        }
                    }
                }
            }

            if (!hasBrush) {
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

    private void updateBrushPreview(Player player, Location targetLoc, BrushInfo brush) {
        java.util.UUID uuid = player.getUniqueId();
        BlockDisplay display = activePreviews.get(uuid);

        int radius = brush.getRadius();
        double size = radius * 2 + 1;
        org.bukkit.Material previewMat = brush.getType() == BrushInfo.BrushType.ERASER
                ? org.bukkit.Material.RED_STAINED_GLASS
                : (brush.getType() == BrushInfo.BrushType.PAINTER
                   ? org.bukkit.Material.LIME_STAINED_GLASS
                   : org.bukkit.Material.LIGHT_BLUE_STAINED_GLASS);

        Location loc = targetLoc.clone().add(-radius - 0.01, -radius - 0.01, -radius - 0.01);
        org.bukkit.util.Transformation trans = new org.bukkit.util.Transformation(
                new org.joml.Vector3f(0, 0, 0),
                new org.joml.Quaternionf(),
                new org.joml.Vector3f((float) size + 0.02f, (float) size + 0.02f, (float) size + 0.02f),
                new org.joml.Quaternionf());

        if (display != null && display.isValid() && display.getWorld().getName().equals(player.getWorld().getName())) {
            display.teleport(loc);
            display.setTransformation(trans);
            display.setBlock(Bukkit.createBlockData(previewMat));
        } else {
            if (display != null)
                display.remove();

            BlockDisplay newDisplay = player.getWorld().spawn(loc, BlockDisplay.class, entity -> {
                entity.setBlock(Bukkit.createBlockData(previewMat));
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
