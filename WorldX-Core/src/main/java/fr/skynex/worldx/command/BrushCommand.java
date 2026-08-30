package fr.skynex.worldx.command;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.edit.BrushInfo;
import fr.skynex.worldx.session.Session;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
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
import org.bukkit.NamespacedKey;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

public class BrushCommand implements CommandExecutor, TabCompleter {

    private final WorldX plugin;

    public BrushCommand(WorldX plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Seuls les joueurs peuvent configurer les brosses."));
            return true;
        }

        if (!player.hasPermission("worldx.brush")) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Vous n'avez pas la permission d'utiliser les brosses."));
            return true;
        }

        Session session = plugin.getSessionManager().getSession(player);

        if (args.length == 0) {
            sendUsage(player, session);
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("none") || sub.equals("off") || sub.equals("clear")) {
            session.setBrushInfo(null);
            player.sendMessage(MiniMessage.miniMessage().deserialize("<green>Brosse désactivée."));
            return true;
        }

        if (args.length < 2 && !sub.equalsIgnoreCase("clipboard")) {
            sendUsage(player, session);
            return true;
        }

        BrushInfo.BrushType type;
        try {
            type = BrushInfo.BrushType.valueOf(sub.toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Type de brosse inconnu. Choisissez parmi: sphere, eraser, smooth, height, noise, flatten, undo, tree, road, blend, spline, clipboard, erosion, biome, ruins, greeble, painter."));
            return true;
        }

        int radius = 1;
        if (args.length >= 2) {
            try {
                radius = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Le rayon doit être un nombre entier."));
                return true;
            }

            int maxRadius = plugin.getConfig().getInt("edit.max-brush-radius", 6);
            if (radius < 1 || radius > maxRadius) {
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Le rayon doit être compris entre 1 et " + maxRadius + "."));
                return true;
            }
        }

        BlockData blockData = Material.STONE.createBlockData();
        String metadata = null;
        if (type == BrushInfo.BrushType.TREE) {
            String treeType = (args.length >= 3) ? args[2].toLowerCase() : "oak";
            if (!treeType.equals("oak") && !treeType.equals("spruce") && !treeType.equals("birch")) {
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Type d'arbre inconnu. Choisissez parmi: oak, spruce, birch."));
                return true;
            }
            Material logMat = Material.OAK_LOG;
            if (treeType.equals("spruce")) logMat = Material.SPRUCE_LOG;
            if (treeType.equals("birch")) logMat = Material.BIRCH_LOG;
            blockData = logMat.createBlockData();
        } else if (type == BrushInfo.BrushType.BIOME) {
            if (args.length < 3) {
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Usage: /brush biome <rayon> <nom_du_biome>"));
                return true;
            }
            String biomeInput = args[2].toLowerCase();
            NamespacedKey key = null;
            try {
                if (biomeInput.contains(":")) {
                    key = NamespacedKey.fromString(biomeInput);
                } else {
                    key = NamespacedKey.minecraft(biomeInput);
                }
            } catch (IllegalArgumentException e) {
                // Invalid key characters
            }
            if (key == null) {
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Biome inconnu."));
                return true;
            }
            org.bukkit.block.Biome biome = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).get(key);
            if (biome == null) {
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Biome inconnu."));
                return true;
            }
            metadata = key.toString();
        } else if (args.length >= 3 && type != BrushInfo.BrushType.ERASER && type != BrushInfo.BrushType.UNDO && type != BrushInfo.BrushType.EROSION && type != BrushInfo.BrushType.RUINS && type != BrushInfo.BrushType.GREEBLE) {
            String matName = args[2].toUpperCase();
            Material mat = Material.getMaterial(matName);
            if (mat == null || !mat.isBlock()) {
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Bloc invalide."));
                return true;
            }
            blockData = mat.createBlockData();
        }
        
        if (type == BrushInfo.BrushType.PAINTER) {
            if (args.length < 3) {
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Usage: /brush painter <rayon> <nom_palette>"));
                return true;
            }
            String paletteName = args[2];
            int finalRadius = radius;
            plugin.getDatabaseManager().loadPalette(paletteName).thenAccept(pal -> {
                fr.skynex.worldx.scheduler.FoliaScheduler.runSync(plugin, () -> {
                    if (pal == null) {
                        player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Palette introuvable : " + paletteName));
                        return;
                    }
                    session.setActivePaintPalette(pal);
                    BrushInfo brush = new BrushInfo(BrushInfo.BrushType.PAINTER, finalRadius, Material.STONE.createBlockData(), paletteName);
                    session.setBrushInfo(brush);
                    String triggerItem = plugin.getConfig().getString("edit.brush-item", "BLAZE_ROD");
                    player.sendMessage(MiniMessage.miniMessage().deserialize(
                            String.format("<green>Brosse configurée : <light_purple>PAINTER<green> (Palette: %s, Rayon: %d). Activez-la en faisant un clic droit avec un(e) <yellow>%s<green>.",
                                    paletteName, finalRadius, triggerItem)
                    ));
                });
            });
            return true;
        }

        BrushInfo brush = new BrushInfo(type, radius, blockData, metadata);
        session.setBrushInfo(brush);

        String triggerItem = plugin.getConfig().getString("edit.brush-item", "BLAZE_ROD");
        player.sendMessage(MiniMessage.miniMessage().deserialize(
                String.format("<green>Brosse configurée : <light_purple>%s<green> (Rayon: %d). Activez-la en faisant un clic droit avec un(e) <yellow>%s<green>.",
                        type.name(), radius, triggerItem)
        ));

        return true;
    }

    private void sendUsage(Player player, Session session) {
        player.sendMessage(MiniMessage.miniMessage().deserialize("<gold>=== Aide WorldX Brushes ==="));
        player.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>/brush \\<sphere|eraser|smooth|undo|tree|road|blend|spline|clipboard|erosion|biome|ruins|greeble\\> \\<rayon\\> [bloc/type]<gray> - Active une brosse"));
        player.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>/brush off<gray> - Désactive la brosse"));
        
        BrushInfo active = session.getBrushInfo();
        if (active != null) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    String.format("<light_purple>Brosse active : <green>%s (Rayon: %d, Bloc: %s)",
                            active.getType().name(), active.getRadius(), active.getBlockData().getMaterial().name())
            ));
        } else {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<light_purple>Aucune brosse active."));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("sphere", "eraser", "smooth", "height", "noise", "flatten", "undo", "tree", "road", "blend", "spline", "clipboard", "erosion", "biome", "ruins", "greeble", "painter", "off").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 2 && !args[0].equalsIgnoreCase("off")) {
            return Arrays.asList("1", "2", "3", "4", "5", "6").stream()
                    .filter(s -> s.startsWith(args[1])).collect(Collectors.toList());
        }

        if (args.length == 3 && !args[0].equalsIgnoreCase("off") && !args[0].equalsIgnoreCase("eraser") && !args[0].equalsIgnoreCase("undo") && !args[0].equalsIgnoreCase("erosion") && !args[0].equalsIgnoreCase("ruins") && !args[0].equalsIgnoreCase("greeble") && !args[0].equalsIgnoreCase("painter")) {
            if (args[0].equalsIgnoreCase("painter")) {
                try {
                    return plugin.getDatabaseManager().getPaletteNames().get(100, java.util.concurrent.TimeUnit.MILLISECONDS).stream()
                            .filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
                } catch (Exception e) {
                    return Collections.emptyList();
                }
            }
            if (args[0].equalsIgnoreCase("tree")) {
                return Arrays.asList("oak", "spruce", "birch").stream()
                        .filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }
            if (args[0].equalsIgnoreCase("biome")) {
                return RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).stream()
                        .map(b -> b.getKey().getKey())
                        .filter(s -> s.startsWith(args[2].toLowerCase()))
                        .limit(20)
                        .collect(Collectors.toList());
            }
            return Arrays.stream(Material.values())
                    .filter(m -> m.isBlock())
                    .map(m -> m.name().toLowerCase())
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .limit(20)
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}

