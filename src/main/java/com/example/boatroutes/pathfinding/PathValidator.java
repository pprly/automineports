package com.example.boatroutes.pathfinding;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.cache.WaterWorldCache;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * PathValidator v5.1 - С COST CALCULATION + DEADEND DETECTION!
 *
 * ИСПРАВЛЕНО v5.1:
 * - Добавлена проверка соединенности блоков воды
 * - Детекция изолированных блоков (тупиков)
 * - Повышенный cost для изолированной воды
 *
 * @author BoatRoutes Team
 * @version 5.1-DEADEND-FIX
 */
public class PathValidator {

    private final BoatRoutesPlugin plugin;
    private final WaterWorldCache cache;

    private int seaLevel;
    private int minDepth;
    private int safetyRadius;

    // 8 направлений для проверки соседей
    private static final int[][] DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

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
            // Чанк не прогружен и нет в кеше - считаем землёй!
            return false;
        }

        // Проверяем блок
        Block block = world.getBlockAt(x, y, z);
        boolean isWater = isWaterBlock(block);

        // Кешируем результат С COST!
        int cost = calculateCostForBlock(x, z, isWater, world, y);
        cache.setWater(x, z, isWater, cost);

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
     * ✅ НОВОЕ: Проверяет что два блока воды ДЕЙСТВИТЕЛЬНО соединены
     * (нет стены/земли между ними на высоте Y=62)
     *
     * @param x1, z1 - первый блок
     * @param x2, z2 - второй блок
     * @return true если можно проплыть из (x1,z1) в (x2,z2)
     */
    public boolean areWaterBlocksConnected(int x1, int z1, int x2, int z2, World world) {
        // Если соседи (расстояние 1-2) - проверяем напрямую
        int dx = Math.abs(x2 - x1);
        int dz = Math.abs(z2 - z1);

        if (dx <= 1 && dz <= 1) {
            // Прямые соседи - проверяем только воду между
            return isNavigableWater(x1, seaLevel, z1, world) &&
                    isNavigableWater(x2, seaLevel, z2, world);
        }

        // Для дальних блоков - проверяем линию между ними
        return hasWaterLine(x1, z1, x2, z2, world);
    }

    /**
     * ✅ НОВОЕ: Проверяет что между двумя точками есть непрерывная линия воды
     */
    private boolean hasWaterLine(int x1, int z1, int x2, int z2, World world) {
        double distance = Math.sqrt((x2-x1)*(x2-x1) + (z2-z1)*(z2-z1));
        int steps = (int)Math.ceil(distance);

        for (int i = 0; i <= steps; i++) {
            double t = (double)i / steps;
            int x = (int)(x1 + (x2 - x1) * t);
            int z = (int)(z1 + (z2 - z1) * t);

            if (!isNavigableWater(x, seaLevel, z, world)) {
                return false; // Стена!
            }
        }

        return true; // Путь чист
    }

    /**
     * ✅ НОВОЕ: Проверка изолированности блока воды
     * Возвращает true если блок воды окружен землей (потенциальный тупик)
     */
    public boolean isIsolatedWater(int x, int z, World world) {
        // Проверяем 8 направлений на расстояние 5 блоков
        int waterDirections = 0;

        int[][] longDirections = {
                {5, 0}, {-5, 0}, {0, 5}, {0, -5},
                {5, 5}, {5, -5}, {-5, 5}, {-5, -5}
        };

        for (int[] dir : longDirections) {
            int nx = x + dir[0];
            int nz = z + dir[1];

            if (isNavigableWater(nx, seaLevel, nz, world)) {
                waterDirections++;
            }
        }

        // Если меньше 4 направлений имеют воду - это изолированный блок
        return waterDirections < 4;
    }

    /**
     * Предварительное кеширование региона С COST CALCULATION!
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
        int stepSize = 2; // Кешируем каждый второй блок

        int cachedCount = 0;
        int waterCount = 0;

        plugin.getLogger().info("Pre-caching region with COSTS: " + minX + "," + minZ + " to " + maxX + "," + maxZ);

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

                    // ВЫЧИСЛЯЕМ COST!
                    int cost = calculateCostForBlock(x, z, isWater, world, y);

                    // Сохраняем с cost
                    cache.setWater(x, z, isWater, cost);
                    if (isWater) waterCount++;
                } else {
                    // Чанк не прогружен - НЕ кешируем
                    // (будет закеширован при загрузке через ChunkLoadListener)
                }

                cachedCount++;
            }
        }

        plugin.getLogger().info("Pre-cache complete: " + cachedCount + " blocks, " + waterCount + " water (with costs)");
    }

    /**
     * Вычисляет cost блока на основе соседей
     *
     * Cost system:
     * - LAND = 100
     * - Water 3+ land neighbors = 5 (у берега)
     * - Water 2 land neighbors = 3
     * - Water 1 land neighbor = 2
     * - Water 0 land neighbors = 1 (глубокая вода!)
     * - ✅ НОВОЕ: Isolated water = 8 (тупик!)
     */
    private int calculateCostForBlock(int x, int z, boolean isWater, World world, int y) {
        if (!isWater) {
            return 100; // ЗЕМЛЯ
        }

        // Считаем сколько земли вокруг
        int landNeighbors = 0;

        for (int[] dir : DIRECTIONS) {
            int nx = x + dir[0];
            int nz = z + dir[1];

            // Проверяем соседа
            Boolean neighborCached = cache.isWater(nx, nz);

            boolean neighborIsWater;
            if (neighborCached != null) {
                // Есть в кеше
                neighborIsWater = neighborCached;
            } else if (isChunkLoaded(world, nx, nz)) {
                // Чанк загружен - проверяем напрямую
                Block neighbor = world.getBlockAt(nx, y, nz);
                neighborIsWater = isWaterBlock(neighbor);
            } else {
                // Чанк не загружен - оптимистично считаем водой
                neighborIsWater = true;
            }

            if (!neighborIsWater) {
                landNeighbors++;
            }
        }

        // Конвертируем количество земли в cost
        int baseCost;
        if (landNeighbors >= 3) baseCost = 5;  // Очень близко к берегу
        if (landNeighbors == 2) baseCost = 3;  // Близко к берегу
        if (landNeighbors == 1) baseCost = 2;  // Немного от берега
        else baseCost = 1;                      // Глубокая вода!

        // ✅ НОВОЕ: Penalty за изолированную воду (тупики!)
        if (isChunkLoaded(world, x, z) && isIsolatedWater(x, z, world)) {
            baseCost = Math.max(baseCost, 8); // Минимум 8 для изолированных
        }

        return baseCost;
    }

    /**
     * Проверяет, является ли блок водой
     */
    public boolean isWaterBlock(Block block) {
        if (block == null) return false;

        Material type = block.getType();

        // Основные типы воды
        if (type == Material.WATER) return true;

        // Проверка на waterlogged блоки
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