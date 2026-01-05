package com.example.boatroutes.pathfinding;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.cache.WaterWorldCache;
import com.example.boatroutes.port.Port;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Pathfinding manager v3.2 - Radius-based pre-caching
 * 
 * Pre-caches a RADIUS around each port instead of a bounding box.
 * This allows paths to go around continents/islands in any direction!
 */
public class PathfindingManager {
    
    private final BoatRoutesPlugin plugin;
    private final WaterWorldCache cache;
    private final WaterPathfinder pathfinder;
    private final PathOptimizer optimizer;
    private final PathStorage storage;
    
    public PathfindingManager(BoatRoutesPlugin plugin) {
        this.plugin = plugin;
        this.cache = new WaterWorldCache(plugin);
        this.pathfinder = new WaterPathfinder(plugin, cache);
        this.optimizer = new PathOptimizer(pathfinder.getValidator());
        this.storage = new PathStorage(plugin);
    }
    
    public void findPathBetweenPortsAsync(Port fromPort, Port toPort, Player player) {
        String fromName = fromPort.getName();
        String toName = toPort.getName();
        
        if (storage.hasPath(fromName, toName)) {
            player.sendMessage("§e⚠ Path already exists!");
            player.sendMessage("§7Use §e/port reconnect " + fromName + " " + toName + " §7to recalculate");
            return;
        }
        
        Location portStart = fromPort.getConvergencePoint();
        Location portEnd = toPort.getSplitPoint();
        
        if (portStart == null || portEnd == null) {
            player.sendMessage("§cPorts missing convergence/split points!");
            return;
        }
        
        player.sendMessage("");
        player.sendMessage("§6⚓ v3.2: Radius-based pathfinding");
        player.sendMessage("§7This may take 10-60 seconds");
        player.sendMessage("");
        
        long totalStartTime = System.currentTimeMillis();
        
        // PHASE 1: Find navigable water
        player.sendMessage("§7Phase 1: Finding navigable water exits...");
        
        NavigableWaterFinder navFinder = pathfinder.getNavFinder();
        Location navStart = navFinder.findNavigableWater(portStart, 50);
        Location navEnd = navFinder.findNavigableWater(portEnd, 50);
        
        if (navStart == null || navEnd == null) {
            player.sendMessage("§c✗ Cannot find navigable water!");
            return;
        }
        
        player.sendMessage("§a✓ Phase 1 complete");
        
        // PHASE 2: Pre-cache RADIUS around both points
        player.sendMessage("§7Phase 2: Pre-caching regions (radius-based)...");
        
        long preCacheStart = System.currentTimeMillis();
        
        // Get radius from config (default 500 blocks)
        int radius = plugin.getConfig().getInt("pathfinding.pre-cache-radius", 500);
        
        int distance = (int) navStart.distance(navEnd);
        
        plugin.getLogger().info("Pre-caching with radius approach:");
        plugin.getLogger().info("  Distance: " + distance + " blocks");
        plugin.getLogger().info("  Radius: " + radius + " blocks");
        
        // Pre-cache radius around START
        Location regionMin1 = new Location(navStart.getWorld(), 
            navStart.getBlockX() - radius, 62, navStart.getBlockZ() - radius);
        Location regionMax1 = new Location(navStart.getWorld(),
            navStart.getBlockX() + radius, 62, navStart.getBlockZ() + radius);
        
        pathfinder.getValidator().preCacheRegion(regionMin1, regionMax1);
        
        // Pre-cache radius around END
        Location regionMin2 = new Location(navEnd.getWorld(),
            navEnd.getBlockX() - radius, 62, navEnd.getBlockZ() - radius);
        Location regionMax2 = new Location(navEnd.getWorld(),
            navEnd.getBlockX() + radius, 62, navEnd.getBlockZ() + radius);
        
        pathfinder.getValidator().preCacheRegion(regionMin2, regionMax2);
        
        long preCacheTime = System.currentTimeMillis() - preCacheStart;
        
        double coverage = cache.getCoveragePercent(navStart, navEnd);
        
        player.sendMessage("§a✓ Phase 2 complete: Regions cached");
        player.sendMessage("§7  Radius: §f" + radius + " blocks");
        player.sendMessage("§7  Pre-cache time: §f" + (preCacheTime / 1000.0) + "s");
        player.sendMessage("§7  Cache coverage: §a" + String.format("%.1f%%", coverage));
        
        // PHASE 3: Async BFS
        player.sendMessage("§7Phase 3: BFS pathfinding (async)...");
        
        final Location finalNavStart = navStart;
        final Location finalNavEnd = navEnd;
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            
            List<Location> rawPath = pathfinder.findPath(finalNavStart, finalNavEnd, player);
            
            if (rawPath == null || rawPath.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§c✗ No path found!");
                    player.sendMessage("§7Path may require more than " + radius + " blocks radius");
                    player.sendMessage("§7Try increasing 'pre-cache-radius' in config.yml");
                });
                return;
            }
            
            // Back to sync for optimization and saving
            Bukkit.getScheduler().runTask(plugin, () -> {
                
                player.sendMessage("§7Optimizing path...");
                
                List<Location> optimizedPath = optimizer.optimizePath(rawPath);
                double reduction = optimizer.calculateReduction(rawPath.size(), optimizedPath.size());
                
                // Add entry/exit paths
                List<Location> exitPath = createSimplePath(portStart, finalNavStart);
                List<Location> entryPath = createSimplePath(finalNavEnd, portEnd);
                
                List<Location> fullPath = new java.util.ArrayList<>();
                fullPath.addAll(exitPath);
                fullPath.addAll(optimizedPath);
                fullPath.addAll(entryPath);
                
                storage.savePath(fromName, toName, fullPath);
                cache.saveCache();
                
                long totalTime = System.currentTimeMillis() - totalStartTime;
                
                plugin.getLogger().info("=== PATH COMPLETE ===");
                plugin.getLogger().info("Exit: " + exitPath.size() + ", Main: " + optimizedPath.size() + ", Entry: " + entryPath.size());
                plugin.getLogger().info("Total waypoints: " + fullPath.size());
                plugin.getLogger().info("Total time: " + (totalTime / 1000.0) + "s");
                
                player.sendMessage("");
                player.sendMessage("§a✓ Path calculation complete!");
                player.sendMessage("§7  Total waypoints: §f" + fullPath.size());
                player.sendMessage("§7  Optimization: §f" + (int)reduction + "%");
                player.sendMessage("§7  Total time: §f" + (totalTime / 1000.0) + "s");
                player.sendMessage("§7💾 Saved to routes.yml + water_cache.yml");
            });
        });
    }
    
    private List<Location> createSimplePath(Location from, Location to) {
        List<Location> path = new java.util.ArrayList<>();
        
        double distance = from.distance(to);
        int steps = (int) Math.ceil(distance / 2);
        
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            
            double x = from.getX() + (to.getX() - from.getX()) * t;
            double z = from.getZ() + (to.getZ() - from.getZ()) * t;
            
            Location point = new Location(from.getWorld(), x, 62, z);
            path.add(point);
        }
        
        return path;
    }
    
    private String formatLoc(Location loc) {
        return loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }
    
    public List<Location> getPath(String fromPort, String toPort) {
        return storage.loadPath(fromPort, toPort);
    }
    
    public boolean hasPath(String fromPort, String toPort) {
        return storage.hasPath(fromPort, toPort);
    }
    
    public void deletePath(String fromPort, String toPort) {
        storage.deletePath(fromPort, toPort);
    }
    
    public void clearAllPaths() {
        storage.clearAllPaths();
    }
    
    public void loadAllPaths() {
        storage.loadAllPaths();
    }
    
    public void saveCache() {
        cache.saveCache();
    }
    
    public WaterPathfinder getPathfinder() {
        return pathfinder;
    }
    
    public PathStorage getStorage() {
        return storage;
    }
    
    public WaterWorldCache getCache() {
        return cache;
    }
}
