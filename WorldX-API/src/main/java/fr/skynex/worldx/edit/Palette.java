package fr.skynex.worldx.edit;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Palette {
    private final String name;
    private final List<Entry> entries;
    private static final Gson GSON = new Gson();
    private static final Random RANDOM = new Random();

    public Palette(String name, List<Entry> entries) {
        this.name = name;
        this.entries = entries;
    }

    public String getName() {
        return name;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public BlockData sampleBlock() {
        if (entries.isEmpty()) {
            return Material.AIR.createBlockData();
        }

        double totalWeight = 0;
        for (Entry entry : entries) {
            totalWeight += entry.weight;
        }

        if (totalWeight <= 0) {
            return Material.AIR.createBlockData();
        }

        double value = RANDOM.nextDouble() * totalWeight;
        double sum = 0;
        for (Entry entry : entries) {
            sum += entry.weight;
            if (sum >= value) {
                try {
                    return Material.valueOf(entry.material.toUpperCase()).createBlockData();
                } catch (IllegalArgumentException e) {
                    return Material.AIR.createBlockData();
                }
            }
        }

        try {
            return Material.valueOf(entries.get(0).material.toUpperCase()).createBlockData();
        } catch (IllegalArgumentException e) {
            return Material.AIR.createBlockData();
        }
    }

    public String serialize() {
        return GSON.toJson(entries);
    }

    public static Palette deserialize(String name, String json) {
        Type listType = new TypeToken<ArrayList<Entry>>(){}.getType();
        List<Entry> entries = GSON.fromJson(json, listType);
        return new Palette(name, entries);
    }

    public static class Entry {
        public String material;
        public double weight;

        public Entry(String material, double weight) {
            this.material = material;
            this.weight = weight;
        }
    }
}
