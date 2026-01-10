package com.example.boatroutes.listeners;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.cache.WaterWorldCache;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * ChunkLoadListener v2.0 - Shore Distance Cost System
 *
 * НОВОЕ v2.0:
 * - Проверка расстояния до берега (1-5+ блоков)
 * - Новая система cost: чем дальше от берега = дешевле
 * - Земля = 999 (непроходимо)
 * - Тупики = 100 (очень дорого)
 *
 * @author BoatRoutes Team
 * @version 2.0-SHORE-DISTANCE
 */
public class ChunkLoadListener implements Listener {

    private final BoatRoutesPlugin plugin;
    private final WaterWorldCache cache;
    private final int seaLevel;

    // 8 направлений для проверки соседей
    private static final int[][] DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    // 8 направлений для проверки изолированности (на расстоянии 5)
    private static final int[][] LONG_DIRECTIONS = {
            {5, 0}, {-5, 0}, {0, 5}, {0, -5},
            {5, 5}, {5, -5}, {-5, 5}, {-5, -5}
    };

    // Статистика
    private int chunksProcessed = 0;
    private int blocksAdded = 0;

    public ChunkLoadListener(BoatRoutesPlugin plugin, WaterWorldCache cache) {
        this.plugin = plugin;
        this.cache = cache;
        this.seaLevel = plugin.getConfig().getInt("pathfinding.sea-level", 62);
    }

