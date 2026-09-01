package fr.skynex.worldx.event;

import fr.skynex.worldx.region.Region;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public class RegionFlagChangeEvent extends RegionEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private final String flagName;
    private final String oldValue;
    private String newValue;
    private boolean cancelled = false;

    public RegionFlagChangeEvent(Region region, String flagName, String oldValue, String newValue) {
        super(region);
        this.flagName = flagName;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public String getFlagName() {
        return flagName;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
