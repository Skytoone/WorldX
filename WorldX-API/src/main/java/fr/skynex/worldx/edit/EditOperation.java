package fr.skynex.worldx.edit;

import org.bukkit.block.data.BlockData;
import java.util.List;

public class EditOperation {

    private final String worldName;
    private final List<BlockChange> changes;

    public EditOperation(String worldName, List<BlockChange> changes) {
        this.worldName = worldName;
        this.changes = changes;
    }

    public String getWorldName() {
        return worldName;
    }

    public List<BlockChange> getChanges() {
        return changes;
    }

    public static class BlockChange {
        private final int x;
        private final int y;
        private final int z;
        private final BlockData previousData;
        private final BlockData newData;

        public BlockChange(int x, int y, int z, BlockData previousData, BlockData newData) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.previousData = previousData;
            this.newData = newData;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getZ() {
            return z;
        }

        public BlockData getPreviousData() {
            return previousData;
        }

        public BlockData getNewData() {
            return newData;
        }
    }
}
