package com.example.boatroutes.cache;

import com.example.boatroutes.BoatRoutesPlugin;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WaterWorldCache v5.0 - Кеш с COST SYSTEM (Thread-Safe)
 * 
 * ИСПРАВЛЕНО: ConcurrentHashMap для thread-safety
 * 
 * @author BoatRoutes Team
 * @version 5.0-FIXED
 */
public class WaterWorldCache {
    
    private final BoatRoutesPlugin plugin;
    private final File cacheFile;
    private FileConfiguration cacheConfig;
    
    // Thread-safe кеш!
    private final Map<Long, BlockData> cache = new ConcurrentHashMap<>();
    
    // Статистика
    private int cacheHits = 0;
    private int cacheMisses = 0;
    
    public WaterWorldCache(BoatRoutesPlugin plugin) {
        this.plugin = plugin;
        this.cacheFile = new File(plugin.getDataFolder(), "water_cache.yml");
        
        loadCache();
    }
    
    /**
     * Проверяет, является ли блок водой (из кеша)
     * 
     * @return true/false если есть в кеше, null если нет
     */
    public Boolean isWater(int x, int z) {
        long key = packCoords(x, z);
        
        BlockData data = cache.get(key);
        
        if (data != null) {
            cacheHits++;
            return data.isWater;
        } else {
            cacheMisses++;
            return null;
        }
    }
    
    /**
     * Получить cost блока (для pathfinding)
     * 
     * @return cost (1-100) если есть в кеше, null если нет
     */
    public Integer getCost(int x, int z) {
        long key = packCoords(x, z);
        
        BlockData data = cache.get(key);
        
        if (data != null) {
            cacheHits++;
            return data.cost;
        } else {
            cacheMisses++;
            return null;
        }
    }
    
    /**
     * Устанавливает значение в кеш (с cost)
     */
    public void setWater(int x, int z, boolean isWater, int cost) {
        long key = packCoords(x, z);
        cache.put(key, new BlockData(isWater, cost));
    }
    
    /**
     * Устанавливает значение в кеш (без cost - backward compatibility)
     */
    public void setWater(int x, int z, boolean isWater) {
        // Default cost: water = 1, land = 100
        int cost = isWater ? 1 : 100;
        setWater(x, z, isWater, cost);
    }
    
    /**
     * Упаковка координат в long
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
    
    /**
     * Вычисляет процент покрытия кеша между двумя точками
     */
    public double getCoveragePercent(Location from, Location to) {
        int minX = Math.min(from.getBlockX(), to.getBlockX());
        int maxX = Math.max(from.getBlockX(), to.getBlockX());
        int minZ = Math.min(from.getBlockZ(), to.getBlockZ());
        int maxZ = Math.max(from.getBlockZ(), to.getBlockZ());
        
        int totalBlocks = 0;
        int cachedBlocks = 0;
        
        // Проверяем каждый 10-й блок для скорости
        for (int x = minX; x <= maxX; x += 10) {
            for (int z = minZ; z <= maxZ; z += 10) {
                totalBlocks++;
                if (cache.containsKey(packCoords(x, z))) {
                    cachedBlocks++;
                }
            }
        }
        
        if (totalBlocks == 0) return 0;
        
        return (cachedBlocks * 100.0) / totalBlocks;
    }
    
    /**
     * Статистика кеша
     */
    public void printStats() {
        int totalRequests = cacheHits + cacheMisses;
        double hitRate = totalRequests > 0 ? (double) cacheHits / totalRequests * 100.0 : 0;
        
        plugin.getLogger().info("=== CACHE STATISTICS ===");
        plugin.getLogger().info("Total blocks cached: " + cache.size());
        plugin.getLogger().info("Cache hits: " + cacheHits);
        plugin.getLogger().info("Cache misses: " + cacheMisses);
        plugin.getLogger().info("Hit rate: " + String.format("%.1f%%", hitRate));
        plugin.getLogger().info("Memory usage: ~" + (getMemoryUsage() / 1024) + " KB");
    }
    
    /**
     * Очистить кеш
     */
    public void clearCache() {
        cache.clear();
        cacheHits = 0;
        cacheMisses = 0;
        
        plugin.getLogger().info("Water cache cleared");
    }
    
    /**
     * Сохраняет кеш на диск
     */
    public void saveCache() {
        try {
            if (!cacheFile.exists()) {
                cacheFile.getParentFile().mkdirs();
                cacheFile.createNewFile();
            }
            
            cacheConfig = new YamlConfiguration();
            
            // Сохраняем метаданные
            cacheConfig.set("meta.version", "5.0");
            cacheConfig.set("meta.blocks", cache.size());
            cacheConfig.set("meta.saved", System.currentTimeMillis());
            
            // Сохраняем данные (группируем для эффективности)
            // Формат: x,z,isWater,cost;x,z,isWater,cost;...
            int blockCount = 0;
            StringBuilder data = new StringBuilder();
            
            for (Map.Entry<Long, BlockData> entry : cache.entrySet()) {
                int[] coords = unpackCoords(entry.getKey());
                BlockData blockData = entry.getValue();
                
                // Формат: x,z,isWater(0/1),cost
                data.append(coords[0]).append(",")
                    .append(coords[1]).append(",")
                    .append(blockData.isWater ? "1" : "0").append(",")
                    .append(blockData.cost).append(";");
                
                blockCount++;
                
                // Разбиваем на чанки по 5000 записей
                if (blockCount % 5000 == 0) {
                    cacheConfig.set("blocks.chunk" + (blockCount / 5000), data.toString());
                    data = new StringBuilder();
                }
            }
            
            // Сохраняем остаток
            if (data.length() > 0) {
                cacheConfig.set("blocks.chunk" + ((blockCount / 5000) + 1), data.toString());
            }
            
            cacheConfig.set("meta.totalBlocks", blockCount);
            
            cacheConfig.save(cacheFile);
            
            plugin.getLogger().info("Water cache saved: " + blockCount + " blocks (v5.0 with costs)");
            
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save water cache: " + e.getMessage());
        }
    }
    
