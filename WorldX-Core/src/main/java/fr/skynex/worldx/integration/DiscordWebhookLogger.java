package fr.skynex.worldx.integration;

import fr.skynex.worldx.WorldX;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

public class DiscordWebhookLogger {

    private static final HttpClient client = HttpClient.newHttpClient();

    public static void logRegionAction(WorldX plugin, String action, String player, String regionId, String details) {
        String url = plugin.getConfig().getString("integration.discord-webhook-url", "");
        if (url == null || url.trim().isEmpty()) {
            return;
        }

        // Prepare JSON payload
        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        
        // Escape json helper
        String escapedAction = escapeJson(action);
        String escapedPlayer = escapeJson(player);
        String escapedRegionId = escapeJson(regionId);
        String escapedDetails = escapeJson(details);

        String json = "{"
                + "\"embeds\": [{"
                + "\"title\": \"📌 Journal des Actions Région WorldX\","
                + "\"color\": 1752220," // Aqua color
                + "\"fields\": ["
                + "{\"name\": \"Action\", \"value\": \"" + escapedAction + "\", \"inline\": true},"
                + "{\"name\": \"Joueur\", \"value\": \"" + escapedPlayer + "\", \"inline\": true},"
                + "{\"name\": \"Région\", \"value\": \"" + escapedRegionId + "\", \"inline\": true},"
                + "{\"name\": \"Détails\", \"value\": \"" + escapedDetails + "\", \"inline\": false}"
                + "],"
                + "\"timestamp\": \"" + timestamp + "\""
                + "}]"
                + "}";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "WorldX-Logger-Agent")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            // Send asynchronously to prevent blocking the main server thread
            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                  .thenAccept(response -> {
                      if (response.statusCode() < 200 || response.statusCode() >= 300) {
                          plugin.getLogger().warning("[WorldX] Failed to send log to Discord Webhook. Code: " + response.statusCode());
                      }
                  });
        } catch (Exception e) {
            plugin.getLogger().warning("[WorldX] Webhook request error: " + e.getMessage());
        }
    }

    public static void logIntrusion(WorldX plugin, String intruder, String regionId, String action, String world, int x, int y, int z) {
        String url = plugin.getConfig().getString("integration.discord-webhook-url", "");
        if (url == null || url.trim().isEmpty()) {
            return;
        }

        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        
        String escapedIntruder = escapeJson(intruder);
        String escapedRegionId = escapeJson(regionId);
        String escapedAction = escapeJson(action);
        String escapedWorld = escapeJson(world);
        String coordStr = "(" + x + ", " + y + ", " + z + ") dans " + escapedWorld;

        String json = "{"
                + "\"embeds\": [{"
                + "\"title\": \"🚨 ALERTE INTRUSION - REGION WorldX\","
                + "\"color\": 15158332," // Red color (HEX: #E74C3C)
                + "\"fields\": ["
                + "{\"name\": \"Intrus\", \"value\": \"" + escapedIntruder + "\", \"inline\": true},"
                + "{\"name\": \"Région\", \"value\": \"" + escapedRegionId + "\", \"inline\": true},"
                + "{\"name\": \"Action tentée\", \"value\": \"" + escapedAction + "\", \"inline\": true},"
                + "{\"name\": \"Coordonnées\", \"value\": \"" + coordStr + "\", \"inline\": false}"
                + "],"
                + "\"timestamp\": \"" + timestamp + "\""
                + "}]"
                + "}";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "WorldX-Logger-Agent")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                  .thenAccept(response -> {
                      if (response.statusCode() < 200 || response.statusCode() >= 300) {
                          plugin.getLogger().warning("[WorldX] Failed to send intrusion log to Discord Webhook. Code: " + response.statusCode());
                      }
                  });
        } catch (Exception e) {
            plugin.getLogger().warning("[WorldX] Webhook request error: " + e.getMessage());
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
