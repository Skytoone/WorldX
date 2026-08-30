package fr.skynex.worldx.listener;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.region.Flag;
import fr.skynex.worldx.region.Region;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Iterator;

public class ProtectionListener implements Listener {

    private final WorldX plugin;
    private final java.util.Map<java.util.UUID, java.util.Map<String, Long>> lastInteractTimes = new java.util.HashMap<>();
    private final java.util.Set<java.util.UUID> flightDisabledPlayers = new java.util.HashSet<>();
    private final java.util.Map<java.util.UUID, org.bukkit.Location> respawnLocations = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, java.util.Collection<org.bukkit.potion.PotionEffect>> deathPotionEffects = new java.util.HashMap<>();

    public ProtectionListener(WorldX plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        long start = System.nanoTime();
        try {
            Player player = event.getPlayer();
            Block block = event.getBlock();

            if (!plugin.getRegionManager().checkPermission(player, block.getLocation(), Flag.BUILD)) {
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Vous n'avez pas la permission de détruire des blocs ici."));
                event.setCancelled(true);
                Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(block.getLocation());
                if (region != null) {
                    plugin.getRegionManager().triggerIntrusionAlert(player, region, "détruire un bloc");
                }
            } else {
                updateRegionActivity(player, block.getLocation());
            }
        } finally {
            long elapsed = System.nanoTime() - start;
            if (plugin.getRegionProfiler().isActive()) {
                plugin.getRegionProfiler().record("Listener: onBlockBreak", elapsed);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        long start = System.nanoTime();
        try {
            Player player = event.getPlayer();
            Block block = event.getBlock();

            // Blocked items check in BlockPlaceEvent
            Region matchRegion = plugin.getRegionManager().getHighestPriorityRegionOfBlock(block.getLocation());
            if (matchRegion != null) {
                String blockedVal = plugin.getRegionManager().getEffectiveFlagValue(matchRegion, "blocked-items");
                if (blockedVal != null && !blockedVal.trim().isEmpty()) {
                    String matName = block.getType().name();
                    for (String blocked : blockedVal.split(",")) {
                        if (matName.equalsIgnoreCase(blocked.trim())) {
                            event.setCancelled(true);
                            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>La pose de cet objet est interdite ici !"));
                            return;
                        }
                    }
                }
            }

            if (!plugin.getRegionManager().checkPermission(player, block.getLocation(), Flag.BUILD)) {
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Vous n'avez pas la permission de poser des blocs ici."));
                event.setCancelled(true);
                Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(block.getLocation());
                if (region != null) {
                    plugin.getRegionManager().triggerIntrusionAlert(player, region, "poser un bloc");
                }
            } else {
                updateRegionActivity(player, block.getLocation());
            }
        } finally {
            long elapsed = System.nanoTime() - start;
            if (plugin.getRegionProfiler().isActive()) {
                plugin.getRegionProfiler().record("Listener: onBlockPlace", elapsed);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        long start = System.nanoTime();
        try {
            Player player = event.getPlayer();

            // Blocked items check in PlayerInteractEvent
            org.bukkit.inventory.ItemStack item = event.getItem();
            if (item != null) {
                Location checkLoc = event.getClickedBlock() != null ? event.getClickedBlock().getLocation() : player.getLocation();
                Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(checkLoc);
                if (region != null) {
                    String val = plugin.getRegionManager().getEffectiveFlagValue(region, "blocked-items");
                    if (val != null && !val.trim().isEmpty()) {
                        String matName = item.getType().name();
                        for (String blocked : val.split(",")) {
                            if (matName.equalsIgnoreCase(blocked.trim())) {
                                event.setCancelled(true);
                                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>L'utilisation de cet objet est interdite ici !"));
                                return;
                            }
                        }
                    }
                }
            }

            Block block = event.getClickedBlock();
            if (block == null) return;

            // Check if this is a wand tool interaction (handled in PlayerListener)
            String wandMatName = plugin.getConfig().getString("edit.wand-item", "WOODEN_AXE");
            if (player.getInventory().getItemInMainHand().getType().name().equals(wandMatName)) {
                return;
            }

            // Only run for the main hand to avoid duplicate clicks
            if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
                return;
            }

            // Identify action type
            if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
                boolean isContainer = block.getState() instanceof Container;
                boolean isInteractable = isInteractable(block.getType());

                if (isContainer || isInteractable) {
                    if (!plugin.getRegionManager().checkPermission(player, block.getLocation(), Flag.USE)) {
                        player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Vous n'avez pas la permission d'interagir avec cela ici."));
                        event.setCancelled(true);
                        Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(block.getLocation());
                        if (region != null) {
                            plugin.getRegionManager().triggerIntrusionAlert(player, region, "interagir avec " + block.getType().name().toLowerCase().replace("_", " "));
                            if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
                                String scriptVal = region.getFlagValue("script-open-chest");
                                if (scriptVal != null && !scriptVal.trim().isEmpty()) {
                                    fr.skynex.worldx.script.ScriptExecutor.runScript(player, region, scriptVal);
                                }
                            }
                        }
                    } else {
                        updateRegionActivity(player, block.getLocation());
                        Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(block.getLocation());
                        if (region != null) {
                            String cooldownStr = plugin.getRegionManager().getEffectiveFlagValue(region, "interact-cooldown");
                            if (cooldownStr != null && !cooldownStr.trim().isEmpty()) {
                                long cooldownMs = parseCooldownMs(cooldownStr);
                                if (cooldownMs > 0) {
                                    java.util.UUID playerUUID = player.getUniqueId();
                                    String key = region.getId() + ":" + block.getX() + "," + block.getY() + "," + block.getZ();
                                    java.util.Map<String, Long> playerCooldowns = lastInteractTimes.computeIfAbsent(playerUUID, k -> new java.util.HashMap<>());
                                    long now = System.currentTimeMillis();
                                    long lastInteract = playerCooldowns.getOrDefault(key, 0L);
                                    long elapsed = now - lastInteract;
                                    if (elapsed < cooldownMs) {
                                        long remainingSec = (cooldownMs - elapsed) / 1000L;
                                        if (remainingSec < 1) remainingSec = 1;
                                        player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Vous devez attendre encore " + remainingSec + " secondes avant d'interagir à nouveau."));
                                        event.setCancelled(true);
                                    } else {
                                        playerCooldowns.put(key, now);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            long elapsed = System.nanoTime() - start;
            if (plugin.getRegionProfiler().isActive()) {
                plugin.getRegionProfiler().record("Listener: onPlayerInteract", elapsed);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        Entity damager = event.getDamager();

        // Projectile Protection (projectile flag)
        if (damager instanceof Projectile) {
            Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(entity.getLocation());
            if (region != null) {
                String val = plugin.getRegionManager().getEffectiveFlagValue(region, "projectile");
                if ("deny".equalsIgnoreCase(val)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // Armor Stand Damage Protection
        if (entity instanceof org.bukkit.entity.ArmorStand) {
            Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(entity.getLocation());
            if (region != null) {
                String val = plugin.getRegionManager().getEffectiveFlagValue(region, "armor-stand-interact");
                if ("deny".equalsIgnoreCase(val)) {
                    if (damager instanceof Player player) {
                        if (!player.isOp() && !player.hasPermission("worldx.bypass")) {
                            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>L'interaction avec les porte-armures est interdite ici !"));
                            event.setCancelled(true);
                            return;
                        }
                    } else {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }

        // Hostile Attack Protection
        if (entity instanceof Player victim) {
            boolean isHostile = damager instanceof org.bukkit.entity.Enemy;
            if (!isHostile && damager instanceof Projectile proj && proj.getShooter() instanceof org.bukkit.entity.Enemy) {
                isHostile = true;
            }

            if (isHostile) {
                Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(victim.getLocation());
                if (region != null) {
                    String val = plugin.getRegionManager().getEffectiveFlagValue(region, "hostile-attack");
                    if ("deny".equalsIgnoreCase(val)) {
                        event.setCancelled(true);
                        return;
                    }

                    // Mob damage multiplier
                    String multStr = plugin.getRegionManager().getEffectiveFlagValue(region, "mob-damage-multiplier");
                    if (multStr != null) {
                        try {
                            double mult = Double.parseDouble(multStr.trim());
                            event.setDamage(event.getDamage() * mult);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }

        // Passive Attack Protection
        if (entity instanceof org.bukkit.entity.Animals || entity instanceof org.bukkit.entity.NPC || entity instanceof org.bukkit.entity.Ambient) {
            Player attacker = null;
            if (damager instanceof Player) {
                attacker = (Player) damager;
            } else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player) {
                attacker = (Player) proj.getShooter();
            }

            if (attacker != null) {
                Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(entity.getLocation());
                if (region != null) {
                    String val = plugin.getRegionManager().getEffectiveFlagValue(region, "passive-attack");
                    if ("deny".equalsIgnoreCase(val) && !attacker.isOp() && !attacker.hasPermission("worldx.bypass")) {
                        attacker.sendMessage(MiniMessage.miniMessage().deserialize("<red>L'attaque d'animaux pacifiques est interdite ici !"));
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }

        // 1. Explosion Damage Protection
        if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || 
            event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            
            Region match = plugin.getRegionManager().getHighestPriorityRegionOfBlock(entity.getLocation());
            if (match != null) {
                boolean isCreeper = damager instanceof org.bukkit.entity.Creeper;
                boolean isTnt = damager instanceof org.bukkit.entity.TNTPrimed || damager instanceof org.bukkit.entity.minecart.ExplosiveMinecart;

                if (isCreeper && "deny".equalsIgnoreCase(match.getFlags().get("creeper-explosion"))) {
                    event.setCancelled(true);
                    return;
                } else if (isTnt && "deny".equalsIgnoreCase(match.getFlags().get("tnt-explosion"))) {
                    event.setCancelled(true);
                    return;
                } else if (!isCreeper && !isTnt) {
                    if ("deny".equalsIgnoreCase(match.getFlags().get("creeper-explosion")) || 
                        "deny".equalsIgnoreCase(match.getFlags().get("tnt-explosion"))) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }

        // 2. PvP Protection
        if (entity instanceof Player victim) {
            Player attacker = null;
            if (damager instanceof Player) {
                attacker = (Player) damager;
            } else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player) {
                attacker = (Player) proj.getShooter();
            }

            if (attacker != null) {
                if (!plugin.getRegionManager().checkPermission(attacker, victim.getLocation(), Flag.PVP)) {
                    attacker.sendMessage(MiniMessage.miniMessage().deserialize("<red>Le PvP est désactivé dans cette zone."));
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Natural spawning checks
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL ||
            event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            
            Location loc = event.getLocation();
            if (!plugin.getRegionManager().checkPermission(null, loc, Flag.MOB_SPAWN)) {
                event.setCancelled(true);
            }
        }
    }

    // region Advanced Protections

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        boolean isCreeper = entity instanceof org.bukkit.entity.Creeper;
        boolean isTnt = event.getEntityType().name().contains("TNT") || event.getEntityType().name().contains("EXPLOSIVE");

        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block b = it.next();
            Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(b.getLocation());
            if (region != null) {
                // Global EXPLOSION flag takes priority
                if (!plugin.getRegionManager().checkPermission(null, b.getLocation(), Flag.EXPLOSION)) {
                    it.remove();
                    continue;
                }

                String blockDmgVal = plugin.getRegionManager().getEffectiveFlagValue(region, "explosion-block-damage");
                if ("deny".equalsIgnoreCase(blockDmgVal)) {
                    it.remove();
                    continue;
                }
                // Granular per-type flags
                if (isCreeper && "deny".equalsIgnoreCase(region.getFlags().get("creeper-explosion"))) {
                    it.remove();
                } else if (isTnt && "deny".equalsIgnoreCase(region.getFlags().get("tnt-explosion"))) {
                    it.remove();
                } else if (!isCreeper && !isTnt) {
                    if ("deny".equalsIgnoreCase(region.getFlags().get("creeper-explosion")) ||
                        "deny".equalsIgnoreCase(region.getFlags().get("tnt-explosion"))) {
                        it.remove();
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        Location loc = event.getBlock().getLocation();
        if (!plugin.getRegionManager().checkPermission(null, loc, Flag.FIRE_SPREAD) ||
            !plugin.getRegionManager().checkPermission(null, loc, Flag.BLOCK_BURN)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (event.getSource().getType() == Material.FIRE || event.getSource().getType() == Material.SOUL_FIRE) {
            Location loc = event.getBlock().getLocation();
            if (!plugin.getRegionManager().checkPermission(null, loc, Flag.FIRE_SPREAD) ||
                !plugin.getRegionManager().checkPermission(null, loc, Flag.BLOCK_BURN)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        Location loc = event.getBlock().getLocation();
        Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(loc);
        if (region != null) {
            String val = plugin.getRegionManager().getEffectiveFlagValue(region, "fire-ignite");
            if ("deny".equalsIgnoreCase(val)) {
                Player player = event.getPlayer();
                if (player != null) {
                    if (!player.isOp() && !player.hasPermission("worldx.bypass")) {
                        player.sendMessage(MiniMessage.miniMessage().deserialize("<red>L'allumage de feu est interdit ici !"));
                        event.setCancelled(true);
                        return;
                    }
                } else {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        if (event.getCause() == BlockIgniteEvent.IgniteCause.SPREAD || event.getCause() == BlockIgniteEvent.IgniteCause.LAVA) {
            if (!plugin.getRegionManager().checkPermission(null, loc, Flag.FIRE_SPREAD) ||
                !plugin.getRegionManager().checkPermission(null, loc, Flag.BLOCK_BURN)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (!plugin.getRegionManager().checkPermission(null, event.getBlock().getLocation(), Flag.LEAF_DECAY)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            Location dest = b.getRelative(event.getDirection()).getLocation();
            Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(dest);
            if (region != null) {
                String val = region.getFlags().get("piston");
                if (val == null) val = region.getFlags().get("piston-interact");
                if ("deny".equalsIgnoreCase(val)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(b.getLocation());
            if (region != null) {
                String val = region.getFlags().get("piston");
                if (val == null) val = region.getFlags().get("piston-interact");
                if ("deny".equalsIgnoreCase(val)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        Block from = event.getBlock();
        Block to = event.getToBlock();
        
        Region targetRegion = plugin.getRegionManager().getHighestPriorityRegionOfBlock(to.getLocation());
        if (targetRegion != null && "deny".equalsIgnoreCase(targetRegion.getFlags().get("liquid-flow"))) {
            Region sourceRegion = plugin.getRegionManager().getHighestPriorityRegionOfBlock(from.getLocation());
            if (sourceRegion == null || !sourceRegion.getId().equalsIgnoreCase(targetRegion.getId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreakLog(BlockBreakEvent event) {
        Block block = event.getBlock();
        Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(block.getLocation());
        if (region != null) {
            plugin.getDatabaseManager().logBlockChange(
                    region.getId(),
                    block.getWorld().getName(),
                    block.getX(), block.getY(), block.getZ(),
                    block.getBlockData().getAsString(),
                    Material.AIR.createBlockData().getAsString(),
                    event.getPlayer().getUniqueId()
            );
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlaceLog(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(block.getLocation());
        if (region != null) {
            plugin.getDatabaseManager().logBlockChange(
                    region.getId(),
                    block.getWorld().getName(),
                    block.getX(), block.getY(), block.getZ(),
                    event.getBlockReplacedState().getBlockData().getAsString(),
                    block.getBlockData().getAsString(),
                    event.getPlayer().getUniqueId()
            );
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCropTramplePlayer(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) {
            Block block = event.getClickedBlock();
            if (block != null && block.getType() == Material.FARMLAND) {
                Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(block.getLocation());
                if (region != null) {
                    String val = plugin.getRegionManager().getEffectiveFlagValue(region, "crop-trample");
                    if ("deny".equalsIgnoreCase(val)) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCropTrampleEntity(EntityInteractEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.FARMLAND) {
            Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(block.getLocation());
            if (region != null) {
                String val = plugin.getRegionManager().getEffectiveFlagValue(region, "crop-trample");
                if ("deny".equalsIgnoreCase(val)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemFrameRotate(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (entity instanceof org.bukkit.entity.ItemFrame || entity instanceof org.bukkit.entity.GlowItemFrame) {
            Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(entity.getLocation());
            if (region != null && "deny".equalsIgnoreCase(region.getFlags().get("item-frame-rotate"))) {
                if (!plugin.getRegionManager().checkPermission(event.getPlayer(), entity.getLocation(), Flag.BUILD)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHangingDestroy(HangingBreakByEntityEvent event) {
        Entity remover = event.getRemover();
        Player player = remover instanceof Player ? (Player) remover : null;
        
        Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(event.getEntity().getLocation());
        if (region != null && "deny".equalsIgnoreCase(region.getFlags().get("hanging-destroy"))) {
            if (player == null || !plugin.getRegionManager().checkPermission(player, event.getEntity().getLocation(), Flag.BUILD)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamageFall(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(player.getLocation());
                if (region != null) {
                    String val = plugin.getRegionManager().getEffectiveFlagValue(region, "fall-damage");
                    if ("deny".equalsIgnoreCase(val)) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (event.getFoodLevel() < player.getFoodLevel()) {
                Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(player.getLocation());
                if (region != null) {
                    String val = plugin.getRegionManager().getEffectiveFlagValue(region, "hunger");
                    if ("deny".equalsIgnoreCase(val)) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(player.getLocation());
        if (region != null) {
            String val = plugin.getRegionManager().getEffectiveFlagValue(region, "keep-inventory");
            String xpVal = plugin.getRegionManager().getEffectiveFlagValue(region, "keep-xp");
            if ("allow".equalsIgnoreCase(val)) {
                event.setKeepInventory(true);
                event.setKeepLevel(true);
                event.getDrops().clear();
                event.setDroppedExp(0);
            } else if ("allow".equalsIgnoreCase(xpVal)) {
                event.setKeepLevel(true);
                event.setDroppedExp(0);
            }

            String respawnVal = plugin.getRegionManager().getEffectiveFlagValue(region, "respawn-location");
            if (respawnVal != null && !respawnVal.trim().isEmpty()) {
                org.bukkit.Location loc = parseLocationString(respawnVal, player.getWorld());
                if (loc != null) {
                    respawnLocations.put(player.getUniqueId(), loc);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMobTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player) {
            Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(player.getLocation());
            if (region != null) {
                String val = plugin.getRegionManager().getEffectiveFlagValue(region, "mob-target");
                if ("deny".equalsIgnoreCase(val)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.isOp() || player.hasPermission("worldx.bypass")) {
            return;
        }

        String message = event.getMessage().substring(1).trim();
        if (message.isEmpty()) return;
        String cmd = message.split(" ")[0].toLowerCase();

        Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(player.getLocation());
        if (region != null) {
            // 1. Blacklist
            String blacklistStr = plugin.getRegionManager().getEffectiveFlagValue(region, "command-blacklist");
            if (blacklistStr != null && !blacklistStr.trim().isEmpty()) {
                String[] list = blacklistStr.split(",");
                for (String blacklisted : list) {
                    if (cmd.equalsIgnoreCase(blacklisted.trim())) {
                        player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Cette commande est interdite dans cette zone."));
                        event.setCancelled(true);
                        return;
                    }
                }
            }

            // 2. Whitelist
            String whitelistStr = plugin.getRegionManager().getEffectiveFlagValue(region, "command-whitelist");
            if (whitelistStr != null && !whitelistStr.trim().isEmpty()) {
                String[] list = whitelistStr.split(",");
                boolean allowed = false;
                for (String whitelisted : list) {
                    if (cmd.equalsIgnoreCase(whitelisted.trim())) {
                        allowed = true;
                        break;
                    }
                }
                if (!allowed) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Seules certaines commandes sont autorisées dans cette zone."));
                    event.setCancelled(true);
                }
            }
        }
    }

    // endregion

    private boolean isInteractable(Material type) {
        String name = type.name();
        return name.endsWith("_BUTTON") ||
               name.endsWith("_DOOR") ||
               name.endsWith("_TRAPDOOR") ||
               name.endsWith("_GATE") ||
               type == Material.LEVER ||
               type == Material.ANVIL ||
               type == Material.CHIPPED_ANVIL ||
               type == Material.DAMAGED_ANVIL ||
               type == Material.REPEATER ||
               type == Material.COMPARATOR;
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        lastInteractTimes.remove(uuid);
        flightDisabledPlayers.remove(uuid);
        respawnLocations.remove(uuid);
        deathPotionEffects.remove(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPotionSplash(org.bukkit.event.entity.PotionSplashEvent event) {
        Location loc = event.getEntity().getLocation();
        Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(loc);
        if (region != null) {
            String val = plugin.getRegionManager().getEffectiveFlagValue(region, "potion-splash");
            if ("deny".equalsIgnoreCase(val)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        if (player.isOp() || player.hasPermission("worldx.bypass")) {
            return;
        }

        java.util.UUID uuid = player.getUniqueId();
        Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(event.getTo());
        
        // Flight check
        String val = region != null ? plugin.getRegionManager().getEffectiveFlagValue(region, "allow-flight") : null;
        if ("deny".equalsIgnoreCase(val)) {
            if (player.getAllowFlight() && player.getGameMode() != org.bukkit.GameMode.CREATIVE && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                player.setFlying(false);
                player.setAllowFlight(false);
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Le vol est interdit dans cette région !"));
                flightDisabledPlayers.add(uuid);
            }
        } else {
            if (flightDisabledPlayers.contains(uuid)) {
                player.setAllowFlight(true);
                flightDisabledPlayers.remove(uuid);
                player.sendMessage(MiniMessage.miniMessage().deserialize("<green>Vous pouvez à nouveau voler."));
            }
        }

        // Elytra check
        if (player.isGliding() && region != null) {
            String elytraVal = plugin.getRegionManager().getEffectiveFlagValue(region, "allow-elytra");
            if ("deny".equalsIgnoreCase(elytraVal)) {
                player.setGliding(false);
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>L'utilisation des Élytres est interdite dans cette région !"));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityToggleGlide(org.bukkit.event.entity.EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (event.isGliding()) {
                Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(player.getLocation());
                if (region != null) {
                    String val = plugin.getRegionManager().getEffectiveFlagValue(region, "allow-elytra");
                    if ("deny".equalsIgnoreCase(val) && !player.isOp() && !player.hasPermission("worldx.bypass")) {
                        event.setCancelled(true);
                        player.sendMessage(MiniMessage.miniMessage().deserialize("<red>L'utilisation des Élytres est interdite ici !"));
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!player.isOp() && !player.hasPermission("worldx.bypass")) {
            // Check prevent-teleport source
            Region fromRegion = plugin.getRegionManager().getHighestPriorityRegionOfBlock(event.getFrom());
            if (fromRegion != null) {
                String val = plugin.getRegionManager().getEffectiveFlagValue(fromRegion, "prevent-teleport");
                if ("deny".equalsIgnoreCase(val)) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Les téléportations sont interdites depuis cette zone !"));
                    event.setCancelled(true);
                    return;
                }
            }

            // Check prevent-teleport destination
            Region toRegion = plugin.getRegionManager().getHighestPriorityRegionOfBlock(event.getTo());
            if (toRegion != null) {
                String val = plugin.getRegionManager().getEffectiveFlagValue(toRegion, "prevent-teleport");
                if ("deny".equalsIgnoreCase(val)) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Les téléportations sont interdites vers cette zone !"));
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // Check enderpearl
        if (event.getCause() == org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(event.getTo());
            if (region != null) {
                String val = plugin.getRegionManager().getEffectiveFlagValue(region, "enderpearl");
                if ("deny".equalsIgnoreCase(val) && !player.isOp() && !player.hasPermission("worldx.bypass")) {
                    event.setCancelled(true);
                    player.sendMessage(MiniMessage.miniMessage().deserialize("<red>La téléportation par perle de l'Ender est interdite dans cette zone !"));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamageCause(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(player.getLocation());
            if (region != null) {
                String val = plugin.getRegionManager().getEffectiveFlagValue(region, "damage-cause");
                if (val != null && !val.trim().isEmpty()) {
                    String causeName = event.getCause().name();
                    for (String blocked : val.split(",")) {
                        if (causeName.equalsIgnoreCase(blocked.trim())) {
                            event.setCancelled(true);
                            return;
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerDropItem(org.bukkit.event.player.PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(player.getLocation());
        if (region != null) {
            String val = plugin.getRegionManager().getEffectiveFlagValue(region, "item-drop");
            if ("deny".equalsIgnoreCase(val) && !player.isOp() && !player.hasPermission("worldx.bypass")) {
                event.setCancelled(true);
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Le dépôt d'objets au sol est interdit ici !"));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityPickupItem(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(player.getLocation());
            if (region != null) {
                String val = plugin.getRegionManager().getEffectiveFlagValue(region, "item-pickup");
                if ("deny".equalsIgnoreCase(val) && !player.isOp() && !player.hasPermission("worldx.bypass")) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockFade(org.bukkit.event.block.BlockFadeEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.ICE || block.getType() == Material.PACKED_ICE || block.getType() == Material.SNOW || block.getType() == Material.SNOW_BLOCK) {
            Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(block.getLocation());
            if (region != null) {
                String val = plugin.getRegionManager().getEffectiveFlagValue(region, "ice-melt");
                if ("deny".equalsIgnoreCase(val)) {
                    event.setCancelled(true);
                }
            }
        } else if (block.getType() == Material.FARMLAND) {
            Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(block.getLocation());
            if (region != null) {
                String val = plugin.getRegionManager().getEffectiveFlagValue(region, "farmland-dehydration");
                if ("deny".equalsIgnoreCase(val)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerExpChange(org.bukkit.event.player.PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(player.getLocation());
        if (region != null) {
            String val = plugin.getRegionManager().getEffectiveFlagValue(region, "xp-multiplier");
            if (val != null) {
                try {
                    double multiplier = Double.parseDouble(val.trim());
                    if (multiplier > 0) {
                        int originalAmount = event.getAmount();
                        int newAmount = (int) Math.round(originalAmount * multiplier);
                        event.setAmount(newAmount);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerSetSpawn(com.destroystokyo.paper.event.player.PlayerSetSpawnEvent event) {
        Location loc = event.getLocation();
        if (loc != null) {
            Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(loc);
            if (region != null) {
                String val = plugin.getRegionManager().getEffectiveFlagValue(region, "spawnpoint");
                if ("deny".equalsIgnoreCase(val)) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(MiniMessage.miniMessage().deserialize("<red>Vous ne pouvez pas définir votre point d'apparition ici !"));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityMount(org.bukkit.event.entity.EntityMountEvent event) {
        if (event.getEntity() instanceof Player player) {
            Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(event.getMount().getLocation());
            if (region != null) {
                String val = plugin.getRegionManager().getEffectiveFlagValue(region, "mount");
                if ("deny".equalsIgnoreCase(val) && !player.isOp() && !player.hasPermission("worldx.bypass")) {
                    event.setCancelled(true);
                    player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Il est interdit de monter sur des véhicules ou des montures ici !"));
                }
            }
        }
    }

    private long parseCooldownMs(String spec) {
        spec = spec.toLowerCase().trim();
        try {
            if (spec.endsWith("s")) {
                return Long.parseLong(spec.replace("s", "")) * 1000L;
            }
            if (spec.endsWith("m")) {
                return Long.parseLong(spec.replace("m", "")) * 60 * 1000L;
            }
            if (spec.endsWith("h")) {
                return Long.parseLong(spec.replace("h", "")) * 60 * 60 * 1000L;
            }
            if (spec.endsWith("d")) {
                return Long.parseLong(spec.replace("d", "")) * 24 * 60 * 60 * 1000L;
            }
            return Long.parseLong(spec) * 1000L;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerBucketEmpty(org.bukkit.event.player.PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(event.getBlock().getLocation());
        if (region != null) {
            String val = plugin.getRegionManager().getEffectiveFlagValue(region, "blocked-items");
            if (val != null && !val.trim().isEmpty()) {
                String matName = event.getBucket().name();
                for (String blocked : val.split(",")) {
                    if (matName.equalsIgnoreCase(blocked.trim())) {
                        event.setCancelled(true);
                        player.sendMessage(MiniMessage.miniMessage().deserialize("<red>L'utilisation de seaux est interdite ici !"));
                        return;
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerItemDamage(org.bukkit.event.player.PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(player.getLocation());
        if (region != null) {
            String val = plugin.getRegionManager().getEffectiveFlagValue(region, "infinite-durability");
            if ("allow".equalsIgnoreCase(val)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockGrow(org.bukkit.event.block.BlockGrowEvent event) {
        Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(event.getBlock().getLocation());
        if (region != null) {
            String val = plugin.getRegionManager().getEffectiveFlagValue(region, "block-growth");
            if ("deny".equalsIgnoreCase(val)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onStructureGrow(org.bukkit.event.world.StructureGrowEvent event) {
        Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(event.getLocation());
        if (region != null) {
            String val = plugin.getRegionManager().getEffectiveFlagValue(region, "block-growth");
            if ("deny".equalsIgnoreCase(val)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockForm(org.bukkit.event.block.BlockFormEvent event) {
        Block block = event.getBlock();
        Material newType = event.getNewState().getType();
        if (newType == Material.SNOW || newType == Material.ICE) {
            Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(block.getLocation());
            if (region != null) {
                String val = plugin.getRegionManager().getEffectiveFlagValue(region, "snow-fall");
                if ("deny".equalsIgnoreCase(val)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            return;
        }
        Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(event.getEntity().getLocation());
        if (region != null) {
            String val = plugin.getRegionManager().getEffectiveFlagValue(region, "mob-loot");
            if ("deny".equalsIgnoreCase(val)) {
                event.getDrops().clear();
                event.setDroppedExp(0);
            }

            // MONEY_LOOT_ON_KILL
            if (event.getEntity().getKiller() != null) {
                Player killer = event.getEntity().getKiller();
                String lootVal = plugin.getRegionManager().getEffectiveFlagValue(region, "money-loot-on-kill");
                if (lootVal != null && !lootVal.trim().isEmpty()) {
                    double amount = 0;
                    if (lootVal.contains("-")) {
                        String[] split = lootVal.split("-");
                        try {
                            double min = Double.parseDouble(split[0].trim());
                            double max = Double.parseDouble(split[1].trim());
                            amount = min + (java.util.concurrent.ThreadLocalRandom.current().nextDouble() * (max - min));
                        } catch (NumberFormatException ignored) {}
                    } else {
                        try {
                            amount = Double.parseDouble(lootVal.trim());
                        } catch (NumberFormatException ignored) {}
                    }

                    if (amount > 0) {
                        String multVal = plugin.getRegionManager().getEffectiveFlagValue(region, "money-multiplier");
                        if (multVal != null) {
                            try {
                                double multiplier = Double.parseDouble(multVal.trim());
                                amount *= multiplier;
                            } catch (NumberFormatException ignored) {}
                        }

                        if (fr.skynex.worldx.integration.EconomyIntegration.setupEconomy()) {
                            fr.skynex.worldx.integration.EconomyIntegration.depositPlayer(killer, amount);
                            killer.sendMessage(MiniMessage.miniMessage().deserialize(
                                String.format("<green>+%s$ gagnés dans cette région !</green>", String.format("%.2f", amount))
                            ));
                        }
                    }
                }
            }
        }
    }

    private void updateRegionActivity(Player player, Location loc) {
        Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(loc);
        if (region != null && (region.isOwner(player.getUniqueId()) || region.isMember(player.getUniqueId()))) {
            long now = System.currentTimeMillis();
            String lastActiveStr = region.getFlags().get("last-active");
            long lastActive = 0;
            if (lastActiveStr != null && !lastActiveStr.isEmpty()) {
                try {
                    lastActive = Long.parseLong(lastActiveStr);
                } catch (NumberFormatException ignored) {}
            }
            region.getFlags().put("last-active", String.valueOf(now));

            // Only save immediately to prevent database spam if last active was updated more than 15 minutes ago
            if (now - lastActive > 15 * 60 * 1000L) {
                plugin.getRegionManager().addRegion(region); // Async database save & redis publish
            }
        }
    }

    private org.bukkit.Location parseLocationString(String str, org.bukkit.World defaultWorld) {
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
                return new org.bukkit.Location(w, x, y, z, yaw, pitch);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        org.bukkit.Location loc = respawnLocations.remove(event.getPlayer().getUniqueId());
        if (loc != null) {
            event.setRespawnLocation(loc);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(player.getLocation());
            if (region != null) {
                String val = plugin.getRegionManager().getEffectiveFlagValue(region, "god-mode");
                if ("allow".equalsIgnoreCase(val)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onProjectileLaunch(org.bukkit.event.entity.ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (projectile.getShooter() instanceof Player player) {
            Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(player.getLocation());
            if (region != null) {
                String val = plugin.getRegionManager().getEffectiveFlagValue(region, "projectile");
                if ("deny".equalsIgnoreCase(val) && !player.isOp() && !player.hasPermission("worldx.bypass")) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Le tir de projectiles est interdit ici !"));
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCropHarvest(BlockBreakEvent event) {
        Block block = event.getBlock();
        org.bukkit.block.data.BlockData data = block.getBlockData();
        if (data instanceof org.bukkit.block.data.Ageable ageable) {
            if (ageable.getAge() == ageable.getMaximumAge()) {
                Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(block.getLocation());
                if (region != null && "allow".equalsIgnoreCase(plugin.getRegionManager().getEffectiveFlagValue(region, "auto-replant"))) {
                    org.bukkit.Material cropMat = block.getType();
                    fr.skynex.worldx.scheduler.FoliaScheduler.runSync(plugin, () -> {
                        if (block.getType() == org.bukkit.Material.AIR) {
                            block.setType(cropMat);
                            org.bukkit.block.data.Ageable newAgeable = (org.bukkit.block.data.Ageable) block.getBlockData();
                            newAgeable.setAge(0);
                            block.setBlockData(newAgeable);
                        }
                    });
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVillagerTrade(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (entity instanceof org.bukkit.entity.Villager || entity instanceof org.bukkit.entity.WanderingTrader) {
            Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(entity.getLocation());
            if (region != null) {
                String val = plugin.getRegionManager().getEffectiveFlagValue(region, "villager-trade");
                if ("deny".equalsIgnoreCase(val)) {
                    Player player = event.getPlayer();
                    if (!player.isOp() && !player.hasPermission("worldx.bypass")) {
                        player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Les échanges avec les villageois sont interdits ici !"));
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onArmorStandManipulate(org.bukkit.event.player.PlayerArmorStandManipulateEvent event) {
        Entity entity = event.getRightClicked();
        Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(entity.getLocation());
        if (region != null) {
            String val = plugin.getRegionManager().getEffectiveFlagValue(region, "armor-stand-interact");
            if ("deny".equalsIgnoreCase(val)) {
                Player player = event.getPlayer();
                if (!player.isOp() && !player.hasPermission("worldx.bypass")) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize("<red>L'interaction avec les porte-armures est interdite ici !"));
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTemporaryBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(block.getLocation());
        if (region != null) {
            String val = plugin.getRegionManager().getEffectiveFlagValue(region, "temporary-blocks");
            if (val != null && !"deny".equalsIgnoreCase(val)) {
                long delaySeconds = 15;
                if (!"allow".equalsIgnoreCase(val)) {
                    try {
                        if (val.trim().toLowerCase().endsWith("s")) {
                            delaySeconds = Long.parseLong(val.trim().substring(0, val.length() - 1));
                        } else {
                            delaySeconds = Long.parseLong(val.trim());
                        }
                    } catch (NumberFormatException ignored) {}
                }

                if (delaySeconds > 0) {
                    Material placedMat = block.getType();
                    fr.skynex.worldx.scheduler.FoliaScheduler.runLater(plugin, () -> {
                        if (block.getType() == placedMat) {
                            block.setType(Material.AIR);
                        }
                    }, delaySeconds * 20L);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onElytraBoost(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player.isGliding() && event.getItem() != null && event.getItem().getType() == Material.FIREWORK_ROCKET) {
            Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(player.getLocation());
            if (region != null) {
                String val = plugin.getRegionManager().getEffectiveFlagValue(region, "deny-elytra-boost");
                if ("deny".equalsIgnoreCase(val) && !player.isOp() && !player.hasPermission("worldx.bypass")) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize("<red>La propulsion en Élytres est interdite ici !"));
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEnderChestOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (event.getInventory().getType() == org.bukkit.event.inventory.InventoryType.ENDER_CHEST) {
            Player player = (Player) event.getPlayer();
            Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(player.getLocation());
            if (region != null) {
                String val = plugin.getRegionManager().getEffectiveFlagValue(region, "deny-ender-chest");
                if ("deny".equalsIgnoreCase(val) && !player.isOp() && !player.hasPermission("worldx.bypass")) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize("<red>L'ouverture des coffres de l'Ender est interdite ici !"));
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCraftItem(org.bukkit.event.inventory.CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(player.getLocation());
            if (region != null) {
                String blockedVal = plugin.getRegionManager().getEffectiveFlagValue(region, "blocked-crafting");
                if (blockedVal != null && !blockedVal.trim().isEmpty()) {
                    String craftedMat = event.getRecipe().getResult().getType().name();
                    for (String blocked : blockedVal.split(",")) {
                        if (craftedMat.equalsIgnoreCase(blocked.trim())) {
                            event.setCancelled(true);
                            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>La fabrication de cet objet est interdite ici !"));
                            return;
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerItemConsume(org.bukkit.event.player.PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (event.getItem().getType() == Material.POTION) {
            Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(player.getLocation());
            if (region != null) {
                String val = plugin.getRegionManager().getEffectiveFlagValue(region, "no-potion-drink");
                if ("deny".equalsIgnoreCase(val) && !player.isOp() && !player.hasPermission("worldx.bypass")) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Il est interdit de boire des potions ici !"));
                    event.setCancelled(true);
                }
            }
        }
    }



    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeathEffects(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player player = event.getEntity();
        Region region = plugin.getRegionManager().getHighestPriorityRegionOfBlock(player.getLocation());
        if (region != null) {
            String val = plugin.getRegionManager().getEffectiveFlagValue(region, "keep-effects-on-death");
            if ("allow".equalsIgnoreCase(val)) {
                java.util.Collection<org.bukkit.potion.PotionEffect> activeEffects = player.getActivePotionEffects();
                if (!activeEffects.isEmpty()) {
                    deathPotionEffects.put(player.getUniqueId(), activeEffects);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawnEffects(org.bukkit.event.player.PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        java.util.Collection<org.bukkit.potion.PotionEffect> savedEffects = deathPotionEffects.remove(player.getUniqueId());
        if (savedEffects != null) {
            fr.skynex.worldx.scheduler.FoliaScheduler.runLater(plugin, () -> {
                if (player.isOnline() && !player.isDead()) {
                    for (org.bukkit.potion.PotionEffect effect : savedEffects) {
                        player.addPotionEffect(effect);
                    }
                }
            }, 5L);
        }
    }
}

