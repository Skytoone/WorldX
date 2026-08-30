package com.sk89q.worldedit.bukkit;

import org.bukkit.Location;
import org.bukkit.World;

public class BukkitAdapter {

    public static Location adapt(Location loc) {
        return loc;
    }

    public static World adapt(World world) {
        return world;
    }
}
