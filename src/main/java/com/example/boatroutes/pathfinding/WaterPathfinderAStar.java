package com.example.boatroutes.pathfinding;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.cache.WaterWorldCache;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;

/**
 * BIDIRECTIONAL A* v10.0 - NAVIGABLE WATER ONLY!
 *
 * ИСПРАВЛЕНО v10.0:
 * - ✅ Навигационная вода (минимум 6 блоков протяжённости)
 * - ✅ Подземные озёра = ЗАПРЕЩЕНЫ
 * - ✅ Локальные лужи = ЗАПРЕЩЕНЫ
 * - ✅ Coast penalty radius
 * - ✅ Euclidean heuristic
 * - ✅ Безопасные диагонали
 *
 * РЕЗУЛЬТАТ:
 * - НЕ сворачивает в подземные озёра
 * - Держится рек и океана
 * - Плавные морские маршруты
 *
 * @author BoatRoutes Team
 * @version 10.0-NAVIGABLE-WATER
 */
public class WaterPathfinderAStar {

    private final BoatRoutesPlugin plugin;
    private final WaterWorldCache cache;
    private final PathValidator validator;
    private final NavigableWaterFinder navFinder;
    private final int seaLevel = 62;

    private org.bukkit.World world;

    // Параметры навигации
    private static final int COAST_PENALTY_RADIUS = 3;
    private static final int MIN_NAVIGABLE_LENGTH = 6; // Минимальная ширина реки/океана

    // 8 направлений
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

    // 4 основных направления для проверки протяжённости
    private static final int[][] CARDINAL_DIRS = {
            {1, 0},   // East
            {-1, 0},  // West
            {0, 1},   // South
            {0, -1}   // North
    };

    public WaterPathfinderAStar(BoatRoutesPlugin plugin, WaterWorldCache cache) {
        this.plugin = plugin;
        this.cache = cache;
        this.validator = new PathValidator(plugin, cache);
        this.navFinder = new NavigableWaterFinder(plugin, validator);
    }

