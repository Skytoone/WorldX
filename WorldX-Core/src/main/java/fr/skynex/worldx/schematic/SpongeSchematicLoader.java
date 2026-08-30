package fr.skynex.worldx.schematic;

import fr.skynex.worldx.edit.Clipboard;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class SpongeSchematicLoader {



    @SuppressWarnings("unchecked")
    public static Clipboard load(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new FileInputStream(file)))) {
            byte rootType = in.readByte();
            if (rootType != 10) { // Compound tag
                throw new IOException("Invalid schematic file: root is not a compound tag.");
            }
            readString(in); // Read root tag name
            
            Map<String, Object> tags = readCompound(in);
            
            // Handle Sponge Schematic V2 / V3 where the actual data is wrapped in a "Schematic" compound tag
            if (tags.containsKey("Schematic") && tags.get("Schematic") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> schematicTags = (Map<String, Object>) tags.get("Schematic");
                tags = schematicTags;
            }

            // Check if this is a classic schematic (.schematic) instead of Sponge schematic
            boolean isClassic = false;
            for (String k : tags.keySet()) {
                if (k.equalsIgnoreCase("Blocks")) {
                    isClassic = true;
                    break;
                }
            }
            if (isClassic) {
                return loadClassic(tags);
            }
            
            short width = tags.containsKey("Width") ? ((Number) tags.get("Width")).shortValue() : 0;
            short height = tags.containsKey("Height") ? ((Number) tags.get("Height")).shortValue() : 0;
            short length = tags.containsKey("Length") ? ((Number) tags.get("Length")).shortValue() : 0;
            
            byte[] blockData = (byte[]) tags.get("BlockData");
            @SuppressWarnings("unchecked")
            Map<String, Object> paletteTags = (Map<String, Object>) tags.get("Palette");
            
            if (width <= 0 || height <= 0 || length <= 0 || blockData == null || paletteTags == null) {
                throw new IOException("Schematic is missing required tags (Width, Height, Length, BlockData, Palette).");
            }
            
            // Build index-to-BlockData map
            Map<Integer, BlockData> indexMap = new HashMap<>();
            for (Map.Entry<String, Object> entry : paletteTags.entrySet()) {
                String blockState = entry.getKey();
                int idx = ((Number) entry.getValue()).intValue();
                try {
                    BlockData bd = Bukkit.createBlockData(blockState);
                    indexMap.put(idx, bd);
                } catch (IllegalArgumentException e) {
                    indexMap.put(idx, Bukkit.createBlockData(org.bukkit.Material.AIR));
                }
            }
            
            // Decode VarInt block indices
            List<Clipboard.ClipboardBlock> blocksList = new ArrayList<>();
            int offset = 0;
            
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        if (offset >= blockData.length) break;
                        
                        // Read VarInt
                        int value = 0;
                        int weight = 0;
                        byte b;
                        do {
                            b = blockData[offset++];
                            value |= (b & 0x7F) << weight;
                            weight += 7;
                        } while ((b & 0x80) != 0);
                        
                        BlockData bd = indexMap.get(value);
                        if (bd == null) {
                            bd = Bukkit.createBlockData(org.bukkit.Material.AIR);
                        }
                        
                        if (!bd.getMaterial().isAir()) {
                            blocksList.add(new Clipboard.ClipboardBlock(x, y, z, bd));
                        }
                    }
                }
            }
            
            return new Clipboard(blocksList, width, height, length);
        }
    }

    private static Clipboard loadClassic(Map<String, Object> tags) throws IOException {
        short width = 0;
        short height = 0;
        short length = 0;
        byte[] blocks = null;
        byte[] data = null;
        byte[] addBlocks = null;

        for (Map.Entry<String, Object> entry : tags.entrySet()) {
            String key = entry.getKey().toLowerCase();
            if (key.equals("width")) width = ((Number) entry.getValue()).shortValue();
            else if (key.equals("height")) height = ((Number) entry.getValue()).shortValue();
            else if (key.equals("length")) length = ((Number) entry.getValue()).shortValue();
            else if (key.equals("blocks")) blocks = (byte[]) entry.getValue();
            else if (key.equals("data")) data = (byte[]) entry.getValue();
            else if (key.equals("addblocks")) addBlocks = (byte[]) entry.getValue();
        }

        Bukkit.getLogger().info("[WorldX] loadClassic: width=" + width + ", height=" + height + ", length=" + length + 
                ", blocks_len=" + (blocks != null ? blocks.length : "null") + 
                ", data_len=" + (data != null ? data.length : "null"));

        if (width <= 0 || height <= 0 || length <= 0 || blocks == null || data == null) {
            throw new IOException("Classic schematic is missing required tags (Width, Height, Length, Blocks, Data).");
        }

        List<Clipboard.ClipboardBlock> clipboardBlocks = new ArrayList<>();

        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    int index = (y * length + z) * width + x;
                    if (index >= blocks.length || index >= data.length) continue;

                    int blockId = blocks[index] & 0xFF;
                    if (addBlocks != null) {
                        int addIndex = index >> 1;
                        if (addIndex < addBlocks.length) {
                            if ((index & 1) == 0) {
                                blockId |= (addBlocks[addIndex] & 0x0F) << 8;
                            } else {
                                blockId |= ((addBlocks[addIndex] & 0xF0) >> 4) << 8;
                            }
                        }
                    }

                    byte blockDataVal = data[index];
                    org.bukkit.Material modernMat = getLegacyMaterial(blockId, blockDataVal);
                    BlockData bd = Bukkit.createBlockData(modernMat);

                    if (!bd.getMaterial().isAir()) {
                        clipboardBlocks.add(new Clipboard.ClipboardBlock(x, y, z, bd));
                    }
                }
            }
        }

        Bukkit.getLogger().info("[WorldX] loadClassic loaded blocks count: " + clipboardBlocks.size());
        return new Clipboard(clipboardBlocks, width, height, length);
    }

    private static Map<String, Object> readCompound(DataInputStream in) throws IOException {
        Map<String, Object> tags = new HashMap<>();
        while (true) {
            byte type = in.readByte();
            if (type == 0) { // End tag
                break;
            }
            String name = readString(in);
            Object value = readTagValue(in, type);
            tags.put(name, value);
        }
        return tags;
    }

    private static String readString(DataInputStream in) throws IOException {
        int len = in.readUnsignedShort();
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static Object readTagValue(DataInputStream in, byte type) throws IOException {
        switch (type) {
            case 1: return in.readByte();
            case 2: return in.readShort();
            case 3: return in.readInt();
            case 4: return in.readLong();
            case 5: return in.readFloat();
            case 6: return in.readDouble();
            case 7: // Byte array
                int len = in.readInt();
                byte[] bytes = new byte[len];
                in.readFully(bytes);
                return bytes;
            case 8: return readString(in);
            case 9: // List
                byte listType = in.readByte();
                int listLen = in.readInt();
                List<Object> list = new ArrayList<>();
                for (int i = 0; i < listLen; i++) {
                    list.add(readTagValue(in, listType));
                }
                return list;
            case 10: return readCompound(in);
            case 11: // Int Array
                int intLen = in.readInt();
                int[] intArray = new int[intLen];
                for (int i = 0; i < intLen; i++) {
                    intArray[i] = in.readInt();
                }
                return intArray;
            case 12: // Long Array
                int longLen = in.readInt();
                long[] longArray = new long[longLen];
                for (int i = 0; i < longLen; i++) {
                    longArray[i] = in.readLong();
                }
                return longArray;
            default:
                throw new IOException("Unsupported NBT tag type: " + type);
        }
    }

    private static final org.bukkit.Material[] WOOLS = {
        org.bukkit.Material.WHITE_WOOL, org.bukkit.Material.ORANGE_WOOL, org.bukkit.Material.MAGENTA_WOOL, org.bukkit.Material.LIGHT_BLUE_WOOL,
        org.bukkit.Material.YELLOW_WOOL, org.bukkit.Material.LIME_WOOL, org.bukkit.Material.PINK_WOOL, org.bukkit.Material.GRAY_WOOL,
        org.bukkit.Material.LIGHT_GRAY_WOOL, org.bukkit.Material.CYAN_WOOL, org.bukkit.Material.PURPLE_WOOL, org.bukkit.Material.BLUE_WOOL,
        org.bukkit.Material.BROWN_WOOL, org.bukkit.Material.GREEN_WOOL, org.bukkit.Material.RED_WOOL, org.bukkit.Material.BLACK_WOOL
    };
    private static final org.bukkit.Material[] CARPETS = {
        org.bukkit.Material.WHITE_CARPET, org.bukkit.Material.ORANGE_CARPET, org.bukkit.Material.MAGENTA_CARPET, org.bukkit.Material.LIGHT_BLUE_CARPET,
        org.bukkit.Material.YELLOW_CARPET, org.bukkit.Material.LIME_CARPET, org.bukkit.Material.PINK_CARPET, org.bukkit.Material.GRAY_CARPET,
        org.bukkit.Material.LIGHT_GRAY_CARPET, org.bukkit.Material.CYAN_CARPET, org.bukkit.Material.PURPLE_CARPET, org.bukkit.Material.BLUE_CARPET,
        org.bukkit.Material.BROWN_CARPET, org.bukkit.Material.GREEN_CARPET, org.bukkit.Material.RED_CARPET, org.bukkit.Material.BLACK_CARPET
    };
    private static final org.bukkit.Material[] STAINED_GLASS = {
        org.bukkit.Material.WHITE_STAINED_GLASS, org.bukkit.Material.ORANGE_STAINED_GLASS, org.bukkit.Material.MAGENTA_STAINED_GLASS, org.bukkit.Material.LIGHT_BLUE_STAINED_GLASS,
        org.bukkit.Material.YELLOW_STAINED_GLASS, org.bukkit.Material.LIME_STAINED_GLASS, org.bukkit.Material.PINK_STAINED_GLASS, org.bukkit.Material.GRAY_STAINED_GLASS,
        org.bukkit.Material.LIGHT_GRAY_STAINED_GLASS, org.bukkit.Material.CYAN_STAINED_GLASS, org.bukkit.Material.PURPLE_STAINED_GLASS, org.bukkit.Material.BLUE_STAINED_GLASS,
        org.bukkit.Material.BROWN_STAINED_GLASS, org.bukkit.Material.GREEN_STAINED_GLASS, org.bukkit.Material.RED_STAINED_GLASS, org.bukkit.Material.BLACK_STAINED_GLASS
    };
    private static final org.bukkit.Material[] STAINED_GLASS_PANES = {
        org.bukkit.Material.WHITE_STAINED_GLASS_PANE, org.bukkit.Material.ORANGE_STAINED_GLASS_PANE, org.bukkit.Material.MAGENTA_STAINED_GLASS_PANE, org.bukkit.Material.LIGHT_BLUE_STAINED_GLASS_PANE,
        org.bukkit.Material.YELLOW_STAINED_GLASS_PANE, org.bukkit.Material.LIME_STAINED_GLASS_PANE, org.bukkit.Material.PINK_STAINED_GLASS_PANE, org.bukkit.Material.GRAY_STAINED_GLASS_PANE,
        org.bukkit.Material.LIGHT_GRAY_STAINED_GLASS_PANE, org.bukkit.Material.CYAN_STAINED_GLASS_PANE, org.bukkit.Material.PURPLE_STAINED_GLASS_PANE, org.bukkit.Material.BLUE_STAINED_GLASS_PANE,
        org.bukkit.Material.BROWN_STAINED_GLASS_PANE, org.bukkit.Material.GREEN_STAINED_GLASS_PANE, org.bukkit.Material.RED_STAINED_GLASS_PANE, org.bukkit.Material.BLACK_STAINED_GLASS_PANE
    };
    private static final org.bukkit.Material[] TERRACOTTAS = {
        org.bukkit.Material.WHITE_TERRACOTTA, org.bukkit.Material.ORANGE_TERRACOTTA, org.bukkit.Material.MAGENTA_TERRACOTTA, org.bukkit.Material.LIGHT_BLUE_TERRACOTTA,
        org.bukkit.Material.YELLOW_TERRACOTTA, org.bukkit.Material.LIME_TERRACOTTA, org.bukkit.Material.PINK_TERRACOTTA, org.bukkit.Material.GRAY_TERRACOTTA,
        org.bukkit.Material.LIGHT_GRAY_TERRACOTTA, org.bukkit.Material.CYAN_TERRACOTTA, org.bukkit.Material.PURPLE_TERRACOTTA, org.bukkit.Material.BLUE_TERRACOTTA,
        org.bukkit.Material.BROWN_TERRACOTTA, org.bukkit.Material.GREEN_TERRACOTTA, org.bukkit.Material.RED_TERRACOTTA, org.bukkit.Material.BLACK_TERRACOTTA
    };
    private static final org.bukkit.Material[] CONCRETES = {
        org.bukkit.Material.WHITE_CONCRETE, org.bukkit.Material.ORANGE_CONCRETE, org.bukkit.Material.MAGENTA_CONCRETE, org.bukkit.Material.LIGHT_BLUE_CONCRETE,
        org.bukkit.Material.YELLOW_CONCRETE, org.bukkit.Material.LIME_CONCRETE, org.bukkit.Material.PINK_CONCRETE, org.bukkit.Material.GRAY_CONCRETE,
        org.bukkit.Material.LIGHT_GRAY_CONCRETE, org.bukkit.Material.CYAN_CONCRETE, org.bukkit.Material.PURPLE_CONCRETE, org.bukkit.Material.BLUE_CONCRETE,
        org.bukkit.Material.BROWN_CONCRETE, org.bukkit.Material.GREEN_CONCRETE, org.bukkit.Material.RED_CONCRETE, org.bukkit.Material.BLACK_CONCRETE
    };
    private static final org.bukkit.Material[] CONCRETE_POWDERS = {
        org.bukkit.Material.WHITE_CONCRETE_POWDER, org.bukkit.Material.ORANGE_CONCRETE_POWDER, org.bukkit.Material.MAGENTA_CONCRETE_POWDER, org.bukkit.Material.LIGHT_BLUE_CONCRETE_POWDER,
        org.bukkit.Material.YELLOW_CONCRETE_POWDER, org.bukkit.Material.LIME_CONCRETE_POWDER, org.bukkit.Material.PINK_CONCRETE_POWDER, org.bukkit.Material.GRAY_CONCRETE_POWDER,
        org.bukkit.Material.LIGHT_GRAY_CONCRETE_POWDER, org.bukkit.Material.CYAN_CONCRETE_POWDER, org.bukkit.Material.PURPLE_CONCRETE_POWDER, org.bukkit.Material.BLUE_CONCRETE_POWDER,
        org.bukkit.Material.BROWN_CONCRETE_POWDER, org.bukkit.Material.GREEN_CONCRETE_POWDER, org.bukkit.Material.RED_CONCRETE_POWDER, org.bukkit.Material.BLACK_CONCRETE_POWDER
    };

    private static org.bukkit.Material getLegacyMaterial(int id, byte data) {
        int meta = data & 0xF;
        switch (id) {
            case 0: return org.bukkit.Material.AIR;
            case 1: 
                if (meta == 1) return org.bukkit.Material.GRANITE;
                if (meta == 2) return org.bukkit.Material.POLISHED_GRANITE;
                if (meta == 3) return org.bukkit.Material.DIORITE;
                if (meta == 4) return org.bukkit.Material.POLISHED_DIORITE;
                if (meta == 5) return org.bukkit.Material.ANDESITE;
                if (meta == 6) return org.bukkit.Material.POLISHED_ANDESITE;
                return org.bukkit.Material.STONE;
            case 2: return org.bukkit.Material.GRASS_BLOCK;
            case 3: 
                if (meta == 1) return org.bukkit.Material.COARSE_DIRT;
                if (meta == 2) return org.bukkit.Material.PODZOL;
                return org.bukkit.Material.DIRT;
            case 4: return org.bukkit.Material.COBBLESTONE;
            case 5: 
                if (meta == 1) return org.bukkit.Material.SPRUCE_PLANKS;
                if (meta == 2) return org.bukkit.Material.BIRCH_PLANKS;
                if (meta == 3) return org.bukkit.Material.JUNGLE_PLANKS;
                if (meta == 4) return org.bukkit.Material.ACACIA_PLANKS;
                if (meta == 5) return org.bukkit.Material.DARK_OAK_PLANKS;
                return org.bukkit.Material.OAK_PLANKS;
            case 6: 
                if (meta == 1) return org.bukkit.Material.SPRUCE_SAPLING;
                if (meta == 2) return org.bukkit.Material.BIRCH_SAPLING;
                if (meta == 3) return org.bukkit.Material.JUNGLE_SAPLING;
                if (meta == 4) return org.bukkit.Material.ACACIA_SAPLING;
                if (meta == 5) return org.bukkit.Material.DARK_OAK_SAPLING;
                return org.bukkit.Material.OAK_SAPLING;
            case 7: return org.bukkit.Material.BEDROCK;
            case 8: case 9: return org.bukkit.Material.WATER;
            case 10: case 11: return org.bukkit.Material.LAVA;
            case 12: 
                if (meta == 1) return org.bukkit.Material.RED_SAND;
                return org.bukkit.Material.SAND;
            case 13: return org.bukkit.Material.GRAVEL;
            case 14: return org.bukkit.Material.GOLD_ORE;
            case 15: return org.bukkit.Material.IRON_ORE;
            case 16: return org.bukkit.Material.COAL_ORE;
            case 17: 
                int type17 = meta & 3;
                if (type17 == 1) return org.bukkit.Material.SPRUCE_LOG;
                if (type17 == 2) return org.bukkit.Material.BIRCH_LOG;
                if (type17 == 3) return org.bukkit.Material.JUNGLE_LOG;
                return org.bukkit.Material.OAK_LOG;
            case 18: 
                int type18 = meta & 3;
                if (type18 == 1) return org.bukkit.Material.SPRUCE_LEAVES;
                if (type18 == 2) return org.bukkit.Material.BIRCH_LEAVES;
                if (type18 == 3) return org.bukkit.Material.JUNGLE_LEAVES;
                return org.bukkit.Material.OAK_LEAVES;
            case 19: 
                if (meta == 1) return org.bukkit.Material.WET_SPONGE;
                return org.bukkit.Material.SPONGE;
            case 20: return org.bukkit.Material.GLASS;
            case 21: return org.bukkit.Material.LAPIS_ORE;
            case 22: return org.bukkit.Material.LAPIS_BLOCK;
            case 23: return org.bukkit.Material.DISPENSER;
            case 24: 
                if (meta == 1) return org.bukkit.Material.CHISELED_SANDSTONE;
                if (meta == 2) return org.bukkit.Material.CUT_SANDSTONE;
                return org.bukkit.Material.SANDSTONE;
            case 25: return org.bukkit.Material.NOTE_BLOCK;
            case 26: return org.bukkit.Material.RED_BED;
            case 27: return org.bukkit.Material.POWERED_RAIL;
            case 28: return org.bukkit.Material.DETECTOR_RAIL;
            case 29: return org.bukkit.Material.STICKY_PISTON;
            case 30: return org.bukkit.Material.COBWEB;
            case 31: 
                if (meta == 0) return org.bukkit.Material.DEAD_BUSH;
                if (meta == 2) return org.bukkit.Material.FERN;
                return org.bukkit.Material.SHORT_GRASS;
            case 32: return org.bukkit.Material.DEAD_BUSH;
            case 33: return org.bukkit.Material.PISTON;
            case 34: return org.bukkit.Material.PISTON_HEAD;
            case 35: return WOOLS[meta];
            case 37: return org.bukkit.Material.DANDELION;
            case 38: 
                if (meta == 1) return org.bukkit.Material.BLUE_ORCHID;
                if (meta == 2) return org.bukkit.Material.ALLIUM;
                if (meta == 3) return org.bukkit.Material.AZURE_BLUET;
                if (meta == 4) return org.bukkit.Material.RED_TULIP;
                if (meta == 5) return org.bukkit.Material.ORANGE_TULIP;
                if (meta == 6) return org.bukkit.Material.WHITE_TULIP;
                if (meta == 7) return org.bukkit.Material.PINK_TULIP;
                if (meta == 8) return org.bukkit.Material.OXEYE_DAISY;
                return org.bukkit.Material.POPPY;
            case 39: return org.bukkit.Material.BROWN_MUSHROOM;
            case 40: return org.bukkit.Material.RED_MUSHROOM;
            case 41: return org.bukkit.Material.GOLD_BLOCK;
            case 42: return org.bukkit.Material.IRON_BLOCK;
            case 43: case 44: 
                if (meta == 1) return org.bukkit.Material.SANDSTONE_SLAB;
                if (meta == 3) return org.bukkit.Material.COBBLESTONE_SLAB;
                if (meta == 4) return org.bukkit.Material.BRICK_SLAB;
                if (meta == 5) return org.bukkit.Material.STONE_BRICK_SLAB;
                if (meta == 6) return org.bukkit.Material.NETHER_BRICK_SLAB;
                if (meta == 7) return org.bukkit.Material.QUARTZ_SLAB;
                return org.bukkit.Material.STONE_SLAB;
            case 45: return org.bukkit.Material.BRICKS;
            case 46: return org.bukkit.Material.TNT;
            case 47: return org.bukkit.Material.BOOKSHELF;
            case 48: return org.bukkit.Material.MOSSY_COBBLESTONE;
            case 49: return org.bukkit.Material.OBSIDIAN;
            case 50: return org.bukkit.Material.TORCH;
            case 51: return org.bukkit.Material.FIRE;
            case 52: return org.bukkit.Material.SPAWNER;
            case 53: return org.bukkit.Material.OAK_STAIRS;
            case 54: return org.bukkit.Material.CHEST;
            case 55: return org.bukkit.Material.REDSTONE_WIRE;
            case 56: return org.bukkit.Material.DIAMOND_ORE;
            case 57: return org.bukkit.Material.DIAMOND_BLOCK;
            case 58: return org.bukkit.Material.CRAFTING_TABLE;
            case 59: return org.bukkit.Material.WHEAT;
            case 60: return org.bukkit.Material.FARMLAND;
            case 61: case 62: return org.bukkit.Material.FURNACE;
            case 63: return org.bukkit.Material.OAK_SIGN;
            case 64: return org.bukkit.Material.OAK_DOOR;
            case 65: return org.bukkit.Material.LADDER;
            case 66: return org.bukkit.Material.RAIL;
            case 67: return org.bukkit.Material.COBBLESTONE_STAIRS;
            case 68: return org.bukkit.Material.OAK_WALL_SIGN;
            case 69: return org.bukkit.Material.LEVER;
            case 70: return org.bukkit.Material.STONE_PRESSURE_PLATE;
            case 71: return org.bukkit.Material.IRON_DOOR;
            case 72: return org.bukkit.Material.OAK_PRESSURE_PLATE;
            case 73: case 74: return org.bukkit.Material.REDSTONE_ORE;
            case 75: case 76: return org.bukkit.Material.REDSTONE_TORCH;
            case 77: return org.bukkit.Material.STONE_BUTTON;
            case 78: return org.bukkit.Material.SNOW;
            case 79: return org.bukkit.Material.ICE;
            case 80: return org.bukkit.Material.SNOW_BLOCK;
            case 81: return org.bukkit.Material.CACTUS;
            case 82: return org.bukkit.Material.CLAY;
            case 83: return org.bukkit.Material.SUGAR_CANE;
            case 84: return org.bukkit.Material.JUKEBOX;
            case 85: return org.bukkit.Material.OAK_FENCE;
            case 86: return org.bukkit.Material.PUMPKIN;
            case 87: return org.bukkit.Material.NETHERRACK;
            case 88: return org.bukkit.Material.SOUL_SAND;
            case 89: return org.bukkit.Material.GLOWSTONE;
            case 90: return org.bukkit.Material.NETHER_PORTAL;
            case 91: return org.bukkit.Material.JACK_O_LANTERN;
            case 92: return org.bukkit.Material.CAKE;
            case 93: case 94: return org.bukkit.Material.REPEATER;
            case 95: return STAINED_GLASS[meta];
            case 96: return org.bukkit.Material.OAK_TRAPDOOR;
            case 97: 
                if (meta == 1) return org.bukkit.Material.INFESTED_COBBLESTONE;
                if (meta == 2) return org.bukkit.Material.INFESTED_STONE_BRICKS;
                if (meta == 3) return org.bukkit.Material.INFESTED_MOSSY_STONE_BRICKS;
                if (meta == 4) return org.bukkit.Material.INFESTED_CRACKED_STONE_BRICKS;
                if (meta == 5) return org.bukkit.Material.INFESTED_CHISELED_STONE_BRICKS;
                return org.bukkit.Material.INFESTED_STONE;
            case 98: 
                if (meta == 1) return org.bukkit.Material.MOSSY_STONE_BRICKS;
                if (meta == 2) return org.bukkit.Material.CRACKED_STONE_BRICKS;
                if (meta == 3) return org.bukkit.Material.CHISELED_STONE_BRICKS;
                return org.bukkit.Material.STONE_BRICKS;
            case 99: return org.bukkit.Material.BROWN_MUSHROOM_BLOCK;
            case 100: return org.bukkit.Material.RED_MUSHROOM_BLOCK;
            case 101: return org.bukkit.Material.IRON_BARS;
            case 102: return org.bukkit.Material.GLASS_PANE;
            case 103: return org.bukkit.Material.MELON;
            case 104: return org.bukkit.Material.PUMPKIN_STEM;
            case 105: return org.bukkit.Material.MELON_STEM;
            case 106: return org.bukkit.Material.VINE;
            case 107: return org.bukkit.Material.OAK_FENCE_GATE;
            case 108: return org.bukkit.Material.BRICK_STAIRS;
            case 109: return org.bukkit.Material.STONE_BRICK_STAIRS;
            case 110: return org.bukkit.Material.MYCELIUM;
            case 111: return org.bukkit.Material.LILY_PAD;
            case 112: return org.bukkit.Material.NETHER_BRICKS;
            case 113: return org.bukkit.Material.NETHER_BRICK_FENCE;
            case 114: return org.bukkit.Material.NETHER_BRICK_STAIRS;
            case 115: return org.bukkit.Material.NETHER_WART;
            case 116: return org.bukkit.Material.ENCHANTING_TABLE;
            case 117: return org.bukkit.Material.BREWING_STAND;
            case 118: return org.bukkit.Material.CAULDRON;
            case 119: return org.bukkit.Material.END_PORTAL;
            case 120: return org.bukkit.Material.END_PORTAL_FRAME;
            case 121: return org.bukkit.Material.END_STONE;
            case 122: return org.bukkit.Material.DRAGON_EGG;
            case 123: case 124: return org.bukkit.Material.REDSTONE_LAMP;
            case 125: case 126: 
                if (meta == 1) return org.bukkit.Material.SPRUCE_SLAB;
                if (meta == 2) return org.bukkit.Material.BIRCH_SLAB;
                if (meta == 3) return org.bukkit.Material.JUNGLE_SLAB;
                if (meta == 4) return org.bukkit.Material.ACACIA_SLAB;
                if (meta == 5) return org.bukkit.Material.DARK_OAK_SLAB;
                return org.bukkit.Material.OAK_SLAB;
            case 127: return org.bukkit.Material.COCOA;
            case 128: return org.bukkit.Material.SANDSTONE_STAIRS;
            case 129: return org.bukkit.Material.EMERALD_ORE;
            case 130: return org.bukkit.Material.ENDER_CHEST;
            case 131: return org.bukkit.Material.TRIPWIRE_HOOK;
            case 132: return org.bukkit.Material.TRIPWIRE;
            case 133: return org.bukkit.Material.EMERALD_BLOCK;
            case 134: return org.bukkit.Material.SPRUCE_STAIRS;
            case 135: return org.bukkit.Material.BIRCH_STAIRS;
            case 136: return org.bukkit.Material.JUNGLE_STAIRS;
            case 137: return org.bukkit.Material.COMMAND_BLOCK;
            case 138: return org.bukkit.Material.BEACON;
            case 139: return org.bukkit.Material.COBBLESTONE_WALL;
            case 140: return org.bukkit.Material.FLOWER_POT;
            case 141: return org.bukkit.Material.CARROTS;
            case 142: return org.bukkit.Material.POTATOES;
            case 143: return org.bukkit.Material.OAK_BUTTON;
            case 144: return org.bukkit.Material.SKELETON_SKULL;
            case 145: return org.bukkit.Material.ANVIL;
            case 146: return org.bukkit.Material.TRAPPED_CHEST;
            case 147: return org.bukkit.Material.LIGHT_WEIGHTED_PRESSURE_PLATE;
            case 148: return org.bukkit.Material.HEAVY_WEIGHTED_PRESSURE_PLATE;
            case 149: case 150: return org.bukkit.Material.COMPARATOR;
            case 151: return org.bukkit.Material.DAYLIGHT_DETECTOR;
            case 152: return org.bukkit.Material.REDSTONE_BLOCK;
            case 153: return org.bukkit.Material.NETHER_QUARTZ_ORE;
            case 154: return org.bukkit.Material.HOPPER;
            case 155: 
                if (meta == 1) return org.bukkit.Material.CHISELED_QUARTZ_BLOCK;
                if (meta == 2) return org.bukkit.Material.QUARTZ_PILLAR;
                return org.bukkit.Material.QUARTZ_BLOCK;
            case 156: return org.bukkit.Material.QUARTZ_STAIRS;
            case 157: return org.bukkit.Material.ACTIVATOR_RAIL;
            case 158: return org.bukkit.Material.DROPPER;
            case 159: return TERRACOTTAS[meta];
            case 160: return STAINED_GLASS_PANES[meta];
            case 161: 
                int type161 = meta & 3;
                if (type161 == 1) return org.bukkit.Material.DARK_OAK_LEAVES;
                return org.bukkit.Material.ACACIA_LEAVES;
            case 162: 
                int type162 = meta & 3;
                if (type162 == 1) return org.bukkit.Material.DARK_OAK_LOG;
                return org.bukkit.Material.ACACIA_LOG;
            case 163: return org.bukkit.Material.ACACIA_STAIRS;
            case 164: return org.bukkit.Material.DARK_OAK_STAIRS;
            case 165: return org.bukkit.Material.SLIME_BLOCK;
            case 166: return org.bukkit.Material.BARRIER;
            case 167: return org.bukkit.Material.IRON_TRAPDOOR;
            case 168: 
                if (meta == 1) return org.bukkit.Material.PRISMARINE_BRICKS;
                if (meta == 2) return org.bukkit.Material.DARK_PRISMARINE;
                return org.bukkit.Material.PRISMARINE;
            case 169: return org.bukkit.Material.SEA_LANTERN;
            case 170: return org.bukkit.Material.HAY_BLOCK;
            case 171: return CARPETS[meta];
            case 172: return org.bukkit.Material.TERRACOTTA;
            case 173: return org.bukkit.Material.COAL_BLOCK;
            case 174: return org.bukkit.Material.PACKED_ICE;
            case 175: 
                if (meta == 1) return org.bukkit.Material.LILAC;
                if (meta == 2) return org.bukkit.Material.TALL_GRASS;
                if (meta == 3) return org.bukkit.Material.LARGE_FERN;
                if (meta == 4) return org.bukkit.Material.ROSE_BUSH;
                if (meta == 5) return org.bukkit.Material.PEONY;
                return org.bukkit.Material.SUNFLOWER;
            case 176: return org.bukkit.Material.WHITE_BANNER;
            case 177: return org.bukkit.Material.WHITE_WALL_BANNER;
            case 178: return org.bukkit.Material.DAYLIGHT_DETECTOR;
            case 179: 
                if (meta == 1) return org.bukkit.Material.CHISELED_RED_SANDSTONE;
                if (meta == 2) return org.bukkit.Material.CUT_RED_SANDSTONE;
                return org.bukkit.Material.RED_SANDSTONE;
            case 180: return org.bukkit.Material.RED_SANDSTONE_STAIRS;
            case 181: case 182: return org.bukkit.Material.RED_SANDSTONE_SLAB;
            case 183: return org.bukkit.Material.SPRUCE_FENCE_GATE;
            case 184: return org.bukkit.Material.BIRCH_FENCE_GATE;
            case 185: return org.bukkit.Material.JUNGLE_FENCE_GATE;
            case 186: return org.bukkit.Material.DARK_OAK_FENCE_GATE;
            case 187: return org.bukkit.Material.ACACIA_FENCE_GATE;
            case 188: return org.bukkit.Material.SPRUCE_FENCE;
            case 189: return org.bukkit.Material.BIRCH_FENCE;
            case 190: return org.bukkit.Material.JUNGLE_FENCE;
            case 191: return org.bukkit.Material.DARK_OAK_FENCE;
            case 192: return org.bukkit.Material.ACACIA_FENCE;
            case 193: return org.bukkit.Material.SPRUCE_DOOR;
            case 194: return org.bukkit.Material.BIRCH_DOOR;
            case 195: return org.bukkit.Material.JUNGLE_DOOR;
            case 196: return org.bukkit.Material.ACACIA_DOOR;
            case 197: return org.bukkit.Material.DARK_OAK_DOOR;
            case 198: return org.bukkit.Material.END_ROD;
            case 199: return org.bukkit.Material.CHORUS_PLANT;
            case 200: return org.bukkit.Material.CHORUS_FLOWER;
            case 201: return org.bukkit.Material.PURPUR_BLOCK;
            case 202: return org.bukkit.Material.PURPUR_PILLAR;
            case 203: return org.bukkit.Material.PURPUR_STAIRS;
            case 204: case 205: return org.bukkit.Material.PURPUR_SLAB;
            case 206: return org.bukkit.Material.END_STONE_BRICKS;
            case 207: return org.bukkit.Material.BEETROOTS;
            case 208: return org.bukkit.Material.DIRT_PATH;
            case 209: return org.bukkit.Material.END_GATEWAY;
            case 210: return org.bukkit.Material.REPEATING_COMMAND_BLOCK;
            case 211: return org.bukkit.Material.CHAIN_COMMAND_BLOCK;
            case 212: return org.bukkit.Material.FROSTED_ICE;
            case 213: return org.bukkit.Material.MAGMA_BLOCK;
            case 214: return org.bukkit.Material.NETHER_WART_BLOCK;
            case 215: return org.bukkit.Material.RED_NETHER_BRICKS;
            case 216: return org.bukkit.Material.BONE_BLOCK;
            case 217: return org.bukkit.Material.STRUCTURE_VOID;
            case 218: return org.bukkit.Material.OBSERVER;
            case 219: return org.bukkit.Material.WHITE_SHULKER_BOX;
            case 220: return org.bukkit.Material.ORANGE_SHULKER_BOX;
            case 221: return org.bukkit.Material.MAGENTA_SHULKER_BOX;
            case 222: return org.bukkit.Material.LIGHT_BLUE_SHULKER_BOX;
            case 223: return org.bukkit.Material.YELLOW_SHULKER_BOX;
            case 224: return org.bukkit.Material.LIME_SHULKER_BOX;
            case 225: return org.bukkit.Material.PINK_SHULKER_BOX;
            case 226: return org.bukkit.Material.GRAY_SHULKER_BOX;
            case 227: return org.bukkit.Material.LIGHT_GRAY_SHULKER_BOX;
            case 228: return org.bukkit.Material.CYAN_SHULKER_BOX;
            case 229: return org.bukkit.Material.PURPLE_SHULKER_BOX;
            case 230: return org.bukkit.Material.BLUE_SHULKER_BOX;
            case 231: return org.bukkit.Material.BROWN_SHULKER_BOX;
            case 232: return org.bukkit.Material.GREEN_SHULKER_BOX;
            case 233: return org.bukkit.Material.RED_SHULKER_BOX;
            case 234: return org.bukkit.Material.BLACK_SHULKER_BOX;
            case 235: return org.bukkit.Material.WHITE_GLAZED_TERRACOTTA;
            case 236: return org.bukkit.Material.ORANGE_GLAZED_TERRACOTTA;
            case 237: return org.bukkit.Material.MAGENTA_GLAZED_TERRACOTTA;
            case 238: return org.bukkit.Material.LIGHT_BLUE_GLAZED_TERRACOTTA;
            case 239: return org.bukkit.Material.YELLOW_GLAZED_TERRACOTTA;
            case 240: return org.bukkit.Material.LIME_GLAZED_TERRACOTTA;
            case 241: return org.bukkit.Material.PINK_GLAZED_TERRACOTTA;
            case 242: return org.bukkit.Material.GRAY_GLAZED_TERRACOTTA;
            case 243: return org.bukkit.Material.LIGHT_GRAY_GLAZED_TERRACOTTA;
            case 244: return org.bukkit.Material.CYAN_GLAZED_TERRACOTTA;
            case 245: return org.bukkit.Material.PURPLE_GLAZED_TERRACOTTA;
            case 246: return org.bukkit.Material.BLUE_GLAZED_TERRACOTTA;
            case 247: return org.bukkit.Material.BROWN_GLAZED_TERRACOTTA;
            case 248: return org.bukkit.Material.GREEN_GLAZED_TERRACOTTA;
            case 249: return org.bukkit.Material.RED_GLAZED_TERRACOTTA;
            case 250: return org.bukkit.Material.BLACK_GLAZED_TERRACOTTA;
            case 251: return CONCRETES[meta];
            case 252: return CONCRETE_POWDERS[meta];
            case 255: return org.bukkit.Material.STRUCTURE_BLOCK;
            default: return org.bukkit.Material.AIR;
        }
    }
}
