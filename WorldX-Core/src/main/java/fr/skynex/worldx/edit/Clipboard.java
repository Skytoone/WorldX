package fr.skynex.worldx.edit;

import org.bukkit.block.data.BlockData;
import java.util.List;

public class Clipboard {

    private final List<ClipboardBlock> blocks;
    private final int width;  // X size
    private final int height; // Y size
    private final int length; // Z size

    public Clipboard(List<ClipboardBlock> blocks, int width, int height, int length) {
        this.blocks = blocks;
        this.width = width;
        this.height = height;
        this.length = length;
    }

    public List<ClipboardBlock> getBlocks() {
        return blocks;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getLength() {
        return length;
    }

    public Clipboard rotate(int degrees) {
        if (degrees % 90 != 0) return this;
        int rotations = ((degrees % 360) + 360) % 360 / 90;
        if (rotations == 0) return this;

        java.util.List<ClipboardBlock> rotatedBlocks = new java.util.ArrayList<>();
        int newWidth = (rotations % 2 == 1) ? length : width;
        int newLength = (rotations % 2 == 1) ? width : length;

        for (ClipboardBlock cb : blocks) {
            int rx = cb.getRelX();
            int ry = cb.getRelY();
            int rz = cb.getRelZ();
            org.bukkit.block.data.BlockData bd = cb.getBlockData().clone();

            for (int i = 0; i < rotations; i++) {
                int temp = rx;
                rx = -rz;
                rz = temp;
                try {
                    bd.rotate(org.bukkit.block.structure.StructureRotation.CLOCKWISE_90);
                } catch (Exception ignored) {}
            }
            rotatedBlocks.add(new ClipboardBlock(rx, ry, rz, bd));
        }

        return new Clipboard(rotatedBlocks, newWidth, height, newLength);
    }

    public Clipboard flip(String direction) {
        boolean flipX = false;
        boolean flipY = false;
        boolean flipZ = false;
        org.bukkit.block.structure.Mirror mirror = org.bukkit.block.structure.Mirror.NONE;

        switch (direction.toLowerCase()) {
            case "up":
            case "down":
                flipY = true;
                break;
            case "north":
            case "south":
                flipZ = true;
                mirror = org.bukkit.block.structure.Mirror.FRONT_BACK;
                break;
            case "east":
            case "west":
                flipX = true;
                mirror = org.bukkit.block.structure.Mirror.LEFT_RIGHT;
                break;
            default:
                return this;
        }

        java.util.List<ClipboardBlock> flippedBlocks = new java.util.ArrayList<>();
        for (ClipboardBlock cb : blocks) {
            int rx = flipX ? -cb.getRelX() : cb.getRelX();
            int ry = flipY ? -cb.getRelY() : cb.getRelY();
            int rz = flipZ ? -cb.getRelZ() : cb.getRelZ();
            
            org.bukkit.block.data.BlockData bd = cb.getBlockData().clone();
            if (mirror != org.bukkit.block.structure.Mirror.NONE) {
                try {
                    bd.mirror(mirror);
                } catch (Exception ignored) {}
            }
            flippedBlocks.add(new ClipboardBlock(rx, ry, rz, bd));
        }

        return new Clipboard(flippedBlocks, width, height, length);
    }

    public static class ClipboardBlock {
        private final int relX;
        private final int relY;
        private final int relZ;
        private final BlockData blockData;

        public ClipboardBlock(int relX, int relY, int relZ, BlockData blockData) {
            this.relX = relX;
            this.relY = relY;
            this.relZ = relZ;
            this.blockData = blockData;
        }

        public int getRelX() {
            return relX;
        }

        public int getRelY() {
            return relY;
        }

        public int getRelZ() {
            return relZ;
        }

        public BlockData getBlockData() {
            return blockData;
        }
    }
}
