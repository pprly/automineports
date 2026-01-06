package com.example.boatroutes.pathfinding;

import com.example.boatroutes.BoatRoutesPlugin;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * NavigableWaterFinder v4.0 - Поиск навигабельной воды
 * 
 * Находит открытую воду для начала/конца маршрута.
 * Порт может быть в бухте, но нам нужна открытая вода
 * где лодка может свободно маневрировать.
 * 
 * @author BoatRoutes Team
 * @version 4.0
 */
public class NavigableWaterFinder {
    
    private final BoatRoutesPlugin plugin;
    private final PathValidator validator;
    
    // Настройки из конфига
    private int searchRadius;
    private int minWaterArea;
    private int minClearDirections;
    private int directionDistance;
    
    // 8 направлений для проверки
    private static final int[][] DIRECTIONS = {
        {1, 0},   // E
        {-1, 0},  // W
        {0, 1},   // S
        {0, -1},  // N
        {1, 1},   // SE
        {1, -1},  // NE
        {-1, 1},  // SW
        {-1, -1}  // NW
    };
    
    public NavigableWaterFinder(BoatRoutesPlugin plugin, PathValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
        
        reloadConfig();
    }
    
    public void reloadConfig() {
        this.searchRadius = plugin.getConfig().getInt("navigable-water.search-radius", 50);
        this.minWaterArea = plugin.getConfig().getInt("navigable-water.min-water-area", 70);
        this.minClearDirections = plugin.getConfig().getInt("navigable-water.min-clear-directions", 6);
        this.directionDistance = plugin.getConfig().getInt("navigable-water.direction-distance", 20);
    }
    
    /**
     * Находит ближайшую навигабельную воду от заданной точки.
     * "Навигабельная" означает открытую воду где лодка может маневрировать.
     * 
     * @param start Начальная точка поиска
     * @param maxRadius Максимальный радиус поиска
     * @return Локация навигабельной воды или null
     */
    public Location findNavigableWater(Location start, int maxRadius) {
        World world = start.getWorld();
        int seaLevel = validator.getSeaLevel();
        int startX = start.getBlockX();
        int startZ = start.getBlockZ();
        
        plugin.getLogger().info("Searching for navigable water from: " + 
            startX + "," + start.getBlockY() + "," + startZ);
        
        // Сначала проверяем саму стартовую точку
        if (isNavigable(startX, startZ, world)) {
            Location result = new Location(world, startX + 0.5, seaLevel, startZ + 0.5);
            plugin.getLogger().info("✓ Start point is navigable!");
            return result;
        }
        
        // Поиск по расширяющейся спирали
        Location best = null;
        int bestScore = 0;
        
        for (int radius = 5; radius <= maxRadius; radius += 5) {
            // Проверяем точки на окружности
            for (int angle = 0; angle < 360; angle += 15) {
                double rad = Math.toRadians(angle);
                int x = startX + (int)(radius * Math.cos(rad));
                int z = startZ + (int)(radius * Math.sin(rad));
                
                if (!validator.isNavigableWater(x, seaLevel, z, world)) {
                    continue;
                }
                
                int score = getNavigabilityScore(x, z, world);
                
                if (score > bestScore) {
                    bestScore = score;
                    best = new Location(world, x + 0.5, seaLevel, z + 0.5);
                    
                    // Если нашли отличную точку - сразу возвращаем
                    if (score >= 80) {
                        plugin.getLogger().info("✓ Found navigable water at: " + 
                            x + "," + seaLevel + "," + z + " (score: " + score + ")");
                        return best;
                    }
                }
            }
        }
        
        if (best != null) {
            plugin.getLogger().info("✓ Found navigable water at: " + 
                best.getBlockX() + "," + seaLevel + "," + best.getBlockZ() + 
                " (score: " + bestScore + ")");
        } else {
            plugin.getLogger().warning("✗ No navigable water found within " + maxRadius + " blocks");
        }
        
        return best;
    }
    
    /**
     * Проверяет, является ли точка навигабельной
     */
    private boolean isNavigable(int x, int z, World world) {
        return getNavigabilityScore(x, z, world) >= 60;
    }
    
    /**
     * Вычисляет "навигабельность" точки (0-100)
     * 
     * @return Оценка от 0 (непроходимо) до 100 (отлично)
     */
    public int getNavigabilityScore(int x, int z, World world) {
        int seaLevel = validator.getSeaLevel();
        
        // Проверка 1: Сама точка должна быть водой
        if (!validator.isNavigableWater(x, seaLevel, z, world)) {
            return 0;
        }
        
        int score = 0;
        
        // Проверка 2: Площадь воды вокруг (10x10)
        int waterCount = 0;
        int totalCount = 0;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                totalCount++;
                if (validator.isNavigableWater(x + dx, seaLevel, z + dz, world)) {
                    waterCount++;
                }
            }
        }
        int areaPercent = (waterCount * 100) / totalCount;
        score += (areaPercent >= minWaterArea) ? 40 : (areaPercent / 2);
        
        // Проверка 3: Количество свободных направлений
        int clearDirections = 0;
        for (int[] dir : DIRECTIONS) {
            if (isDirectionClear(x, z, dir[0], dir[1], directionDistance, world)) {
                clearDirections++;
            }
        }
        score += (clearDirections >= minClearDirections) ? 40 : (clearDirections * 5);
        
        // Проверка 4: Глубина воды
        int depth = validator.getWaterDepth(x, z, world);
        score += Math.min(depth * 4, 20);
        
        return Math.min(score, 100);
    }
    
    /**
     * Публичная версия для использования в командах
     */
    public int getNavigabilityScore(Location loc) {
        return getNavigabilityScore(loc.getBlockX(), loc.getBlockZ(), loc.getWorld());
    }
    
    /**
     * Проверяет, свободно ли направление от препятствий
     */
    private boolean isDirectionClear(int startX, int startZ, int dirX, int dirZ, 
                                     int distance, World world) {
        int seaLevel = validator.getSeaLevel();
        
        for (int i = 1; i <= distance; i++) {
            int x = startX + dirX * i;
            int z = startZ + dirZ * i;
            
            if (!validator.isNavigableWater(x, seaLevel, z, world)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Находит направление к ближайшей открытой воде
     * (для выхода из бухты)
     * 
     * @return int[2] с направлением {dx, dz} или null
     */
    public int[] findDirectionToOpenWater(Location from) {
        World world = from.getWorld();
        int seaLevel = validator.getSeaLevel();
        int x = from.getBlockX();
        int z = from.getBlockZ();
        
        int bestDir = -1;
        int bestClear = 0;
        
        for (int i = 0; i < DIRECTIONS.length; i++) {
            int clearDistance = 0;
            int[] dir = DIRECTIONS[i];
            
            for (int d = 1; d <= 50; d++) {
                int checkX = x + dir[0] * d;
                int checkZ = z + dir[1] * d;
                
                if (validator.isNavigableWater(checkX, seaLevel, checkZ, world)) {
                    clearDistance = d;
                } else {
                    break;
                }
            }
            
            if (clearDistance > bestClear) {
                bestClear = clearDistance;
                bestDir = i;
            }
        }
        
        if (bestDir >= 0 && bestClear >= 10) {
            return DIRECTIONS[bestDir];
        }
        
        return null;
    }
}
