package fr.skynex.worldx.region;

public enum Flag {
    BUILD,       // Block breaking and placing
    PVP,         // Player combat
    USE,         // Chest access, doors, buttons, levers
    MOB_SPAWN,   // Mob spawning
    FALL_DAMAGE, // Fall damage protection
    HUNGER,      // Hunger depletion toggle
    KEEP_INVENTORY, // Keep inventory on death
    MOB_TARGET,  // Mobs targeting players toggle
    FIRE_SPREAD, // Fire and lava fire spread
    EXPLOSION,   // Block damage from explosions (creeper, TNT, etc.)
    LEAF_DECAY,  // Leaf block decay when disconnected from logs
    BLOCK_BURN,  // Fire block burning protection
    POTION_SPLASH, // Allow or deny splash potion effects
    ALLOW_FLIGHT, // Allow or deny flight for players
    ALLOW_ELYTRA, // Allow or deny elytra gliding
    SPEED_BOOST, // Speed multiplier for players
    ENDERPEARL, // Allow or deny enderpearl teleportation
    KEEP_XP, // Keep experience on death
    DAMAGE_CAUSE, // Block specific damage causes
    CROP_TRAMPLE, // Protect crops from tramping
    ITEM_DROP, // Allow or deny item dropping
    ITEM_PICKUP, // Allow or deny item picking up
    ICE_MELT, // Protect ice and snow from melting
    TIME_LOCK, // Fix player's client time
    WEATHER_LOCK, // Fix player's client weather
    ENTRY_TITLE, // Send title to player upon entering region
    XP_MULTIPLIER, // Multiplies player experience gains
    POTION_EFFECTS, // Potion effects applied in region
    SPAWNPOINT, // Allow or deny spawn points setting
    HOSTILE_ATTACK, // Allow or deny hostile mob attacks
    PASSIVE_ATTACK, // Allow or deny attacking passive animals
    MOUNT, // Allow or deny mounting entities
    EXPLOSION_BLOCK_DAMAGE, // Allow or deny block damage from explosions
    FARMLAND_DEHYDRATION, // Prevent farmland block dehydration
    ENTRY_SOUND, // Play sound upon entering region
    ACTION_BAR, // Send action-bar text to players in region
    BLOCKED_ITEMS, // Blocked items in region
    INFINITE_DURABILITY, // Durability loss toggle
    BLOCK_GROWTH, // Natural block growth toggle
    SNOW_FALL, // Natural snow fall/ice formation toggle
    MOB_LOOT, // Mob item/experience drops toggle
    CHAT, // Text chat block toggle
    CONSOLE_COMMAND_ON_ENTRY, // Execute console command on entry
    GOD_MODE, // Protect players from all damage
    RESPAWN_LOCATION, // Custom respawn coordinates
    PROJECTILE, // Control projectile launch and impact
    MONEY_MULTIPLIER, // Multiplies money gains in region
    MONEY_LOOT_ON_KILL, // Gives money on mob kills
    LIQUID_FLOW, // Toggle water/lava spread
    PISTON_INTERACT, // Prevent piston pushes/pulls across borders
    FIRE_IGNITE, // Deny manual fire ignitions
    AUTO_REPLANT, // Automatically replant mature crops when harvested
    VILLAGER_TRADE, // Deny villager trading
    ARMOR_STAND_INTERACT, // Protect armor stands
    EXIT, // Deny leaving the region
    GRAVITY_MODIFIER, // Modify player gravity
    PARTICLES_AMBIENT, // Ambient particle effects around players
    AUTO_ROLLBACK_INTERVAL, // Automatic region reset timer
    TEMPORARY_BLOCKS, // Blocks disappear after a delay
    SCHEMATIC_REGEN_ON_ENTRY, // Reset schematic when player enters empty region
    MAX_PLAYERS, // Maximum player capacity
    MOB_DAMAGE_MULTIPLIER, // Multiplies damage dealt by mobs
    DENY_ELYTRA_BOOST, // Prevent elytra firework boosting
    ABSORPTION_SHIELD, // Temporary absorption hearts inside region
    DENY_ENDER_CHEST, // Deny opening ender chests
    BLOCKED_CRAFTING, // Comma-separated list of items players cannot craft
    REGIONAL_CHAT, // Restrict chat messages to players inside same region
    ANNOUNCEMENT_INTERVAL, // Periodic chat broadcasts to region players
    VOID_TELEPORT, // Void teleportation toggle/spawnpoint
    REGION_CHAT_FORMAT, // Custom chat formatting in region
    NO_POTION_DRINK, // Prevent drinking potions
    FOG_COLOR, // Change biome to modify fog and sky color
    KEEP_EFFECTS_ON_DEATH, // Keep active potion effects on death
    PREVENT_TELEPORT, // Deny teleportation
    INTRUSION_COMMAND, // Console command executed on intrusion
    SCRIPT_GOLD_PLATE, // Actions on stepping on gold pressure plate
    SCRIPT_OPEN_CHEST, // Actions on non-member chest interaction
    SCRIPT_ENTER, // Actions on entry
    SIEGE_MODE, // Enable/disable siege rules
    SIEGE_START_TIME, // Timestamp of siege start
    NATURAL_REFORESTATION,
    DYNAMIC_WEATHER_DOME;

    public enum State {
        ALLOW,
        DENY
    }
}
