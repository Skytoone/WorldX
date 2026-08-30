package fr.skynex.worldx.listener;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.session.Session;
import fr.skynex.worldx.region.Region;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class PlayerListener implements Listener {

    private final WorldX plugin;

    public PlayerListener(WorldX plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        
        if (event.getAction() == org.bukkit.event.block.Action.PHYSICAL) {
            Block block = event.getClickedBlock();
            if (block != null && block.getType() == Material.LIGHT_WEIGHTED_PRESSURE_PLATE) {
                Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(block.getLocation());
                if (region != null) {
                    String scriptVal = region.getFlagValue("script-gold-plate");
                    if (scriptVal != null && !scriptVal.trim().isEmpty()) {
                        fr.skynex.worldx.script.ScriptExecutor.runScript(player, region, scriptVal);
                    }
                }
            }
            return;
        }

        // Only run for the main hand to avoid duplicate clicks
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        String wandMatName = plugin.getConfig().getString("edit.wand-item", "WOODEN_AXE");
        Material wandMat = Material.matchMaterial(wandMatName);
        if (wandMat == null) {
            wandMat = Material.WOODEN_AXE;
        }

        if (player.getInventory().getItemInMainHand().getType() == wandMat) {
            if (!player.hasPermission("worldx.wand")) {
                return;
            }

            Session session = plugin.getSessionManager().getSession(player);
            Location loc = block.getLocation();

            if (session.getSelectionType() == fr.skynex.worldx.region.ShapeType.POLYGON || session.getSelectionType() == fr.skynex.worldx.region.ShapeType.LASSO) {
                String shapeName = session.getSelectionType() == fr.skynex.worldx.region.ShapeType.POLYGON ? "polygonale" : "Lasso";
                if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                    session.getSelectionPoints().clear();
                    session.getSelectionPoints().add(loc);
                    player.sendMessage("§dSélection " + shapeName + " réinitialisée. Sommet 1 défini sur " +
                            "§7(" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")");
                    event.setCancelled(true);
                } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    session.getSelectionPoints().add(loc);
                    player.sendMessage("§dSommet " + session.getSelectionPoints().size() + " ajouté sur " +
                            "§7(" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")");
                    event.setCancelled(true);
                }
            } else {
                if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                    session.setPos1(loc);
                    player.sendMessage("§dPosition 1 définie sur " + 
                            "§7(" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")" +
                            "§d" + getSelectionVolumeString(session));
                    event.setCancelled(true);
                } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    session.setPos2(loc);
                    player.sendMessage("§dPosition 2 définie sur " + 
                            "§7(" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")" +
                            "§d" + getSelectionVolumeString(session));
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        plugin.getSessionManager().removeSession(uuid);
        if (plugin.getSelectionVisualizer() != null) {
            plugin.getSelectionVisualizer().hideRegion(uuid);
        }
        if (plugin.getRegionEffectsTask() != null) {
            plugin.getRegionEffectsTask().clearPlayer(uuid);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreakReforest(org.bukkit.event.block.BlockBreakEvent event) {
        if (plugin.getReforestManager() == null) return;
        org.bukkit.block.Block block = event.getBlock();
        String name = block.getType().name();
        if (name.contains("LOG") || name.contains("LEAVES") || name.contains("WOOD")) {
            plugin.getReforestManager().addBlock(block);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        // CHAT flag check
        Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(player.getLocation());
        if (region != null) {
            String val = plugin.getRegionManager().getEffectiveFlagValue(region, "chat");
            if ("deny".equalsIgnoreCase(val) && !player.isOp() && !player.hasPermission("worldx.bypass")) {
                event.setCancelled(true);
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<red>Le chat est désactivé dans cette région !"));
                return;
            }

            // REGIONAL_CHAT flag check
            String regionalChatVal = plugin.getRegionManager().getEffectiveFlagValue(region, "regional-chat");
            if ("allow".equalsIgnoreCase(regionalChatVal)) {
                event.viewers().removeIf(audience -> {
                    if (audience instanceof Player recipient) {
                        Region recipientRegion = plugin.getRegionManager().getHighestPriorityRegionOfBlock(recipient.getLocation());
                        return recipientRegion == null || !recipientRegion.getId().equalsIgnoreCase(region.getId());
                    }
                    return false;
                });
            }

            // REGION_CHAT_FORMAT flag check
            String chatFormat = plugin.getRegionManager().getEffectiveFlagValue(region, "region-chat-format");
            if (chatFormat != null && !chatFormat.trim().isEmpty()) {
                final String finalFormat = chatFormat
                        .replace("{player}", "<player>")
                        .replace("{message}", "<message>")
                        .replace("{region}", "<region>");
                        
                event.renderer((source, sourceDisplayName, message, viewer) -> {
                    net.kyori.adventure.text.minimessage.tag.resolver.TagResolver resolvers = 
                        net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.resolver(
                            net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("player", sourceDisplayName),
                            net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("message", message),
                            net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("region", region.getId())
                        );
                    return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(finalFormat, resolvers);
                });
            }
        }

        Session session = plugin.getSessionManager().getSession(player);
        if (session != null && "AWAITING_NAME".equalsIgnoreCase(session.getPaletteState())) {
            event.setCancelled(true);
            String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
            session.setPaletteState(null);

            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage("§cCréation de dégradé annulée.");
                session.setPendingPaletteItems(null);
                return;
            }

            if (input.contains(" ")) {
                player.sendMessage("§cLe nom du dégradé ne doit pas contenir d'espaces. Annulé.");
                session.setPendingPaletteItems(null);
                return;
            }

            java.util.List<org.bukkit.inventory.ItemStack> items = session.getPendingPaletteItems();
            if (items == null || items.isEmpty()) {
                player.sendMessage("§cAucun bloc en attente. Annulé.");
                return;
            }

            java.util.List<fr.skynex.worldx.edit.Palette.Entry> entries = new java.util.ArrayList<>();
            for (org.bukkit.inventory.ItemStack item : items) {
                entries.add(new fr.skynex.worldx.edit.Palette.Entry(item.getType().name(), item.getAmount()));
            }

            fr.skynex.worldx.edit.Palette palette = new fr.skynex.worldx.edit.Palette(input, entries);
            plugin.getDatabaseManager().savePalette(palette);
            session.setPendingPaletteItems(null);

            player.sendMessage("§aDégradé #" + input + " sauvegardé avec succès ! (" + entries.size() + " blocs)");
        }
    }

    private String getSelectionVolumeString(Session session) {
        if (session.hasCompleteSelection()) {
            Location p1 = session.getPos1();
            Location p2 = session.getPos2();
            
            int dx = Math.abs(p1.getBlockX() - p2.getBlockX()) + 1;
            int dy = Math.abs(p1.getBlockY() - p2.getBlockY()) + 1;
            int dz = Math.abs(p1.getBlockZ() - p2.getBlockZ()) + 1;
            long volume = (long) dx * dy * dz;
            
            return " (Volume : " + volume + " blocs)";
        }
        return "";
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player player = event.getEntity();
        Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(player.getLocation());
        if (region != null) {
            String keepInv = plugin.getRegionManager().getEffectiveFlagValue(region, "keep-inventory");
            if ("allow".equalsIgnoreCase(keepInv) || "true".equalsIgnoreCase(keepInv)) {
                event.setKeepInventory(true);
                event.setKeepLevel(true);
                event.getDrops().clear();
                event.setDroppedExp(0);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(player.getLocation());
        if (region != null) {
            String respawnLocStr = plugin.getRegionManager().getEffectiveFlagValue(region, "respawn-location");
            if (respawnLocStr != null && !respawnLocStr.trim().isEmpty()) {
                String[] parts = respawnLocStr.split(",");
                if (parts.length >= 3) {
                    try {
                        double x = Double.parseDouble(parts[0].trim());
                        double y = Double.parseDouble(parts[1].trim());
                        double z = Double.parseDouble(parts[2].trim());
                        event.setRespawnLocation(new Location(player.getWorld(), x, y, z));
                    } catch (Exception ignored) {}
                }
            }
        }
    }
}
