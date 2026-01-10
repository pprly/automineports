package com.example.boatroutes.port;

import com.example.boatroutes.dock.Dock;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Data class representing a single port
 * Contains all port information but NO logic
 */
public class Port {
    
    private final String name;
    private UUID creator;
    private long createdAt;
    
    // NPC location (on shore)
    private Location npcLocation;
    private UUID npcUUID;
    
    // Docks (in water)
    private final List<Dock> docks;
    
    // Calculated points for pathfinding
    private Location navigationPoint;  // Single point for entry AND exit
    
    // Legacy fields (deprecated but kept for compatibility)
    @Deprecated
    private Location convergencePoint;
    @Deprecated
    private Location splitPoint;
    
    public Port(String name) {
        this.name = name;
        this.docks = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
    }
    
    // === GETTERS ===
    
    public String getName() {
        return name;
    }
    
    public UUID getCreator() {
        return creator;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public Location getNPCLocation() {
        return npcLocation;
    }
    
    public UUID getNpcUUID() {
        return npcUUID;
    }
    
    public List<Dock> getDocks() {
        return new ArrayList<>(docks);
    }
    
    public Location getConvergencePoint() {
        // Legacy support: return navigationPoint
        return navigationPoint != null ? navigationPoint : convergencePoint;
    }
    
    public Location getSplitPoint() {
        // Legacy support: return navigationPoint
        return navigationPoint != null ? navigationPoint : splitPoint;
    }
    
    public Location getNavigationPoint() {
        return navigationPoint;
    }
    
    // === SETTERS ===
    
    public void setCreator(UUID creator) {
        this.creator = creator;
    }
    
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
    
    public void setNPCLocation(Location npcLocation) {
        this.npcLocation = npcLocation;
    }
    
    public void setNpcUUID(UUID npcUUID) {
        this.npcUUID = npcUUID;
    }
    
    public void setConvergencePoint(Location convergencePoint) {
        this.convergencePoint = convergencePoint;
    }
    
    public void setSplitPoint(Location splitPoint) {
        this.splitPoint = splitPoint;
    }
    
    public void setNavigationPoint(Location navigationPoint) {
        this.navigationPoint = navigationPoint;
        // Also set legacy points for compatibility
        this.convergencePoint = navigationPoint;
        this.splitPoint = navigationPoint;
    }
    
    // === DOCK MANAGEMENT ===
    
    public void addDock(Dock dock) {
        this.docks.add(dock);
    }
    
    public void removeDock(Dock dock) {
        this.docks.remove(dock);
    }
    
    public void clearDocks() {
        this.docks.clear();
    }
    
    public int getDockCount() {
        return docks.size();
    }
    
    public Dock getDock(int number) {
        for (Dock dock : docks) {
            if (dock.getNumber() == number) {
                return dock;
            }
        }
        return null;
    }
    
    // === UTILITY ===
    
    public boolean isFullySetup() {
        return npcLocation != null && 
               !docks.isEmpty() && 
               navigationPoint != null;
    }
    
    @Override
    public String toString() {
        return "Port{" +
                "name='" + name + '\'' +
                ", docks=" + docks.size() +
                ", setup=" + isFullySetup() +
                '}';
    }
}
