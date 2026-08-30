package fr.skynex.worldx.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

public class MenuListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;

        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof Menu menu) {
            event.setCancelled(true); // Prevent taking item out

            // Check if the click is in the GUI inventory, not player inventory
            if (event.getRawSlot() < event.getView().getTopInventory().getSize()) {
                menu.handleMenu(event);
            }
        } else if (holder instanceof PaletteMenu paletteMenu) {
            int slot = event.getRawSlot();
            if (slot >= 18 && slot < event.getView().getTopInventory().getSize()) {
                event.setCancelled(true);
                if (slot == 26) {
                    paletteMenu.handleSave();
                }
            }
        }
    }
}
