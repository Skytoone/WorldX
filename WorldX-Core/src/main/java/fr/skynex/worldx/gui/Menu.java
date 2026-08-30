package fr.skynex.worldx.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Arrays;
import java.util.List;

public abstract class Menu implements InventoryHolder {

    protected Inventory inventory;
    protected final Player player;

    public Menu(Player player) {
        this.player = player;
    }

    public abstract String getMenuName();
    public abstract int getSlots();
    public abstract void handleMenu(InventoryClickEvent event);
    public abstract void setMenuItems();

    public void open() {
        inventory = Bukkit.createInventory(this, getSlots(), LegacyComponentSerializer.legacySection().deserialize(getMenuName()));
        this.setMenuItems();
        player.openInventory(inventory);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    protected ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.displayName(LegacyComponentSerializer.legacySection().deserialize(name));
            }
            if (lore != null) {
                meta.lore(Arrays.stream(lore)
                        .map(line -> LegacyComponentSerializer.legacySection().deserialize(line))
                        .toList());
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    protected ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.displayName(LegacyComponentSerializer.legacySection().deserialize(name));
            }
            if (lore != null) {
                meta.lore(lore.stream()
                        .map(line -> LegacyComponentSerializer.legacySection().deserialize(line))
                        .toList());
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    protected void fillBackground() {
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < getSlots(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, glass);
            }
        }
    }
}
