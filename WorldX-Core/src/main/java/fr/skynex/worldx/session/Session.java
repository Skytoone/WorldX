package fr.skynex.worldx.session;

import fr.skynex.worldx.edit.Clipboard;
import fr.skynex.worldx.edit.EditOperation;
import fr.skynex.worldx.edit.BrushInfo;
import org.bukkit.Location;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.List;

public class Session {

    private final UUID playerUUID;
    private Location pos1;
    private Location pos2;
    private Clipboard clipboard;
    private BrushInfo brushInfo;
    private String activeMask;
    
    // Undo/Redo operations
    private final Deque<EditOperation> undoHistory = new ArrayDeque<>();
    private final Deque<EditOperation> redoHistory = new ArrayDeque<>();
    
    private static final int MAX_HISTORY_SIZE = 5;

    public Session(UUID playerUUID) {
        this.playerUUID = playerUUID;
    }

    public String getActiveMask() {
        return activeMask;
    }

    public void setActiveMask(String activeMask) {
        this.activeMask = activeMask;
    }

    public void setPos1(Location loc) {
        this.pos1 = loc;
    }

    public void setPos2(Location loc) {
        this.pos2 = loc;
    }

    public Location getPos1() {
        if ((selectionType == fr.skynex.worldx.region.ShapeType.POLYGON || selectionType == fr.skynex.worldx.region.ShapeType.LASSO) && !selectionPoints.isEmpty()) {
            return selectionPoints.get(0);
        }
        return pos1;
    }

    public Location getPos2() {
        if ((selectionType == fr.skynex.worldx.region.ShapeType.POLYGON || selectionType == fr.skynex.worldx.region.ShapeType.LASSO) && !selectionPoints.isEmpty()) {
            return selectionPoints.get(selectionPoints.size() - 1);
        }
        return pos2;
    }

    public boolean hasCompleteSelection() {
        if (selectionType == fr.skynex.worldx.region.ShapeType.POLYGON) {
            return selectionPoints.size() >= 3;
        }
        if (selectionType == fr.skynex.worldx.region.ShapeType.LASSO) {
            return selectionPoints.size() >= 4;
        }
        return pos1 != null && pos2 != null && pos1.getWorld() != null && pos1.getWorld().equals(pos2.getWorld());
    }

    public Clipboard getClipboard() {
        return clipboard;
    }

    public void setClipboard(Clipboard clipboard) {
        this.clipboard = clipboard;
    }

