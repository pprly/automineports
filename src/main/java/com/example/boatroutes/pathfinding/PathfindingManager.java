package com.example.boatroutes.pathfinding;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.cache.WaterWorldCache;
import com.example.boatroutes.port.Port;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Pathfinding Manager - теперь с A*!
 * ИСПРАВЛЕННАЯ ВЕРСИЯ - совместима с существующим кодом
 * 
 * WORKFLOW:
 * 1. Find navigable water (SYNC)
 * 2. Pre-cache smart region (SYNC)
 * 3. Run A* pathfinding (ASYNC)
 * 4. Optimize path (ASYNC)
 * 5. Save route (SYNC)
 * 
 * @author BoatRoutes Team
 * @version 5.0-FIXED
 */
public class PathfindingManager {
    
    private final BoatRoutesPlugin plugin;
    private final WaterPathfinderAStar pathfinder; // A* вместо BFS!
    private final PathOptimizer optimizer;
    private final PathStorage storage;
    private final WaterWorldCache cache;
    
    public PathfindingManager(BoatRoutesPlugin plugin) {
        this.plugin = plugin;
        this.cache = new WaterWorldCache(plugin);
        this.pathfinder = new WaterPathfinderAStar(plugin, cache); // A*!
        
        // ИСПРАВЛЕНО: PathOptimizer требует PathValidator
        this.optimizer = new PathOptimizer(pathfinder.getValidator());
        this.storage = new PathStorage(plugin);
        
        // Load existing routes
        storage.loadAllPaths();
        
        plugin.getLogger().info("PathfindingManager v5.0 (A*) initialized");
    }
    
