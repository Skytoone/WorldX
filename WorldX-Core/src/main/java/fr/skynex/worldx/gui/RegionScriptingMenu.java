package fr.skynex.worldx.gui;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.region.Region;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;

public class RegionScriptingMenu extends Menu {

    private final WorldX plugin;
    private final Region region;

    public RegionScriptingMenu(Player player, WorldX plugin, Region region) {
        super(player);
        this.plugin = plugin;
        this.region = region;
    }

    @Override
    public String getMenuName() {
        return translate("<dark_blue>Visual Scripting : " + region.getId());
    }

    @Override
    public int getSlots() {
        return 36;
    }

    @Override
    public void handleMenu(InventoryClickEvent event) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        Material type = clickedItem.getType();

        if (type == Material.ARROW) {
            new RegionDetailsMenu(player, plugin, region).open();
            return;
        }

        if (type == Material.LIGHT_WEIGHTED_PRESSURE_PLATE) {
            new TriggerActionsMenu(player, plugin, region, "script-gold-plate", "Stepping on Gold Pressure Plate").open();
            return;
        }

        if (type == Material.CHEST) {
            new TriggerActionsMenu(player, plugin, region, "script-open-chest", "Non-member Chest Open").open();
            return;
        }

        if (type == Material.OAK_DOOR) {
            new TriggerActionsMenu(player, plugin, region, "script-enter", "Player Region Entry").open();
            return;
        }
    }

    @Override
    public void setMenuItems() {
        fillBackground();

        // 1. Gold pressure plate at slot 11
        List<String> goldPlateLore = new ArrayList<>();
        goldPlateLore.add(translate("<gray>Déclenche des actions quand un joueur"));
        goldPlateLore.add(translate("<gray>marche sur une plaque de pression en or."));
        goldPlateLore.add("");
        String goldVal = region.getFlagValue("script-gold-plate");
        goldPlateLore.add(translate("<gray>Statut : " + (goldVal != null && !goldVal.isEmpty() ? "<green>Actif" : "<red>Inactif")));
        if (goldVal != null && !goldVal.isEmpty()) {
            goldPlateLore.add(translate("<dark_gray>Valeur : " + goldVal));
        }
        goldPlateLore.add("");
        goldPlateLore.add(translate("<yellow>Clic pour configurer"));
        inventory.setItem(11, createItem(Material.LIGHT_WEIGHTED_PRESSURE_PLATE, translate("<gold>Plaque de pression en or"), goldPlateLore));

        // 2. Chest at slot 13
        List<String> chestLore = new ArrayList<>();
        chestLore.add(translate("<gray>Déclenche des actions quand un joueur"));
        chestLore.add(translate("<gray>non-membre tente d'ouvrir un coffre."));
        chestLore.add("");
        String chestVal = region.getFlagValue("script-open-chest");
        chestLore.add(translate("<gray>Statut : " + (chestVal != null && !chestVal.isEmpty() ? "<green>Actif" : "<red>Inactif")));
        if (chestVal != null && !chestVal.isEmpty()) {
            chestLore.add(translate("<dark_gray>Valeur : " + chestVal));
        }
        chestLore.add("");
        chestLore.add(translate("<yellow>Clic pour configurer"));
        inventory.setItem(13, createItem(Material.CHEST, translate("<green>Ouverture de coffre"), chestLore));

        // 3. Door at slot 15
        List<String> doorLore = new ArrayList<>();
        doorLore.add(translate("<gray>Déclenche des actions lorsqu'un joueur"));
        doorLore.add(translate("<gray>entre dans cette région."));
        doorLore.add("");
        String enterVal = region.getFlagValue("script-enter");
        doorLore.add(translate("<gray>Statut : " + (enterVal != null && !enterVal.isEmpty() ? "<green>Actif" : "<red>Inactif")));
        if (enterVal != null && !enterVal.isEmpty()) {
            doorLore.add(translate("<dark_gray>Valeur : " + enterVal));
        }
        doorLore.add("");
        doorLore.add(translate("<yellow>Clic pour configurer"));
        inventory.setItem(15, createItem(Material.OAK_DOOR, translate("<light_purple>Entrée dans la région"), doorLore));

        // Back arrow at slot 27
        inventory.setItem(27, createItem(Material.ARROW, translate("<gray>Retour")));
    }

    private String translate(String miniMessageString) {
        return LegacyComponentSerializer.legacySection().serialize(
                MiniMessage.miniMessage().deserialize(miniMessageString)
        );
    }
}
