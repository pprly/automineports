package com.example.boatroutes.pathfinding;

import org.bukkit.Location;

/**
 * Represents a single node in the pathfinding grid
 * Used for A* algorithm
 */
public class PathNode implements Comparable<PathNode> {
    
    private final Location location;
    private final int x;
    private final int y;
    private final int z;
    
    // A* algorithm values
    private double gCost;  // Distance from start
    private double hCost;  // Heuristic distance to goal
    private double fCost;  // Total cost (g + h)
    
    private PathNode parent;  // Previous node in path
    
    public PathNode(Location location) {
        this.location = location;
        this.x = location.getBlockX();
        this.y = location.getBlockY();
        this.z = location.getBlockZ();
        this.gCost = 0;
        this.hCost = 0;
        this.fCost = 0;
    }
    
    public PathNode(int x, int y, int z, Location worldReference) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.location = new Location(worldReference.getWorld(), x, y, z);
        this.gCost = 0;
        this.hCost = 0;
        this.fCost = 0;
    }
    
    // === GETTERS ===
    
    public Location getLocation() {
        return location.clone();
    }
    
    public int getX() {
        return x;
    }
    
    public int getY() {
        return y;
    }
    
    public int getZ() {
        return z;
    }
    
    public double getGCost() {
        return gCost;
    }
    
    public double getHCost() {
        return hCost;
    }
    
    public double getFCost() {
        return fCost;
    }
    
    public PathNode getParent() {
        return parent;
    }
    
    // === SETTERS ===
    
    public void setGCost(double gCost) {
        this.gCost = gCost;
        this.fCost = this.gCost + this.hCost;
    }
    
    public void setHCost(double hCost) {
        this.hCost = hCost;
        this.fCost = this.gCost + this.hCost;
    }
    
    public void setParent(PathNode parent) {
        this.parent = parent;
    }
    
    // === UTILITY ===
    
    /**
     * Calculate distance to another node (Euclidean distance)
     */
    public double distanceTo(PathNode other) {
        int dx = this.x - other.x;
        int dy = this.y - other.y;
        int dz = this.z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    
    /**
     * Calculate Manhattan distance to another node
     */
    public double manhattanDistanceTo(PathNode other) {
        return Math.abs(this.x - other.x) + 
               Math.abs(this.y - other.y) + 
               Math.abs(this.z - other.z);
    }
    
    /**
     * Get unique key for this node (for HashMap)
     */
    public String getKey() {
        return x + "," + y + "," + z;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PathNode other)) return false;
        return this.x == other.x && this.y == other.y && this.z == other.z;
    }
    
    @Override
    public int hashCode() {
        return (x * 31 + y) * 31 + z;
    }
    
    @Override
    public int compareTo(PathNode other) {
        return Double.compare(this.fCost, other.fCost);
    }
    
    @Override
    public String toString() {
        return "PathNode{" + x + "," + y + "," + z + " f=" + (int)fCost + "}";
    }
}