    /**
     * Главный метод - поиск пути между портами (async)
     */
    public void findPathBetweenPortsAsync(Port fromPort, Port toPort, Player player) {
        String fromName = fromPort.getName();
        String toName = toPort.getName();
        
        player.sendMessage("§e⚓ BoatRoutes Pathfinding v5.0 (A*)");
        player.sendMessage("§7Starting path calculation...");
        player.sendMessage("");
        
        // ИСПРАВЛЕНО: используем getNPCLocation() (с большой NPC)
        Location portStart = fromPort.getNPCLocation();
        Location portEnd = toPort.getNPCLocation();
        
        if (portStart == null || portEnd == null) {
            player.sendMessage("§cPorts missing NPC locations!");
            return;
        }
        
        // Используем convergence/split points если есть
        if (fromPort.getConvergencePoint() != null) {
            portStart = fromPort.getConvergencePoint();
        }
        if (toPort.getSplitPoint() != null) {
            portEnd = toPort.getSplitPoint();
        }
        
        // ===== PHASE 1: Find navigable water =====
        player.sendMessage("§7Phase 1: Finding navigable water...");
        
        // ИСПРАВЛЕНО: используем правильную сигнатуру (Location, int)
        NavigableWaterFinder navFinder = pathfinder.getNavFinder();
        Location navStart = navFinder.findNavigableWater(portStart, 50);
        Location navEnd = navFinder.findNavigableWater(portEnd, 50);
        
        if (navStart == null) {
            player.sendMessage("§c✗ Cannot find navigable water near " + fromName + "!");
            player.sendMessage("§7The port may be in a closed bay or too far from open water.");
            return;
        }
        
        if (navEnd == null) {
            player.sendMessage("§c✗ Cannot find navigable water near " + toName + "!");
            player.sendMessage("§7The port may be in a closed bay or too far from open water.");
            return;
        }
        
        plugin.getLogger().info("✓ Navigable water found:");
        plugin.getLogger().info("  Start: " + formatLoc(navStart));
        plugin.getLogger().info("  End: " + formatLoc(navEnd));
        
        player.sendMessage("§a✓ Phase 1 complete");
        
        // ===== PHASE 2: Minimal pre-cache (только старт/конец) =====
        player.sendMessage("§7Phase 2: Pre-caching start points...");
        
        long preCacheStart = System.currentTimeMillis();
        
        // Кешируем ТОЛЬКО область вокруг стартовых точек (50 блоков)
        // A* будет динамически кешировать остальное во время поиска!
        int localRadius = 50;
        
        plugin.getLogger().info("=== MINIMAL PRE-CACHING ===");
        plugin.getLogger().info("  Caching only start/end areas (radius: " + localRadius + ")");
        
        // Cache around start
        Location regionStart1 = new Location(navStart.getWorld(), 
            navStart.getBlockX() - localRadius, 0, navStart.getBlockZ() - localRadius);
        Location regionStart2 = new Location(navStart.getWorld(), 
            navStart.getBlockX() + localRadius, 255, navStart.getBlockZ() + localRadius);
        pathfinder.getValidator().preCacheRegion(regionStart1, regionStart2);
        
        // Cache around end
        Location regionEnd1 = new Location(navEnd.getWorld(), 
            navEnd.getBlockX() - localRadius, 0, navEnd.getBlockZ() - localRadius);
        Location regionEnd2 = new Location(navEnd.getWorld(), 
            navEnd.getBlockX() + localRadius, 255, navEnd.getBlockZ() + localRadius);
        pathfinder.getValidator().preCacheRegion(regionEnd1, regionEnd2);
        
        long preCacheTime = System.currentTimeMillis() - preCacheStart;
        
        int cachedBlocks = cache.getCachedBlockCount();
        
        player.sendMessage("§a✓ Phase 2 complete");
        player.sendMessage("§7  Cached: §f" + cachedBlocks + " blocks (start/end areas)");
        player.sendMessage("§7  Time: §f" + (preCacheTime / 1000.0) + "s");
        player.sendMessage("§7Note: A* will cache dynamically during search");
        
        // ===== PHASE 3: Async A* pathfinding (UNLIMITED!) =====
        player.sendMessage("§7Phase 3: A* pathfinding (async)...");
        
        final Location finalNavStart = navStart;
        final Location finalNavEnd = navEnd;
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            
            long astarStart = System.currentTimeMillis();
            List<Location> rawPath = pathfinder.findPath(finalNavStart, finalNavEnd, 
                finalNavStart.getWorld());
            long astarTime = System.currentTimeMillis() - astarStart;
            
            if (rawPath == null || rawPath.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§c✗ No path found!");
                    player.sendMessage("§7There may be no water route between these ports.");
                    player.sendMessage("§7Try using /port visualize to check port placement.");
                });
                return;
            }
            
            // ===== PHASE 4: Optimize path =====
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage("§a✓ Phase 3 complete");
                player.sendMessage("§7  Raw waypoints: §f" + rawPath.size());
                player.sendMessage("§7  Time: §f" + (astarTime / 1000.0) + "s");
                player.sendMessage("§7Phase 4: Optimizing path...");
            });
            
            long optimizeStart = System.currentTimeMillis();
            
            // Оптимизируем путь
            List<Location> optimizedPath = optimizer.optimize(rawPath);
            
            long optimizeTime = System.currentTimeMillis() - optimizeStart;
            
            // ===== PHASE 5: Save route =====
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage("§a✓ Phase 4 complete");
                player.sendMessage("§7  Optimized: §f" + optimizedPath.size() + " waypoints");
                player.sendMessage("§7  Time: §f" + (optimizeTime / 1000.0) + "s");
                
                // Save the path
                storage.savePath(fromName, toName, optimizedPath);
                
                long totalTime = System.currentTimeMillis() - preCacheStart;
                int pathDistance = (int) finalNavStart.distance(finalNavEnd);
                
                player.sendMessage("");
                player.sendMessage("§a✓ PATH FOUND!");
                player.sendMessage("§7From: §f" + fromName);
                player.sendMessage("§7To: §f" + toName);
                player.sendMessage("§7Distance: §f" + pathDistance + " blocks");
                player.sendMessage("§7Waypoints: §f" + optimizedPath.size());
                player.sendMessage("§7Total time: §a" + (totalTime / 1000.0) + "s");
                player.sendMessage("");
                player.sendMessage("§7Use §f/port visualize " + fromName + " §7to see the route!");
            });
        });
    }
    
    /**
     * Пересчёт пути (удаляет старый и создаёт новый)
     */
    public void recalculatePath(Port fromPort, Port toPort, Player player) {
        String fromName = fromPort.getName();
        String toName = toPort.getName();
        
        // Удаляем старый путь
        if (storage.hasPath(fromName, toName)) {
            storage.deletePath(fromName, toName);
            player.sendMessage("§7Deleted old path");
        }
        
        // Создаём новый
        findPathBetweenPortsAsync(fromPort, toPort, player);
    }
    
    /**
     * Создаёт простой прямой путь (для fallback)
     */
    public List<Location> createSimplePath(Location from, Location to) {
        List<Location> path = new ArrayList<>();
        
        double distance = from.distance(to);
        int steps = (int) Math.ceil(distance / 2);
        int seaLevel = pathfinder.getSeaLevel();
        
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            
            double x = from.getX() + (to.getX() - from.getX()) * t;
            double z = from.getZ() + (to.getZ() - from.getZ()) * t;
            
            Location point = new Location(from.getWorld(), x, seaLevel, z);
            path.add(point);
        }
        
        return path;
    }
    
    private String formatLoc(Location loc) {
        return loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }
    
    // ===== Публичные методы доступа =====
    
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
    
    public WaterPathfinderAStar getPathfinder() {
        return pathfinder;
    }
    
    public PathStorage getStorage() {
        return storage;
    }
    
    public WaterWorldCache getCache() {
        return cache;
    }
    
    public PathOptimizer getOptimizer() {
        return optimizer;
    }
}
