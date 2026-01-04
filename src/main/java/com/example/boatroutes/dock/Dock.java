package com.example.boatroutes.dock;

import com.example.boatroutes.port.Port;
import org.bukkit.Location;

import java.util.UUID;

/**
 * Data class representing a single dock
 * Contains dock information but NO logic
 */
public class Dock {
    
    private final Port port;
    private final int number;        // 1, 2, or 3
    private final Location location;  // Position in water
    
    // Calculated point for this specific dock
    private Location exitPoint;      // Where boats go when leaving this dock
    private Location entryPoint;     // Where boats go when arriving at this dock
    
    // Current state
    private boolean occupied;
    private UUID currentBoatUUID;
    
    public Dock(Port port, int number, Location location) {
        this.port = port;
        this.number = number;
        this.location = location;
        this.occupied = false;
    }
    
    // === GETTERS ===
    
    public Port getPort() {
        return port;
    }
    
    public int getNumber() {
        return number;
    }
    
    public Location getLocation() {
        return location.clone();
    }
    
    public Location getExitPoint() {
        return exitPoint != null ? exitPoint.clone() : null;
    }
    
    public Location getEntryPoint() {
        return entryPoint != null ? entryPoint.clone() : null;
    }
    
    public boolean isOccupied() {
        return occupied;
    }
    
    public boolean isAvailable() {
        return !occupied;
    }
    
    public UUID getCurrentBoatUUID() {
        return currentBoatUUID;
    }
    
    // === SETTERS ===
    
    public void setExitPoint(Location exitPoint) {
        this.exitPoint = exitPoint;
    }
    
    public void setEntryPoint(Location entryPoint) {
        this.entryPoint = entryPoint;
    }
    
    public void setOccupied(boolean occupied) {
        this.occupied = occupied;
        if (!occupied) {
            this.currentBoatUUID = null;
        }
    }
    
    public void setCurrentBoat(UUID boatUUID) {
        this.currentBoatUUID = boatUUID;
        this.occupied = (boatUUID != null);
    }
    
    // === UTILITY ===
    
    public String getDisplayName() {
        return port.getName() + " Dock #" + number;
    }
    
    @Override
    public String toString() {
        return "Dock{" +
                "port=" + port.getName() +
                ", number=" + number +
                ", occupied=" + occupied +
                '}';
    }
}
