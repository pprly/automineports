package com.example.boatroutes.pathfinding;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.cache.WaterWorldCache;
import com.example.boatroutes.port.Port;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        plugin.getLogger().info("PathfindingManager initialized with A*");
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

        plugin.getLogger().info("? Navigable water found:");
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
        player.sendMessage("§7Phase 3: A* pathfinding...");

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
                });
                return;
            }

            plugin.getLogger().info("✓ Raw path found: " + rawPath.size() + " waypoints in " + pathTime + "ms");

            // ===== СОХРАНЯЕМ RAW PATH ДЛЯ ВИЗУАЛИЗАЦИИ! =====
            String routeId = fromName + "_to_" + toName;
            rawPathCache.put(routeId, new ArrayList<>(rawPath));
            plugin.getLogger().info("✓ Cached raw path for visualization: " + rawPath.size() + " waypoints");

            // ===== PHASE 4: Optimize path =====
            long optimizeStart = System.currentTimeMillis();

            List<Location> optimizedPath = optimizer.optimize(rawPath);

            long optimizeTime = System.currentTimeMillis() - optimizeStart;

            // ===== PHASE 5: Save route =====
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage("§a✓ Phase 4 complete");
                player.sendMessage("§7  Optimized: §f" + optimizedPath.size() + " waypoints");
                player.sendMessage("§7  Time: §f" + (optimizeTime / 1000.0) + "s");

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

    // ===== Публичные методы доступа =====

    public List<Location> getPath(String fromPort, String toPort) {
        return storage.loadPath(fromPort, toPort);
    }

    /**
     * НОВЫЙ МЕТОД: Получить RAW path для визуализации!
     */
    public List<Location> getRawPath(String fromPort, String toPort) {
        String key = fromPort + "_to_" + toPort;
        List<Location> raw = rawPathCache.get(key);

        if (raw != null) {
            plugin.getLogger().info("✓ Returning raw path from cache: " + raw.size() + " waypoints");
            return new ArrayList<>(raw);
        }

        plugin.getLogger().info("⚠ Raw path not in cache, returning optimized path");
        return storage.loadPath(fromPort, toPort);
    }

    public boolean hasPath(String fromPort, String toPort) {
        return storage.hasPath(fromPort, toPort);
    }

    public void deletePath(String fromPort, String toPort) {
        storage.deletePath(fromPort, toPort);
        rawPathCache.remove(fromPort + "_to_" + toPort);
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