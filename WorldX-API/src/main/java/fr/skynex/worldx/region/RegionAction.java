package fr.skynex.worldx.region;

import java.util.ArrayList;

public class RegionAction {

    public enum Type {
        CREATE,
        DELETE,
        UPDATE
    }

    private final Type type;
    private final Region oldState;
    private final Region newState;

    public RegionAction(Type type, Region oldState, Region newState) {
        this.type = type;
        this.oldState = oldState;
        this.newState = newState;
    }

    public Type getType() {
        return type;
    }

    public Region getOldState() {
        return oldState;
    }

    public Region getNewState() {
        return newState;
    }

    /**
     * Helper to clone a Region for snapshotting.
     */
    public static Region cloneRegion(Region source) {
        if (source == null) return null;
        Region clone;
        if (source.getShapeType() == ShapeType.SPHERE) {
            clone = new Region(source.getId(), source.getWorldName(), source.getMinX(), source.getMinY(), source.getMinZ(), source.getMaxX());
        } else {
            clone = new Region(source.getId(), source.getWorldName(), source.getMinX(), source.getMinY(), source.getMinZ(), source.getMaxX(), source.getMaxY(), source.getMaxZ(), source.getShapeType());
        }
        clone.setPriority(source.getPriority());
        clone.setParentId(source.getParentId());
        source.getOwners().forEach(clone::addOwner);
        source.getMembers().forEach(clone::addMember);
        clone.getFlags().putAll(source.getFlags());
        clone.setPolyPoints(new ArrayList<>(source.getPolyPoints()));
        clone.setBoundSchematic(source.getBoundSchematic());
        return clone;
    }
}
