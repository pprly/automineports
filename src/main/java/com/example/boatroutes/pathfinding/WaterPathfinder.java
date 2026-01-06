package com.example.boatroutes.pathfinding;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.cache.WaterWorldCache;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * WaterPathfinder v4.0 - Исправленная версия
 * 
 * Ключевые изменения:
 * - Фиксированный Y=62 (sea level) для всего пути
 * - Bidirectional BFS для быстрого поиска
 * - Использует кеш для непрогруженных чанков
 * - Динамический радиус кеширования
 * 
 * @author BoatRoutes Team
 * @version 4.0
 */
public class WaterPathfinder {
    
    private final BoatRoutesPlugin plugin;
    private final WaterWorldCache cache;
    private final PathValidator validator;
    private final NavigableWaterFinder navFinder;
    
    // Настройки из config
    private int seaLevel;
    private int maxIterations;
    private int stepSize;
    
    // 8 направлений движения (только горизонтально!)
    private static final int[][] DIRECTIONS = {
        {1, 0},   // East
        {-1, 0},  // West
        {0, 1},   // South
        {0, -1},  // North
        {1, 1},   // SE
        {1, -1},  // NE
        {-1, 1},  // SW
        {-1, -1}  // NW
    };
    
    public WaterPathfinder(BoatRoutesPlugin plugin, WaterWorldCache cache) {
        this.plugin = plugin;
        this.cache = cache;
        this.validator = new PathValidator(plugin, cache);
        this.navFinder = new NavigableWaterFinder(plugin, validator);
        
        reloadConfig();
    }
    
    public void reloadConfig() {
        this.seaLevel = plugin.getConfig().getInt("pathfinding.sea-level", 62);
        this.maxIterations = plugin.getConfig().getInt("pathfinding.max-iterations", 50000);
        this.stepSize = plugin.getConfig().getInt("pathfinding.step-size", 2);
    }
    
    /**
     * Основной метод поиска пути между двумя точками.
     * Использует Bidirectional BFS для максимальной скорости.
     * 
     * @param start Начальная точка
     * @param end Конечная точка
     * @param player Игрок для отправки сообщений (может быть null)
     * @return Список точек пути или null если путь не найден
     */
    public List<Location> findPath(Location start, Location end, Player player) {
        World world = start.getWorld();
        
        // Нормализуем координаты на sea level
        int startX = start.getBlockX();
        int startZ = start.getBlockZ();
        int endX = end.getBlockX();
        int endZ = end.getBlockZ();
        
        // Логируем начало поиска
        plugin.getLogger().info("=== BFS PATHFINDING v4.0 ===");
        plugin.getLogger().info("From: " + startX + "," + seaLevel + "," + startZ);
        plugin.getLogger().info("To: " + endX + "," + seaLevel + "," + endZ);
        
        int distance = (int) Math.sqrt(Math.pow(endX - startX, 2) + Math.pow(endZ - startZ, 2));
        plugin.getLogger().info("Distance: " + distance + " blocks");
        
        // Проверяем начальную и конечную точки
        if (!validator.isNavigableWater(startX, seaLevel, startZ, world)) {
            plugin.getLogger().warning("Start point is not navigable water!");
            if (player != null) player.sendMessage("§c✗ Start point is not water!");
            return null;
        }
        
        if (!validator.isNavigableWater(endX, seaLevel, endZ, world)) {
            plugin.getLogger().warning("End point is not navigable water!");
            if (player != null) player.sendMessage("§c✗ End point is not water!");
            return null;
        }
        
        // Для очень коротких расстояний - прямой путь
        if (distance < 20) {
            plugin.getLogger().info("Short distance - trying direct path");
            List<Location> directPath = tryDirectPath(world, startX, startZ, endX, endZ);
            if (directPath != null) {
                plugin.getLogger().info("✓ Direct path found! " + directPath.size() + " waypoints");
                return directPath;
            }
        }
        
        // Bidirectional BFS
        long startTime = System.currentTimeMillis();
        List<Location> path = bidirectionalBFS(world, startX, startZ, endX, endZ);
        long elapsed = System.currentTimeMillis() - startTime;
        
        if (path != null) {
            plugin.getLogger().info("✓ BFS SUCCESS in " + elapsed + "ms!");
            plugin.getLogger().info("  Path length: " + path.size() + " waypoints");
        } else {
            plugin.getLogger().warning("✗ BFS FAILED after " + elapsed + "ms");
        }
        
        return path;
    }
    
