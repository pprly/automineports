package com.example.boatroutes.pathfinding;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.cache.WaterWorldCache;
import com.example.boatroutes.port.Port;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * PathfindingManager v6.1 - С BIDIRECTIONAL PATH SUPPORT!
 *
 * ИСПРАВЛЕНО v6.1:
 * - getPath() автоматически разворачивает путь если нужно
 * - getRawPath() тоже поддерживает реверс
 * - hasPath() проверяет обе стороны
 *
 * @author BoatRoutes Team
 * @version 6.1-BIDIRECTIONAL-FIX
 */
public class PathfindingManager {

    private final BoatRoutesPlugin plugin;
    private final WaterWorldCache cache;
    private final WaterPathfinderAStar pathfinder;
    private final PathOptimizer optimizer;
    private final PathStorage storage;

    // RAW PATH CACHE для визуализации!
    private final Map<String, List<Location>> rawPathCache = new HashMap<>();

    public PathfindingManager(BoatRoutesPlugin plugin) {
        this.plugin = plugin;
        this.cache = new WaterWorldCache(plugin);
        this.pathfinder = new WaterPathfinderAStar(plugin, cache);
        this.optimizer = new PathOptimizer(pathfinder.getValidator());
        this.storage = new PathStorage(plugin);

        storage.loadAllPaths();

        plugin.getLogger().info("PathfindingManager initialized with A* v6.1");
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
        player.sendMessage("§6⚓ BoatRoutes Pathfinding");
        player.sendMessage("§7Calculating path...");
        player.sendMessage("");

        long totalStartTime = System.currentTimeMillis();
        long preCacheStart = totalStartTime;

        // ===== PHASE 1: Find navigable water =====
        player.sendMessage("§7Phase 1: Finding navigable water...");

        NavigableWaterFinder navFinder = pathfinder.getNavFinder();
        Location navStart = navFinder.findNavigableWater(portStart, 50);
        Location navEnd = navFinder.findNavigableWater(portEnd, 50);

        if (navStart == null) {
            player.sendMessage("§c✗ Cannot find navigable water near " + fromName + "!");
            return;
        }

        if (navEnd == null) {
            player.sendMessage("§c✗ Cannot find navigable water near " + toName + "!");
            return;
        }

        plugin.getLogger().info("✓ Navigable water found:");
        plugin.getLogger().info("  Start: " + formatLoc(navStart));
        plugin.getLogger().info("  End: " + formatLoc(navEnd));

        player.sendMessage("§a✓ Phase 1 complete");

        // ===== PHASE 2: Minimal pre-cache (только старт/конец) =====
        player.sendMessage("§7Phase 2: Pre-caching start points...");

        int localRadius = 50;

        plugin.getLogger().info("=== MINIMAL PRE-CACHING ===");
        plugin.getLogger().info("  Caching only start/end areas (radius: " + localRadius + ")");

        Location regionStart1 = new Location(navStart.getWorld(),
                navStart.getBlockX() - localRadius, 0, navStart.getBlockZ() - localRadius);
        Location regionStart2 = new Location(navStart.getWorld(),
                navStart.getBlockX() + localRadius, 255, navStart.getBlockZ() + localRadius);
        pathfinder.getValidator().preCacheRegion(regionStart1, regionStart2);

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
        player.sendMessage("§7Phase 3: A* pathfinding + validation...");

        final Location finalNavStart = navStart;
        final Location finalNavEnd = navEnd;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

            long pathStart = System.currentTimeMillis();
            List<Location> rawPath = pathfinder.findPath(finalNavStart, finalNavEnd, finalNavStart.getWorld());
            long pathTime = System.currentTimeMillis() - pathStart;

            if (rawPath == null || rawPath.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§c✗ No path found!");
                    player.sendMessage("§7Possible reasons:");
                    player.sendMessage("§7  - Land blocks the water route");
                    player.sendMessage("§7  - Ports are on different water bodies");
                    player.sendMessage("§7  - Path validation failed (dead ends detected)");
                });
                return;
            }

            plugin.getLogger().info("✓ Raw path found: " + rawPath.size() + " waypoints in " + pathTime + "ms");

            // ===== СОХРАНЯЕМ RAW PATH ДЛЯ ВИЗУАЛИЗАЦИИ! =====
            String routeId = fromName + "_to_" + toName;
            rawPathCache.put(routeId, new ArrayList<>(rawPath));
            plugin.getLogger().info("✓ Cached raw path for visualization: " + rawPath.size() + " waypoints");

            // ===== PHASE 4: Save route (БЕЗ ОПТИМИЗАЦИИ!) =====
            List<Location> finalPath = new ArrayList<>(rawPath);

            // ===== PHASE 5: Save route =====
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage("§a✓ Phase 3 complete");
                player.sendMessage("§7  Waypoints: §f" + finalPath.size() + " (raw path)");
                player.sendMessage("§7  Validation: §apassed (no dead ends)");

                storage.savePath(fromName, toName, finalPath);

                long totalTime = System.currentTimeMillis() - preCacheStart;
                int pathDistance = (int) finalNavStart.distance(finalNavEnd);

                player.sendMessage("");
                player.sendMessage("§a✓ PATH FOUND!");
                player.sendMessage("§7From: §f" + fromName);
                player.sendMessage("§7To: §f" + toName);
                player.sendMessage("§7Distance: §f" + pathDistance + " blocks");
                player.sendMessage("§7Waypoints: §f" + finalPath.size() + " (full path)");
                player.sendMessage("§7Total time: §a" + (totalTime / 1000.0) + "s");
                player.sendMessage("");
                player.sendMessage("§7Use §f/port visualize " + fromName + " §7to see the route!");
            });
        });
    }

    public void recalculatePath(Port fromPort, Port toPort, Player player) {
        String fromName = fromPort.getName();
        String toName = toPort.getName();

        if (storage.hasPath(fromName, toName)) {
            storage.deletePath(fromName, toName);
            rawPathCache.remove(fromName + "_to_" + toName);
            player.sendMessage("§7Deleted old path");
        }

        findPathBetweenPortsAsync(fromPort, toPort, player);
    }

    private String formatLoc(Location loc) {
        return loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    // ===== Публичные методы доступа (С РЕВЕРСОМ!) =====

    /**
     * ✅ ИСПРАВЛЕНО: Получить путь с автоматическим реверсом!
     *
     * Пытается загрузить путь fromPort → toPort
     * Если не найден, пытается toPort → fromPort и разворачивает
     */
    public List<Location> getPath(String fromPort, String toPort) {
        // 1. Пытаемся загрузить прямой путь
        List<Location> path = storage.loadPath(fromPort, toPort);

        if (path != null && !path.isEmpty()) {
            plugin.getLogger().info("✓ Loaded forward path: " + fromPort + " → " + toPort +
                    " (" + path.size() + " waypoints)");
            return path;
        }

        // 2. ✅ НОВОЕ: Пытаемся загрузить ОБРАТНЫЙ путь и развернуть!
        List<Location> reversePath = storage.loadPath(toPort, fromPort);

        if (reversePath != null && !reversePath.isEmpty()) {
            // Разворачиваем путь
            Collections.reverse(reversePath);

            plugin.getLogger().info("✓ Loaded REVERSED path: " + toPort + " → " + fromPort +
                    " (reversed to " + fromPort + " → " + toPort + ", " +
                    reversePath.size() + " waypoints)");

            return reversePath;
        }

        // 3. Путь не найден ни в одну сторону
        plugin.getLogger().warning("✗ No path found: " + fromPort + " ↔ " + toPort);
        return null;
    }

    /**
     * ✅ ИСПРАВЛЕНО: Получить RAW path с автореверсом
     */
    public List<Location> getRawPath(String fromPort, String toPort) {
        String forwardKey = fromPort + "_to_" + toPort;
        String reverseKey = toPort + "_to_" + fromPort;

        // 1. Проверяем прямой raw path
        List<Location> raw = rawPathCache.get(forwardKey);

        if (raw != null) {
            plugin.getLogger().info("✓ Returning raw path from cache: " + raw.size() + " waypoints");
            return new ArrayList<>(raw);
        }

        // 2. ✅ НОВОЕ: Проверяем ОБРАТНЫЙ raw path
        raw = rawPathCache.get(reverseKey);

        if (raw != null) {
            List<Location> reversed = new ArrayList<>(raw);
            Collections.reverse(reversed);

            plugin.getLogger().info("✓ Returning REVERSED raw path from cache: " +
                    reversed.size() + " waypoints");

            return reversed;
        }

        // 3. Fallback: пытаемся загрузить оптимизированный путь
        plugin.getLogger().info("⚠ Raw path not in cache, trying optimized path...");
        return getPath(fromPort, toPort);
    }

    /**
     * ✅ ИСПРАВЛЕНО: Проверка существования пути (В ЛЮБУЮ СТОРОНУ!)
     */
    public boolean hasPath(String fromPort, String toPort) {
        // Проверяем обе стороны
        return storage.hasPath(fromPort, toPort) ||
                storage.hasPath(toPort, fromPort);
    }

    public void deletePath(String fromPort, String toPort) {
        storage.deletePath(fromPort, toPort);
        rawPathCache.remove(fromPort + "_to_" + toPort);

        // ✅ ТАКЖЕ удаляем обратный путь из кеша
        rawPathCache.remove(toPort + "_to_" + fromPort);
    }

    public void clearAllPaths() {
        storage.clearAllPaths();
        rawPathCache.clear();
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