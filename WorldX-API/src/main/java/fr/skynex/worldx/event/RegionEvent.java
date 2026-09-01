package fr.skynex.worldx.event;

import fr.skynex.worldx.region.Region;
import org.bukkit.event.Event;

public abstract class RegionEvent extends Event {

    protected final Region region;

    public RegionEvent(Region region) {
        this.region = region;
    }

    public RegionEvent(Region region, boolean isAsync) {
        super(isAsync);
        this.region = region;
    }

    public Region getRegion() {
        return region;
    }
}
