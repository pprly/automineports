package com.example.boatroutes.cache;

import com.example.boatroutes.BoatRoutesPlugin;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * WaterWorldCache v4.0 - Кеш информации о воде
 * 
 * Сохраняет информацию о том, какие блоки являются водой.
 * Позволяет искать путь через непрогруженные чанки!
 * 
 * Структура кеша: Map<Long, Boolean>
 * где Long = (x << 32) | (z & 0xFFFFFFFFL)
 * 
 * @author BoatRoutes Team
 * @version 4.0
 */
public class WaterWorldCache {
    
    private final BoatRoutesPlugin plugin;
    private final File cacheFile;
    private FileConfiguration cacheConfig;
    
    // Основной кеш: packed coords -> isWater
    private final Map<Long, Boolean> waterCache = new HashMap<>();
    
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
        
        Boolean result = waterCache.get(key);
        
        if (result != null) {
            cacheHits++;
        } else {
            cacheMisses++;
        }
        
        return result;
    }
    
    /**
     * Устанавливает значение в кеш
     */
    public void setWater(int x, int z, boolean isWater) {
        long key = packCoords(x, z);
        waterCache.put(key, isWater);
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
                if (isWater(x, z) != null) {
                    cachedBlocks++;
                }
            }
        }
        
        if (totalBlocks == 0) return 0;
        
        return (cachedBlocks * 100.0) / totalBlocks;
    }
    
    /**
     * Возвращает количество закешированных блоков
     */
    public int getCachedBlockCount() {
        return waterCache.size();
    }
    
    /**
     * Возвращает статистику кеша (для совместимости с Map)
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cachedBlocks", waterCache.size());
        stats.put("cacheHits", cacheHits);
        stats.put("cacheMisses", cacheMisses);
        
        double hitRate = (cacheHits + cacheMisses) > 0 
            ? (cacheHits * 100.0) / (cacheHits + cacheMisses) 
            : 0;
        stats.put("hitRate", hitRate);
        
        // Оценка размера в памяти (примерно)
        long memoryBytes = waterCache.size() * 17; // 8 bytes key + 1 byte value + overhead
        stats.put("memoryKB", memoryBytes / 1024);
        
        return stats;
    }
    
    /**
     * Возвращает статистику кеша как объект CacheStats
     * (для совместимости с PortCommand)
     */
    public CacheStats getCacheStats() {
        int waterBlocks = 0;
        for (Boolean isWater : waterCache.values()) {
            if (isWater != null && isWater) {
                waterBlocks++;
            }
        }
        
        // Оценка размера файла
        long fileSizeBytes = 0;
        if (cacheFile.exists()) {
            fileSizeBytes = cacheFile.length();
        }
        
        // Примерное количество чанков (16x16 блоков в чанке)
        int cachedChunks = waterCache.size() / 256;
        
        return new CacheStats(cachedChunks, waterBlocks, fileSizeBytes);
    }
    
    /**
     * Класс для статистики кеша (для совместимости с PortCommand)
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
     * Очищает кеш
     */
    public void clearCache() {
        waterCache.clear();
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
            cacheConfig.set("meta.version", "4.0");
            cacheConfig.set("meta.blocks", waterCache.size());
            cacheConfig.set("meta.saved", System.currentTimeMillis());
            
            // Сохраняем данные (группируем по регионам для эффективности)
            // Сохраняем только водные блоки (суша = отсутствие в кеше)
            int waterCount = 0;
            StringBuilder waterData = new StringBuilder();
            
            for (Map.Entry<Long, Boolean> entry : waterCache.entrySet()) {
                if (entry.getValue()) { // Только вода
                    int[] coords = unpackCoords(entry.getKey());
                    waterData.append(coords[0]).append(",").append(coords[1]).append(";");
                    waterCount++;
                    
                    // Разбиваем на чанки по 10000 записей
                    if (waterCount % 10000 == 0) {
                        cacheConfig.set("water.chunk" + (waterCount / 10000), waterData.toString());
                        waterData = new StringBuilder();
                    }
                }
            }
            
            // Сохраняем остаток
            if (waterData.length() > 0) {
                cacheConfig.set("water.chunk" + ((waterCount / 10000) + 1), waterData.toString());
            }
            
            cacheConfig.set("meta.waterBlocks", waterCount);
            
            cacheConfig.save(cacheFile);
            
            plugin.getLogger().info("Water cache saved: " + waterCount + " water blocks");
            
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
            
            String version = cacheConfig.getString("meta.version", "1.0");
            int expectedBlocks = cacheConfig.getInt("meta.waterBlocks", 0);
            
            plugin.getLogger().info("Loading water cache v" + version + " (" + expectedBlocks + " blocks expected)");
            
            // Загружаем все чанки данных
            if (cacheConfig.contains("water")) {
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
                            setWater(x, z, true);
                            loadedCount++;
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                
                plugin.getLogger().info("Water cache loaded: " + loadedCount + " water blocks");
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load water cache: " + e.getMessage());
        }
    }
    
    /**
     * Возвращает примерный размер кеша в байтах
     */
    public long getMemoryUsage() {
        // Примерная оценка: 8 bytes ключ + 1 byte значение + overhead HashMap
        return waterCache.size() * 20L;
    }
}
