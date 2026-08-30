package fr.skynex.worldx.region;

public class RegionTrigger {

    public enum TriggerType {
        ON_ENTER,
        ON_LEAVE,
        ON_INTERACT,
        PERIODIC
    }

    public enum ActionType {
        TELEPORT,
        POTION,
        COMMAND,
        MINIMESSAGE,
        SOUND,
        FIREWORK,
        GAMEMODE
    }

    private final TriggerType triggerType;
    private final ActionType actionType;
    private final String value;

    public RegionTrigger(TriggerType triggerType, ActionType actionType, String value) {
        this.triggerType = triggerType;
        this.actionType = actionType;
        this.value = value;
    }

    public TriggerType getTriggerType() {
        return triggerType;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public String getValue() {
        return value;
    }
}
