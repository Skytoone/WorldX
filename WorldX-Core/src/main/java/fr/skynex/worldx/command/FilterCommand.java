package fr.skynex.worldx.command;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.edit.BlockEditQueue;
import fr.skynex.worldx.region.Region;
import fr.skynex.worldx.session.Session;
import org.bukkit.Bukkit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class FilterCommand implements CommandExecutor, TabCompleter {

    private final WorldX plugin;

    public FilterCommand(WorldX plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Seuls les joueurs peuvent utiliser les filtres.", NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("worldx.filter")) {
            player.sendMessage(Component.text("Vous n'avez pas la permission d'utiliser les filtres.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            sendHelp(player);
            return true;
        }

        Session session = plugin.getSessionManager().getSession(player);
        if (!session.hasCompleteSelection()) {
            player.sendMessage(Component.text("Veuillez d'abord faire une sélection (//wand).", NamedTextColor.RED));
            return true;
        }

        String filterType = args[0].toLowerCase();
        World world = session.getPos1().getWorld();
        if (world == null) return true;

        // Temporary region bounds calculation based on pos1/pos2
        int minX = Math.min(session.getPos1().getBlockX(), session.getPos2().getBlockX());
        int minY = Math.min(session.getPos1().getBlockY(), session.getPos2().getBlockY());
        int minZ = Math.min(session.getPos1().getBlockZ(), session.getPos2().getBlockZ());
        int maxX = Math.max(session.getPos1().getBlockX(), session.getPos2().getBlockX());
        int maxY = Math.max(session.getPos1().getBlockY(), session.getPos2().getBlockY());
        int maxZ = Math.max(session.getPos1().getBlockZ(), session.getPos2().getBlockZ());

        // Simple Region stub to support contains check
        Region tempRegion = new Region("temp", world.getName(), minX, minY, minZ, maxX, maxY, maxZ, session.getSelectionType());
        if (session.getSelectionType() == fr.skynex.worldx.region.ShapeType.POLYGON) {
            List<int[]> polyPoints = new ArrayList<>();
            for (org.bukkit.Location loc : session.getSelectionPoints()) {
                polyPoints.add(new int[]{loc.getBlockX(), loc.getBlockZ()});
            }
            tempRegion.setPolyPoints(polyPoints);
        }

        player.sendMessage(Component.text("Application du filtre \"" + filterType + "\", calcul en cours...", NamedTextColor.YELLOW));

        // Calculate changes asynchronously
        fr.skynex.worldx.scheduler.FoliaScheduler.runAsync(plugin, () -> {
            List<BlockEditQueue.BlockChangeInfo> changes = new ArrayList<>();

            try {
                if (filterType.equals("gravity")) {
                    for (int x = minX; x <= maxX; x++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            // Find highest solid block below selection
                            int landingHeight = minY - 1;
                            while (landingHeight >= world.getMinHeight() && world.getBlockAt(x, landingHeight, z).getType().isAir()) {
                                landingHeight--;
                            }

                            int nextY = landingHeight + 1;
                            for (int y = minY; y <= maxY; y++) {
                                if (tempRegion.contains(x, y, z)) {
                                    Block block = world.getBlockAt(x, y, z);
                                    BlockData blockData = block.getBlockData();
                                    if (!block.getType().isAir()) {
                                        if (y != nextY) {
                                            changes.add(new BlockEditQueue.BlockChangeInfo(x, y, z, Material.AIR.createBlockData()));
                                            changes.add(new BlockEditQueue.BlockChangeInfo(x, nextY, z, blockData));
                                        }
                                        nextY++;
                                    }
                                }
                            }
                        }
                    }
                } else if (filterType.equals("noise")) {
                    if (args.length < 3) {
                        player.sendMessage(Component.text("Usage: /filter noise <bloc> <ratio: 0.0-1.0 ou %>", NamedTextColor.RED));
                        return;
                    }
                    Material mat = Material.matchMaterial(args[1]);
                    if (mat == null || !mat.isBlock()) {
                        player.sendMessage(Component.text("Matériau inconnu ou invalide.", NamedTextColor.RED));
                        return;
                    }
                    String ratioArg = args[2];
                    double ratio = 0.1;
                    try {
                        if (ratioArg.endsWith("%")) {
                            ratio = Double.parseDouble(ratioArg.replace("%", "")) / 100.0;
                        } else {
                            ratio = Double.parseDouble(ratioArg);
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage(Component.text("Ratio invalide (ex: 0.15 ou 15%).", NamedTextColor.RED));
                        return;
                    }

                    ratio = Math.max(0.0, Math.min(1.0, ratio));

                    for (int x = minX; x <= maxX; x++) {
                        for (int y = minY; y <= maxY; y++) {
                            for (int z = minZ; z <= maxZ; z++) {
                                if (tempRegion.contains(x, y, z)) {
                                    Block block = world.getBlockAt(x, y, z);
                                    if (!block.getType().isAir() && Math.random() < ratio) {
                                        changes.add(new BlockEditQueue.BlockChangeInfo(x, y, z, mat.createBlockData()));
                                    }
                                }
                            }
                        }
                    }
                } else if (filterType.equals("erode")) {
                    int[][] neighbors = {{1,0,0}, {-1,0,0}, {0,1,0}, {0,-1,0}, {0,0,1}, {0,0,-1}};
                    for (int x = minX; x <= maxX; x++) {
                        for (int y = minY; y <= maxY; y++) {
                            for (int z = minZ; z <= maxZ; z++) {
                                if (tempRegion.contains(x, y, z)) {
                                    Block block = world.getBlockAt(x, y, z);
                                    boolean isAir = block.getType().isAir();

                                    int airNeighbors = 0;
                                    Material solidNeighborMat = null;

                                    for (int[] n : neighbors) {
                                        Block nb = world.getBlockAt(x + n[0], y + n[1], z + n[2]);
                                        if (nb.getType().isAir()) {
                                            airNeighbors++;
                                        } else {
                                            solidNeighborMat = nb.getType();
                                        }
                                    }

                                    if (!isAir && airNeighbors >= 4) {
                                        changes.add(new BlockEditQueue.BlockChangeInfo(x, y, z, Material.AIR.createBlockData()));
                                    } else if (isAir && airNeighbors <= 1 && solidNeighborMat != null) {
                                        changes.add(new BlockEditQueue.BlockChangeInfo(x, y, z, solidNeighborMat.createBlockData()));
                                    }
                                }
                            }
                        }
                    }
                } else if (filterType.equals("melt")) {
                    int[][] neighbors = {{1,0,0}, {-1,0,0}, {0,1,0}, {0,-1,0}, {0,0,1}, {0,0,-1}};
                    for (int x = minX; x <= maxX; x++) {
                        for (int y = minY; y <= maxY; y++) {
                            for (int z = minZ; z <= maxZ; z++) {
                                if (tempRegion.contains(x, y, z)) {
                                    Block block = world.getBlockAt(x, y, z);
                                    if (!block.getType().isAir()) {
                                        int airNeighbors = 0;
                                        for (int[] n : neighbors) {
                                            if (world.getBlockAt(x + n[0], y + n[1], z + n[2]).getType().isAir()) {
                                                airNeighbors++;
                                            }
                                        }
                                        if (airNeighbors >= 3) {
                                            changes.add(new BlockEditQueue.BlockChangeInfo(x, y, z, Material.AIR.createBlockData()));
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    player.sendMessage(Component.text("Filtre inconnu.", NamedTextColor.RED));
                    return;
                }

                if (!changes.isEmpty()) {
                    plugin.getBlockEditQueue().queueTask(new BlockEditQueue.EditTask(
                            player.getUniqueId(), world.getName(), changes
                    ));
                    player.sendMessage(Component.text("Filtre \"" + filterType + "\" appliqué avec succès ! (" + changes.size() + " blocs modifiés).", NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("Aucun bloc n'a été modifié par le filtre.", NamedTextColor.YELLOW));
                }
            } catch (Exception e) {
                player.sendMessage(Component.text("Erreur d'application du filtre : " + e.getMessage(), NamedTextColor.RED));
            }
        });

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("=== Aide WorldX Filtres ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/filter gravity", NamedTextColor.YELLOW)
                .append(Component.text(" - Fait tomber les blocs non soutenus", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/filter noise <bloc> <ratio>", NamedTextColor.YELLOW)
                .append(Component.text(" - Ajoute une texture de blocs aléatoire (ex: 15%)", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/filter erode", NamedTextColor.YELLOW)
                .append(Component.text(" - Lisse le relief par érosion des angles", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/filter melt", NamedTextColor.YELLOW)
                .append(Component.text(" - Fond le relief en effaçant les sommets et arêtes", NamedTextColor.GRAY)));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("gravity", "noise", "erode", "melt").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("noise")) {
            return Arrays.stream(Material.values())
                    .filter(m -> m.isBlock())
                    .map(m -> m.name().toLowerCase())
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .limit(50)
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("noise")) {
            return Arrays.asList("10%", "25%", "50%", "0.1", "0.25", "0.5").stream()
                    .filter(s -> s.startsWith(args[2])).collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}