    /**
     * Bidirectional BFS - ищет путь с двух сторон одновременно.
     * Работает в 2-4 раза быстрее обычного BFS.
     */
    private List<Location> bidirectionalBFS(World world, int startX, int startZ, int endX, int endZ) {
        
        // Очереди для поиска с обеих сторон
        Queue<long[]> queueStart = new LinkedList<>();
        Queue<long[]> queueEnd = new LinkedList<>();
        
        // Visited maps: key -> parent key
        Map<Long, Long> visitedFromStart = new HashMap<>();
        Map<Long, Long> visitedFromEnd = new HashMap<>();
        
        // Начальные точки
        long startKey = packCoords(startX, startZ);
        long endKey = packCoords(endX, endZ);
        
        queueStart.add(new long[]{startX, startZ});
        queueEnd.add(new long[]{endX, endZ});
        
        visitedFromStart.put(startKey, -1L); // -1 = начало
        visitedFromEnd.put(endKey, -1L);
        
        int iterations = 0;
        Long meetingPoint = null;
        long lastLogTime = System.currentTimeMillis();
        
        plugin.getLogger().info("BFS starting... maxIterations=" + maxIterations);
        
        // Альтернируем поиск с двух сторон
        while (!queueStart.isEmpty() || !queueEnd.isEmpty()) {
            iterations++;
            
            // Логируем прогресс каждые 2 секунды
            if (System.currentTimeMillis() - lastLogTime > 2000) {
                plugin.getLogger().info("BFS progress: iter=" + iterations + 
                    ", qStart=" + queueStart.size() + 
                    ", qEnd=" + queueEnd.size() +
                    ", visitedStart=" + visitedFromStart.size() +
                    ", visitedEnd=" + visitedFromEnd.size());
                lastLogTime = System.currentTimeMillis();
            }
            
            if (iterations > maxIterations) {
                plugin.getLogger().warning("BFS exceeded max iterations: " + maxIterations);
                plugin.getLogger().warning("  Visited from start: " + visitedFromStart.size());
                plugin.getLogger().warning("  Visited from end: " + visitedFromEnd.size());
                break;
            }
            
            // Шаг со стороны старта
            if (!queueStart.isEmpty()) {
                meetingPoint = bfsStep(world, queueStart, visitedFromStart, visitedFromEnd);
                if (meetingPoint != null) {
                    plugin.getLogger().info("BFS: Meeting point found from START side!");
                    break;
                }
            }
            
            // Шаг со стороны конца
            if (!queueEnd.isEmpty()) {
                meetingPoint = bfsStep(world, queueEnd, visitedFromEnd, visitedFromStart);
                if (meetingPoint != null) {
                    plugin.getLogger().info("BFS: Meeting point found from END side!");
                    break;
                }
            }
            
            // Проверяем на пустые очереди (путь невозможен)
            if (queueStart.isEmpty() && queueEnd.isEmpty()) {
                plugin.getLogger().warning("BFS: Both queues empty - no path possible!");
                break;
            }
        }
        
        plugin.getLogger().info("BFS finished: iterations=" + iterations + ", found=" + (meetingPoint != null));
        
        if (meetingPoint == null) {
            return null;
        }
        
        // Восстанавливаем путь
        return reconstructPath(world, meetingPoint, visitedFromStart, visitedFromEnd, startKey, endKey);
    }
    
