package fr.skynex.worldx.session;

import fr.skynex.worldx.WorldX;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private final WorldX plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public SessionManager(WorldX plugin) {
        this.plugin = plugin;
    }

    public Session getSession(UUID uuid) {
        return sessions.computeIfAbsent(uuid, id -> {
            Session session = new Session(id);
            plugin.getDatabaseManager().loadPlayerHistory(id, "UNDO").thenAccept(session::loadUndoHistoryFromDb);
            plugin.getDatabaseManager().loadPlayerHistory(id, "REDO").thenAccept(session::loadRedoHistoryFromDb);
            return session;
        });
    }

    public Session getSession(Player player) {
        return getSession(player.getUniqueId());
    }

    public void removeSession(UUID uuid) {
        Session session = sessions.remove(uuid);
        if (session != null) {
            session.cleanup();
        }
    }

    public void clearAll() {
        for (Session session : sessions.values()) {
            if (session != null) {
                session.cleanup();
            }
        }
        sessions.clear();
    }
}
