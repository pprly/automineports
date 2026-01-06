package com.example.boatroutes.pathfinding;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.cache.WaterWorldCache;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * PathValidator v4.0 - Проверка навигабельности воды
 * 
 * Особенности:
 * - Использует кеш для непрогруженных чанков
 * - Поддержка разных типов воды
 * - Быстрое кеширование регионов
 * 
 * @author BoatRoutes Team
 * @version 4.0
 */
public class PathValidator {
    
    private final BoatRoutesPlugin plugin;
    private final WaterWorldCache cache;
    
    private int seaLevel;
    private int minDepth;
    private int safetyRadius;
    
    public PathValidator(BoatRoutesPlugin plugin, WaterWorldCache cache) {
        this.plugin = plugin;
        this.cache = cache;
        
        reloadConfig();
    }
    
    public void reloadConfig() {
        this.seaLevel = plugin.getConfig().getInt("pathfinding.sea-level", 62);
        this.minDepth = plugin.getConfig().getInt("pathfinding.min-depth", 1);
        this.safetyRadius = plugin.getConfig().getInt("pathfinding.safety-radius", 0);
    }
    
    /**
     * Проверяет, является ли точка навигабельной водой.
     * Использует кеш если чанк не прогружен.
     * 
     * @param x X координата
     * @param y Y координата (обычно sea level)
     * @param z Z координата
     * @param world Мир
     * @return true если можно плыть
     */
    public boolean isNavigableWater(int x, int y, int z, World world) {
        // Сначала проверяем кеш
        Boolean cached = cache.isWater(x, z);
        if (cached != null) {
            return cached;
        }
        
        // Если кеша нет - проверяем мир напрямую
        // (только если чанк прогружен)
        if (!isChunkLoaded(world, x, z)) {
            // Чанк не прогружен и нет в кеше - считаем водой
            // (будет уточнено позже при прогрузке)
            return true;
        }
        
        // Проверяем блок
        Block block = world.getBlockAt(x, y, z);
        boolean isWater = isWaterBlock(block);
        
        // Кешируем результат
        cache.setWater(x, z, isWater);
        
        return isWater;
    }
    
    /**
     * Проверяет, является ли Location навигабельной водой
     */
    public boolean isNavigableWater(Location loc) {
        return isNavigableWater(
            loc.getBlockX(), 
            loc.getBlockY(), 
            loc.getBlockZ(), 
            loc.getWorld()
        );
    }
    
    /**
     * Проверяет, является ли блок водой
     */
    public boolean isWaterBlock(Block block) {
        if (block == null) return false;
        
        Material type = block.getType();
        
        // Основные типы воды
        if (type == Material.WATER) return true;
        
        // Проверка на waterlogged блоки и другие водные типы
        String typeName = type.toString();
        if (typeName.contains("WATER")) return true;
        
        // Проверяем waterlogged состояние
        if (block.getBlockData() instanceof org.bukkit.block.data.Waterlogged) {
            org.bukkit.block.data.Waterlogged waterlogged = 
                (org.bukkit.block.data.Waterlogged) block.getBlockData();
            return waterlogged.isWaterlogged();
        }
        
        return false;
    }
    
    /**
     * Проверяет, прогружен ли чанк
     */
    private boolean isChunkLoaded(World world, int x, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        return world.isChunkLoaded(chunkX, chunkZ);
    }
    
    /**
     * Предварительное кеширование региона.
     * Загружает данные о воде в указанной области.
     * 
     * @param min Минимальный угол региона
     * @param max Максимальный угол региона
     */
    public void preCacheRegion(Location min, Location max) {
        World world = min.getWorld();
        
        int minX = Math.min(min.getBlockX(), max.getBlockX());
        int maxX = Math.max(min.getBlockX(), max.getBlockX());
        int minZ = Math.min(min.getBlockZ(), max.getBlockZ());
        int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());
        
        int y = seaLevel;
        int stepSize = 2; // Кешируем каждый второй блок для скорости
        
        int totalBlocks = ((maxX - minX) / stepSize + 1) * ((maxZ - minZ) / stepSize + 1);
        int cachedCount = 0;
        int waterCount = 0;
        
        plugin.getLogger().info("Pre-caching region: " + minX + "," + minZ + " to " + maxX + "," + maxZ);
        
        for (int x = minX; x <= maxX; x += stepSize) {
            for (int z = minZ; z <= maxZ; z += stepSize) {
                // Проверяем, есть ли уже в кеше
                if (cache.isWater(x, z) != null) {
                    cachedCount++;
                    continue;
                }
                
                // Если чанк прогружен - читаем из мира
                if (isChunkLoaded(world, x, z)) {
                    Block block = world.getBlockAt(x, y, z);
                    boolean isWater = isWaterBlock(block);
                    cache.setWater(x, z, isWater);
                    if (isWater) waterCount++;
                } else {
                    // Чанк не прогружен - пробуем загрузить через chunk snapshot
                    // или помечаем как "unknown" для позднего определения
                    cache.setWater(x, z, true); // Оптимистично считаем водой
                }
                
                cachedCount++;
            }
        }
        
        plugin.getLogger().info("Pre-cache complete: " + cachedCount + " blocks, " + waterCount + " water");
    }
    
    /**
     * Проверяет глубину воды в точке
     * 
     * @param x X координата
     * @param z Z координата  
     * @param world Мир
     * @return Глубина воды (количество водных блоков вниз от sea level)
     */
    public int getWaterDepth(int x, int z, World world) {
        int depth = 0;
        
        for (int y = seaLevel; y >= seaLevel - 10; y--) {
            if (isChunkLoaded(world, x, z)) {
                Block block = world.getBlockAt(x, y, z);
                if (isWaterBlock(block)) {
                    depth++;
                } else {
                    break;
                }
            } else {
                // Если чанк не прогружен, считаем глубину достаточной
                depth = 5;
                break;
            }
        }
        
        return depth;
    }
    
    /**
     * Проверяет безопасность точки (расстояние от берега)
     */
    public boolean isSafeFromShore(int x, int z, World world) {
        if (safetyRadius <= 0) return true;
        
        // Проверяем квадрат вокруг точки
        for (int dx = -safetyRadius; dx <= safetyRadius; dx++) {
            for (int dz = -safetyRadius; dz <= safetyRadius; dz++) {
                if (!isNavigableWater(x + dx, seaLevel, z + dz, world)) {
                    return false; // Есть суша рядом
                }
            }
        }
        
        return true;
    }
    
    // Геттеры
    public int getSeaLevel() {
        return seaLevel;
    }
    
    public int getMinDepth() {
        return minDepth;
    }
    
    public int getSafetyRadius() {
        return safetyRadius;
    }
    
    public WaterWorldCache getCache() {
        return cache;
    }
}
