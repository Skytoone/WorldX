package fr.skynex.worldx.gui;

import fr.skynex.worldx.WorldX;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SchematicGuiMenu implements Menu {

    private final WorldX plugin;
    private final Player player;
    private Inventory inventory;

    public SchematicGuiMenu(WorldX plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    @Override
    public Inventory getInventory() {
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, 54, Component.text("Galerie de Schématiques", NamedTextColor.DARK_PURPLE));
            buildMenu();
        }
        return inventory;
    }

    private void buildMenu() {
        inventory.clear();

        File schematicsDir = new File(plugin.getDataFolder(), "schematics");
        if (schematicsDir.exists()) {
            File[] files = schematicsDir.listFiles((dir, name) -> name.endsWith(".wxschem") || name.endsWith(".schem") || name.endsWith(".schematic"));
            if (files != null) {
                int slot = 0;
                for (File file : files) {
                    if (slot >= 45) break;

                    String name = file.getName().substring(0, file.getName().lastIndexOf('.'));
                    ItemStack item = new ItemStack(Material.PAPER);
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.displayName(Component.text(name, NamedTextColor.GOLD));
                        List<Component> lore = new ArrayList<>();
                        lore.add(Component.text("Taille : " + (file.length() / 1024) + " KB", NamedTextColor.GRAY));
                        lore.add(Component.text("Clic-gauche : Charger dans le presse-papier", NamedTextColor.GREEN));
                        lore.add(Component.text("Clic-droit : Activer l'aperçu 3D", NamedTextColor.LIGHT_PURPLE));
                        meta.lore(lore);
                        item.setItemMeta(meta);
                    }
                    inventory.setItem(slot++, item);
                }
            }
        }

        // Close button
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        if (closeMeta != null) {
            closeMeta.displayName(Component.text("Fermer", NamedTextColor.RED));
            close.setItemMeta(closeMeta);
        }
        inventory.setItem(49, close);
    }

    @Override
    public void handleClick(Player player, int slot, ItemStack clickedItem) {
        if (slot == 49) {
            player.closeInventory();
            return;
        }

        if (clickedItem != null && clickedItem.hasItemMeta()) {
            ItemMeta meta = clickedItem.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                String schemName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(meta.displayName());

                player.closeInventory();
                player.performCommand("schem load " + schemName);
            }
        }
    }
}
