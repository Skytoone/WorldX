package fr.skynex.worldx.gui;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.region.Region;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RegionListMenu extends Menu {

    private final WorldX plugin;
    private List<Region> playerRegions;

    public RegionListMenu(Player player, WorldX plugin) {
        super(player);
        this.plugin = plugin;
    }

    @Override
    public String getMenuName() {
        return ChatColor.DARK_BLUE + "WorldX : Vos Régions";
    }

    @Override
    public int getSlots() {
        return 54;
    }

    @Override
    public void handleMenu(InventoryClickEvent event) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        if (clickedItem.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        if (clickedItem.getType() == Material.OAK_SIGN) {
            ItemMeta meta = clickedItem.getItemMeta();
            if (meta != null) {
                Component displayNameComponent = meta.displayName();
                if (displayNameComponent != null) {
                    String displayName = LegacyComponentSerializer.legacySection().serialize(displayNameComponent);
                    String regionId = ChatColor.stripColor(displayName);
                    Region region = plugin.getRegionManager().getRegion(regionId);
                    if (region != null) {
                        new RegionDetailsMenu(player, plugin, region).open();
                    }
                }
            }
        }
    }

    @Override
    public void setMenuItems() {
        fillBackground();

        UUID uuid = player.getUniqueId();
        playerRegions = new ArrayList<>();
        
        // Find regions where player is owner or member, or list all if player is admin
        boolean isAdmin = player.hasPermission("worldx.region.admin");
        for (Region r : plugin.getRegionManager().getRegions().values()) {
            if (isAdmin || r.isOwner(uuid) || r.isMember(uuid)) {
                playerRegions.add(r);
            }
        }

        int index = 0;
        // Limit to 45 items (slots 0 to 44)
        for (Region region : playerRegions) {
            if (index >= 45) break;

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Monde : " + ChatColor.WHITE + region.getWorldName());
            lore.add(ChatColor.GRAY + "Priorité : " + ChatColor.WHITE + region.getPriority());
            lore.add(ChatColor.GRAY + "Propriétaires : " + ChatColor.WHITE + region.getOwners().size());
            lore.add(ChatColor.GRAY + "Membres : " + ChatColor.WHITE + region.getMembers().size());
            lore.add("");
            lore.add(ChatColor.YELLOW + "Clic pour ouvrir les options");

            inventory.setItem(index, createItem(
                    Material.OAK_SIGN, 
                    ChatColor.GREEN + region.getId(), 
                    lore
            ));
            index++;
        }

        // Close button at slot 49
        inventory.setItem(49, createItem(Material.BARRIER, ChatColor.RED + "Fermer"));
    }
}
