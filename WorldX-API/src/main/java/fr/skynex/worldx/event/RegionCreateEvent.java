package fr.skynex.worldx.event;

import fr.skynex.worldx.region.Region;
import org.bukkit.event.HandlerList;

public class RegionCreateEvent extends RegionEvent {

    private static final HandlerList handlers = new HandlerList();

    public RegionCreateEvent(Region region) {
        super(region);
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
