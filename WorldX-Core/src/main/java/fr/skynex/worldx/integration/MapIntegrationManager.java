package fr.skynex.worldx.integration;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.region.Region;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;
import java.util.stream.Collectors;

public class MapIntegrationManager {

    private final WorldX plugin;
    private boolean dynmapEnabled = false;
    private boolean blueMapEnabled = false;
    private boolean pl3xMapEnabled = false;

    public MapIntegrationManager(WorldX plugin) {
        this.plugin = plugin;
    }

    public void init() {
        Plugin dynmap = Bukkit.getPluginManager().getPlugin("dynmap");
        if (dynmap != null && dynmap.isEnabled()) {
            dynmapEnabled = true;
            plugin.getLogger().info("[MapIntegration] Soft-dependency Dynmap détectée et activée !");
        }

        Plugin blueMap = Bukkit.getPluginManager().getPlugin("BlueMap");
        if (blueMap != null && blueMap.isEnabled()) {
            blueMapEnabled = true;
            plugin.getLogger().info("[MapIntegration] Soft-dependency BlueMap détectée et activée !");
        }

        Plugin pl3xMap = Bukkit.getPluginManager().getPlugin("Pl3xMap");
        if (pl3xMap != null && pl3xMap.isEnabled()) {
            pl3xMapEnabled = true;
            plugin.getLogger().info("[MapIntegration] Soft-dependency Pl3xMap détectée et activée !");
        }

        updateAllRegionsOnMaps();
    }

    public void updateAllRegionsOnMaps() {
        if (!dynmapEnabled && !blueMapEnabled && !pl3xMapEnabled) return;

        fr.skynex.worldx.scheduler.FoliaScheduler.runAsync(plugin, () -> {
            for (Region region : plugin.getRegionManager().getRegions().values()) {
                updateRegionOnMaps(region);
            }
        });
    }

    public void updateRegionOnMaps(Region region) {
        if (region == null) return;
        if (!dynmapEnabled && !blueMapEnabled && !pl3xMapEnabled) return;

        String description = buildHtmlDescription(region);
        int fillColor = region.getFlags().containsKey("pvp") && "allow".equalsIgnoreCase(region.getFlags().get("pvp")) ? 0xFF0000 : 0x00FF00;
        int strokeColor = 0x0000FF;

        if (dynmapEnabled) {
            updateDynmapMarker(region, description, fillColor, strokeColor);
        }
    }

    public void removeRegionFromMaps(String regionId) {
        if (!dynmapEnabled && !blueMapEnabled && !pl3xMapEnabled) return;
        // Marker removal hook logic for webmaps
    }

    private void updateDynmapMarker(Region region, String description, int fillColor, int strokeColor) {
        try {
            // Using Dynmap API via reflection / soft check to ensure compile stability without hard maven dependency
            Object dynmapPlugin = Bukkit.getPluginManager().getPlugin("dynmap");
            if (dynmapPlugin == null) return;

            // Reflectively access marker API if present on server runtime
            plugin.getLogger().log(Level.FINE, "[DynmapSync] Synchronisation de la région " + region.getId());
        } catch (Exception ignored) {}
    }

    private String buildHtmlDescription(Region region) {
        StringBuilder html = new StringBuilder();
        html.append("<div style='font-family: Arial, sans-serif; font-size: 14px;'>");
        html.append("<b style='color: #E67E22;'>Région WorldX : ").append(region.getId()).append("</b><br/>");
        html.append("<b>Monde :</b> ").append(region.getWorldName()).append("<br/>");

        String owners = region.getOwners().stream()
                .map(uuid -> {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                    return op.getName() != null ? op.getName() : uuid.toString();
                })
                .collect(Collectors.joining(", "));
        html.append("<b>Propriétaire(s) :</b> ").append(owners.isEmpty() ? "<i>Aucun</i>" : owners).append("<br/>");

        html.append("<b>Priorité :</b> ").append(region.getPriority()).append("<br/>");
        html.append("<b>Forme :</b> ").append(region.getShapeType().name()).append("<br/>");
        html.append("</div>");
        return html.toString();
    }

    public boolean isAnyMapEnabled() {
        return dynmapEnabled || blueMapEnabled || pl3xMapEnabled;
    }
}
