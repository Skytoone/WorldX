package fr.skynex.worldx.listener;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.session.Session;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;

public class SelectionHandlesListener implements Listener {

    private final WorldX plugin;
    private final NamespacedKey dirKey;
    private final NamespacedKey ownerKey;

    public SelectionHandlesListener(WorldX plugin) {
        this.plugin = plugin;
        this.dirKey = new NamespacedKey(plugin, "handle-direction");
        this.ownerKey = new NamespacedKey(plugin, "handle-owner");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction interaction)) {
            return;
        }

        String ownerStr = interaction.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (ownerStr == null || !ownerStr.equals(event.getPlayer().getUniqueId().toString())) {
            return;
        }

        String direction = interaction.getPersistentDataContainer().get(dirKey, PersistentDataType.STRING);
        if (direction == null) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        resizeSelection(player, direction, 1);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Interaction interaction)) {
            return;
        }

        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        String ownerStr = interaction.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (ownerStr == null || !ownerStr.equals(player.getUniqueId().toString())) {
            return;
        }

        String direction = interaction.getPersistentDataContainer().get(dirKey, PersistentDataType.STRING);
        if (direction == null) {
            return;
        }

        event.setCancelled(true);
        resizeSelection(player, direction, -1);
    }

    private void resizeSelection(Player player, String direction, int amount) {
        Session session = plugin.getSessionManager().getSession(player);
        if (session == null || !session.hasCompleteSelection()) {
            return;
        }

        Location p1 = session.getPos1();
        Location p2 = session.getPos2();
        org.bukkit.World world = p1.getWorld();

        int minX = Math.min(p1.getBlockX(), p2.getBlockX());
        int minY = Math.min(p1.getBlockY(), p2.getBlockY());
        int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
        int maxX = Math.max(p1.getBlockX(), p2.getBlockX());
        int maxY = Math.max(p1.getBlockY(), p2.getBlockY());
        int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());

        switch (direction.toUpperCase()) {
            case "UP":
                if (amount > 0)
                    maxY += amount;
                else
                    maxY = Math.max(minY, maxY + amount);
                break;
            case "DOWN":
                if (amount > 0)
                    minY -= amount;
                else
                    minY = Math.min(maxY, minY - amount);
                break;
            case "NORTH":
                if (amount > 0)
                    minZ -= amount;
                else
                    minZ = Math.min(maxZ, minZ - amount);
                break;
            case "SOUTH":
                if (amount > 0)
                    maxZ += amount;
                else
                    maxZ = Math.max(minZ, maxZ + amount);
                break;
            case "WEST":
                if (amount > 0)
                    minX -= amount;
                else
                    minX = Math.min(maxX, minX - amount);
                break;
            case "EAST":
                if (amount > 0)
                    maxX += amount;
                else
                    maxX = Math.max(minX, maxX + amount);
                break;
            default:
                return;
        }

        session.setPos1(new Location(world, minX, minY, minZ));
        session.setPos2(new Location(world, maxX, maxY, maxZ));

        String act = amount > 0 ? "Élargie" : "Rétrécie";
        player.sendActionBar(
                Component.text(act + " la sélection vers le " + direction.toLowerCase(), NamedTextColor.GREEN));
    }
}
