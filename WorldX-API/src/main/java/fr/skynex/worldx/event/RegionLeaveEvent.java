package fr.skynex.worldx.event;

import fr.skynex.worldx.region.Region;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public class RegionLeaveEvent extends RegionEvent {

    private static final HandlerList handlers = new HandlerList();
    private final Player player;

    public RegionLeaveEvent(Region region, Player player) {
        super(region);
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
