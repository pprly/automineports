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
 * ChunkLoadListener - Passive Caching System
 * 
 * Автоматически кеширует воду + cost при загрузке чанков игроками.
 * 
 * Преимущества:
 * - Кеш наполняется БЕСПЛАТНО (чанки уже загружены)
 * - Не нужно специально прогружать
 * - Со временем весь исследованный мир будет в кеше
 * - Async обработка - не лагает сервер
 * 
 * @author BoatRoutes Team
 * @version 1.0
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
     * Кеширует весь чанк: воду + cost
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
                
                // Получаем блок (SYNC, но мы в async thread!)
                Block block = chunk.getBlock(x, seaLevel, z);
                boolean isWater = isWaterBlock(block);
                
                // Вычисляем cost (проверяем 8 соседей)
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
     * Вычисляет cost блока на основе соседей
     * 
     * Cost system:
     * - LAND = 100 (избегать!)
     * - Water 3+ land neighbors = 5 (у берега)
     * - Water 2 land neighbors = 3
     * - Water 1 land neighbor = 2
     * - Water 0 land neighbors = 1 (глубокая вода - оптимально!)
     */
    private int calculateCost(Chunk chunk, int localX, int localZ, boolean isWater, 
                              int worldX, int worldZ) {
        
        if (!isWater) {
            return 100; // ЗЕМЛЯ
        }
        
        // Считаем сколько земли вокруг
        int landNeighbors = 0;
        
        for (int[] dir : DIRECTIONS) {
            int nx = localX + dir[0];
            int nz = localZ + dir[1];
            
            boolean neighborIsWater;
            
            // Если сосед внутри чанка - проверяем напрямую
            if (nx >= 0 && nx < 16 && nz >= 0 && nz < 16) {
                Block neighbor = chunk.getBlock(nx, seaLevel, nz);
                neighborIsWater = isWaterBlock(neighbor);
            } else {
                // Сосед в другом чанке - проверяем кеш
                int neighborWorldX = worldX + dir[0];
                int neighborWorldZ = worldZ + dir[1];
                
                Boolean cached = cache.isWater(neighborWorldX, neighborWorldZ);
                
                if (cached != null) {
                    neighborIsWater = cached;
                } else {
                    // Нет в кеше - оптимистично считаем водой
                    // (будет уточнено когда тот чанк загрузится)
                    neighborIsWater = true;
                }
            }
            
            if (!neighborIsWater) {
                landNeighbors++;
            }
        }
        
        // Конвертируем количество земли в cost
        if (landNeighbors >= 3) return 5;  // Очень близко к берегу
        if (landNeighbors == 2) return 3;  // Близко к берегу
        if (landNeighbors == 1) return 2;  // Немного от берега
        return 1;                           // Глубокая вода!
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
