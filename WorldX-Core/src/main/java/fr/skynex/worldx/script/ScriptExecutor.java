package fr.skynex.worldx.script;

import fr.skynex.worldx.region.Region;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.time.Duration;

public class ScriptExecutor {

    public static void runScript(Player player, Region region, String script) {
        if (script == null || script.trim().isEmpty()) {
            return;
        }

        for (String actionStr : script.split(";")) {
            actionStr = actionStr.trim();
            if (actionStr.isEmpty()) continue;

            if (actionStr.startsWith("require_item:")) {
                // Format: require_item:Clé du Donjon ? success_action1, success_action2 : fail_action1, fail_action2
                String rest = actionStr.substring(13);
                int questIdx = rest.indexOf('?');
                if (questIdx != -1) {
                    String itemName = rest.substring(0, questIdx).trim();
                    String actionsPart = rest.substring(questIdx + 1);
                    int colonIdx = actionsPart.indexOf(':');
                    String successActions = "";
                    String failActions = "";
                    if (colonIdx != -1) {
                        successActions = actionsPart.substring(0, colonIdx).trim();
                        failActions = actionsPart.substring(colonIdx + 1).trim();
                    } else {
                        successActions = actionsPart.trim();
                    }

                    if (hasNamedItem(player, itemName)) {
                        for (String act : successActions.split(",")) {
                            runSingleAction(player, region, act.trim());
                        }
                    } else {
                        for (String act : failActions.split(",")) {
                            runSingleAction(player, region, act.trim());
                        }
                    }
                }
            } else {
                runSingleAction(player, region, actionStr);
            }
        }
    }

    private static boolean hasNamedItem(Player player, String name) {
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == org.bukkit.Material.AIR) continue;
            if (item.hasItemMeta()) {
                org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    net.kyori.adventure.text.Component displayNameComponent = meta.displayName();
                    if (displayNameComponent != null) {
                        String plainName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                                .serialize(displayNameComponent);
                        String legacyName = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                                .serialize(displayNameComponent);
                        if (plainName.equalsIgnoreCase(name) || legacyName.equalsIgnoreCase(name)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static void runSingleAction(Player player, Region region, String actionStr) {
        if (actionStr.isEmpty()) return;

        if (actionStr.startsWith("title:")) {
            String text = actionStr.substring(6);
            player.showTitle(Title.title(
                    MiniMessage.miniMessage().deserialize(text),
                    Component.empty(),
                    Title.Times.times(
                            Duration.ofMillis(250),
                            Duration.ofMillis(1500),
                            Duration.ofMillis(250)
                    )
            ));
        } else if (actionStr.startsWith("sound:")) {
            String soundInput = actionStr.substring(6).trim();
            try {
                Sound sound = getSound(soundInput);
                if (sound != null) {
                    player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
                }
            } catch (Exception ignored) {}
        } else if (actionStr.equals("teleport:spawn")) {
            String spawnpointStr = region.getFlags().get("spawnpoint");
            Location tpLoc = null;
            if (spawnpointStr != null && !spawnpointStr.trim().isEmpty()) {
                String[] parts = spawnpointStr.split(",");
                if (parts.length >= 3) {
                    try {
                        double x = Double.parseDouble(parts[0].trim());
                        double y = Double.parseDouble(parts[1].trim());
                        double z = Double.parseDouble(parts[2].trim());
                        tpLoc = new Location(player.getWorld(), x, y, z);
                    } catch (Exception ignored) {}
                }
            }
            if (tpLoc == null) {
                double cx = region.getMinX() + (region.getMaxX() - region.getMinX()) / 2.0;
                double cz = region.getMinZ() + (region.getMaxZ() - region.getMinZ()) / 2.0;
                double cy = region.getMinY() + (region.getMaxY() - region.getMinY()) / 2.0;
                if (cy < player.getWorld().getMinHeight()) {
                    cy = player.getWorld().getSpawnLocation().getY();
                }
                tpLoc = new Location(player.getWorld(), cx, cy + 1, cz);
            }
            player.teleport(tpLoc);
        } else if (actionStr.startsWith("spawn_mobs:")) {
            String[] parts = actionStr.split(":");
            if (parts.length >= 3) {
                try {
                    String typeName = parts[1].toUpperCase();
                    int count = Integer.parseInt(parts[2]);
                    EntityType entityType = EntityType.valueOf(typeName);
                    for (int i = 0; i < count; i++) {
                        player.getWorld().spawnEntity(player.getLocation(), entityType);
                    }
                } catch (Exception ignored) {}
            }
        } else if (actionStr.equals("push_back")) {
            player.setVelocity(player.getLocation().getDirection().multiply(-1.2).setY(0.4));
        }
    }

    private static Sound getSound(String soundInput) {
        if (soundInput == null || soundInput.isEmpty()) {
            return null;
        }

        try {
            NamespacedKey key;
            if (soundInput.contains(":")) {
                key = NamespacedKey.fromString(soundInput.toLowerCase());
            } else {
                key = NamespacedKey.minecraft(soundInput.toLowerCase());
            }
            if (key != null) {
                Sound sound = Registry.SOUNDS.get(key);
                if (sound != null) {
                    return sound;
                }
            }
        } catch (Exception ignored) {}

        try {
            String dotted = soundInput.toLowerCase().replace("_", ".");
            NamespacedKey key = NamespacedKey.minecraft(dotted);
            Sound sound = Registry.SOUNDS.get(key);
            if (sound != null) {
                return sound;
            }
        } catch (Exception ignored) {}

        try {
            String targetLegacyName = soundInput.toUpperCase().replace(".", "_");
            for (Sound sound : Registry.SOUNDS) {
                NamespacedKey key = Registry.SOUNDS.getKey(sound);
                if (key != null) {
                    String legacyName = key.getKey().toUpperCase().replace(".", "_");
                    if (legacyName.equals(targetLegacyName) || key.getKey().equalsIgnoreCase(soundInput)) {
                        return sound;
                    }
                }
            }
        } catch (Exception ignored) {}

        return null;
    }
}
