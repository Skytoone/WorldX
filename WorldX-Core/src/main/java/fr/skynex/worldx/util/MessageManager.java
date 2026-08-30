package fr.skynex.worldx.util;

import fr.skynex.worldx.WorldX;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

import java.util.Map;

public class MessageManager {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    public static String getPrefix(WorldX plugin) {
        String prefix = plugin.getMessagesConfig().getString("prefix");
        if (prefix == null) {
            prefix = plugin.getConfig().getString("messages.prefix", "<gradient:#9B51E0:#2F80ED>[WorldX]</gradient> ");
        }
        return prefix;
    }

    public static Component getMessage(WorldX plugin, String key) {
        String raw = plugin.getMessagesConfig().getString(key);
        if (raw == null) {
            raw = plugin.getConfig().getString("messages." + key, "<red>Message introuvable: " + key);
        }
        return mm.deserialize(getPrefix(plugin) + raw);
    }

    public static Component getMessage(WorldX plugin, String key, Map<String, String> placeholders) {
        String raw = plugin.getMessagesConfig().getString(key);
        if (raw == null) {
            raw = plugin.getConfig().getString("messages." + key, "<red>Message introuvable: " + key);
        }
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return mm.deserialize(getPrefix(plugin) + raw);
    }

    public static void sendMessage(WorldX plugin, CommandSender sender, String key) {
        sender.sendMessage(getMessage(plugin, key));
    }

    public static void sendMessage(WorldX plugin, CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(getMessage(plugin, key, placeholders));
    }
}
