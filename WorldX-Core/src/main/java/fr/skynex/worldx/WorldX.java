package fr.skynex.worldx;

import fr.skynex.worldx.auction.ClaimAuctionManager;
import fr.skynex.worldx.command.*;
import fr.skynex.worldx.database.DatabaseManager;
import fr.skynex.worldx.edit.BlockEditQueue;
import fr.skynex.worldx.gui.MenuListener;
import fr.skynex.worldx.integration.MapIntegrationManager;
import fr.skynex.worldx.integration.NetworkSyncManager;
import fr.skynex.worldx.listener.*;
import fr.skynex.worldx.region.RegionManager;
import fr.skynex.worldx.session.SessionManager;
import fr.skynex.worldx.siege.SiegeManager;
import fr.skynex.worldx.task.BrushPreviewTask;
import fr.skynex.worldx.task.RegionEffectsTask;
import fr.skynex.worldx.visual.SelectionVisualizer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class WorldX extends JavaPlugin {

    private static WorldX instance;
    private DatabaseManager databaseManager;
    private RegionManager regionManager;
    private SessionManager sessionManager;
    private BlockEditQueue blockEditQueue;
    private SelectionVisualizer selectionVisualizer;
    private RegionEffectsTask regionEffectsTask;
    private fr.skynex.worldx.task.RegionExpiryTask regionExpiryTask;
    private NetworkSyncManager networkSyncManager;
    private BrushPreviewTask brushPreviewTask;
    private fr.skynex.worldx.task.PastePreviewTask pastePreviewTask;
    private fr.skynex.worldx.task.AutoResetTask autoResetTask;
    private fr.skynex.worldx.task.SchematicPreviewTask schematicPreviewTask;
    private fr.skynex.worldx.region.RegionProfiler regionProfiler;
    private fr.skynex.worldx.redis.RedisManager redisManager;
    private org.bukkit.configuration.file.FileConfiguration presetsConfig;
    private java.io.File presetsFile;
    private org.bukkit.configuration.file.FileConfiguration messagesConfig;
    private java.io.File messagesFile;
    private fr.skynex.worldx.reforest.ReforestManager reforestManager;

    private MapIntegrationManager mapIntegrationManager;
    private SiegeManager siegeManager;
    private ClaimAuctionManager claimAuctionManager;

    @Override
    public void onEnable() {
        instance = this;
        fr.skynex.worldx.api.WorldXProvider.register(new fr.skynex.worldx.api.WorldXAPI() {
            @Override
            public String getVersion() {
                return getDescription().getVersion();
            }

            @Override
            public fr.skynex.worldx.region.Region getRegion(String id) {
                return getRegionManager().getRegion(id);
            }

            @Override
            public java.util.Map<String, fr.skynex.worldx.region.Region> getRegions() {
                return getRegionManager().getRegions();
            }

            @Override
            public fr.skynex.worldx.region.Region getHighestPriorityRegion(org.bukkit.Location location) {
                return getRegionManager().getHighestPriorityRegionOfBlock(location);
            }

            @Override
            public boolean isPlayerInRegion(org.bukkit.entity.Player player, String regionId) {
                fr.skynex.worldx.region.Region r = getRegionManager().getRegion(regionId);
                return r != null && r.contains(player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ());
            }

            @Override
            public String getEffectiveFlagValue(fr.skynex.worldx.region.Region region, String flagName) {
                return getRegionManager().getEffectiveFlagValue(region, flagName);
            }

            @Override
            public void setFlag(fr.skynex.worldx.region.Region region, String flagName, String value) {
                if (region == null || flagName == null) return;
                String oldVal = region.getFlagValue(flagName);
                String newVal = (value == null || value.equalsIgnoreCase("none")) ? null : value;

                fr.skynex.worldx.event.RegionFlagChangeEvent event = new fr.skynex.worldx.event.RegionFlagChangeEvent(region, flagName, oldVal, newVal);
                org.bukkit.Bukkit.getPluginManager().callEvent(event);
                if (event.isCancelled()) return;

                region.setFlagValue(flagName, event.getNewValue());
                getRegionManager().addRegion(region);
            }

            @Override
            public boolean isUnderSiege(String regionId) {
                return siegeManager != null && siegeManager.isUnderSiege(regionId);
            }

            @Override
            public boolean isAuctionActive(String regionId) {
                return claimAuctionManager != null && claimAuctionManager.getAuction(regionId) != null;
            }

            @Override
            public int getPlayerClaimCount(java.util.UUID playerUUID) {
                return (int) getRegionManager().getRegions().values().stream()
                        .filter(r -> r.getOwners().contains(playerUUID))
                        .count();
            }
        });
        regionProfiler = new fr.skynex.worldx.region.RegionProfiler();

        // Save default configs
        saveDefaultConfig();
        loadPresetsConfig();
        loadMessagesConfig();

        // Create schematics directory if not exists
        java.io.File schematicsDir = new java.io.File(getDataFolder(), "schematics");
        if (!schematicsDir.exists()) {
            schematicsDir.mkdirs();
        }

        // Initialize Components
        try {
            // 1. Database
            databaseManager = new DatabaseManager(this);
            databaseManager.initialize();

            // 2. Region Manager
            regionManager = new RegionManager(this, databaseManager);
            regionManager.loadAllRegions();

            // 3. Session Manager
            sessionManager = new SessionManager(this);

            // 4. Block Edit Queue
            blockEditQueue = new BlockEditQueue(this);
            blockEditQueue.startProcessing();

            // 5. Selection Visualizer Task (Run every 10 ticks = 0.5s)
            selectionVisualizer = new SelectionVisualizer(this);
            fr.skynex.worldx.scheduler.FoliaScheduler.runTaskTimer(this, selectionVisualizer, 10L, 10L);

            // 6. Region Effects Task (Run every 20 ticks = 1s)
            regionEffectsTask = new RegionEffectsTask(this);
            fr.skynex.worldx.scheduler.FoliaScheduler.runTaskTimer(this, regionEffectsTask, 20L, 20L);

            // 7. Network Sync Manager
            networkSyncManager = new NetworkSyncManager(this);
            networkSyncManager.register();

            // 7b. Redis Sync Manager
            redisManager = new fr.skynex.worldx.redis.RedisManager(this);
            redisManager.initialize();

            // 7c. Map Integration Manager
            mapIntegrationManager = new MapIntegrationManager(this);
            mapIntegrationManager.init();

            // 7d. Siege Manager & Claim Auction Manager
            siegeManager = new SiegeManager(this);
            claimAuctionManager = new ClaimAuctionManager(this);

            // 7e. Region Expiry Task (Run every 6000 ticks = 5 minutes)
            regionExpiryTask = new fr.skynex.worldx.task.RegionExpiryTask(this);
            fr.skynex.worldx.scheduler.FoliaScheduler.runTaskTimer(this, regionExpiryTask, 100L, 6000L);

            // 8. Brush Preview Task (Run every 2 ticks)
            brushPreviewTask = new BrushPreviewTask(this);
            fr.skynex.worldx.scheduler.FoliaScheduler.runTaskTimer(this, brushPreviewTask, 2L, 2L);

            // 8b. Paste Preview Task (Run every 2 ticks)
            pastePreviewTask = new fr.skynex.worldx.task.PastePreviewTask(this);
            fr.skynex.worldx.scheduler.FoliaScheduler.runTaskTimer(this, pastePreviewTask, 2L, 2L);

            // 8c. Reforest Manager Task (Run every 5 ticks)
            reforestManager = new fr.skynex.worldx.reforest.ReforestManager(this);
            reforestManager.load();
            fr.skynex.worldx.scheduler.FoliaScheduler.runTaskTimer(this, reforestManager, 5L, 5L);

            // Register Listeners
            getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
            getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
            getServer().getPluginManager().registerEvents(new AdvancedFlagsListener(this), this);
            getServer().getPluginManager().registerEvents(new MenuListener(), this);
            getServer().getPluginManager().registerEvents(new BrushListener(this), this);
            getServer().getPluginManager().registerEvents(new fr.skynex.worldx.listener.SelectionHandlesListener(this), this);

            // Register Commands
            EditCommand editCmd = new EditCommand(this);
            String[] editCmdNames = {
                    "/wand", "/pos1", "/pos2", "/hpos1", "/hpos2", "/set", "/replace", "/walls", "/cyl", "/hcyl",
                    "/pyramid", "/hpyramid", "/center", "/count", "/distr", "/stack", "/move", "/fill", "/drain",
                    "/inset", "/outset", "/cut", "/copy", "/paste", "/undo", "/redo", "/expand", "/contract",
                    "/size", "/rotate", "/flip", "/gmask", "/sphere", "/hsphere", "/line"
            };
            for (String cmdName : editCmdNames) {
                org.bukkit.command.PluginCommand cmd = getCommand(cmdName);
                if (cmd != null) {
                    cmd.setExecutor(editCmd);
                    cmd.setTabCompleter(editCmd);
                }
            }

            RegionCommand regionCmd = new RegionCommand(this);
            getCommand("rg").setExecutor(regionCmd);
            getCommand("rg").setTabCompleter(regionCmd);

            SchematicCommand schemCmd = new SchematicCommand(this);
            getCommand("schem").setExecutor(schemCmd);
            getCommand("schem").setTabCompleter(schemCmd);

            BrushCommand brushCmd = new BrushCommand(this);
            getCommand("brush").setExecutor(brushCmd);
            getCommand("brush").setTabCompleter(brushCmd);

            ProfileCommand profileCmd = new ProfileCommand(this);
            getCommand("worldx").setExecutor(profileCmd);
            getCommand("worldx").setTabCompleter(profileCmd);

            SelCommand selCmd = new SelCommand(this);
            getCommand("sel").setExecutor(selCmd);
            getCommand("sel").setTabCompleter(selCmd);

            FilterCommand filterCmd = new FilterCommand(this);
            getCommand("filter").setExecutor(filterCmd);
            getCommand("filter").setTabCompleter(filterCmd);

            // Schedule Region Auto-Reset task (runs every 5 minutes)
            autoResetTask = new fr.skynex.worldx.task.AutoResetTask(this);
            fr.skynex.worldx.scheduler.FoliaScheduler.runTaskTimer(this, autoResetTask, 200L, 200L);

            // Schedule Schematic Bounding Box Preview task (runs every 5 ticks)
            schematicPreviewTask = new fr.skynex.worldx.task.SchematicPreviewTask(this);
            fr.skynex.worldx.scheduler.FoliaScheduler.runTaskTimer(this, schematicPreviewTask, 10L, 5L);

            // Register PlaceholderAPI expansion if present
            if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
                new fr.skynex.worldx.integration.WorldXPlaceholderExpansion(this).register();
                getLogger().info("WorldX PlaceholderAPI Expansion registered successfully!");
            }

            getLogger().info("WorldX has been successfully enabled!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable WorldX!", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (selectionVisualizer != null) {
            try {
                selectionVisualizer.cleanup();
                selectionVisualizer.cancel();
            } catch (IllegalStateException ignored) {
            }
        }

        if (regionEffectsTask != null) {
            try {
                regionEffectsTask.cancel();
            } catch (IllegalStateException ignored) {
            }
        }

        if (regionExpiryTask != null) {
            try {
                regionExpiryTask.cancel();
            } catch (IllegalStateException ignored) {
            }
        }

        if (brushPreviewTask != null) {
            try {
                brushPreviewTask.cleanup();
                brushPreviewTask.cancel();
            } catch (IllegalStateException ignored) {
            }
        }

        if (pastePreviewTask != null) {
            try {
                pastePreviewTask.cancel();
            } catch (IllegalStateException ignored) {
            }
        }

        if (autoResetTask != null) {
            try {
                autoResetTask.cancel();
            } catch (IllegalStateException ignored) {
            }
        }

        if (schematicPreviewTask != null) {
            try {
                schematicPreviewTask.cleanup();
                schematicPreviewTask.cancel();
            } catch (IllegalStateException ignored) {
            }
        }

        if (networkSyncManager != null) {
            networkSyncManager.unregister();
        }

        if (redisManager != null) {
            redisManager.close();
        }

        if (blockEditQueue != null) {
            blockEditQueue.stopProcessing();
        }

        if (reforestManager != null) {
            reforestManager.save();
            try {
                reforestManager.cancel();
            } catch (IllegalStateException ignored) {}
        }

        if (sessionManager != null) {
            sessionManager.clearAll();
        }

        if (databaseManager != null) {
            databaseManager.shutdown();
        }

        fr.skynex.worldx.api.WorldXProvider.unregister();

        getLogger().info("WorldX has been disabled.");
    }

    public static WorldX getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public fr.skynex.worldx.reforest.ReforestManager getReforestManager() {
        return reforestManager;
    }

    public RegionManager getRegionManager() {
        return regionManager;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public BlockEditQueue getBlockEditQueue() {
        return blockEditQueue;
    }

    public SelectionVisualizer getSelectionVisualizer() {
        return selectionVisualizer;
    }

    public RegionEffectsTask getRegionEffectsTask() {
        return regionEffectsTask;
    }

    public NetworkSyncManager getNetworkSyncManager() {
        return networkSyncManager;
    }

    public fr.skynex.worldx.region.RegionProfiler getRegionProfiler() {
        return regionProfiler;
    }

    public fr.skynex.worldx.redis.RedisManager getRedisManager() {
        return redisManager;
    }

    public MapIntegrationManager getMapIntegrationManager() {
        return mapIntegrationManager;
    }

    public SiegeManager getSiegeManager() {
        return siegeManager;
    }

    public ClaimAuctionManager getClaimAuctionManager() {
        return claimAuctionManager;
    }

    public void loadPresetsConfig() {
        presetsFile = new java.io.File(getDataFolder(), "presets.yml");
        if (!presetsFile.exists()) {
            saveResource("presets.yml", false);
        }
        presetsConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(presetsFile);
    }

    public org.bukkit.configuration.file.FileConfiguration getPresetsConfig() {
        if (presetsConfig == null) {
            loadPresetsConfig();
        }
        return presetsConfig;
    }

    public void loadMessagesConfig() {
        messagesFile = new java.io.File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        messagesConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(messagesFile);
    }

    public org.bukkit.configuration.file.FileConfiguration getMessagesConfig() {
        if (messagesConfig == null) {
            loadMessagesConfig();
        }
        return messagesConfig;
    }

    public void reloadPlugin() {
        reloadConfig();
        loadPresetsConfig();
        loadMessagesConfig();
        if (redisManager != null) {
            redisManager.close();
            redisManager.initialize();
        }
        if (mapIntegrationManager != null) {
            mapIntegrationManager.updateAllRegionsOnMaps();
        }
        getLogger().info("WorldX configuration, messages, and presets reloaded successfully!");
    }
}
