package fr.skynex.worldx.command;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.region.Region;
import fr.skynex.worldx.region.RegionAction;
import fr.skynex.worldx.session.Session;
import fr.skynex.worldx.gui.RegionListMenu;
import org.bukkit.Bukkit;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import fr.skynex.worldx.edit.BlockEditQueue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class RegionCommand implements CommandExecutor, TabCompleter {

    private final WorldX plugin;

    public RegionCommand(WorldX plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "define":
            case "create":
                return handleDefine(sender, args);
            case "delete":
            case "remove":
                return handleDelete(sender, args);
            case "info":
                return handleInfo(sender, args);
            case "flag":
                return handleFlag(sender, args);
            case "addmember":
                return handleAddMember(sender, args, false);
            case "addowner":
                return handleAddMember(sender, args, true);
            case "removemember":
                return handleRemoveMember(sender, args, false);
            case "removeowner":
                return handleRemoveMember(sender, args, true);
            case "priority":
                return handlePriority(sender, args);
            case "parent":
                return handleParent(sender, args);
            case "show":
                return handleShow(sender, args);
            case "apply-preset":
            case "preset":
                return handleApplyPreset(sender, args);
            case "gui":
            case "menu":
                return handleGui(sender, args);
            case "script":
                return handleScriptCommand(sender, args);
            case "siege":
                return handleSiegeCommand(sender, args);
            case "rollback":
                return new RollbackCommand(plugin).onCommand(sender, command, label, args);
            case "bindschem":
                return handleBindSchem(sender, args);
            case "reset":
                return handleReset(sender, args);
            case "claim":
                return handleClaim(sender, args);
            case "debug":
            case "trace":
                return handleDebug(sender, args);
            case "undo":
                return handleUndo(sender, args);
            case "redo":
                return handleRedo(sender, args);
            case "save":
                return handleSaveRegion(sender, args);
            case "profile":
                return handleProfile(sender, args);
            case "select":
                return handleSelectRegion(sender, args);
            case "tp":
            case "teleport":
                return handleTpRegion(sender, args);
            case "list":
                return handleListRegions(sender, args);
            case "rentable":
                return handleRentable(sender, args);
            case "rent":
                return handleRent(sender, args);
            case "near":
                return handleNearRegions(sender, args);
            case "rename":
                return handleRenameRegion(sender, args);
            default:
                sendHelp(sender);
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Aide Commandes WorldX Regions ===");
        sender.sendMessage(ChatColor.YELLOW + "/rg define <id>" + ChatColor.GRAY + " - Crée une région à partir de la sélection");
        sender.sendMessage(ChatColor.YELLOW + "/rg debug <joueur> <flag>" + ChatColor.GRAY + " - Diagnostic des permissions d'un joueur");
        sender.sendMessage(ChatColor.YELLOW + "/rg delete <id>" + ChatColor.GRAY + " - Supprime une région");
        sender.sendMessage(ChatColor.YELLOW + "/rg info [id]" + ChatColor.GRAY + " - Affiche des informations sur la région");
        sender.sendMessage(ChatColor.YELLOW + "/rg flag <id> <flag> [allow|deny|none]" + ChatColor.GRAY + " - Modifie les flags");
        sender.sendMessage(ChatColor.YELLOW + "/rg addowner/addmember <id> <joueur>" + ChatColor.GRAY + " - Ajoute un propriétaire/membre");
        sender.sendMessage(ChatColor.YELLOW + "/rg removeowner/removemember <id> <joueur>" + ChatColor.GRAY + " - Retire un propriétaire/membre");
        sender.sendMessage(ChatColor.YELLOW + "/rg priority <id> <priorité>" + ChatColor.GRAY + " - Modifie la priorité d'une région");
        sender.sendMessage(ChatColor.YELLOW + "/rg parent <id> [parent_id]" + ChatColor.GRAY + " - Associe une région parente");
        sender.sendMessage(ChatColor.YELLOW + "/rg show [id|none]" + ChatColor.GRAY + " - Affiche les limites physiques en particules");
        sender.sendMessage(ChatColor.YELLOW + "/rg gui" + ChatColor.GRAY + " - Ouvre l'interface de gestion des régions");
        sender.sendMessage(ChatColor.YELLOW + "/rg rollback <id> [temps] [joueur]" + ChatColor.GRAY + " - Annule les modifications de blocs");
        sender.sendMessage(ChatColor.YELLOW + "/rg bindschem <id> <nom_schéma>" + ChatColor.GRAY + " - Associe un schéma à une région");
        sender.sendMessage(ChatColor.YELLOW + "/rg reset <id>" + ChatColor.GRAY + " - Réinitialise la région avec son schéma");
        sender.sendMessage(ChatColor.YELLOW + "/rg claim <id>" + ChatColor.GRAY + " - Réclame une zone dans votre budget de blocs");
        sender.sendMessage(ChatColor.YELLOW + "/rg tp <id>" + ChatColor.GRAY + " - Téléporte au centre d'une région");
        sender.sendMessage(ChatColor.YELLOW + "/rg near" + ChatColor.GRAY + " - Liste les régions proches (rayon 50 blocs)");
        sender.sendMessage(ChatColor.YELLOW + "/rg rename <ancien_id> <nouveau_id>" + ChatColor.GRAY + " - Renomme une région");
    }

    private boolean handleDefine(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Seuls les joueurs peuvent créer des régions.");
            return true;
        }

        if (!player.hasPermission("worldx.region.admin")) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission de gérer les régions.");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /rg define <id> [cuboid|sphere|cylinder|polygon|global]");
            return true;
        }

        String id = args[1];
        if (plugin.getRegionManager().getRegion(id) != null) {
            player.sendMessage(ChatColor.RED + "Une région avec l'ID \"" + id + "\" existe déjà.");
            return true;
        }

        String shape = args.length >= 3 ? args[2].toLowerCase() : "";

        Region region;

        if (id.equalsIgnoreCase("__global__") || shape.equals("global")) {
            region = new Region(id, player.getWorld().getName());
            player.sendMessage(ChatColor.GREEN + "Région globale \"" + id + "\" créée avec succès pour le monde " + player.getWorld().getName() + " ! (Priorité par défaut: -1)");
        } else {
            Session session = plugin.getSessionManager().getSession(player);
            if (!session.hasCompleteSelection()) {
                player.sendMessage(ChatColor.RED + "Veuillez d'abord faire une sélection (//wand).");
                return true;
            }

            Location p1 = session.getPos1();
            Location p2 = session.getPos2();
            
            if (shape.isEmpty()) {
                shape = session.getSelectionType().name().toLowerCase();
            }

            if (shape.equals("sphere")) {
                if (p1 == null || p2 == null) {
                    player.sendMessage(ChatColor.RED + "Sélection invalide.");
                    return true;
                }
                int radius = Math.max(1, (int) p1.distance(p2));
                region = new Region(id, p1.getWorld().getName(), p1.getBlockX(), p1.getBlockY(), p1.getBlockZ(), radius);
                player.sendMessage(ChatColor.GREEN + "Région sphérique \"" + id + "\" créée (Centre: Pos 1, Rayon: " + radius + " blocs).");
            } else if (shape.equals("cylinder")) {
                if (p1 == null || p2 == null) {
                    player.sendMessage(ChatColor.RED + "Sélection invalide.");
                    return true;
                }
                int radius = Math.max(1, (int) Math.sqrt(Math.pow(p1.getBlockX() - p2.getBlockX(), 2) + Math.pow(p1.getBlockZ() - p2.getBlockZ(), 2)));
                int minY = Math.min(p1.getBlockY(), p2.getBlockY());
                int maxY = Math.max(p1.getBlockY(), p2.getBlockY());
                region = new Region(id, p1.getWorld().getName(), p1.getBlockX(), minY, p1.getBlockZ(), radius, maxY, 0, fr.skynex.worldx.region.ShapeType.CYLINDER);
                player.sendMessage(ChatColor.GREEN + "Région cylindrique \"" + id + "\" créée (Centre: Pos 1, Rayon: " + radius + " blocs, Hauteur: " + minY + " à " + maxY + ").");
            } else if (shape.equals("polygon") || shape.equals("poly")) {
                List<Location> points = session.getSelectionPoints();
                if (points.size() < 3) {
                    player.sendMessage(ChatColor.RED + "Veuillez sélectionner au moins 3 points pour un polygone (utilisez /sel polygon et ajoutez des points avec le clic droit de la baguette).");
                    return true;
                }
                
                // Calculate bounding box and min/max Y
                int minX = Integer.MAX_VALUE;
                int minZ = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE;
                int maxZ = Integer.MIN_VALUE;
                int minY = Integer.MAX_VALUE;
                int maxY = Integer.MIN_VALUE;
                
                List<int[]> polyPoints = new java.util.ArrayList<>();
                for (Location loc : points) {
                    int px = loc.getBlockX();
                    int py = loc.getBlockY();
                    int pz = loc.getBlockZ();
                    
                    minX = Math.min(minX, px);
                    minZ = Math.min(minZ, pz);
                    maxX = Math.max(maxX, px);
                    maxZ = Math.max(maxZ, pz);
                    minY = Math.min(minY, py);
                    maxY = Math.max(maxY, py);
                    
                    polyPoints.add(new int[]{px, pz});
                }
                
                region = new Region(id, points.get(0).getWorld().getName(), minX, minY, minZ, maxX, maxY, maxZ, fr.skynex.worldx.region.ShapeType.POLYGON);
                region.setPolyPoints(polyPoints);
                player.sendMessage(ChatColor.GREEN + "Région polygonale \"" + id + "\" créée avec succès ! (" + polyPoints.size() + " sommets).");
            } else {
                if (p1 == null || p2 == null) {
                    player.sendMessage(ChatColor.RED + "Sélection invalide.");
                    return true;
                }
                region = new Region(
                        id,
                        p1.getWorld().getName(),
                        p1.getBlockX(), p1.getBlockY(), p1.getBlockZ(),
                        p2.getBlockX(), p2.getBlockY(), p2.getBlockZ()
                );
                player.sendMessage(ChatColor.GREEN + "Région cubique \"" + id + "\" créée avec succès !");
            }
        }

        region.addOwner(player.getUniqueId()); // The creator is owner
        Region cloned = RegionAction.cloneRegion(region);
        Session sessionObj = plugin.getSessionManager().getSession(player);
        if (sessionObj != null) {
            sessionObj.addRegionUndo(new RegionAction(RegionAction.Type.CREATE, null, cloned));
        }
        plugin.getRegionManager().addRegion(region);

        // Discord log
        fr.skynex.worldx.integration.DiscordWebhookLogger.logRegionAction(
                plugin, "CRÉATION", player.getName(), id,
                "Forme: " + (region.getShapeType() != null ? region.getShapeType().name() : "CUBOID") + ", Monde: " + region.getWorldName()
        );

        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("worldx.region.admin")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rg delete <id>");
            return true;
        }

        String id = args[1];
        Region deletedRegion = plugin.getRegionManager().getRegion(id);
        if (deletedRegion == null) {
            sender.sendMessage(ChatColor.RED + "Région introuvable.");
            return true;
        }

        if (sender instanceof Player player) {
            Session session = plugin.getSessionManager().getSession(player);
            Region cloned = RegionAction.cloneRegion(deletedRegion);
            session.addRegionUndo(new RegionAction(RegionAction.Type.DELETE, cloned, null));
        }
        plugin.getRegionManager().removeRegion(id);
        sender.sendMessage(ChatColor.GREEN + "Région \"" + id + "\" supprimée avec succès.");

        // Discord log
        fr.skynex.worldx.integration.DiscordWebhookLogger.logRegionAction(
                plugin, "SUPPRESSION", sender.getName(), id,
                "Monde: " + deletedRegion.getWorldName()
        );

        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        Region region = null;

        if (args.length >= 2) {
            region = plugin.getRegionManager().getRegion(args[1]);
        } else if (sender instanceof Player player) {
            List<Region> list = plugin.getRegionManager().getRegionsAt(player.getLocation());
            if (!list.isEmpty()) {
                // Sort by priority to show the most relevant one first
                list.sort((r1, r2) -> Integer.compare(r2.getPriority(), r1.getPriority()));
                region = list.get(0);
            }
        }

        if (region == null) {
            sender.sendMessage(ChatColor.RED + "Aucune région trouvée ici, ou ID non spécifié.");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "=== Région : " + region.getId() + " ===");
        sender.sendMessage(ChatColor.YELLOW + "Monde : " + ChatColor.WHITE + region.getWorldName());
        sender.sendMessage(ChatColor.YELLOW + "Priorité : " + ChatColor.WHITE + region.getPriority());
        sender.sendMessage(ChatColor.YELLOW + "Parent : " + ChatColor.WHITE + (region.getParentId() != null ? region.getParentId() : "Aucun"));
        sender.sendMessage(ChatColor.YELLOW + "Bornes : " + ChatColor.WHITE + 
                String.format("(%d, %d, %d) à (%d, %d, %d)", 
                        region.getMinX(), region.getMinY(), region.getMinZ(),
                        region.getMaxX(), region.getMaxY(), region.getMaxZ()));
        
        String owners = region.getOwners().stream()
                .map(uuid -> Bukkit.getOfflinePlayer(uuid).getName())
                .collect(Collectors.joining(", "));
        sender.sendMessage(ChatColor.YELLOW + "Propriétaires : " + ChatColor.WHITE + (owners.isEmpty() ? "Aucun" : owners));

        String members = region.getMembers().stream()
                .map(uuid -> Bukkit.getOfflinePlayer(uuid).getName())
                .collect(Collectors.joining(", "));
        sender.sendMessage(ChatColor.YELLOW + "Membres : " + ChatColor.WHITE + (members.isEmpty() ? "Aucun" : members));

        StringBuilder flagsSb = new StringBuilder();
        region.getFlags().forEach((key, val) -> flagsSb.append(key).append(": ").append(val).append(", "));
        String flagsStr = flagsSb.toString();
        if (!flagsStr.isEmpty()) {
            flagsStr = flagsStr.substring(0, flagsStr.length() - 2);
        } else {
            flagsStr = "Aucun";
        }
        sender.sendMessage(ChatColor.YELLOW + "Flags : " + ChatColor.WHITE + flagsStr);

        return true;
    }

    private boolean handleFlag(CommandSender sender, String[] args) {
        if (!sender.hasPermission("worldx.region.admin")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rg flag <id> <flag> [valeur|none]");
            return true;
        }

        Region region = plugin.getRegionManager().getRegion(args[1]);
        if (region == null) {
            sender.sendMessage(ChatColor.RED + "Région introuvable.");
            return true;
        }

        String flagName = args[2].toLowerCase();
        List<String> knownFlags = Arrays.asList("build", "pvp", "use", "mob-spawn", "entry", "greeting", "farewell", "heal-delay", "feed-delay", "gamemode", "crop-trample", "item-frame-rotate", "hanging-destroy", "fall-damage", "hunger", "keep-inventory", "mob-target", "command-blacklist", "command-whitelist", "fire-spread", "explosion", "leaf-decay", "god-mode", "respawn-location", "projectile", "money-multiplier", "money-loot-on-kill", "liquid-flow", "piston-interact", "fire-ignite", "auto-replant", "villager-trade", "armor-stand-interact", "exit", "gravity-modifier", "particles-ambient", "auto-rollback-interval", "temporary-blocks", "schematic-regen-on-entry", "max-players", "mob-damage-multiplier", "deny-elytra-boost", "absorption-shield", "deny-ender-chest", "blocked-crafting", "regional-chat", "announcement-interval", "void-teleport", "region-chat-format", "no-potion-drink", "fog-color", "keep-effects-on-death", "prevent-teleport", "intrusion-command");
        if (!knownFlags.contains(flagName)) {
            sender.sendMessage(ChatColor.YELLOW + "Attention : \"" + flagName + "\" n'est pas un flag standard.");
        }

        Region oldState = RegionAction.cloneRegion(region);

        if (args.length < 4 || args[3].equalsIgnoreCase("none")) {
            region.getFlags().remove(flagName);
            plugin.getRegionManager().addRegion(region); // Save
            recordRegionUpdate(sender, oldState, region);
            sender.sendMessage(ChatColor.GREEN + "Flag \"" + flagName + "\" supprimé de la région.");
            fr.skynex.worldx.integration.DiscordWebhookLogger.logRegionAction(plugin, "RETRAIT_FLAG", sender.getName(), region.getId(), "Flag: " + flagName);
            return true;
        }

        // Join arguments to support message flags with spaces (e.g. greeting)
        StringBuilder valBuilder = new StringBuilder();
        for (int i = 3; i < args.length; i++) {
            valBuilder.append(args[i]).append(" ");
        }
        String value = valBuilder.toString().trim();

        region.getFlags().put(flagName, value);
        if (flagName.equalsIgnoreCase("fog-color") || flagName.equalsIgnoreCase("biome")) {
            applyRegionBiomeFromName(region, value);
        }
        plugin.getRegionManager().addRegion(region); // Save
        recordRegionUpdate(sender, oldState, region);
        sender.sendMessage(ChatColor.GREEN + "Flag \"" + flagName + "\" défini sur \"" + value + "\".");
        fr.skynex.worldx.integration.DiscordWebhookLogger.logRegionAction(plugin, "MODIF_FLAG", sender.getName(), region.getId(), "Flag: " + flagName + " = " + value);
        return true;
    }

    private boolean handleAddMember(CommandSender sender, String[] args, boolean isOwner) {
        if (!sender.hasPermission("worldx.region.admin")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rg add" + (isOwner ? "owner" : "member") + " <id> <joueur>");
            return true;
        }

        Region region = plugin.getRegionManager().getRegion(args[1]);
        if (region == null) {
            sender.sendMessage(ChatColor.RED + "Région introuvable.");
            return true;
        }

        String targetName = args[2];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            sender.sendMessage(ChatColor.RED + "Joueur inconnu ou n'ayant jamais joué.");
            return true;
        }

        Region oldState = RegionAction.cloneRegion(region);

        if (isOwner) {
            region.addOwner(target.getUniqueId());
        } else {
            region.addMember(target.getUniqueId());
        }

        plugin.getRegionManager().addRegion(region); // Save
        recordRegionUpdate(sender, oldState, region);
        sender.sendMessage(ChatColor.GREEN + "Ajouté avec succès comme " + (isOwner ? "propriétaire" : "membre") + ".");
        return true;
    }

    private boolean handleRemoveMember(CommandSender sender, String[] args, boolean isOwner) {
        if (!sender.hasPermission("worldx.region.admin")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rg remove" + (isOwner ? "owner" : "member") + " <id> <joueur>");
            return true;
        }

        Region region = plugin.getRegionManager().getRegion(args[1]);
        if (region == null) {
            sender.sendMessage(ChatColor.RED + "Région introuvable.");
            return true;
        }

        String targetName = args[2];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        UUID uuid = target.getUniqueId();

        Region oldState = RegionAction.cloneRegion(region);

        boolean removed;
        if (isOwner) {
            removed = region.removeOwner(uuid);
        } else {
            removed = region.removeMember(uuid);
        }

        if (removed) {
            plugin.getRegionManager().addRegion(region); // Save
            recordRegionUpdate(sender, oldState, region);
            sender.sendMessage(ChatColor.GREEN + "Retiré avec succès.");
        } else {
            sender.sendMessage(ChatColor.RED + "Le joueur n'était pas dans ce groupe.");
        }
        return true;
    }

    private boolean handlePriority(CommandSender sender, String[] args) {
        if (!sender.hasPermission("worldx.region.admin")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rg priority <id> <priorité>");
            return true;
        }

        Region region = plugin.getRegionManager().getRegion(args[1]);
        if (region == null) {
            sender.sendMessage(ChatColor.RED + "Région introuvable.");
            return true;
        }

        Region oldState = RegionAction.cloneRegion(region);

        try {
            int priority = Integer.parseInt(args[2]);
            region.setPriority(priority);
            plugin.getRegionManager().addRegion(region); // Save
            recordRegionUpdate(sender, oldState, region);
            sender.sendMessage(ChatColor.GREEN + "Priorité de \"" + region.getId() + "\" définie sur " + priority + ".");
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Priorité invalide (nombre entier requis).");
        }
        return true;
    }

    private boolean handleParent(CommandSender sender, String[] args) {
        if (!sender.hasPermission("worldx.region.admin")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rg parent <id> [parent_id]");
            return true;
        }

        Region region = plugin.getRegionManager().getRegion(args[1]);
        if (region == null) {
            sender.sendMessage(ChatColor.RED + "Région introuvable.");
            return true;
        }

        Region oldState = RegionAction.cloneRegion(region);

        if (args.length < 3 || args[2].equalsIgnoreCase("none")) {
            region.setParentId(null);
            plugin.getRegionManager().addRegion(region); // Save
            recordRegionUpdate(sender, oldState, region);
            sender.sendMessage(ChatColor.GREEN + "Parent retiré pour la région \"" + region.getId() + "\".");
            return true;
        }

        String parentId = args[2];
        Region parent = plugin.getRegionManager().getRegion(parentId);
        if (parent == null) {
            sender.sendMessage(ChatColor.RED + "Région parente \"" + parentId + "\" introuvable.");
            return true;
        }

        // Circular checks
        if (parentId.equalsIgnoreCase(region.getId())) {
            sender.sendMessage(ChatColor.RED + "Une région ne peut pas être son propre parent.");
            return true;
        }

        region.setParentId(parent.getId());
        plugin.getRegionManager().addRegion(region); // Save
        recordRegionUpdate(sender, oldState, region);
        sender.sendMessage(ChatColor.GREEN + "Parent de \"" + region.getId() + "\" défini sur \"" + parent.getId() + "\".");
        return true;
    }

    private boolean handleShow(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Seuls les joueurs peuvent utiliser cette commande.");
            return true;
        }

        if (!player.hasPermission("worldx.region.admin")) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
            return true;
        }

        if (args.length < 2) {
            plugin.getSelectionVisualizer().hideRegion(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "Affichage des régions masqué.");
            return true;
        }

        String id = args[1];
        if (id.equalsIgnoreCase("none")) {
            plugin.getSelectionVisualizer().hideRegion(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "Affichage des régions masqué.");
            return true;
        }

        Region region = plugin.getRegionManager().getRegion(id);
        if (region == null) {
            player.sendMessage(ChatColor.RED + "Région introuvable.");
            return true;
        }

        boolean forceParticles = false;
        if (args.length >= 3 && args[2].equalsIgnoreCase("particles")) {
            forceParticles = true;
        }

        plugin.getSelectionVisualizer().showRegion(player.getUniqueId(), region.getId(), 30, forceParticles);
        if (forceParticles) {
            player.sendMessage(ChatColor.GREEN + "Affichage de la région \"" + region.getId() + "\" pendant 30 secondes en particules vertes.");
        } else {
            player.sendMessage(ChatColor.GREEN + "Affichage de la région \"" + region.getId() + "\" pendant 30 secondes.");
        }
        return true;
    }

    private boolean handleApplyPreset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("worldx.region.admin")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rg apply-preset <id> <preset>");
            return true;
        }

        Region region = plugin.getRegionManager().getRegion(args[1]);
        if (region == null) {
            sender.sendMessage(ChatColor.RED + "Région introuvable.");
            return true;
        }

        String presetName = args[2];
        org.bukkit.configuration.file.FileConfiguration presets = plugin.getPresetsConfig();
        if (!presets.contains(presetName)) {
            sender.sendMessage(ChatColor.RED + "Preset \"" + presetName + "\" introuvable.");
            return true;
        }

        org.bukkit.configuration.ConfigurationSection section = presets.getConfigurationSection(presetName);
        if (section == null) {
            sender.sendMessage(ChatColor.RED + "Preset \"" + presetName + "\" mal configuré.");
            return true;
        }

        Region oldState = RegionAction.cloneRegion(region);

        for (String key : section.getKeys(false)) {
            String value = section.getString(key);
            region.getFlags().put(key.toLowerCase(), value);
            if (key.equalsIgnoreCase("fog-color") || key.equalsIgnoreCase("biome")) {
                applyRegionBiomeFromName(region, value);
            }
        }

        plugin.getRegionManager().addRegion(region); // Save
        recordRegionUpdate(sender, oldState, region);
        sender.sendMessage(ChatColor.GREEN + "Le preset \"" + presetName + "\" a été appliqué avec succès à la région \"" + region.getId() + "\".");
        fr.skynex.worldx.integration.DiscordWebhookLogger.logRegionAction(plugin, "APPLY_PRESET", sender.getName(), region.getId(), "Preset: " + presetName);
        return true;
    }

    private void applyRegionBiomeFromName(Region region, String biomeName) {
        if (biomeName == null || biomeName.trim().isEmpty()) return;
        org.bukkit.NamespacedKey key;
        if (biomeName.contains(":")) {
            key = org.bukkit.NamespacedKey.fromString(biomeName.toLowerCase());
        } else {
            key = org.bukkit.NamespacedKey.minecraft(biomeName.toLowerCase());
        }
        if (key == null) return;
        
        org.bukkit.block.Biome targetBiome = io.papermc.paper.registry.RegistryAccess.registryAccess().getRegistry(io.papermc.paper.registry.RegistryKey.BIOME).get(key);
        if (targetBiome == null) return;

        org.bukkit.World world = Bukkit.getWorld(region.getWorldName());
        if (world == null) return;

        int minX = region.getMinX();
        int maxX = region.getMaxX();
        int minY = region.getMinY();
        int maxY = region.getMaxY();
        int minZ = region.getMinZ();
        int maxZ = region.getMaxZ();

        for (int x = minX; x <= maxX; x += 4) {
            for (int y = minY; y <= maxY; y += 4) {
                for (int z = minZ; z <= maxZ; z += 4) {
                    world.setBiome(x, y, z, targetBiome);
                }
            }
        }

        // Refresh chunks
        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                world.refreshChunk(cx, cz);
            }
        }
    }

    private boolean handleGui(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Seuls les joueurs peuvent utiliser cette commande.");
            return true;
        }

        new RegionListMenu(player, plugin).open();
        return true;
    }

    private boolean handleScriptCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Seuls les joueurs peuvent utiliser cette commande.");
            return true;
        }

        if (!player.hasPermission("worldx.region.script")) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
            return true;
        }

        Region region = null;
        if (args.length >= 2) {
            region = plugin.getRegionManager().getRegion(args[1]);
        } else {
            List<Region> list = plugin.getRegionManager().getRegionsAt(player.getLocation());
            if (!list.isEmpty()) {
                list.sort((r1, r2) -> Integer.compare(r2.getPriority(), r1.getPriority()));
                region = list.get(0);
            }
        }

        if (region == null) {
            player.sendMessage(ChatColor.RED + "Région introuvable ici ou ID non spécifié.");
            return true;
        }

        new fr.skynex.worldx.gui.RegionScriptingMenu(player, plugin, region).open();
        return true;
    }

    private boolean handleBindSchem(CommandSender sender, String[] args) {
        if (!sender.hasPermission("worldx.region.admin")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rg bindschem <id> <nom_schéma>");
            return true;
        }
        String id = args[1];
        String schemName = args[2];
        Region region = plugin.getRegionManager().getRegion(id);
        if (region == null) {
            sender.sendMessage(ChatColor.RED + "Région introuvable.");
            return true;
        }

        File file = new File(new File(plugin.getDataFolder(), "schematics"), schemName + ".wxschem");
        if (!file.exists()) {
            plugin.getDatabaseManager().loadSchematic(schemName).thenAccept(dbBytes -> {
                if (dbBytes == null) {
                    sender.sendMessage(ChatColor.RED + "Schéma \"" + schemName + "\" introuvable (ni sur le disque, ni en BDD).");
                } else {
                    bindAndSave(sender, region, schemName);
                }
            });
        } else {
            bindAndSave(sender, region, schemName);
        }
        return true;
    }

    private void bindAndSave(CommandSender sender, Region region, String schemName) {
        region.setBoundSchematic(schemName);
        plugin.getRegionManager().addRegion(region); // Saves in DB and memory
        sender.sendMessage(ChatColor.GREEN + "Le schéma \"" + schemName + "\" a été associé à la région \"" + region.getId() + "\".");
    }

    private boolean handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("worldx.region.admin")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rg reset <id>");
            return true;
        }
        String id = args[1];
        Region region = plugin.getRegionManager().getRegion(id);
        if (region == null) {
            sender.sendMessage(ChatColor.RED + "Région introuvable.");
            return true;
        }
        String schemName = region.getBoundSchematic();
        if (schemName == null || schemName.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Cette région n'est associée à aucun schéma. Associez-en un avec /rg bindschem.");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Réinitialisation de la région \"" + id + "\" en cours...");

        plugin.getDatabaseManager().loadSchematic(schemName).thenAccept(dbBytes -> {
            byte[] bytes = dbBytes;
            if (bytes == null) {
                File file = new File(new File(plugin.getDataFolder(), "schematics"), schemName + ".wxschem");
                if (file.exists()) {
                    try {
                        bytes = Files.readAllBytes(file.toPath());
                    } catch (IOException ignored) {}
                }
            }

            if (bytes == null) {
                sender.sendMessage(ChatColor.RED + "Impossible de charger le schéma associé.");
                return;
            }

            try {
                fr.skynex.worldx.edit.Clipboard clipboard = fr.skynex.worldx.edit.SchematicEngine.deserialize(bytes);
                List<BlockEditQueue.BlockChangeInfo> changes = new java.util.ArrayList<>();
                
                int minX = region.getMinX();
                int minY = region.getMinY();
                int minZ = region.getMinZ();
                int maxX = region.getMaxX();
                int maxY = region.getMaxY();
                int maxZ = region.getMaxZ();
                
                if (region.getShapeType() == fr.skynex.worldx.region.ShapeType.SPHERE) {
                    minX = region.getMinX() - region.getMaxX();
                    minY = region.getMinY() - region.getMaxX();
                    minZ = region.getMinZ() - region.getMaxX();
                    maxX = region.getMinX() + region.getMaxX();
                    maxY = region.getMinY() + region.getMaxX();
                    maxZ = region.getMinZ() + region.getMaxX();
                } else if (region.getShapeType() == fr.skynex.worldx.region.ShapeType.CYLINDER) {
                    minX = region.getMinX() - region.getMaxX();
                    minZ = region.getMinZ() - region.getMaxX();
                    maxX = region.getMinX() + region.getMaxX();
                    maxZ = region.getMinZ() + region.getMaxX();
                }

                org.bukkit.World world = Bukkit.getWorld(region.getWorldName());
                if (world == null) return;

                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            if (region.contains(x, y, z)) {
                                changes.add(new BlockEditQueue.BlockChangeInfo(x, y, z, org.bukkit.Material.AIR.createBlockData()));
                            }
                        }
                    }
                }

                for (fr.skynex.worldx.edit.Clipboard.ClipboardBlock block : clipboard.getBlocks()) {
                    int targetX = minX + block.getRelX();
                    int targetY = minY + block.getRelY();
                    int targetZ = minZ + block.getRelZ();
                    
                    if (region.contains(targetX, targetY, targetZ)) {
                        changes.add(new BlockEditQueue.BlockChangeInfo(targetX, targetY, targetZ, block.getBlockData()));
                    }
                }

                if (!changes.isEmpty()) {
                    UUID consoleUUID = UUID.nameUUIDFromBytes("WORLDX-CONSOLE".getBytes());
                    plugin.getBlockEditQueue().queueTask(new BlockEditQueue.EditTask(
                            consoleUUID, region.getWorldName(), changes
                    ));
                    sender.sendMessage(ChatColor.GREEN + "Région réinitialisée avec succès !");
                }
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Erreur de réinitialisation : " + e.getMessage());
            }
        });
        return true;
    }

    private long calculateVolume(Region region) {
        long w = region.getMaxX() - region.getMinX() + 1;
        long h = region.getMaxY() - region.getMinY() + 1;
        long d = region.getMaxZ() - region.getMinZ() + 1;
        if (region.getShapeType() == fr.skynex.worldx.region.ShapeType.SPHERE) {
            long r = region.getMaxX();
            return (long) ((4.0 / 3.0) * Math.PI * r * r * r);
        } else if (region.getShapeType() == fr.skynex.worldx.region.ShapeType.CYLINDER) {
            long r = region.getMaxX();
            return (long) (Math.PI * r * r * h);
        }
        return w * h * d;
    }

    private boolean handleClaim(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Seuls les joueurs peuvent claim des régions.");
            return true;
        }

        if (!player.hasPermission("worldx.region.claim")) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission de claim des régions.");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /rg claim <id>");
            return true;
        }

        String id = args[1];
        if (plugin.getRegionManager().getRegion(id) != null) {
            player.sendMessage(ChatColor.RED + "Une région avec l'ID \"" + id + "\" existe déjà.");
            return true;
        }

        Session session = plugin.getSessionManager().getSession(player);
        if (!session.hasCompleteSelection()) {
            player.sendMessage(ChatColor.RED + "Veuillez d'abord faire une sélection (//wand).");
            return true;
        }

        Location p1 = session.getPos1();
        Location p2 = session.getPos2();
        if (p1 == null || p2 == null) {
            player.sendMessage(ChatColor.RED + "Sélection invalide.");
            return true;
        }

        fr.skynex.worldx.region.ShapeType shapeType = session.getSelectionType();
        Region newRegion;

        if (shapeType == fr.skynex.worldx.region.ShapeType.SPHERE) {
            int radius = Math.max(1, (int) p1.distance(p2));
            newRegion = new Region(id, p1.getWorld().getName(), p1.getBlockX(), p1.getBlockY(), p1.getBlockZ(), radius);
        } else if (shapeType == fr.skynex.worldx.region.ShapeType.CYLINDER) {
            int radius = Math.max(1, (int) Math.sqrt(Math.pow(p1.getBlockX() - p2.getBlockX(), 2) + Math.pow(p1.getBlockZ() - p2.getBlockZ(), 2)));
            int minY = Math.min(p1.getBlockY(), p2.getBlockY());
            int maxY = Math.max(p1.getBlockY(), p2.getBlockY());
            newRegion = new Region(id, p1.getWorld().getName(), p1.getBlockX(), minY, p1.getBlockZ(), radius, maxY, 0, fr.skynex.worldx.region.ShapeType.CYLINDER);
        } else if (shapeType == fr.skynex.worldx.region.ShapeType.POLYGON) {
            List<Location> points = session.getSelectionPoints();
            if (points.size() < 3) {
                player.sendMessage(ChatColor.RED + "Veuillez sélectionner au moins 3 points pour un polygone.");
                return true;
            }
            int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            List<int[]> polyPoints = new ArrayList<>();
            for (Location loc : points) {
                int px = loc.getBlockX(), py = loc.getBlockY(), pz = loc.getBlockZ();
                minX = Math.min(minX, px); minZ = Math.min(minZ, pz);
                maxX = Math.max(maxX, px); maxZ = Math.max(maxZ, pz);
                minY = Math.min(minY, py); maxY = Math.max(maxY, py);
                polyPoints.add(new int[]{px, pz});
            }
            newRegion = new Region(id, points.get(0).getWorld().getName(), minX, minY, minZ, maxX, maxY, maxZ, fr.skynex.worldx.region.ShapeType.POLYGON);
            newRegion.setPolyPoints(polyPoints);
        } else {
            newRegion = new Region(id, p1.getWorld().getName(), p1.getBlockX(), p1.getBlockY(), p1.getBlockZ(), p2.getBlockX(), p2.getBlockY(), p2.getBlockZ());
        }

        long newVolume = calculateVolume(newRegion);

        // Budget check
        int budget = plugin.getConfig().getInt("claim.default-budget", 10000);
        for (org.bukkit.permissions.PermissionAttachmentInfo attachment : player.getEffectivePermissions()) {
            String perm = attachment.getPermission();
            if (perm.startsWith("worldx.claim.budget.")) {
                try {
                    budget = Math.max(budget, Integer.parseInt(perm.replace("worldx.claim.budget.", "")));
                } catch (NumberFormatException ignored) {}
            }
        }

        long alreadyClaimed = 0;
        for (Region r : plugin.getRegionManager().getRegions().values()) {
            if (r.getOwners().contains(player.getUniqueId())) {
                alreadyClaimed += calculateVolume(r);
            }
        }

        if (alreadyClaimed + newVolume > budget) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas assez de budget de blocs disponible !");
            player.sendMessage(ChatColor.RED + "Volume sélectionné : " + newVolume + " blocs.");
            player.sendMessage(ChatColor.RED + "Budget total : " + budget + " blocs (Utilisé: " + alreadyClaimed + " blocs, Restant: " + (budget - alreadyClaimed) + " blocs).");
            return true;
        }

        // Intersect check with other regions
        for (Region r : plugin.getRegionManager().getRegions().values()) {
            if (r.getWorldName().equalsIgnoreCase(newRegion.getWorldName())) {
                // If they overlap on bounding box
                boolean intersect = !(r.getMinX() > newRegion.getMaxX() || r.getMaxX() < newRegion.getMinX() ||
                                      r.getMinY() > newRegion.getMaxY() || r.getMaxY() < newRegion.getMinY() ||
                                      r.getMinZ() > newRegion.getMaxZ() || r.getMaxZ() < newRegion.getMinZ());
                if (intersect) {
                    if (!r.getOwners().contains(player.getUniqueId())) {
                        player.sendMessage(ChatColor.RED + "Votre claim chevauche une région existante dont vous n'êtes pas propriétaire (\"" + r.getId() + "\").");
                        return true;
                    }
                }
            }
        }

        newRegion.addOwner(player.getUniqueId());
        Region cloned = RegionAction.cloneRegion(newRegion);
        session.addRegionUndo(new RegionAction(RegionAction.Type.CREATE, null, cloned));
        plugin.getRegionManager().addRegion(newRegion);

        player.sendMessage(ChatColor.GREEN + "Région \"" + id + "\" réclamée avec succès ! (Volume: " + newVolume + " blocs).");
        player.sendMessage(ChatColor.GREEN + "Budget restant: " + (budget - alreadyClaimed - newVolume) + " / " + budget + " blocs.");

        // Discord Log
        fr.skynex.worldx.integration.DiscordWebhookLogger.logRegionAction(
                plugin, "CLAIM", player.getName(), id,
                "Forme: " + shapeType.name() + ", Volume: " + newVolume + " blocs, Monde: " + newRegion.getWorldName()
        );

        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission("worldx.region.admin")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission de débugger les régions.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rg debug <joueur> <flag>");
            return true;
        }

        String targetPlayerName = args[1];
        Player targetPlayer = Bukkit.getPlayer(targetPlayerName);
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            sender.sendMessage(ChatColor.RED + "Joueur \"" + targetPlayerName + "\" introuvable ou hors-ligne.");
            return true;
        }

        String flagName = args[2].toLowerCase();

        sender.sendMessage(ChatColor.GOLD + "=== Diagnostic WorldX : " + targetPlayer.getName() + " -> " + flagName.toUpperCase() + " ===");
        List<String> trace = plugin.getRegionManager().tracePermission(targetPlayer, targetPlayer.getLocation(), flagName);
        for (String line : trace) {
            sender.sendMessage(line);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("define", "delete", "info", "flag", "addowner", "addmember", "removeowner", "removemember", "priority", "parent", "show", "gui", "rollback", "bindschem", "reset", "claim", "debug", "undo", "redo", "save", "profile", "select", "tp", "teleport", "list", "rent", "rentable", "near", "rename", "preset", "apply-preset", "script", "siege")
                    .stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }

        String sub = args[0].toLowerCase();

        if (args.length == 2) {
            if (sub.equals("rent")) {
                return plugin.getRegionManager().getRegions().values().stream()
                        .filter(r -> "true".equalsIgnoreCase(r.getFlags().get("rentable")))
                        .map(r -> r.getId())
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (sub.equals("profile")) {
                return Arrays.asList("start", "stop").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
            if (sub.equals("define") || sub.equals("create")) {
                return Collections.emptyList();
            }
            if (sub.equals("debug")) {
                return Bukkit.getOnlinePlayers().stream().map((Player player) -> player.getName())
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
            // List region names
            return plugin.getRegionManager().getRegions().keySet().stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        }

        if (sub.equals("rollback")) {
            return new RollbackCommand(plugin).onTabComplete(sender, command, alias, args);
        }

        if (args.length == 3) {
            if (sub.equals("apply-preset") || sub.equals("preset")) {
                return plugin.getPresetsConfig().getKeys(false).stream()
                        .filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }
            if (sub.equals("show")) {
                return Arrays.asList("particles", "display").stream()
                        .filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }
            if (sub.equals("debug")) {
                List<String> knownFlags = Arrays.asList("build", "pvp", "use", "mob-spawn", "entry", "greeting", "farewell", "heal-delay", "feed-delay", "gamemode", "crop-trample", "item-frame-rotate", "hanging-destroy", "fall-damage", "hunger", "keep-inventory", "mob-target", "command-blacklist", "command-whitelist", "teleport-on-entry", "damage-on-entry", "interact-cooldown", "intrusion-alert", "fire-spread", "explosion", "leaf-decay", "god-mode", "respawn-location", "projectile", "money-multiplier", "money-loot-on-kill", "liquid-flow", "piston-interact", "fire-ignite", "auto-replant", "villager-trade", "armor-stand-interact", "exit", "gravity-modifier", "particles-ambient", "auto-rollback-interval", "temporary-blocks", "schematic-regen-on-entry", "max-players", "mob-damage-multiplier", "deny-elytra-boost", "absorption-shield", "deny-ender-chest", "blocked-crafting", "regional-chat", "announcement-interval", "void-teleport", "region-chat-format", "no-potion-drink", "fog-color", "keep-effects-on-death", "prevent-teleport", "intrusion-command");
                return knownFlags.stream()
                        .filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }
            if (sub.equals("flag")) {
                List<String> knownFlags = Arrays.asList("build", "pvp", "use", "mob-spawn", "entry", "greeting", "farewell", "heal-delay", "feed-delay", "gamemode", "crop-trample", "item-frame-rotate", "hanging-destroy", "fall-damage", "hunger", "keep-inventory", "mob-target", "command-blacklist", "command-whitelist", "teleport-on-entry", "damage-on-entry", "interact-cooldown", "intrusion-alert", "fire-spread", "explosion", "leaf-decay", "god-mode", "respawn-location", "projectile", "money-multiplier", "money-loot-on-kill", "liquid-flow", "piston-interact", "fire-ignite", "auto-replant", "villager-trade", "armor-stand-interact", "exit", "gravity-modifier", "particles-ambient", "auto-rollback-interval", "temporary-blocks", "schematic-regen-on-entry", "max-players", "mob-damage-multiplier", "deny-elytra-boost", "absorption-shield", "deny-ender-chest", "blocked-crafting", "regional-chat", "announcement-interval", "void-teleport", "region-chat-format", "no-potion-drink", "fog-color", "keep-effects-on-death", "prevent-teleport", "intrusion-command");
                return knownFlags.stream()
                        .filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }
            if (sub.equals("parent")) {
                // List region names (excluding own region name)
                String ownId = args[1].toLowerCase();
                return plugin.getRegionManager().getRegions().keySet().stream()
                        .filter(s -> !s.equals(ownId) && s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }
            if (sub.equals("define") || sub.equals("create")) {
                return Arrays.asList("cuboid", "sphere", "cylinder", "polygon").stream()
                        .filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }
            if (sub.equals("bindschem")) {
                File schematicsDir = new File(plugin.getDataFolder(), "schematics");
                if (schematicsDir.exists()) {
                    File[] files = schematicsDir.listFiles((dir, name) -> name.endsWith(".wxschem"));
                    if (files != null) {
                        return Arrays.stream(files)
                                .map(f -> f.getName().replace(".wxschem", ""))
                                .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                }
            }
            if (sub.endsWith("member") || sub.endsWith("owner")) {
                // Online players list
                return Bukkit.getOnlinePlayers().stream().map((Player player) -> player.getName())
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }
        }

        if (args.length == 4) {
            if (sub.equals("flag")) {
                String flagName = args[2].toLowerCase();
                if (Arrays.asList("build", "pvp", "use", "mob-spawn", "entry", "god-mode", "projectile", "liquid-flow", "piston-interact", "fire-ignite", "auto-replant", "villager-trade", "armor-stand-interact", "exit", "temporary-blocks", "schematic-regen-on-entry", "deny-elytra-boost", "deny-ender-chest", "regional-chat").contains(flagName)) {
                    return Arrays.asList("allow", "deny", "none").stream()
                            .filter(s -> s.startsWith(args[3].toLowerCase())).collect(Collectors.toList());
                }
                if (flagName.equals("gamemode")) {
                    return Arrays.asList("survival", "creative", "adventure", "spectator", "none").stream()
                            .filter(s -> s.startsWith(args[3].toLowerCase())).collect(Collectors.toList());
                }
                if (flagName.equals("intrusion-alert")) {
                    return Arrays.asList("owner", "sound", "discord", "none").stream()
                            .filter(s -> s.startsWith(args[3].toLowerCase())).collect(Collectors.toList());
                }
                return Arrays.asList("none").stream()
                        .filter(s -> s.startsWith(args[3].toLowerCase())).collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }

    private boolean handleUndo(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Seuls les joueurs peuvent utiliser le undo des régions.");
            return true;
        }

        if (!player.hasPermission("worldx.region.admin")) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
            return true;
        }

        Session session = plugin.getSessionManager().getSession(player);
        RegionAction action = session.popRegionUndo();
        if (action == null) {
            player.sendMessage(ChatColor.RED + "Aucune action de région à annuler.");
            return true;
        }

        applyRegionInverse(action);
        session.addRegionRedo(action);

        player.sendMessage(ChatColor.GREEN + "Annulation de l'action de région : " + ChatColor.LIGHT_PURPLE + action.getType().name() + ChatColor.GREEN + " effectuée.");
        return true;
    }

    private boolean handleRedo(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Seuls les joueurs peuvent utiliser le redo des régions.");
            return true;
        }

        if (!player.hasPermission("worldx.region.admin")) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
            return true;
        }

        Session session = plugin.getSessionManager().getSession(player);
        RegionAction action = session.popRegionRedo();
        if (action == null) {
            player.sendMessage(ChatColor.RED + "Rien à rétablir.");
            return true;
        }

        applyRegionDirect(action);
        session.addRegionUndo(action);

        player.sendMessage(ChatColor.GREEN + "Rétablissement de l'action de région : " + ChatColor.LIGHT_PURPLE + action.getType().name() + ChatColor.GREEN + " effectuée.");
        return true;
    }

    private void applyRegionInverse(RegionAction action) {
        switch (action.getType()) {
            case CREATE:
                plugin.getRegionManager().removeRegion(action.getNewState().getId());
                break;
            case DELETE:
                plugin.getRegionManager().addRegion(action.getOldState());
                break;
            case UPDATE:
                plugin.getRegionManager().addRegion(action.getOldState());
                break;
        }
    }

    private void applyRegionDirect(RegionAction action) {
        switch (action.getType()) {
            case CREATE:
                plugin.getRegionManager().addRegion(action.getNewState());
                break;
            case DELETE:
                plugin.getRegionManager().removeRegion(action.getOldState().getId());
                break;
            case UPDATE:
                plugin.getRegionManager().addRegion(action.getNewState());
                break;
        }
    }

    private void recordRegionUpdate(CommandSender sender, Region oldState, Region newState) {
        if (sender instanceof Player player) {
            Session session = plugin.getSessionManager().getSession(player);
            session.addRegionUndo(new RegionAction(RegionAction.Type.UPDATE, oldState, RegionAction.cloneRegion(newState)));
        }
    }

    private boolean handleSaveRegion(CommandSender sender, String[] args) {
        if (!sender.hasPermission("worldx.region.admin")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission de sauvegarder une région.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rg save <id> [nom_schéma]");
            return true;
        }

        String id = args[1];
        Region region = plugin.getRegionManager().getRegion(id);
        if (region == null) {
            sender.sendMessage(ChatColor.RED + "Région \"" + id + "\" introuvable.");
            return true;
        }

        String schemName = (args.length >= 3) ? args[2].replaceAll("[^a-zA-Z0-9_-]", "") : id;

        org.bukkit.World world = Bukkit.getWorld(region.getWorldName());
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "Le monde \"" + region.getWorldName() + "\" de la région est introuvable ou déchargé.");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Sérialisation des blocs de la région...");

        int minX = region.getMinX();
        int minY = region.getMinY();
        int minZ = region.getMinZ();
        int maxX = region.getMaxX();
        int maxY = region.getMaxY();
        int maxZ = region.getMaxZ();
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int length = maxZ - minZ + 1;

        List<fr.skynex.worldx.edit.Clipboard.ClipboardBlock> blocks = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (region.contains(x, y, z)) {
                        blocks.add(new fr.skynex.worldx.edit.Clipboard.ClipboardBlock(x - minX, y - minY, z - minZ, world.getBlockAt(x, y, z).getBlockData()));
                    }
                }
            }
        }

        fr.skynex.worldx.edit.Clipboard clipboard = new fr.skynex.worldx.edit.Clipboard(blocks, width, height, length);

        fr.skynex.worldx.scheduler.FoliaScheduler.runAsync(plugin, () -> {
            try {
                byte[] bytes = fr.skynex.worldx.edit.SchematicEngine.serialize(clipboard);

                File schematicsDir = new File(plugin.getDataFolder(), "schematics");
                if (!schematicsDir.exists()) schematicsDir.mkdirs();
                File file = new File(schematicsDir, schemName + ".wxschem");
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                    fos.write(bytes);
                }

                plugin.getDatabaseManager().saveSchematic(schemName, bytes);
                sender.sendMessage(ChatColor.GREEN + "Région \"" + id + "\" sauvegardée en tant que schéma \"" + schemName + "\" !");
            } catch (IOException e) {
                sender.sendMessage(ChatColor.RED + "Erreur de sauvegarde : " + e.getMessage());
            }
        });

        return true;
    }

    private boolean handleProfile(CommandSender sender, String[] args) {
        if (!sender.hasPermission("worldx.region.admin")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission de profiler les régions.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rg profile <start|stop>");
            return true;
        }

        String sub = args[1].toLowerCase();
        if (sub.equals("start")) {
            plugin.getRegionProfiler().start();
            sender.sendMessage(ChatColor.GREEN + "[WorldX Profiler] Enregistrement des performances démarré.");
        } else if (sub.equals("stop")) {
            if (!plugin.getRegionProfiler().isActive()) {
                sender.sendMessage(ChatColor.RED + "[WorldX Profiler] Le profileur n'est pas démarré.");
                return true;
            }

            plugin.getRegionProfiler().stop();
            sender.sendMessage(ChatColor.GREEN + "[WorldX Profiler] Enregistrement arrêté. Résultats :");

            java.util.Map<String, fr.skynex.worldx.region.RegionProfiler.ProfilerStats> statsMap = plugin.getRegionProfiler().getStats();
            if (statsMap.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "Aucune donnée enregistrée pendant cette session.");
                return true;
            }

            sender.sendMessage(ChatColor.GOLD + "--------------------------------------------------");
            sender.sendMessage(String.format(ChatColor.YELLOW + "%-30s | %-6s | %-10s | %-8s", "Opération", "Appels", "Total (ms)", "Moy (ms)"));
            sender.sendMessage(ChatColor.GOLD + "--------------------------------------------------");

            statsMap.entrySet().stream()
                    .sorted((e1, e2) -> Long.compare(e2.getValue().totalNanos.sum(), e1.getValue().totalNanos.sum()))
                    .forEach(entry -> {
                        long totalNanos = entry.getValue().totalNanos.sum();
                        long count = entry.getValue().count.sum();
                        double totalMs = totalNanos / 1_000_000.0;
                        double avgMs = count > 0 ? (totalMs / count) : 0.0;

                        String label = entry.getKey();
                        if (label.length() > 30) {
                            label = label.substring(0, 27) + "...";
                        }

                        sender.sendMessage(String.format(
                                ChatColor.WHITE + "%-30s | " + ChatColor.GREEN + "%-6d" + ChatColor.WHITE + " | " + ChatColor.LIGHT_PURPLE + "%-10.3f" + ChatColor.WHITE + " | " + ChatColor.AQUA + "%-8.4f",
                                label, count, totalMs, avgMs
                        ));
                    });
            sender.sendMessage(ChatColor.GOLD + "--------------------------------------------------");
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /rg profile <start|stop>");
        }

        return true;
    }

    private boolean handleSelectRegion(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Seuls les joueurs peuvent utiliser cette commande.");
            return true;
        }

        if (!player.hasPermission("worldx.region.admin")) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /rg select <id>");
            return true;
        }

        String id = args[1];
        Region region = plugin.getRegionManager().getRegion(id);
        if (region == null) {
            player.sendMessage(ChatColor.RED + "Région \"" + id + "\" introuvable.");
            return true;
        }

        org.bukkit.World world = Bukkit.getWorld(region.getWorldName());
        if (world == null) {
            player.sendMessage(ChatColor.RED + "Le monde de la région est introuvable ou non chargé.");
            return true;
        }

        Session session = plugin.getSessionManager().getSession(player.getUniqueId());
        Location p1 = new Location(world, region.getMinX(), region.getMinY(), region.getMinZ());
        Location p2 = new Location(world, region.getMaxX(), region.getMaxY(), region.getMaxZ());
        
        session.setPos1(p1);
        session.setPos2(p2);

        player.sendMessage(ChatColor.GREEN + "Sélection mise à jour aux limites de la région \"" + id + "\" (" + 
                region.getMinX() + "," + region.getMinY() + "," + region.getMinZ() + " à " + 
                region.getMaxX() + "," + region.getMaxY() + "," + region.getMaxZ() + ").");
        return true;
    }

    private boolean handleTpRegion(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Seuls les joueurs peuvent utiliser cette commande.");
            return true;
        }

        if (!player.hasPermission("worldx.region.admin")) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /rg tp <id>");
            return true;
        }

        String id = args[1];
        Region region = plugin.getRegionManager().getRegion(id);
        if (region == null) {
            player.sendMessage(ChatColor.RED + "Région \"" + id + "\" introuvable.");
            return true;
        }

        org.bukkit.World world = Bukkit.getWorld(region.getWorldName());
        if (world == null) {
            player.sendMessage(ChatColor.RED + "Le monde de la région est introuvable ou non chargé.");
            return true;
        }

        double cx = region.getMinX() + (region.getMaxX() - region.getMinX()) / 2.0;
        double cz = region.getMinZ() + (region.getMaxZ() - region.getMinZ()) / 2.0;
        double cy = region.getMinY() + 1.0;

        Location tpLoc = new Location(world, cx, cy, cz);
        player.teleport(tpLoc);
        player.sendMessage(ChatColor.GREEN + "Téléporté au centre de la région \"" + id + "\".");
        return true;
    }

    private boolean handleListRegions(CommandSender sender, String[] args) {
        if (!sender.hasPermission("worldx.region.admin")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
            return true;
        }

        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {}
        }

        List<Region> sortedRegions = new ArrayList<>(plugin.getRegionManager().getRegions().values());
        sortedRegions.sort(java.util.Comparator.comparing((Region region) -> region.getId()));

        if (sortedRegions.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Aucune région enregistrée.");
            return true;
        }

        int itemsPerPage = 10;
        int maxPages = (int) Math.ceil((double) sortedRegions.size() / itemsPerPage);
        if (page < 1) page = 1;
        if (page > maxPages) page = maxPages;

        sender.sendMessage(ChatColor.GOLD + "=== Liste des Régions (Page " + page + "/" + maxPages + ") ===");
        int startIdx = (page - 1) * itemsPerPage;
        int endIdx = Math.min(startIdx + itemsPerPage, sortedRegions.size());

        for (int i = startIdx; i < endIdx; i++) {
            Region r = sortedRegions.get(i);
            sender.sendMessage(ChatColor.YELLOW + "- " + ChatColor.GREEN + r.getId() + 
                    ChatColor.GRAY + " (Monde: " + r.getWorldName() + ", Priorité: " + r.getPriority() + ", Forme: " + r.getShapeType().name() + ")");
        }
        sender.sendMessage(ChatColor.GOLD + "--------------------------------------------------");
        return true;
    }

    private boolean handleRentable(CommandSender sender, String[] args) {
        if (!sender.hasPermission("worldx.region.admin")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /rg rentable <id> <prix> <durée: 7d/30d> [inactivité: 14d]");
            return true;
        }

        String id = args[1];
        Region region = plugin.getRegionManager().getRegion(id);
        if (region == null) {
            sender.sendMessage(ChatColor.RED + "Région \"" + id + "\" introuvable.");
            return true;
        }

        double price;
        try {
            price = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Le prix doit être un nombre décimal.");
            return true;
        }

        String durationStr = args[3];
        long durationMs = parseDurationMs(durationStr);
        if (durationMs <= 0) {
            sender.sendMessage(ChatColor.RED + "Durée invalide (ex: 7d, 30d).");
            return true;
        }

        region.getFlags().put("rentable", "true");
        region.getFlags().put("rent-price", String.valueOf(price));
        region.getFlags().put("rent-duration", durationStr);
        region.getFlags().remove("rent-expiry");

        if (args.length >= 5) {
            String inactivityStr = args[4];
            long inactivityMs = parseDurationMs(inactivityStr);
            if (inactivityMs > 0) {
                region.getFlags().put("inactivity-expiry", inactivityStr);
            }
        }

        plugin.getRegionManager().addRegion(region); // Save & sync
        sender.sendMessage(ChatColor.GREEN + "La région \"" + id + "\" est désormais disponible à la location pour " + price + " (durée: " + durationStr + ").");
        return true;
    }

    private boolean handleRent(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Seuls les joueurs peuvent louer des régions.");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /rg rent <id>");
            return true;
        }

        String id = args[1];
        Region region = plugin.getRegionManager().getRegion(id);
        if (region == null) {
            player.sendMessage(ChatColor.RED + "Région \"" + id + "\" introuvable.");
            return true;
        }

        String rentable = region.getFlags().get("rentable");
        String expiryStr = region.getFlags().get("rent-expiry");
        boolean isRentable = "true".equalsIgnoreCase(rentable);
        boolean isOwner = region.isOwner(player.getUniqueId());

        if (!isRentable && !isOwner) {
            player.sendMessage(ChatColor.RED + "Cette région n'est pas disponible à la location.");
            return true;
        }

        double price = 0;
        try {
            price = Double.parseDouble(region.getFlags().getOrDefault("rent-price", "0"));
        } catch (NumberFormatException ignored) {}

        String durationStr = region.getFlags().getOrDefault("rent-duration", "7d");
        long durationMs = parseDurationMs(durationStr);

        // Vault check
        if (price > 0) {
            if (fr.skynex.worldx.integration.EconomyIntegration.setupEconomy()) {
                double balance = fr.skynex.worldx.integration.EconomyIntegration.getBalance(player);
                if (balance < price) {
                    player.sendMessage(ChatColor.RED + "Vous n'avez pas assez d'argent ! Requis: " + price + " (Solde: " + balance + ").");
                    return true;
                }
                if (!fr.skynex.worldx.integration.EconomyIntegration.withdrawPlayer(player, price)) {
                    player.sendMessage(ChatColor.RED + "Transaction échouée.");
                    return true;
                }
            } else {
                player.sendMessage(ChatColor.YELLOW + "[WorldX] Vault non détecté. Location gratuite accordée (simulation).");
            }
        }

        long now = System.currentTimeMillis();
        long currentExpiry = 0;
        if (expiryStr != null && !expiryStr.isEmpty() && !expiryStr.equalsIgnoreCase("expired")) {
            try {
                currentExpiry = Long.parseLong(expiryStr);
            } catch (NumberFormatException ignored) {}
        }

        long newExpiry = (currentExpiry > now ? currentExpiry : now) + durationMs;
        region.getFlags().put("rent-expiry", String.valueOf(newExpiry));
        region.getFlags().put("rentable", "false");
        region.getFlags().put("last-active", String.valueOf(now));

        // Add player as owner if not already
        if (!region.isOwner(player.getUniqueId())) {
            region.clearOwners(); // Reset previous owners on new rent
            region.addOwner(player.getUniqueId());
            region.clearMembers();
        }

        plugin.getRegionManager().addRegion(region); // Save & sync

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm");
        player.sendMessage(ChatColor.GREEN + "Félicitations ! Vous louez désormais la région \"" + id + "\" jusqu'au " + sdf.format(new java.util.Date(newExpiry)) + ".");
        return true;
    }

    private long parseDurationMs(String spec) {
        spec = spec.toLowerCase();
        try {
            if (spec.endsWith("m")) {
                return Long.parseLong(spec.replace("m", "")) * 60 * 1000L;
            }
            if (spec.endsWith("h")) {
                return Long.parseLong(spec.replace("h", "")) * 60 * 60 * 1000L;
            }
            if (spec.endsWith("d")) {
                return Long.parseLong(spec.replace("d", "")) * 24 * 60 * 60 * 1000L;
            }
        } catch (NumberFormatException ignored) {}
        return 0L;
    }

    private boolean handleNearRegions(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Seuls les joueurs peuvent utiliser cette commande.");
            return true;
        }

        if (!player.hasPermission("worldx.region.admin")) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
            return true;
        }

        int radius = 50;
        if (args.length >= 2) {
            try {
                radius = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {}
        }

        Location playerLoc = player.getLocation();
        String worldName = player.getWorld().getName();

        List<Region> nearby = new ArrayList<>();
        for (Region r : plugin.getRegionManager().getRegions().values()) {
            if (!r.getWorldName().equals(worldName)) continue;
            // Approximate center for any shape
            double cx, cy, cz;
            if (r.getShapeType() == fr.skynex.worldx.region.ShapeType.SPHERE ||
                r.getShapeType() == fr.skynex.worldx.region.ShapeType.CYLINDER) {
                cx = r.getMinX();
                cy = r.getMinY();
                cz = r.getMinZ();
            } else {
                cx = r.getMinX() + (r.getMaxX() - r.getMinX()) / 2.0;
                cy = r.getMinY() + (r.getMaxY() - r.getMinY()) / 2.0;
                cz = r.getMinZ() + (r.getMaxZ() - r.getMinZ()) / 2.0;
            }
            double dist = playerLoc.distance(new Location(player.getWorld(), cx, cy, cz));
            if (dist <= radius) {
                nearby.add(r);
            }
        }

        if (nearby.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "Aucune région trouvée dans un rayon de " + radius + " blocs.");
            return true;
        }

        // Sort by distance
        nearby.sort((r1, r2) -> {
            double cx1 = r1.getShapeType() == fr.skynex.worldx.region.ShapeType.SPHERE ? r1.getMinX() : r1.getMinX() + (r1.getMaxX() - r1.getMinX()) / 2.0;
            double cz1 = r1.getShapeType() == fr.skynex.worldx.region.ShapeType.SPHERE ? r1.getMinZ() : r1.getMinZ() + (r1.getMaxZ() - r1.getMinZ()) / 2.0;
            double cx2 = r2.getShapeType() == fr.skynex.worldx.region.ShapeType.SPHERE ? r2.getMinX() : r2.getMinX() + (r2.getMaxX() - r2.getMinX()) / 2.0;
            double cz2 = r2.getShapeType() == fr.skynex.worldx.region.ShapeType.SPHERE ? r2.getMinZ() : r2.getMinZ() + (r2.getMaxZ() - r2.getMinZ()) / 2.0;
            double d1 = playerLoc.distanceSquared(new Location(player.getWorld(), cx1, playerLoc.getY(), cz1));
            double d2 = playerLoc.distanceSquared(new Location(player.getWorld(), cx2, playerLoc.getY(), cz2));
            return Double.compare(d1, d2);
        });

        player.sendMessage(ChatColor.GOLD + "=== Régions proches (rayon " + radius + " blocs) ===");
        for (Region r : nearby) {
            double cx = r.getShapeType() == fr.skynex.worldx.region.ShapeType.SPHERE ? r.getMinX() : r.getMinX() + (r.getMaxX() - r.getMinX()) / 2.0;
            double cy = r.getShapeType() == fr.skynex.worldx.region.ShapeType.SPHERE ? r.getMinY() : r.getMinY() + (r.getMaxY() - r.getMinY()) / 2.0;
            double cz = r.getShapeType() == fr.skynex.worldx.region.ShapeType.SPHERE ? r.getMinZ() : r.getMinZ() + (r.getMaxZ() - r.getMinZ()) / 2.0;
            int dist = (int) playerLoc.distance(new Location(player.getWorld(), cx, cy, cz));
            player.sendMessage(ChatColor.YELLOW + "- " + ChatColor.GREEN + r.getId()
                    + ChatColor.GRAY + " (" + r.getShapeType().name() + ", Priorité: " + r.getPriority()
                    + ", Distance: ~" + dist + " blocs)");
        }
        return true;
    }

    private boolean handleRenameRegion(CommandSender sender, String[] args) {
        if (!sender.hasPermission("worldx.region.admin")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rg rename <ancien_id> <nouveau_id>");
            return true;
        }

        String oldId = args[1];
        String newId = args[2];

        if (oldId.equalsIgnoreCase(newId)) {
            sender.sendMessage(ChatColor.RED + "L'ancien et le nouveau ID sont identiques.");
            return true;
        }

        Region oldRegion = plugin.getRegionManager().getRegion(oldId);
        if (oldRegion == null) {
            sender.sendMessage(ChatColor.RED + "Région \"" + oldId + "\" introuvable.");
            return true;
        }

        if (plugin.getRegionManager().getRegion(newId) != null) {
            sender.sendMessage(ChatColor.RED + "Une région avec l'ID \"" + newId + "\" existe déjà.");
            return true;
        }

        // Clone with new ID by reconstructing via RegionAction helper logic
        Region cloned = RegionAction.cloneRegion(oldRegion);
        // Build a new Region with the new ID (same bounds/shape)
        Region newRegion;
        if (cloned.getShapeType() == fr.skynex.worldx.region.ShapeType.SPHERE) {
            newRegion = new Region(newId, cloned.getWorldName(), cloned.getMinX(), cloned.getMinY(), cloned.getMinZ(), cloned.getMaxX());
        } else {
            newRegion = new Region(newId, cloned.getWorldName(), cloned.getMinX(), cloned.getMinY(), cloned.getMinZ(), cloned.getMaxX(), cloned.getMaxY(), cloned.getMaxZ(), cloned.getShapeType());
        }
        newRegion.setPriority(cloned.getPriority());
        newRegion.setParentId(cloned.getParentId());
        cloned.getOwners().forEach(newRegion::addOwner);
        cloned.getMembers().forEach(newRegion::addMember);
        newRegion.getFlags().putAll(cloned.getFlags());
        newRegion.setPolyPoints(new ArrayList<>(cloned.getPolyPoints()));
        newRegion.setBoundSchematic(cloned.getBoundSchematic());

        // Remove old, add new
        plugin.getRegionManager().removeRegion(oldId);
        plugin.getRegionManager().addRegion(newRegion);

        // Update any child regions that referenced the old ID as parentId
        int updatedChildren = 0;
        for (Region r : plugin.getRegionManager().getRegions().values()) {
            if (oldId.equalsIgnoreCase(r.getParentId())) {
                r.setParentId(newId);
                plugin.getRegionManager().addRegion(r);
                updatedChildren++;
            }
        }

        sender.sendMessage(ChatColor.GREEN + "Région \"" + oldId + "\" renommée en \"" + newId + "\" avec succès.");
        if (updatedChildren > 0) {
            sender.sendMessage(ChatColor.YELLOW + String.valueOf(updatedChildren) + " région(s) enfant(s) mise(s) à jour.");
        }

        // Discord log
        fr.skynex.worldx.integration.DiscordWebhookLogger.logRegionAction(
                plugin, "RENOMMAGE", sender.getName(), oldId,
                "Nouveau ID: " + newId + ", Monde: " + newRegion.getWorldName()
        );

        return true;
    }

    private boolean handleSiegeCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("worldx.region.siege")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission de gérer les sièges.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rg siege <id> <start|end>");
            return true;
        }

        String id = args[1];
        String action = args[2].toLowerCase();

        Region region = plugin.getRegionManager().getRegion(id);
        if (region == null) {
            sender.sendMessage(ChatColor.RED + "Région introuvable.");
            return true;
        }

        String siegeModeVal = plugin.getRegionManager().getEffectiveFlagValue(region, "siege-mode");
        if (!"allow".equalsIgnoreCase(siegeModeVal)) {
            sender.sendMessage(ChatColor.RED + "Le mode siège n'est pas activé/autorisé sur cette région (flag siege-mode).");
            return true;
        }

        if (action.equals("start")) {
            String existingStart = region.getFlagValue("siege-start-time");
            if (existingStart != null && !existingStart.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "Le siège est déjà en cours dans cette région !");
                return true;
            }

            long now = System.currentTimeMillis();
            region.setFlagValue("siege-start-time", String.valueOf(now));
            plugin.getDatabaseManager().saveRegion(region);

            sender.sendMessage(ChatColor.GOLD + "=== LE SIÈGE DE LA RÉGION \"" + id.toUpperCase() + "\" A COMMENCÉ ===");
            sender.sendMessage(ChatColor.YELLOW + "Toutes les destructions de blocs seront enregistrées et restaurées à la fin du siège.");
            
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld().getName().equals(region.getWorldName())) {
                    player.showTitle(net.kyori.adventure.title.Title.title(
                            net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<gold><b>🚨 SIÈGE DÉBUTÉ 🚨</b></gold>"),
                            net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<yellow>Région: " + id + "</yellow>")
                    ));
                    player.playSound(player.getLocation(), org.bukkit.Sound.EVENT_RAID_HORN, 1.0f, 1.0f);
                }
            }

            fr.skynex.worldx.integration.DiscordWebhookLogger.logRegionAction(
                    plugin, "SIÈGE_DEBUT", sender.getName(), id,
                    "Le siège a commencé."
            );

        } else if (action.equals("end") || action.equals("stop")) {
            String startStr = region.getFlagValue("siege-start-time");
            if (startStr == null || startStr.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "Aucun siège n'est actuellement en cours dans cette région.");
                return true;
            }

            long startTime = Long.parseLong(startStr);
            region.setFlagValue("siege-start-time", null);
            plugin.getDatabaseManager().saveRegion(region);

            sender.sendMessage(ChatColor.GOLD + "=== LE SIÈGE DE LA RÉGION \"" + id.toUpperCase() + "\" EST TERMINÉ ===");
            sender.sendMessage(ChatColor.YELLOW + "Lancement de la restauration automatique des modifications de blocs...");

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld().getName().equals(region.getWorldName())) {
                    player.showTitle(net.kyori.adventure.title.Title.title(
                            net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<green><b>🛡️ SIÈGE TERMINÉ 🛡️</b></green>"),
                            net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<gray>Restauration de la zone en cours...</gray>")
                    ));
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                }
            }

            long ageMs = System.currentTimeMillis() - startTime;
            plugin.getDatabaseManager().getRollbackLogs(region.getId(), null, ageMs)
                    .thenAccept(logs -> {
                        if (logs.isEmpty()) {
                            sender.sendMessage(ChatColor.GREEN + "Restauration terminée : aucun bloc n'a été endommagé pendant le siège !");
                            return;
                        }

                        List<BlockEditQueue.BlockChangeInfo> changes = new ArrayList<>();
                        for (fr.skynex.worldx.database.RollbackEntry entry : logs) {
                            try {
                                changes.add(new BlockEditQueue.BlockChangeInfo(
                                         entry.getX(), entry.getY(), entry.getZ(),
                                         Bukkit.createBlockData(entry.getPreviousBlock())
                                ));
                            } catch (Exception ignored) {}
                        }

                        if (!changes.isEmpty()) {
                            fr.skynex.worldx.scheduler.FoliaScheduler.runSync(plugin, () -> {
                                plugin.getBlockEditQueue().queueTask(new BlockEditQueue.EditTask(
                                        new UUID(0L, 0L), region.getWorldName(), changes
                                ));
                                sender.sendMessage(ChatColor.GREEN + "Restauration en cours : " + changes.size() + " blocs sont en train d'être remis en place.");
                            });
                        }
                    });

            fr.skynex.worldx.integration.DiscordWebhookLogger.logRegionAction(
                    plugin, "SIÈGE_FIN", sender.getName(), id,
                    "Le siège est terminé. Restauration de la zone lancée."
            );
        } else {
            sender.sendMessage(ChatColor.RED + "Action invalide. Choisissez 'start' ou 'end'.");
        }

        return true;
    }
}

