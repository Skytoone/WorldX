package fr.skynex.worldx.command;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.edit.Clipboard;
import fr.skynex.worldx.edit.SchematicEngine;
import fr.skynex.worldx.session.Session;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SchematicCommand implements CommandExecutor, TabCompleter {

    private final WorldX plugin;

    public SchematicCommand(WorldX plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Seuls les joueurs peuvent gérer les schémas.", NamedTextColor.RED));
            return true;
        }

        if (!plugin.getConfig().getBoolean("features.schematics", true)) {
            player.sendMessage(Component.text("Le système de schématiques (schematics) est actuellement désactivé sur ce serveur.", NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("worldx.schematic")) {
            player.sendMessage(Component.text("Vous n'avez pas la permission.", NamedTextColor.RED));
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("preview")) {
            handlePreview(player);
            return true;
        }

        if (args.length >= 1 && (args[0].equalsIgnoreCase("gui") || args[0].equalsIgnoreCase("menu"))) {
            player.openInventory(new fr.skynex.worldx.gui.SchematicGuiMenu(player, plugin).getInventory());
            return true;
        }

        if (args.length < 2) {
            sendHelp(player);
            return true;
        }

        String action = args[0].toLowerCase();
        String name = args[1].replaceAll("[^a-zA-Z0-9_-]", ""); // Sanitize name

        if (action.equals("save")) {
            handleSave(player, name);
        } else if (action.equals("load")) {
            handleLoad(player, name);
        } else if (action.equals("share")) {
            handleShare(player, name);
        } else if (action.equals("import")) {
            handleImport(player, args[1]); // import uses raw code which has dashes
        } else if (action.equals("animate")) {
            if (!plugin.getConfig().getBoolean("features.animated-3d", true)) {
                player.sendMessage(Component.text("Les schématiques animées 3D sont actuellement désactivées sur ce serveur.", NamedTextColor.RED));
                return true;
            }
            Session session = plugin.getSessionManager().getSession(player);
            if (session.getClipboard() != null) {
                fr.skynex.worldx.edit.AnimatedSchematicEngine.animateClipboard(plugin, player.getLocation(), session.getClipboard(), 1.0);
                player.sendMessage(Component.text("Animation 3D démarrée à 60 FPS avec entités BlockDisplay !", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("Votre presse-papier est vide. Chargez ou copiez un schéma d'abord.", NamedTextColor.RED));
            }
        } else if (action.equals("history")) {
            handleHistory(player, name);
        } else if (action.equals("rollback")) {
            if (args.length < 3) {
                player.sendMessage(Component.text("Usage: /schem rollback <nom> <version>", NamedTextColor.RED));
            } else {
                handleRollback(player, name, args[2]);
            }
        } else {
            sendHelp(player);
        }

        return true;
    }

    private void handleSave(Player player, String name) {
        Session session = plugin.getSessionManager().getSession(player);
        Clipboard clipboard = session.getClipboard();

        if (clipboard == null) {
            player.sendMessage(Component.text("Votre presse-papier est vide. Utilisez d'abord //copy.", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("Sauvegarde du schéma \"" + name + "\" en cours...", NamedTextColor.YELLOW));

        // Async task
        fr.skynex.worldx.scheduler.FoliaScheduler.runAsync(plugin, () -> {
            try {
                byte[] bytes = SchematicEngine.serialize(clipboard);

                // Save to file
                File schematicsDir = new File(plugin.getDataFolder(), "schematics");
                if (!schematicsDir.exists())
                    schematicsDir.mkdirs();
                File file = new File(schematicsDir, name + ".wxschem");
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(bytes);
                }

                // Save to DB (async internally)
                plugin.getDatabaseManager().saveSchematic(name, bytes);

                player.sendMessage(Component.text("Schéma \"" + name + "\" sauvegardé avec succès sur le disque et en base de données !", NamedTextColor.GREEN));
            } catch (IOException e) {
                player.sendMessage(Component.text("Erreur lors de la sauvegarde : " + e.getMessage(), NamedTextColor.RED));
                plugin.getLogger().warning("Error saving schematic " + name + ": " + e.getMessage());
            }
        });
    }

    private void handleLoad(Player player, String name) {
        player.sendMessage(Component.text("Chargement du schéma \"" + name + "\" en cours...", NamedTextColor.YELLOW));

        fr.skynex.worldx.scheduler.FoliaScheduler.runAsync(plugin, () -> {
            File schematicsDir = new File(plugin.getDataFolder(), "schematics");
            File matchedFile = null;

            if (schematicsDir.exists()) {
                File[] files = schematicsDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        String fName = f.getName();
                        // Get base name without last extension
                        String base = fName.contains(".") ? fName.substring(0, fName.lastIndexOf('.')) : fName;
                        if (base.equalsIgnoreCase(name)) {
                            matchedFile = f;
                            break;
                        }
                    }
                }
            }

            if (matchedFile != null && matchedFile.exists()) {
                final File finalFile = matchedFile;
                String fName = finalFile.getName().toLowerCase();
                if (fName.endsWith(".schem") || fName.endsWith(".schematic")) {
                    try {
                        Clipboard clipboard = fr.skynex.worldx.schematic.SpongeSchematicLoader.load(finalFile);
                        fr.skynex.worldx.scheduler.FoliaScheduler.runSync(plugin, () -> {
                            Session session = plugin.getSessionManager().getSession(player);
                            session.setClipboard(clipboard);
                            player.sendMessage(Component.text("Schéma Sponge \"" + finalFile.getName() + "\" chargé avec succès ("
                                    + clipboard.getBlocks().size() + " blocs). Utilisez //paste pour le coller.", NamedTextColor.GREEN));
                        });
                        return;
                    } catch (IOException e) {
                        player.sendMessage(Component.text("Erreur lors de la lecture du fichier schematic : " + e.getMessage(), NamedTextColor.RED));
                    }
                } else if (fName.endsWith(".wxschem")) {
                    try {
                        byte[] bytes = Files.readAllBytes(finalFile.toPath());
                        fr.skynex.worldx.scheduler.FoliaScheduler.runSync(plugin, () -> applyLoadedBytes(player, finalFile.getName().replace(".wxschem", ""), bytes));
                        return;
                    } catch (IOException ignored) {
                    }
                }
            }

            // 3. Fallback to database
            plugin.getDatabaseManager().loadSchematic(name).thenAccept(dbBytes -> {
                if (dbBytes == null) {
                    player.sendMessage(Component.text("Schéma \"" + name + "\" introuvable.", NamedTextColor.RED));
                    return;
                }
                fr.skynex.worldx.scheduler.FoliaScheduler.runSync(plugin, () -> applyLoadedBytes(player, name, dbBytes));
            });
        });
    }

    private void handleShare(Player player, String name) {
        File file = new File(new File(plugin.getDataFolder(), "schematics"), name + ".wxschem");
        if (!file.exists()) {
            player.sendMessage(
                    Component.text("Schéma \"" + name + "\" introuvable sur le disque. Sauvegardez-le d'abord.", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("Partage du schéma \"" + name + "\" sur le réseau...", NamedTextColor.YELLOW));

        fr.skynex.worldx.scheduler.FoliaScheduler.runAsync(plugin, () -> {
            try {
                byte[] bytes = Files.readAllBytes(file.toPath());

                String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
                java.util.Random rnd = new java.util.Random();
                StringBuilder sb = new StringBuilder("WX-");
                for (int i = 0; i < 4; i++) {
                    sb.append(chars.charAt(rnd.nextInt(chars.length())));
                }
                String shareCode = sb.toString();

                plugin.getDatabaseManager().shareSchematic(shareCode, bytes);
                player.sendMessage(Component.text()
                        .append(Component.text("Schéma partagé avec succès ! Code de partage : ", NamedTextColor.GREEN))
                        .append(Component.text(shareCode, NamedTextColor.LIGHT_PURPLE))
                        .build());
            } catch (IOException e) {
                player.sendMessage(Component.text("Erreur de lecture du fichier : " + e.getMessage(), NamedTextColor.RED));
            }
        });
    }

    private void handleImport(Player player, String code) {
        final String finalCode = code.toUpperCase();
        if (!finalCode.startsWith("WX-")) {
            player.sendMessage(Component.text("Code de partage invalide. Doit commencer par WX- (ex: WX-A89F).", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("Téléchargement du schéma réseau \"" + finalCode + "\"...", NamedTextColor.YELLOW));

        plugin.getDatabaseManager().importSchematic(finalCode).thenAccept(bytes -> {
            if (bytes == null) {
                player.sendMessage(Component.text("Code de partage inconnu ou expiré.", NamedTextColor.RED));
                return;
            }

            try {
                Clipboard clipboard = SchematicEngine.deserialize(bytes);
                Session session = plugin.getSessionManager().getSession(player);
                session.setClipboard(clipboard);

                File schematicsDir = new File(plugin.getDataFolder(), "schematics");
                if (!schematicsDir.exists())
                    schematicsDir.mkdirs();
                File file = new File(schematicsDir, "imported_" + finalCode.toLowerCase() + ".wxschem");
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(bytes);
                } catch (IOException ignored) {
                }

                player.sendMessage(Component.text("Schéma réseau chargé avec succès dans votre presse-papier ! Utilisez //paste.", NamedTextColor.GREEN));
            } catch (IOException e) {
                player.sendMessage(Component.text("Erreur de décodage du schéma réseau : " + e.getMessage(), NamedTextColor.RED));
            }
        });
    }

    private void applyLoadedBytes(Player player, String name, byte[] bytes) {
        try {
            Clipboard clipboard = SchematicEngine.deserialize(bytes);
            Session session = plugin.getSessionManager().getSession(player);
            session.setClipboard(clipboard);
            player.sendMessage(Component.text("Schéma \"" + name + "\" chargé avec succès ("
                    + clipboard.getBlocks().size() + " blocs). Utilisez //paste pour le coller.", NamedTextColor.GREEN));
        } catch (IOException e) {
            player.sendMessage(Component.text("Erreur de décodage du schéma : " + e.getMessage(), NamedTextColor.RED));
            plugin.getLogger().warning("Error deserializing schematic " + name + ": " + e.getMessage());
        }
    }

    private void handlePreview(Player player) {
        Session session = plugin.getSessionManager().getSession(player);
        if (session.getClipboard() == null) {
            player.sendMessage(Component.text("Votre presse-papier est vide. Chargez d'abord un schéma.", NamedTextColor.RED));
            return;
        }
        boolean active = !session.isSchematicPreviewActive();
        session.setSchematicPreviewActive(active);
        if (active) {
            player.sendMessage(Component.text("Aperçu holographique 3D du schéma activé ! Regardez le sol pour le voir.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Aperçu holographique 3D désactivé.", NamedTextColor.RED));
        }
    }

    private void handleHistory(Player player, String name) {
        player.sendMessage(Component.text("Chargement de l'historique pour le schéma \"" + name + "\", veuillez patienter...", NamedTextColor.YELLOW));
        plugin.getDatabaseManager().getSchematicHistory(name).thenAccept(versions -> {
            if (versions.isEmpty()) {
                player.sendMessage(Component.text("Aucun historique trouvé pour le schéma \"" + name + "\".", NamedTextColor.RED));
                return;
            }
            player.sendMessage(Component.text("=== Historique de \"" + name + "\" ===", NamedTextColor.GOLD));
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            for (fr.skynex.worldx.database.DatabaseManager.SchematicVersion sv : versions) {
                String dateStr = sdf.format(new java.util.Date(sv.getTimestamp()));
                player.sendMessage(Component.text()
                        .append(Component.text("v" + sv.getVersion(), NamedTextColor.YELLOW))
                        .append(Component.text(" - Sauvegardé le ", NamedTextColor.GRAY))
                        .append(Component.text(dateStr, NamedTextColor.WHITE))
                        .build());
            }
            player.sendMessage(Component.text("Pour restaurer une version, faites : /schem rollback " + name + " <version>", NamedTextColor.GRAY));
        });
    }

    private void handleRollback(Player player, String name, String versionStr) {
        int version;
        try {
            version = Integer.parseInt(versionStr.replace("v", "").replace("V", ""));
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Version invalide. Indiquez un nombre (ex: 2 ou v2).", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("Restauration de la version v" + version + " pour le schéma \"" + name + "\"...", NamedTextColor.YELLOW));
        plugin.getDatabaseManager().loadSchematicVersion(name, version).thenAccept(bytes -> {
            if (bytes == null) {
                player.sendMessage(Component.text("Version v" + version + " introuvable dans l'historique.", NamedTextColor.RED));
                return;
            }
            applyLoadedBytes(player, name, bytes);
        });
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("=== Aide WorldX Schematics ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text()
                .append(Component.text("/schem save <nom>", NamedTextColor.YELLOW))
                .append(Component.text(" - Sauvegarde votre presse-papier", NamedTextColor.GRAY))
                .build());
        player.sendMessage(Component.text()
                .append(Component.text("/schem load <nom>", NamedTextColor.YELLOW))
                .append(Component.text(" - Charge un schéma", NamedTextColor.GRAY))
                .build());
        player.sendMessage(Component.text()
                .append(Component.text("/schem share <nom>", NamedTextColor.YELLOW))
                .append(Component.text(" - Publie un schéma sur le réseau", NamedTextColor.GRAY))
                .build());
        player.sendMessage(Component.text()
                .append(Component.text("/schem import <code_partage>", NamedTextColor.YELLOW))
                .append(Component.text(" - Importe un schéma depuis le réseau", NamedTextColor.GRAY))
                .build());
        player.sendMessage(Component.text()
                .append(Component.text("/schem preview", NamedTextColor.YELLOW))
                .append(Component.text(" - Active/désactive l'aperçu 3D en particules", NamedTextColor.GRAY))
                .build());
        player.sendMessage(Component.text()
                .append(Component.text("/schem history <nom>", NamedTextColor.YELLOW))
                .append(Component.text(" - Affiche l'historique des sauvegardes", NamedTextColor.GRAY))
                .build());
        player.sendMessage(Component.text()
                .append(Component.text("/schem rollback <nom> <version>", NamedTextColor.YELLOW))
                .append(Component.text(" - Restaure une version du schéma", NamedTextColor.GRAY))
                .build());
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("save", "load", "share", "import", "preview", "history", "rollback").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("load") || args[0].equalsIgnoreCase("share")
                || args[0].equalsIgnoreCase("history") || args[0].equalsIgnoreCase("rollback"))) {
            File schematicsDir = new File(plugin.getDataFolder(), "schematics");
            if (schematicsDir.exists()) {
                File[] files = schematicsDir.listFiles((dir, nameVal) -> {
                    String lower = nameVal.toLowerCase();
                    return lower.endsWith(".wxschem") || lower.endsWith(".schem") || lower.endsWith(".schematic");
                });
                if (files != null) {
                    return Arrays.stream(files)
                            .map(f -> {
                                String fName = f.getName();
                                int dotIdx = fName.lastIndexOf('.');
                                return dotIdx > 0 ? fName.substring(0, dotIdx) : fName;
                            })
                            .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("rollback")) {
            return Arrays.asList("v1", "v2", "v3", "v4", "v5").stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}

