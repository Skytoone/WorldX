package fr.skynex.worldx.api;

public final class WorldXProvider {

    private static WorldXAPI instance;

    private WorldXProvider() {
    }

    public static WorldXAPI get() {
        if (instance == null) {
            throw new IllegalStateException("WorldXAPI is not initialized yet!");
        }
        return instance;
    }

    public static void register(WorldXAPI api) {
        instance = api;
    }

    public static void unregister() {
        instance = null;
    }
}