    private int getMaxHistoryLimit() {
        fr.skynex.worldx.WorldX plugin = fr.skynex.worldx.WorldX.getInstance();
        if (plugin != null) {
            org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                return fr.skynex.worldx.util.PermissionQuotaManager.getMaxHistorySize(plugin, player);
            }
        }
        return MAX_HISTORY_SIZE;
    }

    public synchronized void addUndoOperation(EditOperation op) {
        int limit = getMaxHistoryLimit();
        if (undoHistory.size() >= limit) {
            undoHistory.pollLast(); // Remove oldest operation
        }
        undoHistory.push(op);
        redoHistory.clear(); // Clear redo on new action
        saveHistoryToDb();
    }

    public synchronized EditOperation popUndo() {
        EditOperation op = undoHistory.pollFirst();
        saveHistoryToDb();
        return op;
    }

    public synchronized java.util.Deque<EditOperation> getUndoHistory() {
        return undoHistory;
    }

    public synchronized java.util.Deque<EditOperation> getRedoHistory() {
        return redoHistory;
    }

    public synchronized void loadUndoHistoryFromDb(List<EditOperation> list) {
        if (list != null) {
            undoHistory.clear();
            for (EditOperation op : list) {
                undoHistory.addLast(op);
            }
        }
    }

    public synchronized void loadRedoHistoryFromDb(List<EditOperation> list) {
        if (list != null) {
            redoHistory.clear();
            for (EditOperation op : list) {
                redoHistory.addLast(op);
            }
        }
    }

    public synchronized void addRedoOperation(EditOperation op) {
        int limit = getMaxHistoryLimit();
        if (redoHistory.size() >= limit) {
            redoHistory.pollLast();
        }
        redoHistory.push(op);
        saveHistoryToDb();
    }

    public synchronized EditOperation popRedo() {
        EditOperation op = redoHistory.pollFirst();
        saveHistoryToDb();
        return op;
    }

    private synchronized void saveHistoryToDb() {
        fr.skynex.worldx.WorldX plugin = fr.skynex.worldx.WorldX.getInstance();
        if (plugin != null && plugin.getDatabaseManager() != null) {
            plugin.getDatabaseManager().savePlayerHistory(playerUUID, "UNDO", new java.util.ArrayList<>(undoHistory));
            plugin.getDatabaseManager().savePlayerHistory(playerUUID, "REDO", new java.util.ArrayList<>(redoHistory));
        }
    }

    public synchronized void clearHistory() {
        undoHistory.clear();
        redoHistory.clear();
        saveHistoryToDb();
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    private org.bukkit.GameMode originalGameMode;
    private Float originalWalkSpeed;
    private Double originalGravity;

    public org.bukkit.GameMode getOriginalGameMode() {
        return originalGameMode;
    }

    public void setOriginalGameMode(org.bukkit.GameMode originalGameMode) {
        this.originalGameMode = originalGameMode;
    }

    public Float getOriginalWalkSpeed() {
        return originalWalkSpeed;
    }

    public void setOriginalWalkSpeed(Float originalWalkSpeed) {
        this.originalWalkSpeed = originalWalkSpeed;
    }

    public Double getOriginalGravity() {
        return originalGravity;
    }

    public void setOriginalGravity(Double originalGravity) {
        this.originalGravity = originalGravity;
    }

    public BrushInfo getBrushInfo() {
        return brushInfo;
    }

    public void setBrushInfo(BrushInfo brushInfo) {
        this.brushInfo = brushInfo;
    }

    private String paletteState;
    private List<org.bukkit.inventory.ItemStack> pendingPaletteItems;

    public String getPaletteState() {
        return paletteState;
    }

    public void setPaletteState(String paletteState) {
        this.paletteState = paletteState;
    }

    public List<org.bukkit.inventory.ItemStack> getPendingPaletteItems() {
        return pendingPaletteItems;
    }

    public void setPendingPaletteItems(List<org.bukkit.inventory.ItemStack> pendingPaletteItems) {
        this.pendingPaletteItems = pendingPaletteItems;
    }

    private fr.skynex.worldx.region.ShapeType selectionType = fr.skynex.worldx.region.ShapeType.CUBOID;
    private final List<Location> selectionPoints = new java.util.ArrayList<>();

    public fr.skynex.worldx.region.ShapeType getSelectionType() {
        return selectionType;
    }

    public void setSelectionType(fr.skynex.worldx.region.ShapeType selectionType) {
        this.selectionType = selectionType;
        this.selectionPoints.clear(); // Clear old points on type change
    }

    public List<Location> getSelectionPoints() {
        return selectionPoints;
    }

    private boolean schematicPreviewActive = false;

    public boolean isSchematicPreviewActive() {
        return schematicPreviewActive;
    }

    public void setSchematicPreviewActive(boolean schematicPreviewActive) {
        this.schematicPreviewActive = schematicPreviewActive;
    }

    private List<fr.skynex.worldx.edit.BlockEditQueue.BlockChangeInfo> pendingRollback;
    private String pendingRollbackWorldName;
    private String pendingRollbackRegionId;

    public List<fr.skynex.worldx.edit.BlockEditQueue.BlockChangeInfo> getPendingRollback() {
        return pendingRollback;
    }

    public void setPendingRollback(List<fr.skynex.worldx.edit.BlockEditQueue.BlockChangeInfo> pendingRollback) {
        this.pendingRollback = pendingRollback;
    }

    public String getPendingRollbackWorldName() {
        return pendingRollbackWorldName;
    }

    public void setPendingRollbackWorldName(String pendingRollbackWorldName) {
        this.pendingRollbackWorldName = pendingRollbackWorldName;
    }

    public String getPendingRollbackRegionId() {
        return pendingRollbackRegionId;
    }

    public void setPendingRollbackRegionId(String pendingRollbackRegionId) {
        this.pendingRollbackRegionId = pendingRollbackRegionId;
    }

    // Region action history
    private final Deque<fr.skynex.worldx.region.RegionAction> regionUndoHistory = new ArrayDeque<>();
    private final Deque<fr.skynex.worldx.region.RegionAction> regionRedoHistory = new ArrayDeque<>();

    public void addRegionUndo(fr.skynex.worldx.region.RegionAction action) {
        if (regionUndoHistory.size() >= MAX_HISTORY_SIZE) {
            regionUndoHistory.pollLast();
        }
        regionUndoHistory.push(action);
        regionRedoHistory.clear(); // Clear redo on new action
    }

    public fr.skynex.worldx.region.RegionAction popRegionUndo() {
        return regionUndoHistory.pollFirst();
    }

    public void addRegionRedo(fr.skynex.worldx.region.RegionAction action) {
        if (regionRedoHistory.size() >= MAX_HISTORY_SIZE) {
            regionRedoHistory.pollLast();
        }
        regionRedoHistory.push(action);
    }

    public fr.skynex.worldx.region.RegionAction popRegionRedo() {
        return regionRedoHistory.pollFirst();
    }

    private java.util.List<org.bukkit.entity.BlockDisplay> pastePreviewDisplays;
    private org.bukkit.Location pastePreviewLocation;

    public java.util.List<org.bukkit.entity.BlockDisplay> getPastePreviewDisplays() {
        return pastePreviewDisplays;
    }

    public void setPastePreviewDisplays(java.util.List<org.bukkit.entity.BlockDisplay> displays) {
        this.pastePreviewDisplays = displays;
    }

    public org.bukkit.Location getPastePreviewLocation() {
        return pastePreviewLocation;
    }

    public void setPastePreviewLocation(org.bukkit.Location loc) {
        this.pastePreviewLocation = loc;
    }

    public void cleanup() {
        if (pastePreviewDisplays != null) {
            for (org.bukkit.entity.BlockDisplay bd : pastePreviewDisplays) {
                if (bd != null && bd.isValid()) {
                    bd.remove();
                }
            }
            pastePreviewDisplays = null;
        }
        pastePreviewLocation = null;
        activePaintPalette = null;
        pos1 = null;
        pos2 = null;
        clipboard = null;
        brushInfo = null;
        pendingPaletteItems = null;
        pendingRollback = null;
        selectionPoints.clear();
        undoHistory.clear();
        redoHistory.clear();
        regionUndoHistory.clear();
        regionRedoHistory.clear();
    }

    public void clearWorld(org.bukkit.World world) {
        if (world == null) return;
        if (pos1 != null && pos1.getWorld() != null && world.equals(pos1.getWorld())) {
            pos1 = null;
        }
        if (pos2 != null && pos2.getWorld() != null && world.equals(pos2.getWorld())) {
            pos2 = null;
        }
        selectionPoints.removeIf(loc -> loc != null && loc.getWorld() != null && world.equals(loc.getWorld()));
        if (pastePreviewLocation != null && pastePreviewLocation.getWorld() != null && world.equals(pastePreviewLocation.getWorld())) {
            pastePreviewLocation = null;
        }
    }

    private fr.skynex.worldx.edit.Palette activePaintPalette;

    public fr.skynex.worldx.edit.Palette getActivePaintPalette() {
        return activePaintPalette;
    }

    public void setActivePaintPalette(fr.skynex.worldx.edit.Palette activePaintPalette) {
        this.activePaintPalette = activePaintPalette;
    }

    // Position & Region cache for performance
    private int lastBlockX = Integer.MIN_VALUE;
    private int lastBlockY = Integer.MIN_VALUE;
    private int lastBlockZ = Integer.MIN_VALUE;
    private String lastRegionId;

    public boolean hasMovedBlock(int x, int y, int z) {
        return x != lastBlockX || y != lastBlockY || z != lastBlockZ;
    }

    public void updateLastBlock(int x, int y, int z) {
        this.lastBlockX = x;
        this.lastBlockY = y;
        this.lastBlockZ = z;
    }

    public String getLastRegionId() {
        return lastRegionId;
    }

    public void setLastRegionId(String lastRegionId) {
        this.lastRegionId = lastRegionId;
    }
}