    /**
     * Один шаг BFS - обрабатываем один уровень очереди
     */
    private Long bfsStep(World world, Queue<long[]> queue, 
                         Map<Long, Long> visited, Map<Long, Long> otherVisited) {
        
        int levelSize = queue.size();
        int addedCount = 0;
        int blockedCount = 0;
        
        for (int i = 0; i < levelSize; i++) {
            long[] current = queue.poll();
            if (current == null) continue;
            
            int x = (int) current[0];
            int z = (int) current[1];
            long currentKey = packCoords(x, z);
            
            // Проверяем соседей
            for (int[] dir : DIRECTIONS) {
                int nx = x + dir[0] * stepSize;
                int nz = z + dir[1] * stepSize;
                long neighborKey = packCoords(nx, nz);
                
                // Уже посещён с нашей стороны?
                if (visited.containsKey(neighborKey)) continue;
                
                // Проверяем, можно ли идти туда
                if (!validator.isNavigableWater(nx, seaLevel, nz, world)) {
                    blockedCount++;
                    continue;
                }
                
                // Добавляем в visited
                visited.put(neighborKey, currentKey);
                addedCount++;
                
                // Встретились с другой стороной?
                if (otherVisited.containsKey(neighborKey)) {
                    plugin.getLogger().info("BFS MATCH at " + nx + "," + nz + " after " + visited.size() + " nodes");
                    return neighborKey;
                }
                
                queue.add(new long[]{nx, nz});
            }
        }
        
        return null;
    }
    
    /**
     * Восстанавливаем путь от точки встречи
     */
    private List<Location> reconstructPath(World world, long meetingPoint,
                                           Map<Long, Long> visitedFromStart,
                                           Map<Long, Long> visitedFromEnd,
                                           long startKey, long endKey) {
        
        List<Location> pathFromStart = new ArrayList<>();
        List<Location> pathFromEnd = new ArrayList<>();
        
        // Путь от meeting point к старту (reverse)
        Long current = meetingPoint;
        while (current != null && current != -1L && current != startKey) {
            int[] coords = unpackCoords(current);
            pathFromStart.add(new Location(world, coords[0] + 0.5, seaLevel, coords[1] + 0.5));
            current = visitedFromStart.get(current);
        }
        // Добавляем старт
        int[] startCoords = unpackCoords(startKey);
        pathFromStart.add(new Location(world, startCoords[0] + 0.5, seaLevel, startCoords[1] + 0.5));
        
        // Разворачиваем путь от старта
        Collections.reverse(pathFromStart);
        
        // Путь от meeting point к концу
        current = visitedFromEnd.get(meetingPoint);
        while (current != null && current != -1L) {
            int[] coords = unpackCoords(current);
            pathFromEnd.add(new Location(world, coords[0] + 0.5, seaLevel, coords[1] + 0.5));
            current = visitedFromEnd.get(current);
        }
        
        // Объединяем пути
        pathFromStart.addAll(pathFromEnd);
        
        return pathFromStart;
    }
    
    /**
     * Пробуем прямой путь (для коротких расстояний)
     */
    private List<Location> tryDirectPath(World world, int startX, int startZ, int endX, int endZ) {
        List<Location> path = new ArrayList<>();
        
        double dx = endX - startX;
        double dz = endZ - startZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        
        int steps = (int) Math.ceil(distance / stepSize);
        
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            int x = (int) (startX + dx * t);
            int z = (int) (startZ + dz * t);
            
            if (!validator.isNavigableWater(x, seaLevel, z, world)) {
                return null; // Прямой путь заблокирован
            }
            
            path.add(new Location(world, x + 0.5, seaLevel, z + 0.5));
        }
        
        return path;
    }
    
    /**
     * Упаковка координат X,Z в long для быстрого хеширования
     */
    private long packCoords(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
    
    /**
     * Распаковка координат из long
     */
    private int[] unpackCoords(long packed) {
        int x = (int) (packed >> 32);
        int z = (int) packed;
        return new int[]{x, z};
    }
    
    // Геттеры
    public PathValidator getValidator() {
        return validator;
    }
    
    public NavigableWaterFinder getNavFinder() {
        return navFinder;
    }
    
    public int getSeaLevel() {
        return seaLevel;
    }
}
