package fr.skynex.worldx.command;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.region.ShapeType;
import fr.skynex.worldx.session.Session;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SelCommand implements CommandExecutor, TabCompleter {

    private final WorldX plugin;

    public SelCommand(WorldX plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Seuls les joueurs peuvent modifier leur forme de sélection.", NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("worldx.wand")) {
            player.sendMessage(Component.text("Vous n'avez pas la permission de changer la forme de sélection.", NamedTextColor.RED));
            return true;
        }

        Session session = plugin.getSessionManager().getSession(player);

        if (args.length == 0) {
            player.sendMessage(Component.text("=== Forme de sélection actuelle ===", NamedTextColor.GOLD));
            player.sendMessage(Component.text("Type : ", NamedTextColor.YELLOW)
                    .append(Component.text(session.getSelectionType().name(), NamedTextColor.LIGHT_PURPLE)));
            player.sendMessage(Component.text("Utilisation : /sel <cuboid|sphere|cylinder|polygon>", NamedTextColor.GRAY));
            return true;
        }

        String typeStr = args[0].toUpperCase();
        ShapeType type;
        try {
            type = ShapeType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("Forme inconnue. Choisissez parmi: cuboid, sphere, cylinder, polygon.", NamedTextColor.RED));
            return true;
        }

        session.setSelectionType(type);
        player.sendMessage(Component.text("Forme de sélection définie sur : ", NamedTextColor.GREEN)
                .append(Component.text(type.name(), NamedTextColor.LIGHT_PURPLE)));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.stream(ShapeType.values())
                    .map(s -> s.name().toLowerCase())
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
