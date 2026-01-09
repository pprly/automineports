package com.example.boatroutes.pathfinding;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.cache.WaterWorldCache;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;

/**
 * BIDIRECTIONAL A* v6.0 - С COST-BASED PATHFINDING!
 * 
 * НОВОЕ В v6.0:
 * - Cost-based pathfinding: учитывает стоимость блоков
 * - Предпочитает глубокую воду (cost=1)
 * - Избегает берегов (cost=5)
 * - НО всё равно идёт через узкий пролив если это короче!
 * 
 * ПРИМЕР:
 * Узкий пролив (100 блоков, cost=5): 100 * 5 = 500
 * Обход (5000 блоков, cost=1): 5000 * 1 = 5000
 * → Выбрать ПРОЛИВ! ✅
 * 
 * A* сам находит оптимальный баланс между:
 * - Коротким путём
 * - Безопасным путём (подальше от берега)
 * 
 * @author BoatRoutes Team
 * @version 6.0-COST
 */
public class WaterPathfinderAStar {
    
    private final BoatRoutesPlugin plugin;
    private final WaterWorldCache cache;
    private final PathValidator validator;
    private final NavigableWaterFinder navFinder;
    private final int seaLevel = 62;
    
    // World для динамической проверки блоков
    private org.bukkit.World world;
    
    // 8 направлений (N, S, E, W, NE, NW, SE, SW)
    private static final int[][] DIRECTIONS = {
        {1, 0},   // East
        {-1, 0},  // West
        {0, 1},   // South
        {0, -1},  // North
        {1, 1},   // SE diagonal
        {-1, -1}, // NW diagonal
        {1, -1},  // NE diagonal
        {-1, 1}   // SW diagonal
    };
    
    public WaterPathfinderAStar(BoatRoutesPlugin plugin, WaterWorldCache cache) {
        this.plugin = plugin;
        this.cache = cache;
        this.validator = new PathValidator(plugin, cache);
        this.navFinder = new NavigableWaterFinder(plugin, validator);
    }
    
    /**
     * Основной метод поиска пути - Bidirectional A* + COST SYSTEM
     */
    public List<Location> findPath(Location start, Location end, World world) {
        long startTime = System.currentTimeMillis();
        
        this.world = world;
        
        int startX = start.getBlockX();
        int startZ = start.getBlockZ();
        int endX = end.getBlockX();
        int endZ = end.getBlockZ();
        
        double totalDistance = calculateDistance(startX, startZ, endX, endZ);
        
        plugin.getLogger().info("=== A* PATHFINDING v6.0 (COST SYSTEM) ===");
        plugin.getLogger().info("From: " + startX + "," + seaLevel + "," + startZ);
        plugin.getLogger().info("To: " + endX + "," + seaLevel + "," + endZ);
        plugin.getLogger().info("Distance: " + String.format("%.1f", totalDistance) + " blocks");
        plugin.getLogger().info("Algorithm: Bidirectional A* + Cost-based");
        plugin.getLogger().info("Cost system: prefers deep water (1), avoids shore (5)");
        
        // Priority queues для обеих волн
        PriorityQueue<AStarNode> openStart = new PriorityQueue<>();
        PriorityQueue<AStarNode> openEnd = new PriorityQueue<>();
        
        // Visited sets
        Set<Long> visitedStart = new HashSet<>();
        Set<Long> visitedEnd = new HashSet<>();
        
        // Maps для хранения лучших узлов
        Map<Long, AStarNode> nodesStart = new HashMap<>();
        Map<Long, AStarNode> nodesEnd = new HashMap<>();
        
        // Стартовые узлы
        AStarNode startNode = new AStarNode(startX, startZ, 0, 
            calculateHeuristic(startX, startZ, endX, endZ), null, true);
        AStarNode endNode = new AStarNode(endX, endZ, 0, 
            calculateHeuristic(endX, endZ, startX, startZ), null, false);
        
        openStart.add(startNode);
        openEnd.add(endNode);
        nodesStart.put(hash(startX, startZ), startNode);
        nodesEnd.put(hash(endX, endZ), endNode);
        
        int iterations = 0;
        int maxIterations = 1000000;
        
        AStarNode meetingPointStart = null;
        AStarNode meetingPointEnd = null;
        
        // Статистика для cost system
        double totalCostAccumulated = 0;
        int costsCalculated = 0;
        
        // MAIN LOOP
        while (!openStart.isEmpty() && !openEnd.isEmpty() && iterations < maxIterations) {
            iterations++;
            
            if (iterations % 10000 == 0) {
                plugin.getLogger().info("A* progress: iter=" + iterations + 
                    ", openStart=" + openStart.size() + 
                    ", openEnd=" + openEnd.size());
            }
            
            // === EXPAND FROM START ===
            if (!openStart.isEmpty()) {
                AStarNode current = openStart.poll();
                long currentHash = hash(current.x, current.z);
                
                // Проверка встречи волн
                if (visitedEnd.contains(currentHash)) {
                    meetingPointStart = current;
                    meetingPointEnd = nodesEnd.get(currentHash);
                    plugin.getLogger().info("✓ Waves met at: " + current.x + "," + current.z);
                    break;
                }
                
                if (visitedStart.contains(currentHash)) continue;
                visitedStart.add(currentHash);
                
                // Explore neighbors
                for (int[] dir : DIRECTIONS) {
                    int nx = current.x + dir[0];
                    int nz = current.z + dir[1];
                    long neighborHash = hash(nx, nz);
                    
                    if (visitedStart.contains(neighborHash)) continue;
                    
                    // Проверяем воду
                    if (!isWaterCached(nx, nz)) continue;
                    
                    // ===== COST CALCULATION =====
                    // Базовая дистанция движения (Euclidean для диагоналей)
                    double moveCost = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1]);
                    
                    // Получаем cost блока (1-100)
                    int blockCost = getBlockCost(nx, nz);
                    
                    // ИТОГОВАЯ СТОИМОСТЬ = дистанция * cost блока
                    // Глубокая вода (cost=1): moveCost * 1 = дешево!
                    // У берега (cost=5): moveCost * 5 = дорого!
                    // 
                    // ВАЖНО: Узкий пролив (100 блоков * 5) = 500
                    //        Обход (5000 блоков * 1) = 5000
                    //        → A* выберет ПРОЛИВ!
                    double totalMoveCost = moveCost * blockCost;
                    
                    double newGCost = current.gCost + totalMoveCost;
                    double hCost = calculateHeuristic(nx, nz, endX, endZ);
                    
                    AStarNode neighbor = nodesStart.get(neighborHash);
                    if (neighbor == null) {
                        neighbor = new AStarNode(nx, nz, newGCost, hCost, current, true);
                        nodesStart.put(neighborHash, neighbor);
                        openStart.add(neighbor);
                        
                        totalCostAccumulated += blockCost;
                        costsCalculated++;
                    } else if (newGCost < neighbor.gCost) {
                        openStart.remove(neighbor);
                        neighbor.gCost = newGCost;
                        neighbor.fCost = newGCost + hCost;
                        neighbor.parent = current;
                        openStart.add(neighbor);
                    }
                }
            }
            
