package com.example.boatroutes.dock;

import com.example.boatroutes.port.Port;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Calculates exit/entry points for port automatically
 * RESPONSIBILITY: ONLY point calculation, no other logic
 */
public class DockPointCalculator {
    
    /**
     * Calculate all points for a port:
     * - Exit point for each dock
     * - Convergence point (where all exit paths meet)
     * - Split point (where incoming boats diverge to docks)
     */
    public void calculatePortPoints(Port port) {
        List<Dock> docks = port.getDocks();
        
        if (docks.isEmpty()) {
            throw new IllegalStateException("No docks to calculate points for!");
        }
        
        // 1. Find geometric center of all docks
        Location dockCenter = calculateCenter(docks);
        
        // 2. Find direction away from NPC (if NPC exists)
        Vector exitDirection;
        if (port.getNPCLocation() != null) {
            exitDirection = dockCenter.toVector()
                .subtract(port.getNPCLocation().toVector())
                .normalize();
        } else {
            // No NPC yet - use default direction (away from shore)
            exitDirection = findBestExitDirection(dockCenter, docks);
        }
        
        // 3. Calculate exit point for each dock (15 blocks in exit direction)
        for (Dock dock : docks) {
            Location exitPoint = dock.getLocation().clone()
                .add(exitDirection.clone().multiply(15));
            
            exitPoint = ensureWater(exitPoint, 10);
            dock.setExitPoint(exitPoint);
            
            // Entry point is same as exit point (boats arrive from same direction)
            dock.setEntryPoint(exitPoint);
        }
        
        // 4. Convergence point (30 blocks from center in exit direction)
        Location convergencePoint = dockCenter.clone()
            .add(exitDirection.clone().multiply(30));
        convergencePoint = ensureWater(convergencePoint, 15);
        port.setConvergencePoint(convergencePoint);
        
        // 5. Split point (20 blocks from center, for incoming boats)
        Location splitPoint = dockCenter.clone()
            .add(exitDirection.clone().multiply(20));
        splitPoint = ensureWater(splitPoint, 15);
        port.setSplitPoint(splitPoint);
    }
    
    /**
     * Calculate geometric center of all docks
     */
    private Location calculateCenter(List<Dock> docks) {
        double x = 0, y = 0, z = 0;
        World world = docks.get(0).getLocation().getWorld();
        
        for (Dock dock : docks) {
            Location loc = dock.getLocation();
            x += loc.getX();
            y += loc.getY();
            z += loc.getZ();
        }
        
        return new Location(
            world,
            x / docks.size(),
            y / docks.size(),
            z / docks.size()
        );
    }
    
    /**
     * Find best exit direction (away from nearest land)
     */
    private Vector findBestExitDirection(Location center, List<Dock> docks) {
        // Try to find direction with most water
        Vector bestDirection = new Vector(0, 0, 1); // Default: south
        int maxWaterBlocks = 0;
        
        // Check 8 directions
        Vector[] directions = {
            new Vector(1, 0, 0),   // East
            new Vector(-1, 0, 0),  // West
            new Vector(0, 0, 1),   // South
            new Vector(0, 0, -1),  // North
            new Vector(1, 0, 1),   // SE
            new Vector(1, 0, -1),  // NE
            new Vector(-1, 0, 1),  // SW
            new Vector(-1, 0, -1)  // NW
        };
        
        for (Vector dir : directions) {
            int waterCount = countWaterInDirection(center, dir, 30);
            if (waterCount > maxWaterBlocks) {
                maxWaterBlocks = waterCount;
                bestDirection = dir.clone().normalize();
            }
        }
        
        return bestDirection;
    }
    
    /**
     * Count water blocks in a direction
     */
    private int countWaterInDirection(Location start, Vector direction, int distance) {
        int waterCount = 0;
        Location current = start.clone();
        
        for (int i = 0; i < distance; i++) {
            current.add(direction);
            if (isWater(current.getBlock())) {
                waterCount++;
            }
        }
        
        return waterCount;
    }
    
    /**
     * Ensure location is water, or find nearest water within radius
     */
    private Location ensureWater(Location location, int searchRadius) {
        if (isWater(location.getBlock())) {
            return location;
        }
        
        // Search in expanding circles
        return findNearestWater(location, searchRadius);
    }
    
    /**
     * Find nearest water block within radius
     */
    private Location findNearestWater(Location center, int radius) {
        World world = center.getWorld();
        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();
        
        // Search in expanding radius
        for (int r = 1; r <= radius; r++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    // Only check blocks on the edge of current radius
                    if (Math.abs(x) != r && Math.abs(z) != r) continue;
                    
                    Location checkLoc = new Location(world, centerX + x, centerY, centerZ + z);
                    
                    if (isWater(checkLoc.getBlock())) {
                        return checkLoc;
                    }
                    
                    // Also check 1 block down
                    checkLoc.add(0, -1, 0);
                    if (isWater(checkLoc.getBlock())) {
                        return checkLoc;
                    }
                }
            }
        }
        
        // No water found - return original location
        return center;
    }
    
    /**
     * Check if block is water
     */
    private boolean isWater(Block block) {
        Material type = block.getType();
        return type == Material.WATER || type.toString().contains("WATER");
    }
}