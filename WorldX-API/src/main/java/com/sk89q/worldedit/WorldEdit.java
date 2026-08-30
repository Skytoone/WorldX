package com.sk89q.worldedit;

import fr.skynex.worldx.api.WorldXProvider;

public class WorldEdit {

    private static final WorldEdit instance = new WorldEdit();

    public static WorldEdit getInstance() {
        return instance;
    }

    public String getVersion() {
        return WorldXProvider.get().getVersion();
    }
}
