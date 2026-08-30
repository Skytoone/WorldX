package fr.skynex.worldx.edit;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;

public class MaskParser {

    public static Mask parse(String input) {
        if (input == null || input.trim().isEmpty()) {
            return block -> true;
        }
        input = input.trim();

        String[] parts = input.split(" ");
        List<Mask> masks = new ArrayList<>();
        for (String part : parts) {
            masks.add(parseSingle(part));
        }

        return block -> {
            for (Mask mask : masks) {
                if (!mask.matches(block))
                    return false;
            }
            return true;
        };
    }

    private static Mask parseSingle(String part) {
        boolean negate = part.startsWith("!");
        String clean = negate ? part.substring(1) : part;

        Mask base;
        if (clean.equalsIgnoreCase("#exposed")) {
            base = block -> {
                for (BlockFace face : BlockFace.values()) {
                    if (face.isCartesian()) {
                        if (block.getRelative(face).getType().isAir()) {
                            return true;
                        }
                    }
                }
                return false;
            };
        } else {
            Material mat = Material.matchMaterial(clean.toUpperCase());
            if (mat != null) {
                base = block -> block.getType() == mat;
            } else {
                base = block -> true;
            }
        }

        if (negate) {
            return block -> !base.matches(block);
        }
        return base;
    }
}
