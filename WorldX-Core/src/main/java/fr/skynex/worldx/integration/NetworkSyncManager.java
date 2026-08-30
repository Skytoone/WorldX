package fr.skynex.worldx.integration;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import fr.skynex.worldx.WorldX;
import fr.skynex.worldx.region.Region;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class NetworkSyncManager implements PluginMessageListener {

    private final WorldX plugin;
    public static final String CHANNEL = "worldx:sync";

    public NetworkSyncManager(WorldX plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    public void unregister() {
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
    }

    public void sendRegionSave(Region region) {
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("SAVE");
        out.writeUTF(region.getId());
        out.writeUTF(region.getWorldName());
        out.writeInt(region.getMinX());
        out.writeInt(region.getMinY());
        out.writeInt(region.getMinZ());
        out.writeInt(region.getMaxX());
        out.writeInt(region.getMaxY());
        out.writeInt(region.getMaxZ());
        out.writeInt(region.getPriority());
        out.writeUTF(region.getParentId() != null ? region.getParentId() : "");
        out.writeUTF(serializeUUIDList(region.getOwners()));
        out.writeUTF(serializeUUIDList(region.getMembers()));
        out.writeUTF(serializeFlags(region.getFlags()));
        out.writeUTF(region.getShapeType().name());

        // Send plugin message via first available online player
        Player carrier = Bukkit.getOnlinePlayers().iterator().next();
        carrier.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
    }

    public void sendRegionDelete(String id) {
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("DELETE");
        out.writeUTF(id);

        Player carrier = Bukkit.getOnlinePlayers().iterator().next();
        carrier.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (!channel.equals(CHANNEL)) {
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(Objects.requireNonNull(message));
        try {
            String action = in.readUTF();

            if (action.equals("SAVE")) {
                String id = in.readUTF();
                String worldName = in.readUTF();
                int minX = in.readInt();
                int minY = in.readInt();
                int minZ = in.readInt();
                int maxX = in.readInt();
                int maxY = in.readInt();
                int maxZ = in.readInt();
                int priority = in.readInt();
                String parentIdStr = in.readUTF();
                String parentId = parentIdStr.isEmpty() ? null : parentIdStr;
                String ownersStr = in.readUTF();
                String membersStr = in.readUTF();
                String flagsStr = in.readUTF();
                String shapeTypeStr = in.readUTF();

                fr.skynex.worldx.region.ShapeType shapeType = fr.skynex.worldx.region.ShapeType.valueOf(shapeTypeStr.toUpperCase());

                // Create region and set values
                Region region = new Region(id, worldName, minX, minY, minZ, maxX, maxY, maxZ, shapeType);
                region.setPriority(priority);
                region.setParentId(parentId);
                parseUUIDList(ownersStr).forEach(region::addOwner);
                parseUUIDList(membersStr).forEach(region::addMember);
                region.getFlags().putAll(parseFlags(flagsStr));

                // Update local memory and spatial index only
                plugin.getRegionManager().updateLocalCacheOnly(region);

            } else if (action.equals("DELETE")) {
                String id = in.readUTF();
                plugin.getRegionManager().removeLocalCacheOnly(id);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to process network sync message: " + e.getMessage());
        }
    }

    // region Helpers

    private List<UUID> parseUUIDList(String str) {
        List<UUID> list = new ArrayList<>();
        if (str == null || str.trim().isEmpty()) {
            return list;
        }
        for (String s : str.split(",")) {
            try {
                list.add(UUID.fromString(s.trim()));
            } catch (IllegalArgumentException ignored) {}
        }
        return list;
    }

    private String serializeUUIDList(List<UUID> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (UUID uuid : list) {
            sb.append(uuid.toString()).append(",");
        }
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    private Map<String, String> parseFlags(String str) {
        Map<String, String> map = new HashMap<>();
        if (str == null || str.trim().isEmpty()) {
            return map;
        }
        for (String pair : str.split(";")) {
            String[] parts = pair.split("=");
            if (parts.length == 2) {
                map.put(parts[0].trim(), parts[1].trim());
            }
        }
        return map;
    }

    private String serializeFlags(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            sb.append(entry.getKey())
              .append("=")
              .append(entry.getValue())
              .append(";");
        }
        return sb.toString();
    }

    // endregion
}