    /**
     * Обрабатываем загрузку чанка
     * Priority = MONITOR (самый последний) чтобы не мешать другим плагинам
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        World world = chunk.getWorld();

        // Проверяем что это overworld (не Nether/End)
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return;
        }

        // Кешируем ASYNC чтобы не лагать сервер
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            cacheChunk(chunk);
        });
    }

    /**
     * Кеширует весь чанк: воду + cost (shore distance + isolated detection)
     */
    private void cacheChunk(Chunk chunk) {
        int chunkX = chunk.getX() * 16;
        int chunkZ = chunk.getZ() * 16;

        int newBlocks = 0;

        // Сканируем 16x16 блоков на уровне воды
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX + x;
                int worldZ = chunkZ + z;

                // Пропускаем если уже в кеше
                if (cache.isWater(worldX, worldZ) != null) {
                    continue;
                }

                // Получаем блок
                Block block = chunk.getBlock(x, seaLevel, z);
                boolean isWater = isWaterBlock(block);

                // Вычисляем cost (shore distance + изолированность)
                int cost = calculateCost(chunk, x, z, isWater, worldX, worldZ);

                // Сохраняем в кеш
                cache.setWater(worldX, worldZ, isWater, cost);
                newBlocks++;
            }
        }

        // Статистика
        chunksProcessed++;
        blocksAdded += newBlocks;

        // Логируем каждые 10 чанков
        if (chunksProcessed % 10 == 0) {
            plugin.getLogger().info("[Passive Cache] Processed " + chunksProcessed +
                    " chunks, cached " + blocksAdded + " blocks");
        }
    }

    /**
     * Вычисляет расстояние до ближайшего берега (1-6+ блоков)
     */
    private int calculateShoreDistance(Chunk chunk, int localX, int localZ, int worldX, int worldZ) {
        // Проверяем радиусы от 1 до 5
        for (int radius = 1; radius <= 5; radius++) {
            // Проверяем квадрат на этом радиусе
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // Только края квадрата (не внутренность)
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;

                    int checkLocalX = localX + dx;
                    int checkLocalZ = localZ + dz;
                    int checkWorldX = worldX + dx;
                    int checkWorldZ = worldZ + dz;

                    boolean isLand;

                    // Проверяем внутри чанка или из кеша
                    if (checkLocalX >= 0 && checkLocalX < 16 && checkLocalZ >= 0 && checkLocalZ < 16) {
                        Block block = chunk.getBlock(checkLocalX, seaLevel, checkLocalZ);
                        isLand = !isWaterBlock(block);
                    } else {
                        // Другой чанк - проверяем кеш
                        Boolean cached = cache.isWater(checkWorldX, checkWorldZ);
                        isLand = (cached != null && !cached);
                    }

                    if (isLand) {
                        return radius; // Нашли берег на расстоянии radius!
                    }
                }
            }
        }

        return 6; // Берег дальше 5 блоков
    }

    /**
     * Вычисляет cost блока на основе расстояния до берега + изолированности
     *
     * Cost system:
     * - LAND = 999 (непроходимо!)
     * - Water 1 блок от берега = 50
     * - Water 2 блока от берега = 30
     * - Water 3 блока от берега = 10
     * - Water 4 блока от берега = 5
     * - Water 5 блоков от берега = 2
     * - Water 6+ блоков от берега = 1 (оптимально!)
     * - Isolated water (тупик) = 100
     */
    private int calculateCost(Chunk chunk, int localX, int localZ, boolean isWater,
                              int worldX, int worldZ) {

        if (!isWater) {
            return 999; // ЗЕМЛЯ - НЕПРОХОДИМО!
        }

        // Вычисляем расстояние до берега
        int shoreDistance = calculateShoreDistance(chunk, localX, localZ, worldX, worldZ);

        int baseCost;

        // Чем ближе к берегу = тем ДОРОЖЕ (больше cost)
        // Чем дальше от берега = тем ДЕШЕВЛЕ (меньше cost)
        switch (shoreDistance) {
            case 1:  baseCost = 50;  break;  // Вплотную к берегу
            case 2:  baseCost = 30;  break;  // 2 блока
            case 3:  baseCost = 10;  break;  // 3 блока
            case 4:  baseCost = 5;   break;  // 4 блока
            case 5:  baseCost = 2;   break;  // 5 блоков
            default: baseCost = 1;   break;  // 6+ блоков - ДЁШЕВО!
        }

        // Проверка на изолированность (тупики)
        if (isIsolatedWater(chunk, localX, localZ, worldX, worldZ)) {
            baseCost = Math.max(baseCost, 100); // Тупик = очень дорого
        }

        return baseCost;
    }

    /**
     * Проверяет изолированность блока воды
     * Возвращает true если < 4 направлений на расстояние 5 блоков имеют воду
     */
    private boolean isIsolatedWater(Chunk chunk, int localX, int localZ,
                                    int worldX, int worldZ) {
        int waterDirections = 0;

        // Проверяем 8 направлений на расстояние 5 блоков
        for (int[] dir : LONG_DIRECTIONS) {
            int checkWorldX = worldX + dir[0];
            int checkWorldZ = worldZ + dir[1];

            // Преобразуем обратно в локальные координаты
            int checkLocalX = localX + dir[0];
            int checkLocalZ = localZ + dir[1];

            boolean hasWater;

            // Если внутри чанка - проверяем напрямую
            if (checkLocalX >= 0 && checkLocalX < 16 &&
                    checkLocalZ >= 0 && checkLocalZ < 16) {
                Block block = chunk.getBlock(checkLocalX, seaLevel, checkLocalZ);
                hasWater = isWaterBlock(block);
            } else {
                // За пределами чанка - проверяем кеш
                Boolean cached = cache.isWater(checkWorldX, checkWorldZ);
                hasWater = (cached != null && cached);
            }

            if (hasWater) {
                waterDirections++;
            }
        }

        // Если меньше 4 направлений имеют воду - изолирован
        return waterDirections < 4;
    }

    /**
     * Проверяет является ли блок водой
     */
    private boolean isWaterBlock(Block block) {
        if (block == null) return false;

        Material type = block.getType();

        // Основная вода
        if (type == Material.WATER) return true;

        // Waterlogged блоки
        if (block.getBlockData() instanceof org.bukkit.block.data.Waterlogged) {
            org.bukkit.block.data.Waterlogged waterlogged =
                    (org.bukkit.block.data.Waterlogged) block.getBlockData();
            return waterlogged.isWaterlogged();
        }

        return false;
    }

    /**
     * Получить статистику кеширования
     */
    public void printStats() {
        plugin.getLogger().info("=== PASSIVE CACHE STATISTICS ===");
        plugin.getLogger().info("Chunks processed: " + chunksProcessed);
        plugin.getLogger().info("Blocks cached: " + blocksAdded);
        plugin.getLogger().info("Cache size: " + cache.size() + " blocks");
    }

    /**
     * Сбросить статистику
     */
    public void resetStats() {
        chunksProcessed = 0;
        blocksAdded = 0;
    }
}