    /**
     * Загружает кеш с диска
     */
    public void loadCache() {
        if (!cacheFile.exists()) {
            plugin.getLogger().info("No water cache file found");
            return;
        }
        
        try {
            cacheConfig = YamlConfiguration.loadConfiguration(cacheFile);
            
            String version = cacheConfig.getString("meta.version", "4.0");
            int expectedBlocks = cacheConfig.getInt("meta.totalBlocks", 
                                 cacheConfig.getInt("meta.waterBlocks", 0));
            
            plugin.getLogger().info("Loading water cache v" + version + 
                " (" + expectedBlocks + " blocks expected)");
            
            // Загружаем v5.0 формат (с cost)
            if (cacheConfig.contains("blocks")) {
                loadV5Format();
            } 
            // Backward compatibility с v4.0 (без cost)
            else if (cacheConfig.contains("water")) {
                loadV4Format();
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load water cache: " + e.getMessage());
        }
    }
    
    /**
     * Загрузка v5.0 формата (с cost)
     */
    private void loadV5Format() {
        Set<String> chunks = cacheConfig.getConfigurationSection("blocks").getKeys(false);
        
        int loadedCount = 0;
        for (String chunkKey : chunks) {
            String data = cacheConfig.getString("blocks." + chunkKey, "");
            String[] entries = data.split(";");
            
            for (String entry : entries) {
                if (entry.isEmpty()) continue;
                
                String[] parts = entry.split(",");
                if (parts.length < 4) continue;
                
                try {
                    int x = Integer.parseInt(parts[0]);
                    int z = Integer.parseInt(parts[1]);
                    boolean isWater = parts[2].equals("1");
                    int cost = Integer.parseInt(parts[3]);
                    
                    setWater(x, z, isWater, cost);
                    loadedCount++;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        
        plugin.getLogger().info("Water cache loaded: " + loadedCount + " blocks (v5.0 with costs)");
    }
    
    /**
     * Загрузка v4.0 формата (без cost - backward compatibility)
     */
    private void loadV4Format() {
        plugin.getLogger().info("Loading old format cache (v4.0, no costs)...");
        
        Set<String> chunks = cacheConfig.getConfigurationSection("water").getKeys(false);
        int loadedCount = 0;
        
        for (String chunkKey : chunks) {
            String data = cacheConfig.getString("water." + chunkKey, "");
            String[] entries = data.split(";");
            
            for (String entry : entries) {
                if (entry.isEmpty()) continue;
                
                String[] coords = entry.split(",");
                if (coords.length < 2) continue;
                
                try {
                    int x = Integer.parseInt(coords[0]);
                    int z = Integer.parseInt(coords[1]);
                    setWater(x, z, true); // Default cost = 1
                    loadedCount++;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        
        plugin.getLogger().info("Loaded " + loadedCount + " blocks from v4.0 format");
        plugin.getLogger().info("Costs will be calculated as chunks are loaded by players");
    }
    
    /**
     * Возвращает примерный размер кеша в байтах
     */
    public long getMemoryUsage() {
        // Примерная оценка: 8 bytes ключ + 5 bytes BlockData + overhead
        return cache.size() * 25L;
    }
    
    /**
     * Получить размер кеша (количество блоков)
     */
    public int size() {
        return cache.size();
    }
    
    /**
     * Получить количество закешированных блоков
     */
    public int getCachedBlockCount() {
        return cache.size();
    }
    
    /**
     * Возвращает статистику кеша как объект (для совместимости)
     */
    public CacheStats getCacheStats() {
        int waterBlocks = 0;
        for (BlockData data : cache.values()) {
            if (data.isWater) {
                waterBlocks++;
            }
        }
        
        long fileSizeBytes = 0;
        if (cacheFile.exists()) {
            fileSizeBytes = cacheFile.length();
        }
        
        int cachedChunks = cache.size() / 256;
        
        return new CacheStats(cachedChunks, waterBlocks, fileSizeBytes);
    }
    
    /**
     * Класс для статистики кеша
     */
    public static class CacheStats {
        public final int cachedChunks;
        public final int waterBlocks;
        public final long fileSizeBytes;
        
        public CacheStats(int cachedChunks, int waterBlocks, long fileSizeBytes) {
            this.cachedChunks = cachedChunks;
            this.waterBlocks = waterBlocks;
            this.fileSizeBytes = fileSizeBytes;
        }
    }
    
    /**
     * Вспомогательный класс для хранения данных блока
     */
    private static class BlockData {
        final boolean isWater;
        final int cost;
        
        BlockData(boolean isWater, int cost) {
            this.isWater = isWater;
            this.cost = cost;
        }
    }
}
