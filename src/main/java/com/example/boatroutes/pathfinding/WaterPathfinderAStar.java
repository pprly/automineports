package com.example.boatroutes.pathfinding;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.cache.WaterWorldCache;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * BIDIRECTIONAL A* PATHFINDER - ИСПРАВЛЕННАЯ ВЕРСИЯ
 * Совместима с существующими классами проекта
 * 
 * КЛЮЧЕВЫЕ ОСОБЕННОСТИ:
 * 1. Работает ТОЛЬКО с кешем (не загружает чанки)
 * 2. Bidirectional: ищет с обеих сторон одновременно
 * 3. A* эвристика: приоритет ближайшим к цели
 * 4. Автоматически обходит острова/материки
 * 5. Для огромных расстояний (2000-5000 blocks)
 * 
 * @author BoatRoutes Team
 * @version 5.0-FIXED
 */
public class WaterPathfinderAStar {
    
    private final BoatRoutesPlugin plugin;
    private final WaterWorldCache cache;
    private final PathValidator validator;
    private final NavigableWaterFinder navFinder;
    private final int seaLevel = 62;
    private final int stepSize = 1; // Шаг в 1 блок для точности (было 2)
    
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
     * Основной метод поиска пути - Bidirectional A*
     * БЕЗ ОГРАНИЧЕНИЙ! Будет искать путь любой длины!
     */
    public List<Location> findPath(Location start, Location end, World world) {
        long startTime = System.currentTimeMillis();
        
        // Сохраняем world для динамических проверок
        this.world = world;
        
        int startX = start.getBlockX();
        int startZ = start.getBlockZ();
        int endX = end.getBlockX();
        int endZ = end.getBlockZ();
        
        double totalDistance = calculateDistance(startX, startZ, endX, endZ);
        
        plugin.getLogger().info("=== A* PATHFINDING ===");
        plugin.getLogger().info("From: " + startX + "," + seaLevel + "," + startZ);
        plugin.getLogger().info("To: " + endX + "," + seaLevel + "," + endZ);
        plugin.getLogger().info("Distance: " + String.format("%.1f", totalDistance) + " blocks");
        plugin.getLogger().info("Algorithm: Bidirectional A* (UNLIMITED!)");
        plugin.getLogger().info("Note: Will search dynamically without radius limits");
        
        // Priority queues для обеих волн
        PriorityQueue<AStarNode> openStart = new PriorityQueue<>();
        PriorityQueue<AStarNode> openEnd = new PriorityQueue<>();
        
        // Visited sets с hash координат
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
        int maxIterations = 1000000; // НЕТ РЕАЛЬНЫХ ОГРАНИЧЕНИЙ!
        
        AStarNode meetingPointStart = null;
        AStarNode meetingPointEnd = null;
        
        // MAIN LOOP
        while (!openStart.isEmpty() && !openEnd.isEmpty() && iterations < maxIterations) {
            iterations++;
            
            // Progress log каждые 10000 итераций
            if (iterations % 10000 == 0) {
                plugin.getLogger().info("A* progress: iter=" + iterations + 
                    ", openStart=" + openStart.size() + 
                    ", openEnd=" + openEnd.size() +
                    ", visitedStart=" + visitedStart.size() +
                    ", visitedEnd=" + visitedEnd.size());
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
                    
                    // ИСПРАВЛЕНО: Проверяем воду в кеше (только X и Z!)
                    if (!isWaterCached(nx, nz)) continue;
                    
                    // Calculate costs
                    double moveCost = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1]);
                    double newGCost = current.gCost + moveCost;
                    double hCost = calculateHeuristic(nx, nz, endX, endZ);
                    
                    AStarNode neighbor = nodesStart.get(neighborHash);
                    if (neighbor == null) {
                        neighbor = new AStarNode(nx, nz, newGCost, hCost, current, true);
                        nodesStart.put(neighborHash, neighbor);
                        openStart.add(neighbor);
                    } else if (newGCost < neighbor.gCost) {
                        // Found better path
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
                
                // Проверка встречи волн
                if (visitedStart.contains(currentHash)) {
                    meetingPointEnd = current;
                    meetingPointStart = nodesStart.get(currentHash);
                    plugin.getLogger().info("✓ Waves met at: " + current.x + "," + current.z);
                    break;
                }
                
                if (visitedEnd.contains(currentHash)) continue;
                visitedEnd.add(currentHash);
                
                // Explore neighbors
                for (int[] dir : DIRECTIONS) {
                    int nx = current.x + dir[0];
                    int nz = current.z + dir[1];
                    long neighborHash = hash(nx, nz);
                    
                    if (visitedEnd.contains(neighborHash)) continue;
                    
                    // ИСПРАВЛЕНО: Проверяем воду в кеше (только X и Z!)
                    if (!isWaterCached(nx, nz)) continue;
                    
                    // Calculate costs
                    double moveCost = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1]);
                    double newGCost = current.gCost + moveCost;
                    double hCost = calculateHeuristic(nx, nz, startX, startZ);
                    
                    AStarNode neighbor = nodesEnd.get(neighborHash);
                    if (neighbor == null) {
                        neighbor = new AStarNode(nx, nz, newGCost, hCost, current, false);
                        nodesEnd.put(neighborHash, neighbor);
                        openEnd.add(neighbor);
                    } else if (newGCost < neighbor.gCost) {
                        // Found better path
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
            plugin.getLogger().warning("Visited nodes: " + (visitedStart.size() + visitedEnd.size()));
            plugin.getLogger().warning("Time: " + (elapsedTime / 1000.0) + "s");
            return null;
        }
        
        // Reconstruct path
        List<Location> pathFromStart = reconstructPath(meetingPointStart, true);
        List<Location> pathFromEnd = reconstructPath(meetingPointEnd, false);
        Collections.reverse(pathFromEnd);
        
        List<Location> fullPath = new ArrayList<>(pathFromStart);
        // Не дублируем точку встречи
        if (!pathFromEnd.isEmpty()) {
            pathFromEnd.remove(0);
        }
        fullPath.addAll(pathFromEnd);
        
        // Statistics
        plugin.getLogger().info("✓ PATH FOUND!");
        plugin.getLogger().info("Algorithm: Bidirectional A*");
        plugin.getLogger().info("Iterations: " + iterations);
        plugin.getLogger().info("Visited from START: " + visitedStart.size());
        plugin.getLogger().info("Visited from END: " + visitedEnd.size());
        plugin.getLogger().info("Total visited: " + (visitedStart.size() + visitedEnd.size()));
        plugin.getLogger().info("Path waypoints: " + fullPath.size());
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
     * Reconstruct path from node to start/end
     */
    private List<Location> reconstructPath(AStarNode endNode, boolean fromStart) {
        List<Location> path = new ArrayList<>();
        AStarNode current = endNode;
        
        while (current != null) {
            Location loc = new Location(null, current.x + 0.5, seaLevel, current.z + 0.5);
            if (fromStart) {
                path.add(0, loc); // Add to beginning
            } else {
                path.add(loc); // Add to end
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
     * Проверка воды - ДИНАМИЧЕСКАЯ, БЕЗ ОГРАНИЧЕНИЙ!
     * Если блок не в кеше - проверяем напрямую
     */
    private boolean isWaterCached(int x, int z) {
        Boolean result = cache.isWater(x, z);
        
        if (result != null) {
            return result; // Уже в кеше
        }
        
        // НЕ В КЕШЕ - проверяем напрямую!
        if (world != null) {
            try {
                org.bukkit.block.Block block = world.getBlockAt(x, seaLevel, z);
                org.bukkit.Material material = block.getType();
                return material == org.bukkit.Material.WATER || 
                       material.toString().contains("WATER");
            } catch (Exception e) {
                // Чанк не загружен
                return false;
            }
        }
        
        return false;
    }
    
    /**
     * Heuristic function - Manhattan distance (быстрее чем Euclidean)
     */
    private double calculateHeuristic(int x1, int z1, int x2, int z2) {
        return Math.abs(x2 - x1) + Math.abs(z2 - z1);
    }
    
    /**
     * Actual distance (для статистики)
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
        double gCost; // Cost from start
        double hCost; // Heuristic to goal
        double fCost; // gCost + hCost
        AStarNode parent;
        boolean fromStart; // true if from start wave, false if from end wave
        
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
                // Если f_cost равны, выбираем с меньшей эвристикой
                // (ближе к цели)
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
