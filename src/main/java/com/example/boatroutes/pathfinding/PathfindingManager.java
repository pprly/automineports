package com.example.boatroutes.pathfinding;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.port.Port;
import org.bukkit.Location;

import java.util.List;

/**
 * Manages pathfinding operations
 * Coordinates between ports and pathfinding algorithms
 */
public class PathfindingManager {
    
    private final BoatRoutesPlugin plugin;
    private final WaterPathfinder pathfinder;
    
    public PathfindingManager(BoatRoutesPlugin plugin) {
        this.plugin = plugin;
        this.pathfinder = new WaterPathfinder(plugin);
    }
    
    /**
     * Find path between two ports (convergence to split point)
     */
    public List<Location> findPathBetweenPorts(Port fromPort, Port toPort) {
        plugin.getLogger().info("PathfindingManager: Finding path from " + 
            fromPort.getName() + " to " + toPort.getName());
        
        Location start = fromPort.getConvergencePoint();
        Location end = toPort.getSplitPoint();
        
        if (start == null || end == null) {
            plugin.getLogger().warning("PathfindingManager: Missing convergence/split points!");
            return null;
        }
        
        return pathfinder.findPath(start, end);
    }
    
    /**
     * Find path between two locations with optional debug
     */
    public List<Location> findPath(Location start, Location end, boolean debug) {
        return pathfinder.findPath(start, end, debug);
    }
    
    /**
     * Get the pathfinder instance
     */
    public WaterPathfinder getPathfinder() {
        return pathfinder;
    }
}
