package fr.skynex.worldx.command;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.edit.BlockEditQueue;
import fr.skynex.worldx.edit.Clipboard;
import fr.skynex.worldx.edit.EditOperation;
import fr.skynex.worldx.edit.Palette;
import fr.skynex.worldx.session.Session;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.BlockDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class EditCommand implements CommandExecutor {

    private final WorldX plugin;

    public EditCommand(WorldX plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Seuls les joueurs peuvent utiliser cette commande.", NamedTextColor.RED));
            return true;
        }

        String cmdName = command.getName().toLowerCase();

        switch (cmdName) {
            case "/wand":
                return handleWand(player);
            case "/set":
                return handleSet(player, args);
            case "/replace":
                return handleReplace(player, args);
            case "/cut":
                return handleCut(player);
            case "/copy":
                return handleCopy(player);
            case "/paste":
                return handlePaste(player, args);
            case "/undo":
                return handleUndo(player);
            case "/redo":
                return handleRedo(player);
            case "/expand":
                return handleExpand(player, args);
            case "/contract":
                return handleContract(player, args);
            case "/size":
                return handleSize(player);
            case "/rotate":
                return handleRotate(player, args);
            case "/flip":
                return handleFlip(player, args);
            case "/gmask":
                return handleGmask(player, args);
            case "/sphere":
                return handleSphere(player, args, false);
            case "/hsphere":
                return handleSphere(player, args, true);
            case "/line":
                return handleLine(player, args);
            default:
                return false;
        }
    }

    private boolean handleWand(Player player) {
        if (!player.hasPermission("worldx.wand")) {
            player.sendMessage(Component.text("Vous n'avez pas la permission d'utiliser cette commande.", NamedTextColor.RED));
            return true;
        }

        String wandMatName = plugin.getConfig().getString("edit.wand-item", "WOODEN_AXE");
        Material wandMat = Material.matchMaterial(wandMatName);
        if (wandMat == null) wandMat = Material.WOODEN_AXE;

        ItemStack wand = new ItemStack(wandMat);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Baguette de sélection WorldX", NamedTextColor.LIGHT_PURPLE));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Clic gauche : Définir Pos 1", NamedTextColor.GRAY));
            lore.add(Component.text("Clic droit : Définir Pos 2", NamedTextColor.GRAY));
            meta.lore(lore);
            wand.setItemMeta(meta);
        }

        player.getInventory().addItem(wand);
        player.sendMessage(Component.text("Vous avez reçu la baguette de sélection WorldX.", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleSet(Player player, String[] args) {
        if (!player.hasPermission("worldx.edit.set")) {
            player.sendMessage(Component.text("Vous n'avez pas la permission.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: //set <bloc>", NamedTextColor.RED));
            return true;
        }

        Session session = plugin.getSessionManager().getSession(player);
        if (!session.hasCompleteSelection()) {
            player.sendMessage(Component.text("Veuillez d'abord faire une sélection.", NamedTextColor.RED));
            return true;
        }

        Palette palette = null;
        BlockData blockData = null;

        if (args[0].startsWith("#")) {
            String palName = args[0].substring(1);
            try {
                palette = plugin.getDatabaseManager().loadPalette(palName).get();
            } catch (Exception e) {
                player.sendMessage(Component.text("Erreur de chargement du dégradé : " + e.getMessage(), NamedTextColor.RED));
                return true;
            }
            if (palette == null) {
                player.sendMessage(Component.text("Dégradé introuvable : " + palName, NamedTextColor.RED));
                return true;
            }
        } else {
            blockData = parseBlockData(args[0]);
            if (blockData == null) {
                player.sendMessage(Component.text("Bloc invalide : " + args[0], NamedTextColor.RED));
                return true;
            }
        }

        Location p1 = session.getPos1();
        Location p2 = session.getPos2();
        
        List<Location> points = session.getSelectionPoints();
        if (points.isEmpty()) {
            points = List.of(p1, p2);
        }

        int minX = 0, maxX = 0, minY = 0, maxY = 0, minZ = 0, maxZ = 0;
        boolean first = true;
        for (Location loc : points) {
            if (loc != null) {
                int x = loc.getBlockX();
                int y = loc.getBlockY();
                int z = loc.getBlockZ();
                if (first) {
                    minX = x;
                    maxX = x;
                    minY = y;
                    maxY = y;
                    minZ = z;
                    maxZ = z;
                    first = false;
                } else {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                    if (z < minZ) minZ = z;
                    if (z > maxZ) maxZ = z;
                }
            }
        }

        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        int limit = plugin.getConfig().getInt("edit.max-blocks-per-edit", 500000);
        if (limit > 0 && volume > limit) {
            player.sendMessage(Component.text("Sélection trop grande ! Limite : " + limit + " blocs (demandé: " + volume + ").", NamedTextColor.RED));
            return true;
        }

        player.sendMessage(Component.text("Modification en cours de " + volume + " blocs...", NamedTextColor.YELLOW));
        plugin.getBlockEditQueue().queueTask(new BlockEditQueue.EditTask(
                player.getUniqueId(),
                p1.getWorld().getName(),
                session.getSelectionType(),
                session.getSelectionPoints(),
                palette,
                blockData,
                null,
                false,
                minX, maxX, minY, maxY, minZ, maxZ,
                session.getActiveMask()
        ));

        return true;
    }

    private boolean handleReplace(Player player, String[] args) {
        if (!player.hasPermission("worldx.edit.replace")) {
            player.sendMessage(Component.text("Vous n'avez pas la permission.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: //replace [<bloc_source>] <bloc_dest>", NamedTextColor.RED));
            return true;
        }

        Session session = plugin.getSessionManager().getSession(player);
        if (!session.hasCompleteSelection()) {
            player.sendMessage(Component.text("Veuillez d'abord faire une sélection.", NamedTextColor.RED));
            return true;
        }

        BlockData fromBlock = null;
        BlockData toBlock = null;
        Palette palette = null;

        String toArg = (args.length == 1) ? args[0] : args[1];

        if (toArg.startsWith("#")) {
            String palName = toArg.substring(1);
            try {
                palette = plugin.getDatabaseManager().loadPalette(palName).get();
            } catch (Exception e) {
                player.sendMessage(Component.text("Erreur de chargement du dégradé : " + e.getMessage(), NamedTextColor.RED));
                return true;
            }
            if (palette == null) {
                player.sendMessage(Component.text("Dégradé introuvable : " + palName, NamedTextColor.RED));
                return true;
            }
        } else {
            toBlock = parseBlockData(toArg);
            if (toBlock == null) {
                player.sendMessage(Component.text("Bloc cible invalide : " + toArg, NamedTextColor.RED));
                return true;
            }
        }

        if (args.length > 1) {
            fromBlock = parseBlockData(args[0]);
            if (fromBlock == null) {
                player.sendMessage(Component.text("Bloc source invalide : " + args[0], NamedTextColor.RED));
                return true;
            }
        }

        Location p1 = session.getPos1();
        Location p2 = session.getPos2();
        
        List<Location> points = session.getSelectionPoints();
        if (points.isEmpty()) {
            points = List.of(p1, p2);
        }

        int minX = 0, maxX = 0, minY = 0, maxY = 0, minZ = 0, maxZ = 0;
        boolean first = true;
        for (Location loc : points) {
            if (loc != null) {
                int x = loc.getBlockX();
                int y = loc.getBlockY();
                int z = loc.getBlockZ();
                if (first) {
                    minX = x;
                    maxX = x;
                    minY = y;
                    maxY = y;
                    minZ = z;
                    maxZ = z;
                    first = false;
                } else {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                    if (z < minZ) minZ = z;
                    if (z > maxZ) maxZ = z;
                }
            }
        }

        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        int limit = plugin.getConfig().getInt("edit.max-blocks-per-edit", 500000);
        if (limit > 0 && volume > limit) {
            player.sendMessage(Component.text("Sélection trop grande ! Limite : " + limit + " blocs.", NamedTextColor.RED));
            return true;
        }

        player.sendMessage(Component.text("Remplacement en cours...", NamedTextColor.YELLOW));
        plugin.getBlockEditQueue().queueTask(new BlockEditQueue.EditTask(
                player.getUniqueId(),
                p1.getWorld().getName(),
                session.getSelectionType(),
                session.getSelectionPoints(),
                palette,
                toBlock,
                fromBlock,
                true,
                minX, maxX, minY, maxY, minZ, maxZ,
                session.getActiveMask()
        ));

        return true;
    }

    private boolean handleCut(Player player) {
        if (!player.hasPermission("worldx.edit.cut")) {
            player.sendMessage(Component.text("Vous n'avez pas la permission.", NamedTextColor.RED));
            return true;
        }

        Session session = plugin.getSessionManager().getSession(player);
        if (!session.hasCompleteSelection()) {
            player.sendMessage(Component.text("Veuillez d'abord faire une sélection.", NamedTextColor.RED));
            return true;
        }

        // Perform copy first
        performCopyInternal(player, session);

        // Perform set air
        Location p1 = session.getPos1();
        Location p2 = session.getPos2();
        List<BlockEditQueue.BlockChangeInfo> changes = new ArrayList<>();
        int minX = Math.min(p1.getBlockX(), p2.getBlockX());
        int maxX = Math.max(p1.getBlockX(), p2.getBlockX());
        int minY = Math.min(p1.getBlockY(), p2.getBlockY());
        int maxY = Math.max(p1.getBlockY(), p2.getBlockY());
        int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
        int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());

        BlockData air = Bukkit.createBlockData(Material.AIR);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    changes.add(new BlockEditQueue.BlockChangeInfo(x, y, z, air));
                }
            }
        }

        player.sendMessage(Component.text("Coupe en cours...", NamedTextColor.YELLOW));
        plugin.getBlockEditQueue().queueTask(new BlockEditQueue.EditTask(
                player.getUniqueId(), p1.getWorld().getName(), changes
        ));

        return true;
    }

    private boolean handleCopy(Player player) {
        if (!player.hasPermission("worldx.edit.copy")) {
            player.sendMessage(Component.text("Vous n'avez pas la permission.", NamedTextColor.RED));
            return true;
        }

        Session session = plugin.getSessionManager().getSession(player);
        if (!session.hasCompleteSelection()) {
            player.sendMessage(Component.text("Veuillez d'abord faire une sélection.", NamedTextColor.RED));
            return true;
        }

        performCopyInternal(player, session);
        return true;
    }

    private void performCopyInternal(Player player, Session session) {
        Location p1 = session.getPos1();
        Location p2 = session.getPos2();
        Location playerLoc = player.getLocation();

        int minX = Math.min(p1.getBlockX(), p2.getBlockX());
        int maxX = Math.max(p1.getBlockX(), p2.getBlockX());
        int minY = Math.min(p1.getBlockY(), p2.getBlockY());
        int maxY = Math.max(p1.getBlockY(), p2.getBlockY());
        int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
        int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());

        List<Clipboard.ClipboardBlock> blocks = new ArrayList<>();
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int length = maxZ - minZ + 1;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = p1.getWorld().getBlockAt(x, y, z);
                    int relX = x - playerLoc.getBlockX();
                    int relY = y - playerLoc.getBlockY();
                    int relZ = z - playerLoc.getBlockZ();
                    blocks.add(new Clipboard.ClipboardBlock(relX, relY, relZ, block.getBlockData()));
                }
            }
        }

        session.setClipboard(new Clipboard(blocks, width, height, length));
        player.sendMessage(Component.text("Sélection copiée dans le presse-papier (" + blocks.size() + " blocs).", NamedTextColor.GREEN));
    }

    private boolean handlePaste(Player player, String[] args) {
        if (!player.hasPermission("worldx.edit.paste")) {
            player.sendMessage(Component.text("Vous n'avez pas la permission.", NamedTextColor.RED));
            return true;
        }

        Session session = plugin.getSessionManager().getSession(player);

        // Subcommand: /paste confirm
        if (args.length >= 1 && args[0].equalsIgnoreCase("confirm")) {
            List<BlockDisplay> displays = session.getPastePreviewDisplays();
            Location targetLoc = session.getPastePreviewLocation();
            if (displays == null || targetLoc == null) {
                player.sendMessage(Component.text("Aucun aperçu de collage actif.", NamedTextColor.RED));
                return true;
            }

            Clipboard clipboard = session.getClipboard();
            if (clipboard == null) {
                player.sendMessage(Component.text("Votre presse-papier est vide.", NamedTextColor.RED));
                return true;
            }

            // Remove displays
            for (BlockDisplay bd : displays) {
                if (bd != null && bd.isValid()) bd.remove();
            }
            session.setPastePreviewDisplays(null);
            session.setPastePreviewLocation(null);

            // Apply blocks physically
            List<BlockEditQueue.BlockChangeInfo> changes = new ArrayList<>();
            for (Clipboard.ClipboardBlock clipBlock : clipboard.getBlocks()) {
                int targetX = targetLoc.getBlockX() + clipBlock.getRelX();
                int targetY = targetLoc.getBlockY() + clipBlock.getRelY();
                int targetZ = targetLoc.getBlockZ() + clipBlock.getRelZ();
                changes.add(new BlockEditQueue.BlockChangeInfo(targetX, targetY, targetZ, clipBlock.getBlockData()));
            }

            player.sendMessage(Component.text("Collage physique en cours...", NamedTextColor.YELLOW));
            plugin.getBlockEditQueue().queueTask(new BlockEditQueue.EditTask(
                    player.getUniqueId(), targetLoc.getWorld().getName(), changes
            ));
            return true;
        }

        // Subcommand: /paste cancel
        if (args.length >= 1 && args[0].equalsIgnoreCase("cancel")) {
            List<BlockDisplay> displays = session.getPastePreviewDisplays();
            if (displays == null) {
                player.sendMessage(Component.text("Aucun aperçu de collage actif.", NamedTextColor.RED));
                return true;
            }

            for (BlockDisplay bd : displays) {
                if (bd != null && bd.isValid()) bd.remove();
            }
            session.setPastePreviewDisplays(null);
            session.setPastePreviewLocation(null);

            player.sendMessage(Component.text("Aperçu du collage annulé.", NamedTextColor.GREEN));
            return true;
        }

        // Standard /paste: spawn preview displays
        Clipboard clipboard = session.getClipboard();
        if (clipboard == null) {
            player.sendMessage(Component.text("Le presse-papier est vide.", NamedTextColor.RED));
            return true;
        }

        // Clean up any old preview first
        List<BlockDisplay> oldDisplays = session.getPastePreviewDisplays();
        if (oldDisplays != null) {
            for (BlockDisplay bd : oldDisplays) {
                if (bd != null && bd.isValid()) bd.remove();
            }
        }

        // If clipboard is too large, paste directly
        if (clipboard.getBlocks().size() > 1000) {
            player.sendMessage(Component.text("Schéma trop grand pour l'aperçu. Collage physique direct...", NamedTextColor.YELLOW));
            Location playerLoc = player.getLocation();
            List<BlockEditQueue.BlockChangeInfo> changes = new ArrayList<>();
            for (Clipboard.ClipboardBlock clipBlock : clipboard.getBlocks()) {
                int targetX = playerLoc.getBlockX() + clipBlock.getRelX();
                int targetY = playerLoc.getBlockY() + clipBlock.getRelY();
                int targetZ = playerLoc.getBlockZ() + clipBlock.getRelZ();
                changes.add(new BlockEditQueue.BlockChangeInfo(targetX, targetY, targetZ, clipBlock.getBlockData()));
            }
            plugin.getBlockEditQueue().queueTask(new BlockEditQueue.EditTask(
                    player.getUniqueId(), playerLoc.getWorld().getName(), changes
            ));
            return true;
        }

        // Spawn BlockDisplays representing each block in the clipboard
        Location startLoc = player.getLocation().getBlock().getLocation();
        session.setPastePreviewLocation(startLoc);

        List<BlockDisplay> displays = new ArrayList<>();
        NamespacedKey rxKey = new NamespacedKey(plugin, "rel-x");
        NamespacedKey ryKey = new NamespacedKey(plugin, "rel-y");
        NamespacedKey rzKey = new NamespacedKey(plugin, "rel-z");

        for (Clipboard.ClipboardBlock cb : clipboard.getBlocks()) {
            Location loc = startLoc.clone().add(cb.getRelX(), cb.getRelY(), cb.getRelZ());
            BlockDisplay bd = startLoc.getWorld().spawn(loc, BlockDisplay.class, ent -> {
                ent.setBlock(cb.getBlockData());
                ent.setPersistent(false);
                ent.getPersistentDataContainer().set(rxKey, org.bukkit.persistence.PersistentDataType.INTEGER, cb.getRelX());
                ent.getPersistentDataContainer().set(ryKey, org.bukkit.persistence.PersistentDataType.INTEGER, cb.getRelY());
                ent.getPersistentDataContainer().set(rzKey, org.bukkit.persistence.PersistentDataType.INTEGER, cb.getRelZ());
            });
            displays.add(bd);
        }

        session.setPastePreviewDisplays(displays);

        player.sendMessage(Component.text("Aperçu de collage activé ! Regardez où poser.", NamedTextColor.GREEN));
        player.sendMessage(Component.text("Tapez /paste confirm pour valider ou /paste cancel pour annuler.", NamedTextColor.YELLOW));

        return true;
    }

    private boolean handleUndo(Player player) {
        if (!player.hasPermission("worldx.edit.undo")) {
            player.sendMessage(Component.text("Vous n'avez pas la permission.", NamedTextColor.RED));
            return true;
        }

        Session session = plugin.getSessionManager().getSession(player);
        EditOperation op = session.popUndo();
        if (op == null) {
            player.sendMessage(Component.text("Rien à annuler.", NamedTextColor.RED));
            return true;
        }

        List<BlockEditQueue.BlockChangeInfo> changes = new ArrayList<>();
        // Apply historical previous block states to undo
        for (EditOperation.BlockChange change : op.getChanges()) {
            changes.add(new BlockEditQueue.BlockChangeInfo(change.getX(), change.getY(), change.getZ(), change.getPreviousData()));
        }

        player.sendMessage(Component.text("Annulation en cours (" + changes.size() + " blocs)...", NamedTextColor.YELLOW));
        plugin.getBlockEditQueue().queueTask(new BlockEditQueue.EditTask(
                player.getUniqueId(), op.getWorldName(), changes, true, true
        ));

        return true;
    }

    private boolean handleRedo(Player player) {
        if (!player.hasPermission("worldx.edit.redo")) {
            player.sendMessage(Component.text("Vous n'avez pas la permission.", NamedTextColor.RED));
            return true;
        }

        Session session = plugin.getSessionManager().getSession(player);
        EditOperation op = session.popRedo();
        if (op == null) {
            player.sendMessage(Component.text("Rien à rétablir.", NamedTextColor.RED));
            return true;
        }

        List<BlockEditQueue.BlockChangeInfo> changes = new ArrayList<>();
        // Apply historical new block states to redo
        for (EditOperation.BlockChange change : op.getChanges()) {
            changes.add(new BlockEditQueue.BlockChangeInfo(change.getX(), change.getY(), change.getZ(), change.getNewData()));
        }

        player.sendMessage(Component.text("Rétablissement en cours (" + changes.size() + " blocs)...", NamedTextColor.YELLOW));
        plugin.getBlockEditQueue().queueTask(new BlockEditQueue.EditTask(
                player.getUniqueId(), op.getWorldName(), changes, true, false
        ));

        return true;
    }

    private BlockData parseBlockData(String name) {
        try {
            String input = name.toLowerCase();
            if (!input.contains(":")) {
                input = "minecraft:" + input;
            }
            return Bukkit.createBlockData(input);
        } catch (IllegalArgumentException e) {
            // Fallback for simple materials if direct parse fails
            Material mat = Material.matchMaterial(name.toUpperCase());
            if (mat != null && mat.isBlock()) {
                return Bukkit.createBlockData(mat);
            }
            return null;
        }
    }

    private String getFacingDirection(Player player) {
        org.bukkit.block.BlockFace face = player.getFacing();
        switch (face) {
            case NORTH: return "north";
            case SOUTH: return "south";
            case EAST: return "east";
            case WEST: return "west";
            case UP: return "up";
            case DOWN: return "down";
            default: return "north";
        }
    }

    private boolean handleExpand(Player player, String[] args) {
        if (!player.hasPermission("worldx.wand")) {
            player.sendMessage(Component.text("Vous n'avez pas la permission.", NamedTextColor.RED));
            return true;
        }

        Session session = plugin.getSessionManager().getSession(player);
        if (!session.hasCompleteSelection()) {
            player.sendMessage(Component.text("Veuillez d'abord faire une sélection complète (Pos 1 & Pos 2).", NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: //expand <quantité> [direction]", NamedTextColor.RED));
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("La quantité doit être un nombre entier.", NamedTextColor.RED));
            return true;
        }

        String direction = (args.length >= 2) ? args[1].toLowerCase() : getFacingDirection(player);

        Location p1 = session.getPos1();
        Location p2 = session.getPos2();

        int minX = Math.min(p1.getBlockX(), p2.getBlockX());
        int minY = Math.min(p1.getBlockY(), p2.getBlockY());
        int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
        int maxX = Math.max(p1.getBlockX(), p2.getBlockX());
        int maxY = Math.max(p1.getBlockY(), p2.getBlockY());
        int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());

        switch (direction) {
            case "up":
                maxY += amount;
                break;
            case "down":
                minY -= amount;
                break;
            case "north":
                minZ -= amount;
                break;
            case "south":
                maxZ += amount;
                break;
            case "west":
                minX -= amount;
                break;
            case "east":
                maxX += amount;
                break;
            default:
                player.sendMessage(Component.text("Direction inconnue. Choisissez parmi: up, down, north, south, east, west.", NamedTextColor.RED));
                return true;
        }

        org.bukkit.World world = p1.getWorld();
        session.setPos1(new Location(world, minX, minY, minZ));
        session.setPos2(new Location(world, maxX, maxY, maxZ));

        player.sendMessage(Component.text("Sélection élargie de " + amount + " blocs vers le " + direction + ".", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleContract(Player player, String[] args) {
        if (!player.hasPermission("worldx.wand")) {
            player.sendMessage(Component.text("Vous n'avez pas la permission.", NamedTextColor.RED));
            return true;
        }

        Session session = plugin.getSessionManager().getSession(player);
        if (!session.hasCompleteSelection()) {
            player.sendMessage(Component.text("Veuillez d'abord faire une sélection complète (Pos 1 & Pos 2).", NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: //contract <quantité> [direction]", NamedTextColor.RED));
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("La quantité doit être un nombre entier.", NamedTextColor.RED));
            return true;
        }

        String direction = (args.length >= 2) ? args[1].toLowerCase() : getFacingDirection(player);

        Location p1 = session.getPos1();
        Location p2 = session.getPos2();

        int minX = Math.min(p1.getBlockX(), p2.getBlockX());
        int minY = Math.min(p1.getBlockY(), p2.getBlockY());
        int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
        int maxX = Math.max(p1.getBlockX(), p2.getBlockX());
        int maxY = Math.max(p1.getBlockY(), p2.getBlockY());
        int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());

        switch (direction) {
            case "up":
                maxY = Math.max(minY, maxY - amount);
                break;
            case "down":
                minY = Math.min(maxY, minY + amount);
                break;
            case "north":
                minZ = Math.min(maxZ, minZ + amount);
                break;
            case "south":
                maxZ = Math.max(minZ, maxZ - amount);
                break;
            case "west":
                minX = Math.min(maxX, minX + amount);
                break;
            case "east":
                maxX = Math.max(minX, maxX - amount);
                break;
            default:
                player.sendMessage(Component.text("Direction inconnue. Choisissez parmi: up, down, north, south, east, west.", NamedTextColor.RED));
                return true;
        }

        org.bukkit.World world = p1.getWorld();
        session.setPos1(new Location(world, minX, minY, minZ));
        session.setPos2(new Location(world, maxX, maxY, maxZ));

        player.sendMessage(Component.text("Sélection rétrécie de " + amount + " blocs vers le " + direction + ".", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleSize(Player player) {
        if (!player.hasPermission("worldx.wand")) {
            player.sendMessage(Component.text("Vous n'avez pas la permission.", NamedTextColor.RED));
            return true;
        }

        Session session = plugin.getSessionManager().getSession(player);
        if (!session.hasCompleteSelection()) {
            player.sendMessage(Component.text("Veuillez d'abord faire une sélection complète (Pos 1 & Pos 2).", NamedTextColor.RED));
            return true;
        }

        Location p1 = session.getPos1();
        Location p2 = session.getPos2();

        int dx = Math.abs(p1.getBlockX() - p2.getBlockX()) + 1;
        int dy = Math.abs(p1.getBlockY() - p2.getBlockY()) + 1;
        int dz = Math.abs(p1.getBlockZ() - p2.getBlockZ()) + 1;
        long volume = (long) dx * dy * dz;

        player.sendMessage(Component.text("=== Taille de la Sélection ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Dimensions : ", NamedTextColor.YELLOW)
                .append(Component.text(dx + " x " + dy + " x " + dz, NamedTextColor.WHITE)));
        player.sendMessage(Component.text("Volume : ", NamedTextColor.YELLOW)
                .append(Component.text(volume + " blocs", NamedTextColor.GREEN)));
        return true;
    }

    private boolean handleRotate(Player player, String[] args) {
        if (!player.hasPermission("worldx.edit.paste")) {
            player.sendMessage(Component.text("Vous n'avez pas la permission.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: //rotate <angle>", NamedTextColor.RED));
            return true;
        }

        int angle;
        try {
            angle = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("L'angle doit être un nombre entier (ex: 90, 180, 270).", NamedTextColor.RED));
            return true;
        }

        if (angle % 90 != 0) {
            player.sendMessage(Component.text("L'angle doit être un multiple de 90 degrés.", NamedTextColor.RED));
            return true;
        }

        Session session = plugin.getSessionManager().getSession(player);
        Clipboard clipboard = session.getClipboard();
        if (clipboard == null) {
            player.sendMessage(Component.text("Votre presse-papier est vide. Utilisez d'abord /copy.", NamedTextColor.RED));
            return true;
        }

        Clipboard rotated = clipboard.rotate(angle);
        session.setClipboard(rotated);
        respawnPastePreview(player, session, rotated);

        player.sendMessage(Component.text("Presse-papier pivoté de " + angle + " degrés.", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleFlip(Player player, String[] args) {
        if (!player.hasPermission("worldx.edit.paste")) {
            player.sendMessage(Component.text("Vous n'avez pas la permission.", NamedTextColor.RED));
            return true;
        }

        String direction = (args.length >= 1) ? args[0].toLowerCase() : getFacingDirection(player);

        Session session = plugin.getSessionManager().getSession(player);
        Clipboard clipboard = session.getClipboard();
        if (clipboard == null) {
            player.sendMessage(Component.text("Votre presse-papier est vide. Utilisez d'abord /copy.", NamedTextColor.RED));
            return true;
        }

        if (!java.util.Arrays.asList("up", "down", "north", "south", "east", "west").contains(direction)) {
            player.sendMessage(Component.text("Direction inconnue. Choisissez parmi: up, down, north, south, east, west.", NamedTextColor.RED));
            return true;
        }

        Clipboard flipped = clipboard.flip(direction);
        session.setClipboard(flipped);
        respawnPastePreview(player, session, flipped);

        player.sendMessage(Component.text("Presse-papier retourné vers le " + direction + ".", NamedTextColor.GREEN));
        return true;
    }

    private void respawnPastePreview(Player player, Session session, Clipboard clipboard) {
        List<BlockDisplay> oldDisplays = session.getPastePreviewDisplays();
        if (oldDisplays == null || oldDisplays.isEmpty()) return;

        for (BlockDisplay bd : oldDisplays) {
            if (bd != null && bd.isValid()) bd.remove();
        }

        Location startLoc = session.getPastePreviewLocation();
        if (startLoc == null) startLoc = player.getLocation().getBlock().getLocation();

        List<BlockDisplay> displays = new ArrayList<>();
        NamespacedKey rxKey = new NamespacedKey(plugin, "rel-x");
        NamespacedKey ryKey = new NamespacedKey(plugin, "rel-y");
        NamespacedKey rzKey = new NamespacedKey(plugin, "rel-z");

        for (Clipboard.ClipboardBlock cb : clipboard.getBlocks()) {
            Location loc = startLoc.clone().add(cb.getRelX(), cb.getRelY(), cb.getRelZ());
            BlockDisplay bd = startLoc.getWorld().spawn(loc, BlockDisplay.class, ent -> {
                ent.setBlock(cb.getBlockData());
                ent.setPersistent(false);
                ent.getPersistentDataContainer().set(rxKey, org.bukkit.persistence.PersistentDataType.INTEGER, cb.getRelX());
                ent.getPersistentDataContainer().set(ryKey, org.bukkit.persistence.PersistentDataType.INTEGER, cb.getRelY());
                ent.getPersistentDataContainer().set(rzKey, org.bukkit.persistence.PersistentDataType.INTEGER, cb.getRelZ());
            });
            displays.add(bd);
        }
        session.setPastePreviewDisplays(displays);
    }

    private boolean handleGmask(Player player, String[] args) {
        if (!player.hasPermission("worldx.edit.mask")) {
            player.sendMessage(Component.text("Vous n'avez pas la permission.", NamedTextColor.RED));
            return true;
        }

        Session session = plugin.getSessionManager().getSession(player);
        if (args.length < 1 || args[0].equalsIgnoreCase("clear") || args[0].equalsIgnoreCase("none")) {
            session.setActiveMask(null);
            player.sendMessage(Component.text("Masque global désactivé.", NamedTextColor.GREEN));
            return true;
        }

        String maskStr = String.join(" ", args);
        session.setActiveMask(maskStr);
        player.sendMessage(Component.text("Masque global défini sur : " + maskStr, NamedTextColor.GREEN));
        return true;
    }

    private boolean handleSphere(Player player, String[] args, boolean hollow) {
        if (!player.hasPermission("worldx.edit.sphere")) {
            player.sendMessage(Component.text("Vous n'avez pas la permission.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: //" + (hollow ? "hsphere" : "sphere") + " <bloc> <rayon>", NamedTextColor.RED));
            return true;
        }

        BlockData blockData = parseBlockData(args[0]);
        if (blockData == null) {
            player.sendMessage(Component.text("Bloc invalide : " + args[0], NamedTextColor.RED));
            return true;
        }

        int radius;
        try {
            radius = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Le rayon doit être un nombre entier.", NamedTextColor.RED));
            return true;
        }

        if (radius <= 0 || radius > 50) {
            player.sendMessage(Component.text("Le rayon doit être compris entre 1 et 50.", NamedTextColor.RED));
            return true;
        }

        Location center = player.getLocation().getBlock().getLocation();
        org.bukkit.World world = center.getWorld();

        List<BlockEditQueue.BlockChangeInfo> changes = new ArrayList<>();
        double rSq = radius * radius;
        double innerRSq = (radius - 1) * (radius - 1);

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    double distSq = x * x + y * y + z * z;
                    if (distSq <= rSq) {
                        if (!hollow || distSq > innerRSq) {
                            Location blockLoc = new Location(world, center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
                            if (plugin.getRegionManager().checkPermission(player, blockLoc, fr.skynex.worldx.region.Flag.BUILD)) {
                                changes.add(new BlockEditQueue.BlockChangeInfo(blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ(), blockData));
                            }
                        }
                    }
                }
            }
        }

        if (changes.isEmpty()) {
            player.sendMessage(Component.text("Aucun bloc modifié (protection de région active).", NamedTextColor.RED));
            return true;
        }

        player.sendMessage(Component.text("Génération de la sphère (" + changes.size() + " blocs)...", NamedTextColor.YELLOW));
        plugin.getBlockEditQueue().queueTask(new BlockEditQueue.EditTask(
                player.getUniqueId(), world.getName(), changes
        ));

        return true;
    }

    private boolean handleLine(Player player, String[] args) {
        if (!player.hasPermission("worldx.edit.line")) {
            player.sendMessage(Component.text("Vous n'avez pas la permission.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: //line <bloc>", NamedTextColor.RED));
            return true;
        }

        Session session = plugin.getSessionManager().getSession(player);
        if (!session.hasCompleteSelection()) {
            player.sendMessage(Component.text("Veuillez d'abord faire une sélection complète (Pos 1 & Pos 2).", NamedTextColor.RED));
            return true;
        }

        BlockData blockData = parseBlockData(args[0]);
        if (blockData == null) {
            player.sendMessage(Component.text("Bloc invalide : " + args[0], NamedTextColor.RED));
            return true;
        }

        Location p1 = session.getPos1();
        Location p2 = session.getPos2();
        org.bukkit.World world = p1.getWorld();

        List<Location> linePoints = getBresenhamLinePoints(p1, p2);
        List<BlockEditQueue.BlockChangeInfo> changes = new ArrayList<>();

        for (Location loc : linePoints) {
            if (plugin.getRegionManager().checkPermission(player, loc, fr.skynex.worldx.region.Flag.BUILD)) {
                changes.add(new BlockEditQueue.BlockChangeInfo(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), blockData));
            }
        }

        if (changes.isEmpty()) {
            player.sendMessage(Component.text("Aucun bloc modifié (protection de région active).", NamedTextColor.RED));
            return true;
        }

        player.sendMessage(Component.text("Tracé de la ligne (" + changes.size() + " blocs)...", NamedTextColor.YELLOW));
        plugin.getBlockEditQueue().queueTask(new BlockEditQueue.EditTask(
                player.getUniqueId(), world.getName(), changes
        ));

        return true;
    }

    private List<Location> getBresenhamLinePoints(Location p1, Location p2) {
        List<Location> points = new ArrayList<>();
        int x1 = p1.getBlockX();
        int y1 = p1.getBlockY();
        int z1 = p1.getBlockZ();
        int x2 = p2.getBlockX();
        int y2 = p2.getBlockY();
        int z2 = p2.getBlockZ();

        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int dz = Math.abs(z2 - z1);

        int xs = (x2 > x1) ? 1 : -1;
        int ys = (y2 > y1) ? 1 : -1;
        int zs = (z2 > z1) ? 1 : -1;

        if (dx >= dy && dx >= dz) {
            int p1_err = 2 * dy - dx;
            int p2_err = 2 * dz - dx;
            while (x1 != x2) {
                points.add(new Location(p1.getWorld(), x1, y1, z1));
                if (p1_err >= 0) {
                    y1 += ys;
                    p1_err -= 2 * dx;
                }
                if (p2_err >= 0) {
                    z1 += zs;
                    p2_err -= 2 * dx;
                }
                p1_err += 2 * dy;
                p2_err += 2 * dz;
                x1 += xs;
            }
        } else if (dy >= dx && dy >= dz) {
            int p1_err = 2 * dx - dy;
            int p2_err = 2 * dz - dy;
            while (y1 != y2) {
                points.add(new Location(p1.getWorld(), x1, y1, z1));
                if (p1_err >= 0) {
                    x1 += xs;
                    p1_err -= 2 * dy;
                }
                if (p2_err >= 0) {
                    z1 += zs;
                    p2_err -= 2 * dy;
                }
                p1_err += 2 * dx;
                p2_err += 2 * dz;
                y1 += ys;
            }
        } else {
            int p1_err = 2 * dy - dz;
            int p2_err = 2 * dx - dz;
            while (z1 != z2) {
                points.add(new Location(p1.getWorld(), x1, y1, z1));
                if (p1_err >= 0) {
                    y1 += ys;
                    p1_err -= 2 * dz;
                }
                if (p2_err >= 0) {
                    x1 += xs;
                    p2_err -= 2 * dz;
                }
                p1_err += 2 * dy;
                p2_err += 2 * dx;
                z1 += zs;
            }
        }
        points.add(new Location(p1.getWorld(), x2, y2, z2));
        return points;
    }
}
