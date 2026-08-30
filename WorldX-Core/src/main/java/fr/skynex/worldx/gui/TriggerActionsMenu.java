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

public class TriggerActionsMenu extends Menu {

    private final WorldX plugin;
    private final Region region;
    private final String flagKey;
    private final String friendlyName;

    public TriggerActionsMenu(Player player, WorldX plugin, Region region, String flagKey, String friendlyName) {
        super(player);
        this.plugin = plugin;
        this.region = region;
        this.flagKey = flagKey;
        this.friendlyName = friendlyName;
    }

    @Override
    public String getMenuName() {
        return translate("<dark_blue>Actions : " + friendlyName);
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
            new RegionScriptingMenu(player, plugin, region).open();
            return;
        }

        String currentVal = region.getFlagValue(flagKey);
        if (currentVal == null) currentVal = "";

        List<String> actions = new ArrayList<>();
        for (String act : currentVal.split(";")) {
            if (!act.trim().isEmpty()) {
                actions.add(act.trim());
            }
        }

        if (type == Material.PAPER) {
            // Toggle Title
            toggleAction(actions, "title:", "title:Attention !");
        } else if (type == Material.JUKEBOX) {
            // Toggle Sound
            toggleAction(actions, "sound:", "sound:block.note_block.bell");
        } else if (type == Material.ENDER_PEARL) {
            // Toggle Teleport
            toggleAction(actions, "teleport:", "teleport:spawn");
        } else if (type == Material.ZOMBIE_HEAD) {
            // Toggle Guard Mobs
            toggleAction(actions, "spawn_mobs:", "spawn_mobs:zombie:3");
        }

        // Save
        String newVal = String.join(";", actions);
        region.setFlagValue(flagKey, newVal.isEmpty() ? null : newVal);
        plugin.getDatabaseManager().saveRegion(region); // Persist changes

        // Refresh Menu
        open();
    }

    private void toggleAction(List<String> actions, String prefix, String defaultVal) {
        boolean removed = actions.removeIf(act -> act.startsWith(prefix));
        if (!removed) {
            actions.add(defaultVal);
        }
    }

    @Override
    public void setMenuItems() {
        fillBackground();

        String currentVal = region.getFlagValue(flagKey);
        if (currentVal == null) currentVal = "";

        boolean hasTitle = currentVal.contains("title:");
        boolean hasSound = currentVal.contains("sound:");
        boolean hasTeleport = currentVal.contains("teleport:");
        boolean hasSpawn = currentVal.contains("spawn_mobs:");

        // 1. Title at slot 10
        List<String> titleLore = new ArrayList<>();
        titleLore.add(translate("<gray>Affiche un titre d'alerte à l'écran."));
        titleLore.add("");
        titleLore.add(translate("<gray>Statut : " + (hasTitle ? "<green>ACTIVE" : "<red>DESACTIVE")));
        titleLore.add("");
        titleLore.add(translate("<yellow>Clic pour basculer"));
        inventory.setItem(10, createItem(Material.PAPER, translate("<gold>Alerte de Titre"), titleLore));

        // 2. Sound at slot 12
        List<String> soundLore = new ArrayList<>();
        soundLore.add(translate("<gray>Joue une cloche d'alarme sonore."));
        soundLore.add("");
        soundLore.add(translate("<gray>Statut : " + (hasSound ? "<green>ACTIVE" : "<red>DESACTIVE")));
        soundLore.add("");
        soundLore.add(translate("<yellow>Clic pour basculer"));
        inventory.setItem(12, createItem(Material.JUKEBOX, translate("<green>Signal Sonore"), soundLore));

        // 3. Teleport at slot 14
        List<String> tpLore = new ArrayList<>();
        tpLore.add(translate("<gray>Téléporte le joueur au point de spawn de la région."));
        tpLore.add("");
        tpLore.add(translate("<gray>Statut : " + (hasTeleport ? "<green>ACTIVE" : "<red>DESACTIVE")));
        tpLore.add("");
        tpLore.add(translate("<yellow>Clic pour basculer"));
        inventory.setItem(14, createItem(Material.ENDER_PEARL, translate("<light_purple>Téléportation au Spawn"), tpLore));

        // 4. Spawn Guard Mobs at slot 16
        List<String> spawnLore = new ArrayList<>();
        spawnLore.add(translate("<gray>Fait apparaître 3 monstres de garde agressifs."));
        spawnLore.add("");
        spawnLore.add(translate("<gray>Statut : " + (hasSpawn ? "<green>ACTIVE" : "<red>DESACTIVE")));
        spawnLore.add("");
        spawnLore.add(translate("<yellow>Clic pour basculer"));
        inventory.setItem(16, createItem(Material.ZOMBIE_HEAD, translate("<red>Faire apparaître des Gardes"), spawnLore));

        // Back arrow at slot 27
        inventory.setItem(27, createItem(Material.ARROW, translate("<gray>Retour")));
    }

    private String translate(String miniMessageString) {
        return LegacyComponentSerializer.legacySection().serialize(
                MiniMessage.miniMessage().deserialize(miniMessageString)
        );
    }
}
