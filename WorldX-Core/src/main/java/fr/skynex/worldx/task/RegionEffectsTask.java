package fr.skynex.worldx.task;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.region.Region;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RegionEffectsTask extends BukkitRunnable {

    private final WorldX plugin;
    
    // Map: PlayerUUID -> RegionID -> LastExecutionTime (ms)
    private final Map<UUID, Map<String, Long>> lastHealTimes = new HashMap<>();
    private final Map<UUID, Map<String, Long>> lastFeedTimes = new HashMap<>();
    private final Map<UUID, Map<String, Long>> lastDamageTimes = new HashMap<>();
    private final Map<UUID, Map<String, Long>> lastAnnouncementTimes = new HashMap<>();
    private final Map<UUID, org.bukkit.WeatherType> playerWeatherStates = new HashMap<>();

    public RegionEffectsTask(WorldX plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOnline() || player.isDead()) continue;

            UUID uuid = player.getUniqueId();

            // Weather dome check
            Region highestRegion = plugin.getRegionManager().getHighestPriorityRegionOfBlock(player.getLocation());
            String weatherDome = highestRegion != null ? plugin.getRegionManager().getEffectiveFlagValue(highestRegion, "dynamic-weather-dome") : null;
            if (weatherDome != null && !weatherDome.trim().isEmpty()) {
                String type = weatherDome.trim().toLowerCase();
                if (type.equals("rain") || type.equals("snow") || type.equals("sandstorm")) {
                    if (playerWeatherStates.get(uuid) != org.bukkit.WeatherType.DOWNFALL) {
                        player.setPlayerWeather(org.bukkit.WeatherType.DOWNFALL);
                        playerWeatherStates.put(uuid, org.bukkit.WeatherType.DOWNFALL);
                    }
                    if (type.equals("snow")) {
                        player.spawnParticle(org.bukkit.Particle.SNOWFLAKE, player.getLocation().add(0, 3, 0), 10, 5.0, 3.0, 5.0, 0.05);
                    } else if (type.equals("sandstorm")) {
                        player.spawnParticle(org.bukkit.Particle.DUST, player.getLocation().add(0, 2, 0), 15, 6.0, 3.0, 6.0, 0.1, new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(218, 165, 32), 1.5f));
                        player.spawnParticle(org.bukkit.Particle.WHITE_SMOKE, player.getLocation().add(0, 2, 0), 5, 6.0, 3.0, 6.0, 0.05);
                    }
                } else if (type.equals("clear")) {
                    if (playerWeatherStates.get(uuid) != org.bukkit.WeatherType.CLEAR) {
                        player.setPlayerWeather(org.bukkit.WeatherType.CLEAR);
                        playerWeatherStates.put(uuid, org.bukkit.WeatherType.CLEAR);
                    }
                } else {
                    if (playerWeatherStates.containsKey(uuid)) {
                        player.resetPlayerWeather();
                        playerWeatherStates.remove(uuid);
                    }
                }
            } else {
                if (playerWeatherStates.containsKey(uuid)) {
                    player.resetPlayerWeather();
                    playerWeatherStates.remove(uuid);
                }
            }

            List<Region> regions = plugin.getRegionManager().getRegionsAt(player.getLocation());

            for (Region region : regions) {
                // 1. Heal effect
                String healDelayStr = plugin.getRegionManager().getEffectiveFlagValue(region, "heal-delay");
                if (healDelayStr != null) {
                    try {
                        int delaySeconds = Integer.parseInt(healDelayStr.trim());
                        if (delaySeconds > 0) {
                            Map<String, Long> playerHeals = lastHealTimes.computeIfAbsent(uuid, k -> new HashMap<>());
                            long lastHeal = playerHeals.getOrDefault(region.getId(), 0L);
                            
                            if (now - lastHeal >= delaySeconds * 1000L) {
                                applyHeal(player);
                                playerHeals.put(region.getId(), now);
                            }
                        }
                    } catch (NumberFormatException ignored) {}
                }

                // 2. Feed effect
                String feedDelayStr = plugin.getRegionManager().getEffectiveFlagValue(region, "feed-delay");
                if (feedDelayStr != null) {
                    try {
                        int delaySeconds = Integer.parseInt(feedDelayStr.trim());
                        if (delaySeconds > 0) {
                            Map<String, Long> playerFeeds = lastFeedTimes.computeIfAbsent(uuid, k -> new HashMap<>());
                            long lastFeed = playerFeeds.getOrDefault(region.getId(), 0L);
                            
                            if (now - lastFeed >= delaySeconds * 1000L) {
                                applyFeed(player);
                                playerFeeds.put(region.getId(), now);
                            }
                        }
                    } catch (NumberFormatException ignored) {}
                }

                // 3. Damage effect (damage-on-entry)
                String damageOnEntryStr = plugin.getRegionManager().getEffectiveFlagValue(region, "damage-on-entry");
                if (damageOnEntryStr != null) {
                    try {
                        double damage = Double.parseDouble(damageOnEntryStr.trim());
                        if (damage > 0) {
                            Map<String, Long> playerDamages = lastDamageTimes.computeIfAbsent(uuid, k -> new HashMap<>());
                            long lastDamage = playerDamages.getOrDefault(region.getId(), 0L);

                            if (now - lastDamage >= 1000L) {
                                player.damage(damage);
                                playerDamages.put(region.getId(), now);
                            }
                        }
                    } catch (NumberFormatException ignored) {}
                }

                // 4. Potion effects
                String potEffectsStr = plugin.getRegionManager().getEffectiveFlagValue(region, "potion-effects");
                if (potEffectsStr != null) {
                    applyPotionEffects(player, potEffectsStr);
                }

                // 5. Action Bar message
                String actionBarStr = plugin.getRegionManager().getEffectiveFlagValue(region, "action-bar");
                if (actionBarStr != null && !actionBarStr.trim().isEmpty()) {
                    net.kyori.adventure.text.Component formatted = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(actionBarStr);
                    player.sendActionBar(formatted);
                }

                // 6. Ambient particles
                String particlesStr = plugin.getRegionManager().getEffectiveFlagValue(region, "particles-ambient");
                if (particlesStr != null && !particlesStr.trim().isEmpty()) {
                    try {
                        org.bukkit.Particle particle = org.bukkit.Particle.valueOf(particlesStr.trim().toUpperCase());
                        for (int i = 0; i < 4; i++) {
                            player.getWorld().spawnParticle(
                                particle,
                                player.getLocation().add(
                                    (java.util.concurrent.ThreadLocalRandom.current().nextDouble() - 0.5) * 1.5,
                                    java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 2.0,
                                    (java.util.concurrent.ThreadLocalRandom.current().nextDouble() - 0.5) * 1.5
                                ),
                                1, // count
                                0.0, 0.0, 0.0, 0.0 // speed
                            );
                        }
                    } catch (IllegalArgumentException ignored) {}
                }

                // 7. Announcement Interval
                String announceVal = plugin.getRegionManager().getEffectiveFlagValue(region, "announcement-interval");
                if (announceVal != null && announceVal.contains(":")) {
                    int lastColon = announceVal.lastIndexOf(":");
                    String message = announceVal.substring(0, lastColon);
                    String timeStr = announceVal.substring(lastColon + 1);
                    long interval = parseIntervalMillis(timeStr);
                    if (interval > 0) {
                        Map<String, Long> playerAnnounces = lastAnnouncementTimes.computeIfAbsent(uuid, k -> new HashMap<>());
                        long lastAnnounce = playerAnnounces.getOrDefault(region.getId(), 0L);
                        if (now - lastAnnounce >= interval) {
                            net.kyori.adventure.text.Component formatted = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(message);
                            player.sendMessage(formatted);
                            playerAnnounces.put(region.getId(), now);
                        }
                    }
                }
            }
        }
    }

    private void applyPotionEffects(Player player, String effectsStr) {
        if (effectsStr == null || effectsStr.trim().isEmpty()) return;
        for (String part : effectsStr.split(",")) {
            String[] split = part.split(":");
            org.bukkit.potion.PotionEffectType type = getPotionEffectType(split[0]);
            if (type != null) {
                int amplifier = 0;
                if (split.length > 1) {
                    try {
                        amplifier = Integer.parseInt(split[1].trim()) - 1;
                        if (amplifier < 0) amplifier = 0;
                    } catch (NumberFormatException ignored) {}
                }
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(type, 100, amplifier, true, false, true));
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

    private void applyHeal(Player player) {
        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
        double currentHealth = player.getHealth();

        if (currentHealth < maxHealth) {
            double nextHealth = Math.min(currentHealth + 2.0, maxHealth); // Restore 1 heart (2 HP)
            player.setHealth(nextHealth);
        }
    }

    private void applyFeed(Player player) {
        int currentFood = player.getFoodLevel();
        if (currentFood < 20) {
            int nextFood = Math.min(currentFood + 2, 20); // Restore 1 hunger point (2 levels)
            player.setFoodLevel(nextFood);
        }
    }

    public synchronized void clearPlayer(UUID playerUUID) {
        lastHealTimes.remove(playerUUID);
        lastFeedTimes.remove(playerUUID);
        lastDamageTimes.remove(playerUUID);
        lastAnnouncementTimes.remove(playerUUID);
        playerWeatherStates.remove(playerUUID);
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null && player.isOnline()) {
            player.resetPlayerWeather();
        }
    }

    private long parseIntervalMillis(String val) {
        if (val == null || val.trim().isEmpty()) return 0;
        val = val.trim().toLowerCase();
        try {
            if (val.endsWith("h")) {
                double hours = Double.parseDouble(val.substring(0, val.length() - 1));
                return (long) (hours * 3600000L);
            } else if (val.endsWith("m")) {
                double minutes = Double.parseDouble(val.substring(0, val.length() - 1));
                return (long) (minutes * 60000L);
            } else if (val.endsWith("s")) {
                double seconds = Double.parseDouble(val.substring(0, val.length() - 1));
                return (long) (seconds * 1000L);
            } else {
                double minutes = Double.parseDouble(val);
                return (long) (minutes * 60000L);
            }
        } catch (NumberFormatException ignored) {}
        return 0;
    }
}
