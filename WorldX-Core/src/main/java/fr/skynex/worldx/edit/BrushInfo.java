package fr.skynex.worldx.edit;

import org.bukkit.block.data.BlockData;

public class BrushInfo {

    public enum BrushType {
        SPHERE,
        ERASER,
        SMOOTH,
        UNDO,
        TREE,
        ROAD,
        BLEND,
        SPLINE,
        CLIPBOARD,
        EROSION,
        BIOME,
        RUINS,
        GREEBLE,
        PAINTER,
        HEIGHT,
        NOISE,
        FLATTEN
    }

    private final BrushType type;
    private final int radius;
    private final BlockData blockData;
    private final String metadata;

    public BrushInfo(BrushType type, int radius, BlockData blockData) {
        this(type, radius, blockData, null);
    }

    public BrushInfo(BrushType type, int radius, BlockData blockData, String metadata) {
        this.type = type;
        this.radius = radius;
        this.blockData = blockData;
        this.metadata = metadata;
    }

    public BrushType getType() {
        return type;
    }

    public int getRadius() {
        return radius;
    }

    public BlockData getBlockData() {
        return blockData;
    }

    public String getMetadata() {
        return metadata;
    }
}