    /**
     * Основной метод поиска пути
     */
    public List<Location> findPath(Location start, Location end, World world) {
        long startTime = System.currentTimeMillis();

        this.world = world;

        int startX = start.getBlockX();
        int startZ = start.getBlockZ();
        int endX = end.getBlockX();
        int endZ = end.getBlockZ();

        double totalDistance = calculateDistance(startX, startZ, endX, endZ);

        plugin.getLogger().info("=== A* PATHFINDING v10.0 (NAVIGABLE WATER) ===");
        plugin.getLogger().info("From: " + startX + "," + seaLevel + "," + startZ);
        plugin.getLogger().info("To: " + endX + "," + seaLevel + "," + endZ);
        plugin.getLogger().info("Distance: " + String.format("%.1f", totalDistance) + " blocks");
        plugin.getLogger().info("Min navigable length: " + MIN_NAVIGABLE_LENGTH + " blocks");

        PriorityQueue<AStarNode> openStart = new PriorityQueue<>();
        PriorityQueue<AStarNode> openEnd = new PriorityQueue<>();

        Set<Long> visitedStart = new HashSet<>();
        Set<Long> visitedEnd = new HashSet<>();

        Map<Long, AStarNode> nodesStart = new HashMap<>();
        Map<Long, AStarNode> nodesEnd = new HashMap<>();

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

        int blockedByLand = 0;
        int blockedByNoCache = 0;
        int blockedByDiagonal = 0;
        int blockedByNonNavigable = 0; // НОВОЕ!

        // MAIN LOOP
        while (!openStart.isEmpty() && !openEnd.isEmpty() && iterations < maxIterations) {
            iterations++;

            if (iterations % 10000 == 0) {
                plugin.getLogger().info("A* progress: iter=" + iterations +
                        ", open=" + (openStart.size() + openEnd.size()) +
                        ", blocked: land=" + blockedByLand +
                        ", no-cache=" + blockedByNoCache +
                        ", diagonal=" + blockedByDiagonal +
                        ", non-navigable=" + blockedByNonNavigable);
            }

            // === EXPAND FROM START ===
            if (!openStart.isEmpty()) {
                AStarNode current = openStart.poll();
                long currentHash = hash(current.x, current.z);

                if (visitedEnd.contains(currentHash)) {
                    meetingPointStart = current;
                    meetingPointEnd = nodesEnd.get(currentHash);
                    plugin.getLogger().info("✓ Waves met at: " + current.x + "," + current.z);
                    break;
                }

                if (visitedStart.contains(currentHash)) continue;
                visitedStart.add(currentHash);

                for (int[] dir : DIRECTIONS) {
                    int nx = current.x + dir[0];
                    int nz = current.z + dir[1];
                    long neighborHash = hash(nx, nz);

                    if (visitedStart.contains(neighborHash)) continue;

                    // Проверка диагоналей
                    if (!canMoveDiagonal(current.x, current.z, dir[0], dir[1])) {
                        blockedByDiagonal++;
                        continue;
                    }

                    // ТОЛЬКО кеш
                    int blockCost = getBlockCostFromCache(nx, nz);

                    if (blockCost >= 999) {
                        blockedByLand++;
                        continue;
                    }

                    if (blockCost < 0) {
                        blockedByNoCache++;
                        continue;
                    }

                    // ✅ НОВАЯ ПРОВЕРКА: Навигационная вода!
                    if (!isNavigableWater(nx, nz)) {
                        blockedByNonNavigable++;
                        continue; // Подземное озеро или лужа!
                    }

                    // Coast penalty
                    double moveCost = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1]);
                    int coastPenalty = getCoastPenalty(nx, nz);

                    double totalMoveCost =
                            moveCost
                                    + (blockCost * 2.0)
                                    + coastPenalty;

                    double newGCost = current.gCost + totalMoveCost;
                    double hCost = calculateHeuristic(nx, nz, endX, endZ);

                    AStarNode neighbor = nodesStart.get(neighborHash);
                    if (neighbor == null) {
                        neighbor = new AStarNode(nx, nz, newGCost, hCost, current, true);
                        nodesStart.put(neighborHash, neighbor);
                        openStart.add(neighbor);
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

                    if (!canMoveDiagonal(current.x, current.z, dir[0], dir[1])) {
                        blockedByDiagonal++;
                        continue;
                    }

                    int blockCost = getBlockCostFromCache(nx, nz);

                    if (blockCost >= 999) {
                        blockedByLand++;
                        continue;
                    }

                    if (blockCost < 0) {
                        blockedByNoCache++;
                        continue;
                    }

                    // ✅ НАВИГАЦИОННАЯ ВОДА!
                    if (!isNavigableWater(nx, nz)) {
                        blockedByNonNavigable++;
                        continue;
                    }

                    double moveCost = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1]);
                    int coastPenalty = getCoastPenalty(nx, nz);

                    double totalMoveCost =
                            moveCost
                                    + (blockCost * 2.0)
                                    + coastPenalty;

                    double newGCost = current.gCost + totalMoveCost;
                    double hCost = calculateHeuristic(nx, nz, startX, startZ);

                    AStarNode neighbor = nodesEnd.get(neighborHash);
                    if (neighbor == null) {
                        neighbor = new AStarNode(nx, nz, newGCost, hCost, current, false);
                        nodesEnd.put(neighborHash, neighbor);
                        openEnd.add(neighbor);
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

        if (meetingPointStart == null || meetingPointEnd == null) {
            plugin.getLogger().warning("✗ No path found!");
            plugin.getLogger().warning("Iterations: " + iterations);
            plugin.getLogger().warning("Blocked: land=" + blockedByLand +
                    ", no-cache=" + blockedByNoCache +
                    ", diagonal=" + blockedByDiagonal +
                    ", non-navigable=" + blockedByNonNavigable);
            plugin.getLogger().warning("Time: " + (elapsedTime / 1000.0) + "s");
            return null;
        }

        // Reconstruct
        List<Location> pathFromStart = reconstructPath(meetingPointStart, true);
        List<Location> pathFromEnd = reconstructPath(meetingPointEnd, false);
        Collections.reverse(pathFromEnd);

        List<Location> fullPath = new ArrayList<>(pathFromStart);
        if (!pathFromEnd.isEmpty()) {
            pathFromEnd.remove(0);
        }
        fullPath.addAll(pathFromEnd);

        if (!validatePath(fullPath)) {
            plugin.getLogger().severe("✗ PATH VALIDATION FAILED");
            return null;
        }

        plugin.getLogger().info("✓ PATH FOUND!");
        plugin.getLogger().info("Iterations: " + iterations);
        plugin.getLogger().info("Visited: " + (visitedStart.size() + visitedEnd.size()));
        plugin.getLogger().info("Waypoints: " + fullPath.size());
        plugin.getLogger().info("Blocked non-navigable water: " + blockedByNonNavigable);
        plugin.getLogger().info("Time: " + (elapsedTime / 1000.0) + "s");

        List<Location> finalPath = new ArrayList<>();
        for (Location loc : fullPath) {
            finalPath.add(new Location(world, loc.getX(), seaLevel, loc.getZ()));
        }

        return finalPath;
    }

    /**
     * ✅ НОВЫЙ МЕТОД: Проверка навигационной воды
     *
     * Вода считается навигационной ТОЛЬКО если есть
     * протяжённость минимум 6 блоков в одну сторону.
     *
     * Подземные озёра, лужи → НЕ навигационная вода!
     */
    private boolean isNavigableWater(int x, int z) {
        // Проверяем 4 основных направления
        for (int[] dir : CARDINAL_DIRS) {
            int count = 0;

            // Считаем сколько воды подряд в этом направлении
            for (int i = 1; i <= MIN_NAVIGABLE_LENGTH; i++) {
                int nx = x + dir[0] * i;
                int nz = z + dir[1] * i;

                int cost = getBlockCostFromCache(nx, nz);

                if (cost >= 0 && cost < 999) {
                    count++; // Вода!
                } else {
                    break; // Земля или нет кеша
                }
            }

            // Если хотя бы одно направление имеет протяжённость
            if (count >= MIN_NAVIGABLE_LENGTH) {
                return true; // НАВИГАЦИОННАЯ ВОДА!
            }
        }

        return false; // Локальная вода - ЗАПРЕТ!
    }

    /**
     * Coast penalty radius
     */
    private int getCoastPenalty(int x, int z) {
        int penalty = 0;

        for (int dx = -COAST_PENALTY_RADIUS; dx <= COAST_PENALTY_RADIUS; dx++) {
            for (int dz = -COAST_PENALTY_RADIUS; dz <= COAST_PENALTY_RADIUS; dz++) {
                if (dx == 0 && dz == 0) continue;

                int cost = getBlockCostFromCache(x + dx, z + dz);

                if (cost >= 999) {
                    int distance = Math.max(Math.abs(dx), Math.abs(dz));
                    penalty += (COAST_PENALTY_RADIUS - distance + 1) * 3;
                }
            }
        }

        return penalty;
    }

    /**
     * Безопасная проверка диагоналей
     */
    private boolean canMoveDiagonal(int x, int z, int dx, int dz) {
        if (dx == 0 || dz == 0) return true;

        int cost1 = getBlockCostFromCache(x + dx, z);
        int cost2 = getBlockCostFromCache(x, z + dz);

        return cost1 < 999 && cost1 >= 0
                && cost2 < 999 && cost2 >= 0;
    }

    /**
     * Получение cost ТОЛЬКО из кеша
     */
    private int getBlockCostFromCache(int x, int z) {
        Integer cachedCost = cache.getCost(x, z);
        if (cachedCost != null) return cachedCost;
        return -1;
    }

    private boolean validatePath(List<Location> path) {
        if (path == null || path.size() < 2) return false;

        int blockedCount = 0;

        for (int i = 0; i < path.size() - 1; i++) {
            Location from = path.get(i);
            Location to = path.get(i + 1);

            if (!validator.areWaterBlocksConnected(
                    from.getBlockX(), from.getBlockZ(),
                    to.getBlockX(), to.getBlockZ(), world)) {
                blockedCount++;

                if (blockedCount > path.size() * 0.05) {
                    return false;
                }
            }
        }

        return true;
    }

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

    private long hash(int x, int z) {
        return ((long)x << 32) | (z & 0xFFFFFFFFL);
    }

    /**
     * Euclidean heuristic
     */
    private double calculateHeuristic(int x1, int z1, int x2, int z2) {
        int dx = x2 - x1;
        int dz = z2 - z1;
        return Math.sqrt(dx * dx + dz * dz);
    }

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

    private static class AStarNode implements Comparable<AStarNode> {
        int x, z;
        double gCost;
        double hCost;
        double fCost;
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