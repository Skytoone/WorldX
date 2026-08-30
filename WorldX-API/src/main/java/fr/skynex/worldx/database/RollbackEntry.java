package fr.skynex.worldx.database;

public class RollbackEntry {
    private final int x, y, z;
    private final String previousBlock;
    private final String newBlock;
    private final String playerUUID;
    private final long timestamp;

    public RollbackEntry(int x, int y, int z, String previousBlock, String newBlock, String playerUUID, long timestamp) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.previousBlock = previousBlock;
        this.newBlock = newBlock;
        this.playerUUID = playerUUID;
        this.timestamp = timestamp;
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

    public String getPreviousBlock() {
        return previousBlock;
    }

    public String getNewBlock() {
        return newBlock;
    }

    public String getPlayerUUID() {
        return playerUUID;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
