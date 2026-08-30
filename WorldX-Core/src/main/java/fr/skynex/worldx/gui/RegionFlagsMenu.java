package fr.skynex.worldx.gui;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.region.Region;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class RegionFlagsMenu extends Menu {

    private final WorldX plugin;
    private final Region region;

    public RegionFlagsMenu(Player player, WorldX plugin, Region region) {
        super(player);
        this.plugin = plugin;
        this.region = region;
    }

    @Override
    public String getMenuName() {
        return ChatColor.DARK_BLUE + "Flags : " + region.getId();
    }

    @Override
    public int getSlots() {
        return 27;
    }

    @Override
    public void handleMenu(InventoryClickEvent event) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        int slot = event.getRawSlot();

        if (clickedItem.getType() == Material.ARROW) {
            new RegionDetailsMenu(player, plugin, region).open();
            return;
        }

        String targetFlag = null;
        if (slot == 10 || slot == 19) targetFlag = "build";
        else if (slot == 11 || slot == 20) targetFlag = "pvp";
        else if (slot == 12 || slot == 21) targetFlag = "use";
        else if (slot == 13 || slot == 22) targetFlag = "mob-spawn";
        else if (slot == 14 || slot == 23) targetFlag = "entry";
        else if (slot == 15 || slot == 24) targetFlag = "fall-damage";
        else if (slot == 16 || slot == 25) targetFlag = "keep-inventory";

        if (targetFlag != null) {
            cycleFlag(targetFlag);
            setMenuItems(); // Instant refresh
        }
    }

    private void cycleFlag(String flagName) {
        String current = region.getFlags().get(flagName);
        
        if (current == null) {
            region.getFlags().put(flagName, "allow");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.4f);
            player.spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 5, 0.2, 0.2, 0.2, 0.05);
        } else if (current.equalsIgnoreCase("allow")) {
            region.getFlags().put(flagName, "deny");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
            player.spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 5, 0.2, 0.2, 0.2, 0.05);
        } else {
            region.getFlags().remove(flagName);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
        }

        plugin.getRegionManager().addRegion(region); // Save region
    }

    @Override
    public void setMenuItems() {
        fillBackground();

        // 1. Build Flag (Slot 10 & 19)
        setupFlagItem(10, 19, Material.GRASS_BLOCK, "build", "Construction", 
                "Contrôle la pose et la destruction de blocs.");

        // 2. PvP Flag (Slot 11 & 20)
        setupFlagItem(11, 20, Material.IRON_SWORD, "pvp", "PvP", 
                "Contrôle les combats entre joueurs.");

        // 3. Use Flag (Slot 12 & 21)
        setupFlagItem(12, 21, Material.CHEST, "use", "Interactions", 
                "Contrôle l'accès aux coffres, portes, boutons, etc.");

        // 4. Mob-spawn Flag (Slot 13 & 22)
        setupFlagItem(13, 22, Material.ZOMBIE_HEAD, "mob-spawn", "Spawn de monstres", 
                "Contrôle l'apparition naturelle des monstres.");

        // 5. Entry Flag (Slot 14 & 23)
        setupFlagItem(14, 23, Material.IRON_DOOR, "entry", "Accès à la zone", 
                "Interdit ou autorise l'entrée des joueurs.");

        // 6. Fall-Damage Flag (Slot 15 & 24)
        setupFlagItem(15, 24, Material.FEATHER, "fall-damage", "Dégâts de chute", 
                "Contrôle si les joueurs subissent des dégâts de chute.");

        // 7. Keep-Inventory Flag (Slot 16 & 25)
        setupFlagItem(16, 25, Material.TOTEM_OF_UNDYING, "keep-inventory", "Conserver Inventaire", 
                "Conserve l'inventaire des joueurs à la mort.");

        // Return arrow at slot 18
        inventory.setItem(18, createItem(Material.ARROW, ChatColor.GRAY + "Retour"));
    }

    private void setupFlagItem(int flagSlot, int paneSlot, Material material, String flagName, String displayName, String description) {
        String value = region.getFlags().get(flagName);

        // 1. Set the main flag item
        List<String> flagLore = new ArrayList<>();
        flagLore.add(ChatColor.GRAY + description);
        flagLore.add("");
        flagLore.add(ChatColor.GRAY + "Valeur actuelle : " + formatValue(value));
        inventory.setItem(flagSlot, createItem(material, ChatColor.GOLD + displayName, flagLore));

        // 2. Set the state pane item
        Material paneMat;
        String paneName;
        if (value == null) {
            paneMat = Material.LIGHT_GRAY_STAINED_GLASS_PANE;
            paneName = ChatColor.GRAY + "État : NON DÉFINI (Hérité)";
        } else if (value.equalsIgnoreCase("allow")) {
            paneMat = Material.LIME_STAINED_GLASS_PANE;
            paneName = ChatColor.GREEN + "État : AUTORISÉ (ALLOW)";
        } else {
            paneMat = Material.RED_STAINED_GLASS_PANE;
            paneName = ChatColor.RED + "État : INTERDIT (DENY)";
        }

        inventory.setItem(paneSlot, createItem(paneMat, paneName, "", ChatColor.YELLOW + "Clic pour modifier (Cycle: Unset -> Allow -> Deny)"));
    }

    private String formatValue(String val) {
        if (val == null) return ChatColor.GRAY + "Non défini";
        if (val.equalsIgnoreCase("allow")) return ChatColor.GREEN + "ALLOW";
        return ChatColor.RED + "DENY";
    }
}
