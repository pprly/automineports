package com.example.boatroutes.boat;

import org.bukkit.entity.Boat;

import java.util.UUID;

/**
 * Data class representing a player's boat
 * Contains boat information but NO logic
 */
public class PlayerBoat {
    
    private final UUID ownerUUID;
    private String name;
    private UUID boatEntityUUID;  // Current spawned boat entity
    
    // Current location
    private String currentPortName;
    private Integer currentDockNumber;
    
    // Statistics
    private long createdAt;
    private int totalTrips;
    
    public PlayerBoat(UUID ownerUUID, String name) {
        this.ownerUUID = ownerUUID;
        this.name = name;
        this.createdAt = System.currentTimeMillis();
        this.totalTrips = 0;
    }
    
    // === GETTERS ===
    
    public UUID getOwnerUUID() {
        return ownerUUID;
    }
    
    public String getName() {
        return name;
    }
    
    public UUID getBoatEntityUUID() {
        return boatEntityUUID;
    }
    
    public String getCurrentPortName() {
        return currentPortName;
    }
    
    public Integer getCurrentDockNumber() {
        return currentDockNumber;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public int getTotalTrips() {
        return totalTrips;
    }
    
    // === SETTERS ===
    
    public void setName(String name) {
        this.name = name;
    }
    
    public void setBoatEntityUUID(UUID boatEntityUUID) {
        this.boatEntityUUID = boatEntityUUID;
    }
    
    public void setCurrentPort(String portName, int dockNumber) {
        this.currentPortName = portName;
        this.currentDockNumber = dockNumber;
    }
    
    public void clearCurrentPort() {
        this.currentPortName = null;
        this.currentDockNumber = null;
    }
    
    public void incrementTrips() {
        this.totalTrips++;
    }
    
    // === UTILITY ===
    
    public boolean isSpawned() {
        return boatEntityUUID != null;
    }
    
    public String getDisplayName() {
        return "§b🚤 " + name;
    }
    
    @Override
    public String toString() {
        return "PlayerBoat{" +
                "owner=" + ownerUUID +
                ", name='" + name + '\'' +
                ", location=" + currentPortName +
                '}';
    }
}
