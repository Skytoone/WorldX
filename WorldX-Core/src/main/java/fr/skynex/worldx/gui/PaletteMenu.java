package fr.skynex.worldx.gui;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.session.Session;
import org.bukkit.Bukkit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PaletteMenu implements InventoryHolder {

    private final Player player;
    private final WorldX plugin;
    private final Inventory inventory;

    public PaletteMenu(Player player, WorldX plugin) {
        this.player = player;
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 27, Component.text("Créateur de Dégradé"));
        setupItems();
    }

    private void setupItems() {
        // Fill slots 18 to 25 with gray glass panes (borders)
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        if (borderMeta != null) {
            borderMeta.displayName(Component.text(" "));
            border.setItemMeta(borderMeta);
        }
        for (int i = 18; i < 26; i++) {
            inventory.setItem(i, border);
        }

        // Slot 26 is the Save Button
        ItemStack save = new ItemStack(Material.NAME_TAG);
        ItemMeta saveMeta = save.getItemMeta();
        if (saveMeta != null) {
            saveMeta.displayName(Component.text("Sauvegarder le dégradé", NamedTextColor.GREEN));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Glissez vos blocs dans les lignes du haut.", NamedTextColor.GRAY));
            lore.add(Component.text("La quantité de blocs définit sa proportion (poids).", NamedTextColor.GRAY));
            lore.add(Component.text("Cliquez ici pour enregistrer.", NamedTextColor.YELLOW));
            saveMeta.lore(lore);
            save.setItemMeta(saveMeta);
        }
        inventory.setItem(26, save);
    }

    public void handleSave() {
        Session session = plugin.getSessionManager().getSession(player);
        if (session == null) return;

        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 18; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() != Material.AIR && item.getType().isBlock()) {
                items.add(item.clone());
            }
        }

        if (items.isEmpty()) {
            player.sendMessage(Component.text("Veuillez placer au moins un bloc valide dans le menu.", NamedTextColor.RED));
            player.closeInventory();
            return;
        }

        session.setPendingPaletteItems(items);
        session.setPaletteState("AWAITING_NAME");
        player.closeInventory();
        player.sendMessage(Component.text("================================================", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Entrez le nom du dégradé dans le chat (sans espaces).", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Tapez 'cancel' pour annuler.", NamedTextColor.GRAY));
        player.sendMessage(Component.text("================================================", NamedTextColor.GOLD));
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
