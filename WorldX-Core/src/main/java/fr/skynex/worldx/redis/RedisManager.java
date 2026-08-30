package fr.skynex.worldx.redis;

import fr.skynex.worldx.WorldX;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

public class RedisManager {

    private final WorldX plugin;
    private JedisPool jedisPool;
    private Jedis subscriberJedis;
    private JedisPubSub pubSub;

    private boolean enabled = false;
    private String host;
    private int port;
    private String password;
    private String channel;
    private String serverId;

    public RedisManager(WorldX plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        this.enabled = plugin.getConfig().getBoolean("redis.enable", false);
        if (!enabled) return;

        this.host = plugin.getConfig().getString("redis.host", "localhost");
        this.port = plugin.getConfig().getInt("redis.port", 6379);
        this.password = plugin.getConfig().getString("redis.password", "");
        this.channel = plugin.getConfig().getString("redis.channel", "worldx_sync");
        this.serverId = plugin.getConfig().getString("redis.server-id", "server-1");

        plugin.getLogger().info("Connecting to Redis at " + host + ":" + port + "...");

        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(10);
            poolConfig.setMaxIdle(5);
            poolConfig.setMinIdle(1);

            if (password != null && !password.isEmpty()) {
                this.jedisPool = new JedisPool(poolConfig, host, port, 2000, password);
            } else {
                this.jedisPool = new JedisPool(poolConfig, host, port, 2000);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize Redis pool: " + e.getMessage());
            this.enabled = false;
            return;
        }

        // Run subscriber on a dedicated async thread
        fr.skynex.worldx.scheduler.FoliaScheduler.runAsync(plugin, () -> {
            try {
                subscriberJedis = jedisPool.getResource();

                pubSub = new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        try {
                            // Message format: serverId:action:regionId
                            String[] parts = message.split(":");
                            if (parts.length < 3) return;

                            String senderServerId = parts[0];
                            String action = parts[1];
                            String regionId = parts[2];

                            if (senderServerId.equals(serverId)) {
                                return; // Ignore own messages
                            }

                            plugin.getLogger().info("[Redis Sync] Received update from " + senderServerId + " - Action: " + action + " for region: " + regionId);

                            // Sync in-memory region state
                            if (action.equals("DELETE")) {
                                plugin.getRegionManager().removeRegionInMemory(regionId);
                            } else {
                                plugin.getRegionManager().reloadRegionFromDatabase(regionId);
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("Error parsing Redis PubSub message: " + e.getMessage());
                        }
                    }
                };

                subscriberJedis.subscribe(pubSub, channel);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to subscribe to Redis: " + e.getMessage());
            }
        });
    }

    public void publishSync(String action, String regionId) {
        if (!enabled || jedisPool == null) return;

        fr.skynex.worldx.scheduler.FoliaScheduler.runAsync(plugin, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String message = serverId + ":" + action + ":" + regionId;
                jedis.publish(channel, message);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to publish sync message: " + e.getMessage());
            }
        });
    }

    public void close() {
        if (pubSub != null) {
            pubSub.unsubscribe();
        }
        if (subscriberJedis != null) {
            try {
                subscriberJedis.close();
            } catch (Exception ignored) {}
        }
        if (jedisPool != null && !jedisPool.isClosed()) {
            try {
                jedisPool.close();
            } catch (Exception ignored) {}
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getServerId() {
        return serverId;
    }
}

