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
 * PathfindingManager v4.0 - Исправленная версия
 * 
 * Ключевые изменения:
 * - ДИНАМИЧЕСКИЙ радиус кеширования (зависит от расстояния)
 * - Умное кеширование: только нужная область между портами
 * - Быстрое предварительное кеширование
 * - Поддержка непрогруженных чанков через кеш
 * 
 * @author BoatRoutes Team
 * @version 4.0
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
        
        // Загружаем сохранённые пути при старте
        storage.loadAllPaths();
        
        plugin.getLogger().info("PathfindingManager v4.0 initialized");
    }
    
    /**
     * Главный метод поиска пути между портами (async)
     */
    public void findPathBetweenPortsAsync(Port fromPort, Port toPort, Player player) {
        String fromName = fromPort.getName();
        String toName = toPort.getName();
        
        // Проверяем существующий путь
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
        player.sendMessage("§6⚓ BoatRoutes Pathfinding v4.0");
        player.sendMessage("§7Starting path calculation...");
        player.sendMessage("");
        
        long totalStartTime = System.currentTimeMillis();
        
        // ===== PHASE 1: Find navigable water =====
        player.sendMessage("§7Phase 1: Finding navigable water...");
        
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
        
        // ===== PHASE 2: Smart pre-caching =====
        player.sendMessage("§7Phase 2: Pre-caching water data...");
        
        long preCacheStart = System.currentTimeMillis();
        
        int distance = (int) navStart.distance(navEnd);
        
        // КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ: Динамический радиус!
        // Формула: radius = max(distance * 1.5, 100), но не более 500
        int dynamicRadius = Math.min(Math.max((int)(distance * 1.5), 100), 500);
        
        plugin.getLogger().info("=== SMART CACHING ===");
        plugin.getLogger().info("  Distance: " + distance + " blocks");
        plugin.getLogger().info("  Dynamic radius: " + dynamicRadius + " blocks (NOT 3000!)");
        
        // Кешируем ТОЛЬКО область между портами + небольшой буфер
        preCacheSmartRegion(navStart, navEnd, dynamicRadius);
        
        long preCacheTime = System.currentTimeMillis() - preCacheStart;
        
        int cachedBlocks = cache.getCachedBlockCount();
        double coverage = cache.getCoveragePercent(navStart, navEnd);
        
        player.sendMessage("§a✓ Phase 2 complete");
        player.sendMessage("§7  Cached: §f" + cachedBlocks + " blocks");
        player.sendMessage("§7  Coverage: §a" + String.format("%.1f%%", coverage));
        player.sendMessage("§7  Time: §f" + (preCacheTime / 1000.0) + "s");
        
        // ===== PHASE 3: Async BFS pathfinding =====
        player.sendMessage("§7Phase 3: BFS pathfinding (async)...");
        
        final Location finalNavStart = navStart;
        final Location finalNavEnd = navEnd;
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            
            long bfsStart = System.currentTimeMillis();
            List<Location> rawPath = pathfinder.findPath(finalNavStart, finalNavEnd, player);
            long bfsTime = System.currentTimeMillis() - bfsStart;
            
            if (rawPath == null || rawPath.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§c✗ No path found!");
                    player.sendMessage("§7Possible reasons:");
                    player.sendMessage("§7  - Land blocks the water route");
                    player.sendMessage("§7  - Ports are on different water bodies");
                    player.sendMessage("§7  - Try: §e/port find-nav " + fromName);
                });
                return;
            }
            
            plugin.getLogger().info("✓ Raw path found: " + rawPath.size() + " waypoints in " + bfsTime + "ms");
            
            // ===== PHASE 4: Optimize path =====
            List<Location> optimizedPath = optimizer.optimize(rawPath);
            
            plugin.getLogger().info("✓ Optimized path: " + optimizedPath.size() + " waypoints");
            
            // ===== PHASE 5: Build full path =====
            List<Location> fullPath = new ArrayList<>();
            
            // Add dock exit segment (from port)
            fullPath.add(portStart.clone());
            fullPath.add(finalNavStart.clone());
            
            // Add main path
            fullPath.addAll(optimizedPath);
            
            // Add dock entry segment (to port)
            fullPath.add(finalNavEnd.clone());
            fullPath.add(portEnd.clone());
            
            // ===== PHASE 6: Save =====
            storage.savePath(fromName, toName, fullPath);
            cache.saveCache();
            
            long totalTime = System.currentTimeMillis() - totalStartTime;
            double reduction = rawPath.size() > 0 ? 
                (1 - (double) optimizedPath.size() / rawPath.size()) * 100 : 0;
            
            // Report success
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage("");
                player.sendMessage("§a§l✓ PATH FOUND!");
                player.sendMessage("§7  From: §f" + fromName);
                player.sendMessage("§7  To: §f" + toName);
                player.sendMessage("§7  Distance: §f" + distance + " blocks");
                player.sendMessage("§7  Waypoints: §f" + fullPath.size());
                player.sendMessage("§7  Optimization: §f" + (int)reduction + "% reduced");
                player.sendMessage("§7  Total time: §f" + (totalTime / 1000.0) + "s");
                player.sendMessage("§7💾 Saved to routes.yml");
                player.sendMessage("");
            });
        });
    }
    
    /**
     * Умное кеширование - только область между портами
     */
    private void preCacheSmartRegion(Location start, Location end, int buffer) {
        int minX = Math.min(start.getBlockX(), end.getBlockX()) - buffer;
        int maxX = Math.max(start.getBlockX(), end.getBlockX()) + buffer;
        int minZ = Math.min(start.getBlockZ(), end.getBlockZ()) - buffer;
        int maxZ = Math.max(start.getBlockZ(), end.getBlockZ()) + buffer;
        
        int seaLevel = pathfinder.getSeaLevel();
        
        Location regionMin = new Location(start.getWorld(), minX, seaLevel, minZ);
        Location regionMax = new Location(start.getWorld(), maxX, seaLevel, maxZ);
        
        int blocksToCache = (maxX - minX) * (maxZ - minZ);
        int chunksToCache = blocksToCache / 256; // 16x16 блоков в чанке
        
        plugin.getLogger().info("Pre-caching region:");
        plugin.getLogger().info("  From: " + minX + "," + minZ + " to " + maxX + "," + maxZ);
        plugin.getLogger().info("  ~" + chunksToCache + " chunks (was 141,376 with radius 3000!)");
        
        pathfinder.getValidator().preCacheRegion(regionMin, regionMax);
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
    
    public WaterPathfinder getPathfinder() {
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
