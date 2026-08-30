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

        if (!plugin.getConfig().getBoolean("features.editing", true)) {
            player.sendMessage(Component.text("Le système d'édition de monde (editing) est actuellement désactivé sur ce serveur.", NamedTextColor.RED));
            return true;
        }

        String cmdName = command.getName().toLowerCase();

        switch (cmdName) {
            case "/wand":
            case "wand":
                return handleWand(player);
            case "/pos1":
            case "pos1":
                return handlePos(player, 1, false);
            case "/pos2":
            case "pos2":
                return handlePos(player, 2, false);
            case "/hpos1":
            case "hpos1":
                return handlePos(player, 1, true);
            case "/hpos2":
            case "hpos2":
                return handlePos(player, 2, true);
            case "/set":
            case "set":
                return handleSet(player, args);
            case "/replace":
            case "replace":
                return handleReplace(player, args);
            case "/walls":
            case "walls":
                return handleWalls(player, args);
            case "/cyl":
            case "cyl":
                return handleCyl(player, args, false);
            case "/hcyl":
            case "hcyl":
                return handleCyl(player, args, true);
            case "/pyramid":
            case "pyramid":
                return handlePyramid(player, args, false);
            case "/hpyramid":
            case "hpyramid":
                return handlePyramid(player, args, true);
            case "/center":
            case "center":
                return handleCenter(player, args);
            case "/count":
            case "count":
                return handleCount(player, args);
            case "/distr":
            case "distr":
                return handleDistr(player);
            case "/stack":
            case "stack":
                return handleStack(player, args);
            case "/move":
            case "move":
                return handleMove(player, args);
            case "/fill":
            case "fill":
                return handleFill(player, args);
            case "/drain":
            case "drain":
                return handleDrain(player, args);
            case "/inset":
            case "inset":
                return handleInsetOutset(player, args, true);
            case "/outset":
            case "outset":
                return handleInsetOutset(player, args, false);
            case "/cut":
            case "cut":
                return handleCut(player);
            case "/copy":
            case "copy":
                return handleCopy(player);
            case "/paste":
            case "paste":
                return handlePaste(player, args);
            case "/undo":
            case "undo":
                return handleUndo(player);
            case "/redo":
            case "redo":
                return handleRedo(player);
            case "/expand":
            case "expand":
                return handleExpand(player, args);
            case "/contract":
            case "contract":
                return handleContract(player, args);
            case "/size":
            case "size":
                return handleSize(player);
            case "/rotate":
            case "rotate":
                return handleRotate(player, args);
            case "/flip":
            case "flip":
                return handleFlip(player, args);
            case "/gmask":
            case "gmask":
                return handleGmask(player, args);
            case "/sphere":
            case "sphere":
                return handleSphere(player, args, false);
            case "/hsphere":
            case "hsphere":
                return handleSphere(player, args, true);
            case "/line":
            case "line":
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

    private boolean handlePos(Player player, int posIndex, boolean targeted) {
        Location targetLoc = targeted ? player.getTargetBlockExact(100) != null ? player.getTargetBlockExact(100).getLocation() : player.getLocation() : player.getLocation();
        Session session = plugin.getSessionManager().getSession(player);
        if (posIndex == 1) {
            session.setPos1(targetLoc);
            player.sendMessage(Component.text("Position 1 définie sur (" + targetLoc.getBlockX() + ", " + targetLoc.getBlockY() + ", " + targetLoc.getBlockZ() + ").", NamedTextColor.GREEN));
        } else {
            session.setPos2(targetLoc);
            player.sendMessage(Component.text("Position 2 définie sur (" + targetLoc.getBlockX() + ", " + targetLoc.getBlockY() + ", " + targetLoc.getBlockZ() + ").", NamedTextColor.GREEN));
        }
        return true;
    }

    private boolean handleWalls(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: //walls <bloc>", NamedTextColor.RED));
            return true;
        }
        Session session = plugin.getSessionManager().getSession(player);
        if (!session.hasCompleteSelection()) {
            player.sendMessage(Component.text("Veuillez d'abord faire une sélection.", NamedTextColor.RED));
            return true;
        }
        BlockData data = parseBlockData(args[0]);
        if (data == null) {
            player.sendMessage(Component.text("Bloc invalide: " + args[0], NamedTextColor.RED));
            return true;
        }
        Location p1 = session.getPos1();
        Location p2 = session.getPos2();
        int minX = Math.min(p1.getBlockX(), p2.getBlockX());
        int maxX = Math.max(p1.getBlockX(), p2.getBlockX());
        int minY = Math.min(p1.getBlockY(), p2.getBlockY());
        int maxY = Math.max(p1.getBlockY(), p2.getBlockY());
        int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
        int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());

        List<BlockEditQueue.BlockChangeInfo> changes = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (x == minX || x == maxX || z == minZ || z == maxZ) {
                        changes.add(new BlockEditQueue.BlockChangeInfo(x, y, z, data));
                    }
                }
            }
        }
        player.sendMessage(Component.text("Génération des murs (" + changes.size() + " blocs)...", NamedTextColor.YELLOW));
        plugin.getBlockEditQueue().queueTask(new BlockEditQueue.EditTask(player.getUniqueId(), p1.getWorld().getName(), changes));
        return true;
    }

    private boolean handleCyl(Player player, String[] args, boolean hollow) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: //cyl <bloc> <rayon> [hauteur]", NamedTextColor.RED));
            return true;
        }
        BlockData data = parseBlockData(args[0]);
        if (data == null) {
            player.sendMessage(Component.text("Bloc invalide: " + args[0], NamedTextColor.RED));
            return true;
        }
        int radius;
        try {
            radius = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Rayon invalide.", NamedTextColor.RED));
            return true;
        }
        int height = 1;
        if (args.length >= 3) {
            try {
                height = Integer.parseInt(args[2]);
            } catch (NumberFormatException ignored) {}
        }
        Location center = player.getLocation().getBlock().getLocation();
        List<BlockEditQueue.BlockChangeInfo> changes = new ArrayList<>();
        double rSq = radius * radius;
        double innerRSq = (radius - 1) * (radius - 1);

        for (int dy = 0; dy < height; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double distSq = dx * dx + dz * dz;
                    if (distSq <= rSq && (!hollow || distSq > innerRSq)) {
                        changes.add(new BlockEditQueue.BlockChangeInfo(center.getBlockX() + dx, center.getBlockY() + dy, center.getBlockZ() + dz, data));
                    }
                }
            }
        }
        player.sendMessage(Component.text("Génération du cylindre (" + changes.size() + " blocs)...", NamedTextColor.YELLOW));
        plugin.getBlockEditQueue().queueTask(new BlockEditQueue.EditTask(player.getUniqueId(), center.getWorld().getName(), changes));
        return true;
    }

    private boolean handlePyramid(Player player, String[] args, boolean hollow) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: //pyramid <bloc> <taille>", NamedTextColor.RED));
            return true;
        }
        BlockData data = parseBlockData(args[0]);
        if (data == null) {
            player.sendMessage(Component.text("Bloc invalide.", NamedTextColor.RED));
            return true;
        }
        int size = Integer.parseInt(args[1]);
        Location center = player.getLocation().getBlock().getLocation();
        List<BlockEditQueue.BlockChangeInfo> changes = new ArrayList<>();

        for (int y = 0; y < size; y++) {
            int radius = size - y - 1;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (!hollow || Math.abs(x) == radius || Math.abs(z) == radius) {
                        changes.add(new BlockEditQueue.BlockChangeInfo(center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z, data));
                    }
                }
            }
        }
        player.sendMessage(Component.text("Génération de la pyramide (" + changes.size() + " blocs)...", NamedTextColor.YELLOW));
        plugin.getBlockEditQueue().queueTask(new BlockEditQueue.EditTask(player.getUniqueId(), center.getWorld().getName(), changes));
        return true;
    }

    private boolean handleCenter(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: //center <bloc>", NamedTextColor.RED));
            return true;
        }
        Session session = plugin.getSessionManager().getSession(player);
        if (!session.hasCompleteSelection()) {
            player.sendMessage(Component.text("Sélection requise.", NamedTextColor.RED));
            return true;
        }
        BlockData data = parseBlockData(args[0]);
        Location p1 = session.getPos1();
        Location p2 = session.getPos2();
        int cx = (p1.getBlockX() + p2.getBlockX()) / 2;
        int cy = (p1.getBlockY() + p2.getBlockY()) / 2;
        int cz = (p1.getBlockZ() + p2.getBlockZ()) / 2;

        List<BlockEditQueue.BlockChangeInfo> changes = List.of(new BlockEditQueue.BlockChangeInfo(cx, cy, cz, data));
        plugin.getBlockEditQueue().queueTask(new BlockEditQueue.EditTask(player.getUniqueId(), p1.getWorld().getName(), changes));
        player.sendMessage(Component.text("Centre de la sélection défini sur " + args[0] + ".", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleCount(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: //count <bloc>", NamedTextColor.RED));
            return true;
        }
        Session session = plugin.getSessionManager().getSession(player);
        if (!session.hasCompleteSelection()) {
            player.sendMessage(Component.text("Sélection requise.", NamedTextColor.RED));
            return true;
        }
        BlockData target = parseBlockData(args[0]);
        if (target == null) return true;
        Location p1 = session.getPos1();
        Location p2 = session.getPos2();
        int minX = Math.min(p1.getBlockX(), p2.getBlockX());
        int maxX = Math.max(p1.getBlockX(), p2.getBlockX());
        int minY = Math.min(p1.getBlockY(), p2.getBlockY());
        int maxY = Math.max(p1.getBlockY(), p2.getBlockY());
        int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
        int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());

        long count = 0;
        org.bukkit.World w = p1.getWorld();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (w.getBlockAt(x, y, z).getBlockData().equals(target)) {
                        count++;
                    }
                }
            }
        }
        player.sendMessage(Component.text("Nombre de blocs de type " + args[0] + " : " + count, NamedTextColor.GREEN));
        return true;
    }

    private boolean handleDistr(Player player) {
        Session session = plugin.getSessionManager().getSession(player);
        if (!session.hasCompleteSelection()) {
            player.sendMessage(Component.text("Sélection requise.", NamedTextColor.RED));
            return true;
        }
        Location p1 = session.getPos1();
        Location p2 = session.getPos2();
        int minX = Math.min(p1.getBlockX(), p2.getBlockX());
        int maxX = Math.max(p1.getBlockX(), p2.getBlockX());
        int minY = Math.min(p1.getBlockY(), p2.getBlockY());
        int maxY = Math.max(p1.getBlockY(), p2.getBlockY());
        int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
        int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());

        java.util.Map<Material, Integer> counts = new java.util.HashMap<>();
        int total = 0;
        org.bukkit.World w = p1.getWorld();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Material m = w.getBlockAt(x, y, z).getType();
                    counts.put(m, counts.getOrDefault(m, 0) + 1);
                    total++;
                }
            }
        }
        player.sendMessage(Component.text("=== Répartition des Blocs (" + total + " au total) ===", NamedTextColor.GOLD));
        for (java.util.Map.Entry<Material, Integer> entry : counts.entrySet()) {
            double percent = (entry.getValue() * 100.0) / total;
            player.sendMessage(Component.text(entry.getKey().name() + ": " + entry.getValue() + " (" + String.format("%.2f", percent) + "%)", NamedTextColor.YELLOW));
        }
        return true;
    }

    private boolean handleStack(Player player, String[] args) {
        int count = args.length >= 1 ? Integer.parseInt(args[0]) : 1;
        String dir = args.length >= 2 ? args[1].toLowerCase() : getFacingDirection(player);
        Session session = plugin.getSessionManager().getSession(player);
        if (!session.hasCompleteSelection()) return true;

        Location p1 = session.getPos1();
        Location p2 = session.getPos2();
        int dx = Math.abs(p1.getBlockX() - p2.getBlockX()) + 1;
        int dy = Math.abs(p1.getBlockY() - p2.getBlockY()) + 1;
        int dz = Math.abs(p1.getBlockZ() - p2.getBlockZ()) + 1;

        int stepX = dir.equals("east") ? dx : dir.equals("west") ? -dx : 0;
        int stepY = dir.equals("up") ? dy : dir.equals("down") ? -dy : 0;
        int stepZ = dir.equals("south") ? dz : dir.equals("north") ? -dz : 0;

        List<BlockEditQueue.BlockChangeInfo> changes = new ArrayList<>();
        org.bukkit.World w = p1.getWorld();
        int minX = Math.min(p1.getBlockX(), p2.getBlockX());
        int maxX = Math.max(p1.getBlockX(), p2.getBlockX());
        int minY = Math.min(p1.getBlockY(), p2.getBlockY());
        int maxY = Math.max(p1.getBlockY(), p2.getBlockY());
        int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
        int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());

        for (int i = 1; i <= count; i++) {
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockData data = w.getBlockAt(x, y, z).getBlockData();
                        changes.add(new BlockEditQueue.BlockChangeInfo(x + stepX * i, y + stepY * i, z + stepZ * i, data));
                    }
                }
            }
        }
        player.sendMessage(Component.text("Empilement de " + count + " copie(s) vers le " + dir + "...", NamedTextColor.YELLOW));
        plugin.getBlockEditQueue().queueTask(new BlockEditQueue.EditTask(player.getUniqueId(), w.getName(), changes));
        return true;
    }

    private boolean handleMove(Player player, String[] args) {
        int distance = args.length >= 1 ? Integer.parseInt(args[0]) : 1;
        String dir = args.length >= 2 ? args[1].toLowerCase() : getFacingDirection(player);
        Session session = plugin.getSessionManager().getSession(player);
        if (!session.hasCompleteSelection()) return true;

        Location p1 = session.getPos1();
        Location p2 = session.getPos2();
        int minX = Math.min(p1.getBlockX(), p2.getBlockX());
        int maxX = Math.max(p1.getBlockX(), p2.getBlockX());
        int minY = Math.min(p1.getBlockY(), p2.getBlockY());
        int maxY = Math.max(p1.getBlockY(), p2.getBlockY());
        int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
        int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());

        int stepX = dir.equals("east") ? distance : dir.equals("west") ? -distance : 0;
        int stepY = dir.equals("up") ? distance : dir.equals("down") ? -distance : 0;
        int stepZ = dir.equals("south") ? distance : dir.equals("north") ? -distance : 0;

        List<BlockEditQueue.BlockChangeInfo> changes = new ArrayList<>();
        org.bukkit.World w = p1.getWorld();
        BlockData air = Bukkit.createBlockData(Material.AIR);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockData data = w.getBlockAt(x, y, z).getBlockData();
                    changes.add(new BlockEditQueue.BlockChangeInfo(x, y, z, air));
                    changes.add(new BlockEditQueue.BlockChangeInfo(x + stepX, y + stepY, z + stepZ, data));
                }
            }
        }
        player.sendMessage(Component.text("Déplacement de la sélection de " + distance + " blocs vers le " + dir + "...", NamedTextColor.YELLOW));
        plugin.getBlockEditQueue().queueTask(new BlockEditQueue.EditTask(player.getUniqueId(), w.getName(), changes));
        return true;
    }

    private boolean handleFill(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: //fill <bloc> <rayon>", NamedTextColor.RED));
            return true;
        }
        BlockData data = parseBlockData(args[0]);
        int radius = Integer.parseInt(args[1]);
        Location loc = player.getLocation().getBlock().getLocation();
        List<BlockEditQueue.BlockChangeInfo> changes = new ArrayList<>();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = loc.getWorld().getBlockAt(loc.getBlockX() + x, loc.getBlockY() + y, loc.getBlockZ() + z);
                    if (b.getType() == Material.AIR || b.getType() == Material.WATER || b.getType() == Material.LAVA) {
                        changes.add(new BlockEditQueue.BlockChangeInfo(b.getX(), b.getY(), b.getZ(), data));
                    }
                }
            }
        }
        player.sendMessage(Component.text("Remplissage de " + changes.size() + " blocs...", NamedTextColor.YELLOW));
        plugin.getBlockEditQueue().queueTask(new BlockEditQueue.EditTask(player.getUniqueId(), loc.getWorld().getName(), changes));
        return true;
    }

    private boolean handleDrain(Player player, String[] args) {
        int radius = args.length >= 1 ? Integer.parseInt(args[0]) : 10;
        Location loc = player.getLocation().getBlock().getLocation();
        List<BlockEditQueue.BlockChangeInfo> changes = new ArrayList<>();
        BlockData air = Bukkit.createBlockData(Material.AIR);

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = loc.getWorld().getBlockAt(loc.getBlockX() + x, loc.getBlockY() + y, loc.getBlockZ() + z);
                    if (b.getType() == Material.WATER || b.getType() == Material.LAVA) {
                        changes.add(new BlockEditQueue.BlockChangeInfo(b.getX(), b.getY(), b.getZ(), air));
                    }
                }
            }
        }
        player.sendMessage(Component.text("Drainage de " + changes.size() + " blocs de liquides...", NamedTextColor.YELLOW));
        plugin.getBlockEditQueue().queueTask(new BlockEditQueue.EditTask(player.getUniqueId(), loc.getWorld().getName(), changes));
        return true;
    }

    private boolean handleInsetOutset(Player player, String[] args, boolean inset) {
        int amount = args.length >= 1 ? Integer.parseInt(args[0]) : 1;
        Session session = plugin.getSessionManager().getSession(player);
        if (!session.hasCompleteSelection()) return true;

        Location p1 = session.getPos1();
        Location p2 = session.getPos2();
        int delta = inset ? -amount : amount;

        int minX = Math.min(p1.getBlockX(), p2.getBlockX()) - delta;
        int maxX = Math.max(p1.getBlockX(), p2.getBlockX()) + delta;
        int minY = Math.min(p1.getBlockY(), p2.getBlockY()) - delta;
        int maxY = Math.max(p1.getBlockY(), p2.getBlockY()) + delta;
        int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ()) - delta;
        int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ()) + delta;

        org.bukkit.World w = p1.getWorld();
        session.setPos1(new Location(w, minX, minY, minZ));
        session.setPos2(new Location(w, maxX, maxY, maxZ));
        player.sendMessage(Component.text("Sélection " + (inset ? "réduite" : "agrandie") + " de " + amount + " bloc(s) sur tous les axes.", NamedTextColor.GREEN));
        return true;
    }
}
