package com.sk89q.worldguard;

import fr.skynex.worldx.api.WorldXProvider;
import org.bukkit.Location;

public class WorldGuard {

    private static final WorldGuard instance = new WorldGuard();

    public static WorldGuard getInstance() {
        return instance;
    }

    public Object getPlatform() {
        return this;
    }

    public Object getRegionContainer() {
        return this;
    }

    public boolean isPlayerInRegion(org.bukkit.entity.Player player, String regionId) {
        return WorldXProvider.get().isPlayerInRegion(player, regionId);
    }
}
