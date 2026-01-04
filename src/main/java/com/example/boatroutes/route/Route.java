package com.example.boatroutes.route;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Data class representing a route between two ports
 * Contains route information but NO logic
 */
public class Route {
    
    private final String fromPortName;
    private final String toPortName;
    
    // Main path waypoints (from convergence point to split point)
    private final List<Location> mainPath;
    
    // Metadata
    private long calculatedAt;
    private int waypointCount;
    private double distance;
    private int estimatedTime;  // seconds
    
    public Route(String fromPortName, String toPortName) {
        this.fromPortName = fromPortName;
        this.toPortName = toPortName;
        this.mainPath = new ArrayList<>();
        this.calculatedAt = System.currentTimeMillis();
    }
    
    public Route(String fromPortName, String toPortName, List<Location> mainPath) {
        this.fromPortName = fromPortName;
        this.toPortName = toPortName;
        this.mainPath = new ArrayList<>(mainPath);
        this.waypointCount = mainPath.size();
        this.calculatedAt = System.currentTimeMillis();
        this.distance = calculateDistance();
        this.estimatedTime = calculateEstimatedTime();
    }
    
    // === GETTERS ===
    
    public String getFromPortName() {
        return fromPortName;
    }
    
    public String getToPortName() {
        return toPortName;
    }
    
    public List<Location> getMainPath() {
        return new ArrayList<>(mainPath);
    }
    
    public long getCalculatedAt() {
        return calculatedAt;
    }
    
    public int getWaypointCount() {
        return waypointCount;
    }
    
    public double getDistance() {
        return distance;
    }
    
    public int getEstimatedTime() {
        return estimatedTime;
    }
    
    // === SETTERS ===
    
    public void setMainPath(List<Location> waypoints) {
        this.mainPath.clear();
        this.mainPath.addAll(waypoints);
        this.waypointCount = waypoints.size();
        this.distance = calculateDistance();
        this.estimatedTime = calculateEstimatedTime();
    }
    
    // === UTILITY ===
    
    public String getRouteId() {
        return fromPortName + "_to_" + toPortName;
    }
    
    private double calculateDistance() {
        if (mainPath.size() < 2) return 0;
        
        double total = 0;
        for (int i = 0; i < mainPath.size() - 1; i++) {
            total += mainPath.get(i).distance(mainPath.get(i + 1));
        }
        return total;
    }
    
    private int calculateEstimatedTime() {
        // Speed = 0.35 blocks/tick, 20 ticks/second
        // Time = distance / (speed * 20)
        return (int) (distance / (0.35 * 20));
    }
    
    public String getFormattedTime() {
        int minutes = estimatedTime / 60;
        int seconds = estimatedTime % 60;
        
        if (minutes > 0) {
            return minutes + " min " + seconds + " sec";
        } else {
            return seconds + " sec";
        }
    }
    
    @Override
    public String toString() {
        return "Route{" +
                fromPortName + " -> " + toPortName +
                ", distance=" + (int)distance +
                ", waypoints=" + waypointCount +
                '}';
    }
}
