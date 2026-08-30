package fr.skynex.worldx.edit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class SchematicEngine {

    private static final Gson GSON = new GsonBuilder().create();

    public static class SerializedBlock {
        public int x;
        public int y;
        public int z;
        public String data;

        public SerializedBlock(int x, int y, int z, String data) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.data = data;
        }
    }

    public static class SchematicData {
        public int width;
        public int height;
        public int length;
        public List<SerializedBlock> blocks;

        public SchematicData(int width, int height, int length, List<SerializedBlock> blocks) {
            this.width = width;
            this.height = height;
            this.length = length;
            this.blocks = blocks;
        }
    }

    public static byte[] serialize(Clipboard clipboard) throws IOException {
        List<SerializedBlock> list = new ArrayList<>();
        for (Clipboard.ClipboardBlock cb : clipboard.getBlocks()) {
            list.add(new SerializedBlock(cb.getRelX(), cb.getRelY(), cb.getRelZ(), cb.getBlockData().getAsString()));
        }
        
        SchematicData data = new SchematicData(clipboard.getWidth(), clipboard.getHeight(), clipboard.getLength(), list);
        String json = GSON.toJson(data);
        
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(bos)) {
            gzos.write(json.getBytes(StandardCharsets.UTF_8));
        }
        return bos.toByteArray();
    }

    public static Clipboard deserialize(byte[] compressedBytes) throws IOException {
        ByteArrayInputStream bis = new ByteArrayInputStream(compressedBytes);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        
        try (GZIPInputStream gzis = new GZIPInputStream(bis)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzis.read(buffer)) > 0) {
                bos.write(buffer, 0, len);
            }
        }
        
        String json = bos.toString(StandardCharsets.UTF_8);
        SchematicData data = GSON.fromJson(json, SchematicData.class);
        
        List<Clipboard.ClipboardBlock> list = new ArrayList<>();
        for (SerializedBlock sb : data.blocks) {
            BlockData bd = Bukkit.createBlockData(sb.data);
            list.add(new Clipboard.ClipboardBlock(sb.x, sb.y, sb.z, bd));
        }
        
        return new Clipboard(list, data.width, data.height, data.length);
    }
}
