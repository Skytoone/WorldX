package fr.skynex.worldx.gui;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.region.Region;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RegionDetailsMenu extends Menu {

    private final WorldX plugin;
    private final Region region;

    public RegionDetailsMenu(Player player, WorldX plugin, Region region) {
        super(player);
        this.plugin = plugin;
        this.region = region;
    }

    @Override
    public String getMenuName() {
        return translate("<dark_blue>Région : " + region.getId());
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
            new RegionListMenu(player, plugin).open();
            return;
        }

        if (type == Material.BOOK) {
            new RegionFlagsMenu(player, plugin, region).open();
            return;
        }

        if (type == Material.WRITABLE_BOOK) {
            new RegionScriptingMenu(player, plugin, region).open();
            return;
        }

        if (type == Material.COMPASS) {
            plugin.getSelectionVisualizer().showRegion(player.getUniqueId(), region.getId(), 30);
            player.sendMessage(MiniMessage.miniMessage().deserialize("<green>Limites de la région affichées pendant 30 secondes."));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            player.closeInventory();
            return;
        }

        if (type == Material.TNT) {
            plugin.getRegionManager().removeRegion(region.getId());
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>La région \"" + region.getId() + "\" a été supprimée."));
            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
            player.closeInventory();
            return;
        }
    }

    @Override
    public void setMenuItems() {
        fillBackground();

        // 1. Info sign at slot 10
        List<String> infoLore = new ArrayList<>();
        infoLore.add(translate("<gray>Monde : <white>" + region.getWorldName()));
        infoLore.add(translate("<gray>Priorité : <white>" + region.getPriority()));
        infoLore.add(translate("<gray>Parent : <white>" + (region.getParentId() != null ? region.getParentId() : "Aucun")));
        infoLore.add(translate("<gray>Coordonnées : "));
        infoLore.add(translate("<white>" + String.format(" Min: %d, %d, %d", region.getMinX(), region.getMinY(), region.getMinZ())));
        infoLore.add(translate("<white>" + String.format(" Max: %d, %d, %d", region.getMaxX(), region.getMaxY(), region.getMaxZ())));
        inventory.setItem(10, createItem(Material.OAK_SIGN, translate("<gold>Informations"), infoLore));

        // 2. Flags book at slot 12
        List<String> flagLore = new ArrayList<>();
        flagLore.add(translate("<gray>Modifier les autorisations de la région :"));
        flagLore.add(translate("<gray> PvP, Build, Interactions, Spawn de monstres, etc."));
        flagLore.add("");
        flagLore.add(translate("<yellow>Clic pour configurer"));
        inventory.setItem(12, createItem(Material.BOOK, translate("<green>Configuration des Flags"), flagLore));

        // 2b. Visual Scripting writable book at slot 13
        List<String> scriptLore = new ArrayList<>();
        scriptLore.add(translate("<gray>Configurer des scripts interactifs :"));
        scriptLore.add(translate("<gray> Déclencher des actions lors d'événements."));
        scriptLore.add("");
        scriptLore.add(translate("<yellow>Clic pour scripter"));
        inventory.setItem(13, createItem(Material.WRITABLE_BOOK, translate("<gold>Visual Scripting"), scriptLore));

        // 3. Owners at slot 14
        List<String> ownersLore = region.getOwners().stream()
                .map(uuid -> translate("<white> - " + Bukkit.getOfflinePlayer(uuid).getName()))
                .collect(Collectors.toList());
        ownersLore.add(0, translate("<gray>Propriétaires de la zone :"));
        ownersLore.add("");
        ownersLore.add(translate("<dark_gray>Pour modifier, utilisez :"));
        ownersLore.add(translate("<dark_gray>/rg addowner/removeowner"));
        inventory.setItem(14, createItem(Material.GOLDEN_HELMET, translate("<gold>Propriétaires"), ownersLore));

        // 4. Members at slot 15
        List<String> membersLore = region.getMembers().stream()
                .map(uuid -> translate("<white> - " + Bukkit.getOfflinePlayer(uuid).getName()))
                .collect(Collectors.toList());
        membersLore.add(0, translate("<gray>Membres autorisés :"));
        membersLore.add("");
        membersLore.add(translate("<dark_gray>Pour modifier, utilisez :"));
        membersLore.add(translate("<dark_gray>/rg addmember/removemember"));
        inventory.setItem(15, createItem(Material.IRON_HELMET, translate("<blue>Membres"), membersLore));

        // 5. Visualizer compass at slot 16
        List<String> visualLore = new ArrayList<>();
        visualLore.add(translate("<gray>Affiche les bornes tridimensionnelles de"));
        visualLore.add(translate("<gray>cette région avec des particules vertes."));
        visualLore.add("");
        visualLore.add(translate("<yellow>Clic pour afficher"));
        inventory.setItem(16, createItem(Material.COMPASS, translate("<light_purple>Visualiser les limites"), visualLore));

        // 6. Delete TNT at slot 31
        inventory.setItem(31, createItem(
                Material.TNT, 
                translate("<red>Supprimer la région"), 
                translate("<gray>Action irréversible !"), 
                "", 
                translate("<dark_red>Clic pour CONFIRMER la suppression")
        ));

        // 7. Back arrow at slot 27
        inventory.setItem(27, createItem(Material.ARROW, translate("<gray>Retour")));
    }

    private String translate(String miniMessageString) {
        return LegacyComponentSerializer.legacySection().serialize(
                MiniMessage.miniMessage().deserialize(miniMessageString)
        );
    }
}
