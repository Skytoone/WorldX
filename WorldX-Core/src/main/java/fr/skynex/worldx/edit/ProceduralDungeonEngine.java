package fr.skynex.worldx.edit;

import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.region.Region;
import fr.skynex.worldx.region.ShapeType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;

public class ProceduralDungeonEngine {

    public static void generateDungeon(WorldX plugin, Location origin, String dungeonId, int roomCount) {
        int cx = origin.getBlockX();
        int cy = origin.getBlockY();
        int cz = origin.getBlockZ();

        List<BlockEditQueue.BlockChangeInfo> changes = new ArrayList<>();
        BlockData wallData = Material.STONE_BRICKS.createBlockData();
        BlockData airData = Material.AIR.createBlockData();

        int currentX = cx;
        for (int r = 0; r < roomCount; r++) {
            int roomSize = 7;
            for (int x = -roomSize; x <= roomSize; x++) {
                for (int y = 0; y <= 5; y++) {
                    for (int z = -roomSize; z <= roomSize; z++) {
                        int bx = currentX + x;
                        int by = cy + y;
                        int bz = cz + z;

                        if (x == -roomSize || x == roomSize || y == 0 || y == 5 || z == -roomSize || z == roomSize) {
                            changes.add(new BlockEditQueue.BlockChangeInfo(bx, by, bz, wallData));
                        } else {
                            changes.add(new BlockEditQueue.BlockChangeInfo(bx, by, bz, airData));
                        }
                    }
                }
            }

            // Create protected region for this dungeon room
            String regionId = dungeonId + "_room_" + r;
            Region region = new Region(regionId, origin.getWorld().getName(), currentX - roomSize, cy, cz - roomSize, currentX + roomSize, cy + 5, cz + roomSize, ShapeType.CUBOID);
            region.setFlagValue("pvp", "allow");
            region.setFlagValue("mob-spawn", "allow");
            plugin.getRegionManager().addRegion(region);

            currentX += roomSize * 2 + 3;
        }

        plugin.getBlockEditQueue().queueTask(new BlockEditQueue.EditTask(
                null, origin.getWorld().getName(), changes
        ));
    }
}