            // === EXPAND FROM END ===
            if (!openEnd.isEmpty()) {
                AStarNode current = openEnd.poll();
                long currentHash = hash(current.x, current.z);
                
                if (visitedStart.contains(currentHash)) {
                    meetingPointEnd = current;
                    meetingPointStart = nodesStart.get(currentHash);
                    plugin.getLogger().info("✓ Waves met at: " + current.x + "," + current.z);
                    break;
                }
                
                if (visitedEnd.contains(currentHash)) continue;
                visitedEnd.add(currentHash);
                
                for (int[] dir : DIRECTIONS) {
                    int nx = current.x + dir[0];
                    int nz = current.z + dir[1];
                    long neighborHash = hash(nx, nz);
                    
                    if (visitedEnd.contains(neighborHash)) continue;
                    if (!isWaterCached(nx, nz)) continue;
                    
                    double moveCost = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1]);
                    int blockCost = getBlockCost(nx, nz);
                    double totalMoveCost = moveCost * blockCost;
                    
                    double newGCost = current.gCost + totalMoveCost;
                    double hCost = calculateHeuristic(nx, nz, startX, startZ);
                    
                    AStarNode neighbor = nodesEnd.get(neighborHash);
                    if (neighbor == null) {
                        neighbor = new AStarNode(nx, nz, newGCost, hCost, current, false);
                        nodesEnd.put(neighborHash, neighbor);
                        openEnd.add(neighbor);
                        
                        totalCostAccumulated += blockCost;
                        costsCalculated++;
                    } else if (newGCost < neighbor.gCost) {
                        openEnd.remove(neighbor);
                        neighbor.gCost = newGCost;
                        neighbor.fCost = newGCost + hCost;
                        neighbor.parent = current;
                        openEnd.add(neighbor);
                    }
                }
            }
        }
        
        long elapsedTime = System.currentTimeMillis() - startTime;
        
        // Проверка результата
        if (meetingPointStart == null || meetingPointEnd == null) {
            plugin.getLogger().warning("✗ No path found!");
            plugin.getLogger().warning("Iterations: " + iterations);
            plugin.getLogger().warning("Time: " + (elapsedTime / 1000.0) + "s");
            return null;
        }
        
        // Reconstruct path
        List<Location> pathFromStart = reconstructPath(meetingPointStart, true);
        List<Location> pathFromEnd = reconstructPath(meetingPointEnd, false);
        Collections.reverse(pathFromEnd);
        
        List<Location> fullPath = new ArrayList<>(pathFromStart);
        if (!pathFromEnd.isEmpty()) {
            pathFromEnd.remove(0);
        }
        fullPath.addAll(pathFromEnd);
        
        // Вычисляем средний cost пути
        double avgCost = costsCalculated > 0 ? totalCostAccumulated / costsCalculated : 0;
        
        // Statistics
        plugin.getLogger().info("✓ PATH FOUND!");
        plugin.getLogger().info("Algorithm: Bidirectional A* + Cost System");
        plugin.getLogger().info("Iterations: " + iterations);
        plugin.getLogger().info("Visited: " + (visitedStart.size() + visitedEnd.size()));
        plugin.getLogger().info("Path waypoints: " + fullPath.size());
        plugin.getLogger().info("Average block cost: " + String.format("%.1f", avgCost) + 
            " (1=deep water, 5=shore)");
        plugin.getLogger().info("Time: " + (elapsedTime / 1000.0) + "s");
        
        // Конвертируем в Location с правильным Y
        List<Location> finalPath = new ArrayList<>();
        for (Location loc : fullPath) {
            finalPath.add(new Location(world, 
                loc.getX(), 
                seaLevel, 
                loc.getZ()));
        }
        
        return finalPath;
    }
    
    /**
     * Получить cost блока (1-100)
     * Приоритет: кеш -> default
     */
    private int getBlockCost(int x, int z) {
        Integer cachedCost = cache.getCost(x, z);
        
        if (cachedCost != null) {
            return cachedCost;
        }
        
        // Нет в кеше - используем default
        // (будет уточнено когда игрок прогрузит чанк)
        return 1; // Оптимистично считаем глубокой водой
    }
    
    /**
     * Reconstruct path from node
     */
    private List<Location> reconstructPath(AStarNode endNode, boolean fromStart) {
        List<Location> path = new ArrayList<>();
        AStarNode current = endNode;
        
        while (current != null) {
            Location loc = new Location(null, current.x + 0.5, seaLevel, current.z + 0.5);
            if (fromStart) {
                path.add(0, loc);
            } else {
                path.add(loc);
            }
            current = current.parent;
        }
        
        return path;
    }
    
    /**
     * Hash function для координат (x,z) -> Long
     */
    private long hash(int x, int z) {
        return ((long)x << 32) | (z & 0xFFFFFFFFL);
    }
    
    /**
     * Проверка воды из кеша
     */
    private boolean isWaterCached(int x, int z) {
        Boolean result = cache.isWater(x, z);
        
        if (result != null) {
            return result;
        }
        
        // Нет в кеше - оптимистично считаем водой
        // (будет уточнено при загрузке чанка)
        return false;
    }
    
    /**
     * Heuristic function - Manhattan distance
     */
    private double calculateHeuristic(int x1, int z1, int x2, int z2) {
        return Math.abs(x2 - x1) + Math.abs(z2 - z1);
    }
    
    /**
     * Actual distance
     */
    private double calculateDistance(int x1, int z1, int x2, int z2) {
        int dx = x2 - x1;
        int dz = z2 - z1;
        return Math.sqrt(dx * dx + dz * dz);
    }
    
    public int getSeaLevel() {
        return seaLevel;
    }
    
    public PathValidator getValidator() {
        return validator;
    }
    
    public NavigableWaterFinder getNavFinder() {
        return navFinder;
    }
    
    /**
     * A* Node class
     */
    private static class AStarNode implements Comparable<AStarNode> {
        int x, z;
        double gCost; // Cost from start (ВКЛЮЧАЕТ cost блоков!)
        double hCost; // Heuristic to goal
        double fCost; // gCost + hCost
        AStarNode parent;
        boolean fromStart;
        
        AStarNode(int x, int z, double gCost, double hCost, AStarNode parent, boolean fromStart) {
            this.x = x;
            this.z = z;
            this.gCost = gCost;
            this.hCost = hCost;
            this.fCost = gCost + hCost;
            this.parent = parent;
            this.fromStart = fromStart;
        }
        
        @Override
        public int compareTo(AStarNode other) {
            int result = Double.compare(this.fCost, other.fCost);
            if (result == 0) {
                result = Double.compare(this.hCost, other.hCost);
            }
            return result;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AStarNode node = (AStarNode) o;
            return x == node.x && z == node.z;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(x, z);
        }
    }
}
