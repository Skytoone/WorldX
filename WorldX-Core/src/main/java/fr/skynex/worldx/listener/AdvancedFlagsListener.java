package fr.skynex.worldx.listener;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.region.Region;
import fr.skynex.worldx.session.Session;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AdvancedFlagsListener implements Listener {

    private final WorldX plugin;

    public AdvancedFlagsListener(WorldX plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        long start = System.nanoTime();
        try {
            onPlayerMoveInternal(event);
        } finally {
            long elapsed = System.nanoTime() - start;
            if (plugin.getRegionProfiler().isActive()) {
                plugin.getRegionProfiler().record("Listener: onPlayerMove", elapsed);
            }
        }
    }

    private void onPlayerMoveInternal(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();

        // Check if player crossed a block boundary
        if (from.getBlockX() == to.getBlockX() &&
            from.getBlockY() == to.getBlockY() &&
            from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        Session session = plugin.getSessionManager().getSession(playerUUID);

        // VOID_TELEPORT check
        if (to.getY() < to.getWorld().getMinHeight() - 2) {
            Region voidRegion = findRegionForVoidTeleport(player, to);
            if (voidRegion != null) {
                Location tpLoc = null;
                String spawnpointStr = plugin.getRegionManager().getEffectiveFlagValue(voidRegion, "spawnpoint");
                if (spawnpointStr != null && !spawnpointStr.trim().isEmpty()) {
                    tpLoc = parseLocationString(spawnpointStr, to.getWorld());
                }
                
                if (tpLoc == null) {
                    double cx = voidRegion.getMinX() + (voidRegion.getMaxX() - voidRegion.getMinX()) / 2.0;
                    double cz = voidRegion.getMinZ() + (voidRegion.getMaxZ() - voidRegion.getMinZ()) / 2.0;
                    double cy = voidRegion.getMinY() + (voidRegion.getMaxY() - voidRegion.getMinY()) / 2.0;
                    if (cy < to.getWorld().getMinHeight()) {
                        cy = to.getWorld().getSpawnLocation().getY();
                    }
                    tpLoc = new Location(to.getWorld(), cx, cy + 1, cz);
                }

                player.teleport(tpLoc);
                player.setFallDistance(0);
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<green>Vous avez été sauvé du vide !"));
                return;
            }
        }

        List<Region> regionsFrom = plugin.getRegionManager().getRegionsAt(from);
        List<Region> regionsTo = plugin.getRegionManager().getRegionsAt(to);

        // Filter entered regions (in 'to', not in 'from')
        List<Region> enteredRegions = new ArrayList<>();
        for (Region r : regionsTo) {
            if (!regionsFrom.contains(r)) {
                enteredRegions.add(r);
            }
        }

        // Filter exited regions (in 'from', not in 'to')
        List<Region> exitedRegions = new ArrayList<>();
        for (Region r : regionsFrom) {
            if (!regionsTo.contains(r)) {
                exitedRegions.add(r);
            }
        }

        // 0. Process exited regions (Check EXIT protection first!)
        for (Region region : exitedRegions) {
            String exitFlag = plugin.getRegionManager().getEffectiveFlagValue(region, "exit");
            if (exitFlag != null && exitFlag.equalsIgnoreCase("deny")) {
                if (!player.hasPermission("worldx.bypass") && !player.isOp() && !region.isMember(playerUUID)) {
                    player.sendMessage(Component.text("Vous n'êtes pas autorisé à sortir de la région " + region.getId() + ".", NamedTextColor.RED));
                    event.setTo(from);
                    return; // Stop processing further exits/entries
                }
            }
        }

        // 1. Process entered regions (Check ENTRY protection first!)
        for (Region region : enteredRegions) {
            String entryFlag = plugin.getRegionManager().getEffectiveFlagValue(region, "entry");
            if (entryFlag != null && entryFlag.equalsIgnoreCase("deny")) {
                // If player is not owner/member and doesn't bypass, block them
                if (!player.hasPermission("worldx.bypass") && !player.isOp() && !region.isMember(playerUUID)) {
                    player.sendMessage(Component.text("Vous n'êtes pas autorisé à entrer dans la région " + region.getId() + ".", NamedTextColor.RED));
                    
                    // Trigger intrusion alert
                    plugin.getRegionManager().triggerIntrusionAlert(player, region, "pénétrer");

                    // Reset to block entry
                    event.setTo(from);
                    return; // Stop processing further entries
                }
            }

            // MAX_PLAYERS check
            String maxPlayersStr = plugin.getRegionManager().getEffectiveFlagValue(region, "max-players");
            if (maxPlayersStr != null && !player.hasPermission("worldx.bypass") && !player.isOp()) {
                try {
                    int limit = Integer.parseInt(maxPlayersStr.trim());
                    if (limit > 0) {
                        int currentCount = 0;
                        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                            if (p.getUniqueId().equals(playerUUID)) continue; // exclude entering player
                            if (region.contains(p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ())
                                && p.getWorld().getName().equals(region.getWorldName())) {
                                currentCount++;
                            }
                        }
                        if (currentCount >= limit) {
                            player.sendMessage(Component.text("La région " + region.getId() + " est pleine (" + currentCount + "/" + limit + ").", NamedTextColor.RED));
                            event.setTo(from);
                            return; // Stop processing entries
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        // 2. Process greeting messages and gamemode changes for accepted entries
        for (Region region : enteredRegions) {
            // SCRIPT_ENTER trigger
            String scriptVal = plugin.getRegionManager().getEffectiveFlagValue(region, "script-enter");
            if (scriptVal != null && !scriptVal.trim().isEmpty()) {
                fr.skynex.worldx.script.ScriptExecutor.runScript(player, region, scriptVal);
            }

            // Greeting message / title
            String greeting = plugin.getRegionManager().getEffectiveFlagValue(region, "greeting");
            if (greeting != null && !greeting.trim().isEmpty()) {
                // Format chat colors using legacy '&' codes
                Component formattedTitle = LegacyComponentSerializer.legacyAmpersand().deserialize(greeting);
                // Send as Title
                player.showTitle(Title.title(formattedTitle, Component.empty(),
                        Title.Times.times(
                                java.time.Duration.ofMillis(500),
                                java.time.Duration.ofMillis(2000),
                                java.time.Duration.ofMillis(500))));

            }

            // ENTRY_TITLE flag
            String entryTitle = plugin.getRegionManager().getEffectiveFlagValue(region, "entry-title");
            if (entryTitle != null && !entryTitle.trim().isEmpty()) {
                Component formattedTitle = LegacyComponentSerializer.legacyAmpersand().deserialize(entryTitle);
                player.showTitle(Title.title(formattedTitle, Component.empty(),
                        Title.Times.times(
                                java.time.Duration.ofMillis(500),
                                java.time.Duration.ofMillis(2000),
                                java.time.Duration.ofMillis(500))));
            }

            // SPEED_BOOST flag
            String speedStr = plugin.getRegionManager().getEffectiveFlagValue(region, "speed-boost");
            if (speedStr != null) {
                try {
                    float multiplier = Float.parseFloat(speedStr.trim());
                    if (session.getOriginalWalkSpeed() == null) {
                        session.setOriginalWalkSpeed(player.getWalkSpeed());
                    }
                    player.setWalkSpeed(session.getOriginalWalkSpeed() * multiplier);
                } catch (NumberFormatException ignored) {}
            }

            // TIME_LOCK flag
            String timeStr = plugin.getRegionManager().getEffectiveFlagValue(region, "time-lock");
            if (timeStr != null) {
                try {
                    long time = Long.parseLong(timeStr.trim());
                    player.setPlayerTime(time, false);
                } catch (NumberFormatException ignored) {}
            }

            // WEATHER_LOCK flag
            String weatherStr = plugin.getRegionManager().getEffectiveFlagValue(region, "weather-lock");
            if (weatherStr != null) {
                weatherStr = weatherStr.trim().toLowerCase();
                if (weatherStr.equals("clear")) {
                    player.setPlayerWeather(org.bukkit.WeatherType.CLEAR);
                } else if (weatherStr.equals("rain") || weatherStr.equals("storm")) {
                    player.setPlayerWeather(org.bukkit.WeatherType.DOWNFALL);
                }
            }

            // GRAVITY_MODIFIER flag
            String gravityStr = plugin.getRegionManager().getEffectiveFlagValue(region, "gravity-modifier");
            if (gravityStr != null) {
                try {
                    double multiplier = Double.parseDouble(gravityStr.trim());
                    org.bukkit.attribute.AttributeInstance attr = player.getAttribute(org.bukkit.attribute.Attribute.GRAVITY);
                    if (attr != null) {
                        if (session.getOriginalGravity() == null) {
                            session.setOriginalGravity(attr.getBaseValue());
                        }
                        attr.setBaseValue(session.getOriginalGravity() * multiplier);
                    }
                } catch (NumberFormatException ignored) {}
            }

            // ABSORPTION_SHIELD flag
            String shieldStr = plugin.getRegionManager().getEffectiveFlagValue(region, "absorption-shield");
            if (shieldStr != null) {
                try {
                    double amount = Double.parseDouble(shieldStr.trim());
                    player.setAbsorptionAmount(amount);
                } catch (NumberFormatException ignored) {}
            }

            // SCHEMATIC_REGEN_ON_ENTRY flag
            String schemRegen = plugin.getRegionManager().getEffectiveFlagValue(region, "schematic-regen-on-entry");
            if ("allow".equalsIgnoreCase(schemRegen)) {
                String schemName = region.getBoundSchematic();
                if (schemName != null && !schemName.trim().isEmpty()) {
                    int count = 0;
                    for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                        if (region.contains(p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ())
                            && p.getWorld().getName().equals(region.getWorldName())) {
                            count++;
                        }
                    }
                    if (count == 1) {
                        fr.skynex.worldx.task.AutoResetTask.resetRegionAsyncStatic(plugin, region, schemName);
                    }
                }
            }

            // Gamemode changes
            String gmFlag = plugin.getRegionManager().getEffectiveFlagValue(region, "gamemode");
            if (gmFlag != null) {
                try {
                    GameMode targetGM = GameMode.valueOf(gmFlag.trim().toUpperCase());
                    // Save original gamemode before changing if not already saved
                    if (session.getOriginalGameMode() == null) {
                        session.setOriginalGameMode(player.getGameMode());
                    }
                    player.setGameMode(targetGM);
                } catch (IllegalArgumentException ignored) {}
            }

            // ENTRY_SOUND flag
            String soundStr = plugin.getRegionManager().getEffectiveFlagValue(region, "entry-sound");
            if (soundStr != null && !soundStr.trim().isEmpty()) {
                String[] split = soundStr.split(":");
                try {
                    String soundName = split[0].toLowerCase().trim();
                    float volume = split.length > 1 ? Float.parseFloat(split[1].trim()) : 1.0f;
                    float pitch = split.length > 2 ? Float.parseFloat(split[2].trim()) : 1.0f;
                    player.playSound(player.getLocation(), soundName, volume, pitch);
                } catch (Exception ignored) {}
            }

            // CONSOLE_COMMAND_ON_ENTRY flag
            String entryCmd = plugin.getRegionManager().getEffectiveFlagValue(region, "console-command-on-entry");
            if (entryCmd != null && !entryCmd.trim().isEmpty()) {
                String cmdParsed = entryCmd.replace("%player%", player.getName()).trim();
                if (cmdParsed.startsWith("/")) {
                    cmdParsed = cmdParsed.substring(1);
                }
                final String finalCmd = cmdParsed;
                fr.skynex.worldx.scheduler.FoliaScheduler.runSync(plugin, () -> {
                    org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), finalCmd);
                });
            }

            // Teleport on entry
            String tpFlag = plugin.getRegionManager().getEffectiveFlagValue(region, "teleport-on-entry");
            if (tpFlag != null && !tpFlag.trim().isEmpty()) {
                String[] parts = tpFlag.split(",");
                if (parts.length >= 3) {
                    try {
                        org.bukkit.World w = player.getWorld();
                        double tx, ty, tz;
                        float yaw = player.getLocation().getYaw();
                        float pitch = player.getLocation().getPitch();
                        if (parts.length == 3) {
                            tx = Double.parseDouble(parts[0].trim());
                            ty = Double.parseDouble(parts[1].trim());
                            tz = Double.parseDouble(parts[2].trim());
                        } else {
                            org.bukkit.World targetWorld = org.bukkit.Bukkit.getWorld(parts[0].trim());
                            int offset = 0;
                            if (targetWorld != null) {
                                w = targetWorld;
                                offset = 1;
                            }
                            tx = Double.parseDouble(parts[offset].trim());
                            ty = Double.parseDouble(parts[offset + 1].trim());
                            tz = Double.parseDouble(parts[offset + 2].trim());
                            if (parts.length > offset + 3) {
                                yaw = Float.parseFloat(parts[offset + 3].trim());
                            }
                            if (parts.length > offset + 4) {
                                pitch = Float.parseFloat(parts[offset + 4].trim());
                            }
                        }
                        Location tpLoc = new Location(w, tx, ty, tz, yaw, pitch);
                        player.teleport(tpLoc);
                        player.sendMessage(Component.text("[WorldX] Téléportation déclenchée par la région !", NamedTextColor.GOLD));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        // 3. Process exited regions
        for (Region region : exitedRegions) {
            // Farewell message / title
            String farewell = plugin.getRegionManager().getEffectiveFlagValue(region, "farewell");
            if (farewell != null && !farewell.trim().isEmpty()) {
                Component formattedFarewell = LegacyComponentSerializer.legacyAmpersand().deserialize(farewell);
                player.showTitle(Title.title(formattedFarewell, Component.empty(),
                        Title.Times.times(
                                java.time.Duration.ofMillis(500),
                                java.time.Duration.ofMillis(2000),
                                java.time.Duration.ofMillis(500))));
            }

            // Restore original gamemode
            String gmFlag = region.getFlags().get("gamemode");
            if (gmFlag != null) {
                // If the player exited a gamemode region, check if they are entering another gamemode region
                boolean enteringAnotherGM = false;
                for (Region r : regionsTo) {
                    if (plugin.getRegionManager().getEffectiveFlagValue(r, "gamemode") != null) {
                        enteringAnotherGM = true;
                        break;
                    }
                }

                if (!enteringAnotherGM && session.getOriginalGameMode() != null) {
                    player.setGameMode(session.getOriginalGameMode());
                    session.setOriginalGameMode(null); // Clear saved gamemode
                }
            }

            // Restore SPEED_BOOST
            String speedStr = region.getFlags().get("speed-boost");
            if (speedStr != null) {
                boolean enteringAnotherSpeed = false;
                for (Region r : regionsTo) {
                    if (plugin.getRegionManager().getEffectiveFlagValue(r, "speed-boost") != null) {
                        enteringAnotherSpeed = true;
                        break;
                    }
                }
                if (!enteringAnotherSpeed && session.getOriginalWalkSpeed() != null) {
                    player.setWalkSpeed(session.getOriginalWalkSpeed());
                    session.setOriginalWalkSpeed(null);
                }
            }

            // Restore GRAVITY_MODIFIER
            String gravityStr = region.getFlags().get("gravity-modifier");
            if (gravityStr != null) {
                boolean enteringAnotherGravity = false;
                for (Region r : regionsTo) {
                    if (plugin.getRegionManager().getEffectiveFlagValue(r, "gravity-modifier") != null) {
                        enteringAnotherGravity = true;
                        break;
                    }
                }
                if (!enteringAnotherGravity && session.getOriginalGravity() != null) {
                    org.bukkit.attribute.AttributeInstance attr = player.getAttribute(org.bukkit.attribute.Attribute.GRAVITY);
                    if (attr != null) {
                        attr.setBaseValue(session.getOriginalGravity());
                    }
                    session.setOriginalGravity(null);
                }
            }

            // Restore ABSORPTION_SHIELD
            String shieldStr = region.getFlags().get("absorption-shield");
            if (shieldStr != null) {
                boolean enteringAnotherShield = false;
                for (Region r : regionsTo) {
                    if (plugin.getRegionManager().getEffectiveFlagValue(r, "absorption-shield") != null) {
                        enteringAnotherShield = true;
                        break;
                    }
                }
                if (!enteringAnotherShield) {
                    player.setAbsorptionAmount(0.0);
                }
            }

            // Restore TIME_LOCK
            String timeStr = region.getFlags().get("time-lock");
            if (timeStr != null) {
                boolean enteringAnotherTime = false;
                for (Region r : regionsTo) {
                    if (plugin.getRegionManager().getEffectiveFlagValue(r, "time-lock") != null) {
                        enteringAnotherTime = true;
                        break;
                    }
                }
                if (!enteringAnotherTime) {
                    player.resetPlayerTime();
                }
            }

            // Restore WEATHER_LOCK
            String weatherStr = region.getFlags().get("weather-lock");
            if (weatherStr != null) {
                boolean enteringAnotherWeather = false;
                for (Region r : regionsTo) {
                    if (plugin.getRegionManager().getEffectiveFlagValue(r, "weather-lock") != null) {
                        enteringAnotherWeather = true;
                        break;
                    }
                }
                if (!enteringAnotherWeather) {
                    player.resetPlayerWeather();
                }
            }

            // Restore POTION_EFFECTS
            String potEffectsStr = region.getFlags().get("potion-effects");
            if (potEffectsStr != null) {
                boolean enteringAnotherPot = false;
                for (Region r : regionsTo) {
                    if (plugin.getRegionManager().getEffectiveFlagValue(r, "potion-effects") != null) {
                        enteringAnotherPot = true;
                        break;
                    }
                }
                if (!enteringAnotherPot) {
                    removePotionEffects(player, potEffectsStr);
                }
            }
        }
    }

    private void removePotionEffects(Player player, String effectsStr) {
        if (effectsStr == null || effectsStr.trim().isEmpty()) return;
        for (String part : effectsStr.split(",")) {
            String[] split = part.split(":");
            org.bukkit.potion.PotionEffectType type = getPotionEffectType(split[0]);
            if (type != null) {
                player.removePotionEffect(type);
            }
        }
    }

    private org.bukkit.potion.PotionEffectType getPotionEffectType(String name) {
        if (name == null) return null;
        String cleanName = name.trim().toLowerCase(java.util.Locale.ROOT);
        switch (cleanName) {
            case "slow":
                cleanName = "slowness";
                break;
            case "fast_digging":
                cleanName = "haste";
                break;
            case "slow_digging":
                cleanName = "mining_fatigue";
                break;
            case "increase_damage":
                cleanName = "strength";
                break;
            case "heal":
                cleanName = "instant_health";
                break;
            case "harm":
                cleanName = "instant_damage";
                break;
            case "jump":
                cleanName = "jump_boost";
                break;
            case "confusion":
                cleanName = "nausea";
                break;
            case "damage_resistance":
                cleanName = "resistance";
                break;
        }
        try {
            org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.minecraft(cleanName);
            return org.bukkit.Registry.POTION_EFFECT_TYPE.get(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Region findRegionForVoidTeleport(Player player, Location loc) {
        for (Region region : plugin.getRegionManager().getRegions().values()) {
            if (region.getWorldName().equals(loc.getWorld().getName())) {
                if (region.contains(loc.getBlockX(), region.getMinY(), loc.getBlockZ()) ||
                    region.contains(loc.getBlockX(), region.getMaxY(), loc.getBlockZ()) ||
                    (loc.getBlockX() >= region.getMinX() && loc.getBlockX() <= region.getMaxX() &&
                     loc.getBlockZ() >= region.getMinZ() && loc.getBlockZ() <= region.getMaxZ())) {
                    
                    String voidTpVal = plugin.getRegionManager().getEffectiveFlagValue(region, "void-teleport");
                    if (voidTpVal != null && !"deny".equalsIgnoreCase(voidTpVal)) {
                        return region;
                    }
                }
            }
        }
        return null;
    }

    private Location parseLocationString(String str, org.bukkit.World defaultWorld) {
        String[] parts = str.split(",");
        if (parts.length >= 3) {
            try {
                org.bukkit.World w = defaultWorld;
                double x, y, z;
                float yaw = 0.0f;
                float pitch = 0.0f;
                if (parts.length == 3) {
                    x = Double.parseDouble(parts[0].trim());
                    y = Double.parseDouble(parts[1].trim());
                    z = Double.parseDouble(parts[2].trim());
                } else {
                    org.bukkit.World targetWorld = org.bukkit.Bukkit.getWorld(parts[0].trim());
                    int offset = 0;
                    if (targetWorld != null) {
                        w = targetWorld;
                        offset = 1;
                    }
                    x = Double.parseDouble(parts[offset].trim());
                    y = Double.parseDouble(parts[offset + 1].trim());
                    z = Double.parseDouble(parts[offset + 2].trim());
                    if (parts.length > offset + 3) {
                        yaw = Float.parseFloat(parts[offset + 3].trim());
                    }
                    if (parts.length > offset + 4) {
                        pitch = Float.parseFloat(parts[offset + 4].trim());
                    }
                }
                return new Location(w, x, y, z, yaw, pitch);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}

