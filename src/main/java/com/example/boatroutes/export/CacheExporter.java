package com.example.boatroutes.export;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Экспортер кеша мира для визуализатора
 * Добавь эту команду в свой плагин: /port export-cache <radius>
 */
public class CacheExporter {
    
    private static final int WATER_LEVEL = 62;
    
    public static class CachedChunk {
        public int chunkX;
        public int chunkZ;
        public int[][] waterCosts; // 16x16 массив стоимостей
        
        public CachedChunk(int x, int z) {
            this.chunkX = x;
            this.chunkZ = z;
            this.waterCosts = new int[16][16];
        }
    }
    
    public static class WorldCache {
        public String worldName;
        public int centerX;
        public int centerZ;
        public List<CachedChunk> chunks;
        public long timestamp;
        
        public WorldCache(String worldName, int centerX, int centerZ) {
            this.worldName = worldName;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.chunks = new ArrayList<>();
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    /**
     * Экспортирует кеш вокруг указанной точки
     * 
     * @param world Мир Minecraft
     * @param centerX Центр X (в блоках)
     * @param centerZ Центр Z (в блоках)
     * @param radiusChunks Радиус в чанках (например, 50 = 800 блоков)
     * @param outputFile Выходной JSON файл
     */
    public static void exportCache(World world, int centerX, int centerZ, 
                                   int radiusChunks, File outputFile) {
        
        WorldCache cache = new WorldCache(world.getName(), centerX, centerZ);
        
        int centerChunkX = centerX >> 4;
        int centerChunkZ = centerZ >> 4;
        
        Bukkit.getLogger().info("[CacheExporter] Начинаем экспорт...");
        Bukkit.getLogger().info("[CacheExporter] Центр: " + centerX + ", " + centerZ);
        Bukkit.getLogger().info("[CacheExporter] Радиус: " + radiusChunks + " чанков");
        
        int totalChunks = 0;
        int processedChunks = 0;
        
        // Проходим по всем чанкам в радиусе
        for (int cx = centerChunkX - radiusChunks; cx <= centerChunkX + radiusChunks; cx++) {
            for (int cz = centerChunkZ - radiusChunks; cz <= centerChunkZ + radiusChunks; cz++) {
                totalChunks++;
                
                if (!world.isChunkLoaded(cx, cz)) {
                    world.loadChunk(cx, cz);
                }
                
                Chunk chunk = world.getChunkAt(cx, cz);
                CachedChunk cachedChunk = analyzeChunk(chunk);
                
                if (cachedChunk != null) {
                    cache.chunks.add(cachedChunk);
                    processedChunks++;
                }
                
                // Прогресс каждые 100 чанков
                if (totalChunks % 100 == 0) {
                    Bukkit.getLogger().info("[CacheExporter] Обработано: " + processedChunks + "/" + totalChunks);
                }
            }
        }
        
        // Сохраняем в JSON
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            FileWriter writer = new FileWriter(outputFile);
            gson.toJson(cache, writer);
            writer.close();
            
            Bukkit.getLogger().info("[CacheExporter] ✓ Экспорт завершён!");
            Bukkit.getLogger().info("[CacheExporter] Файл: " + outputFile.getAbsolutePath());
            Bukkit.getLogger().info("[CacheExporter] Чанков: " + processedChunks);
            
        } catch (IOException e) {
            Bukkit.getLogger().severe("[CacheExporter] Ошибка записи: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Анализирует чанк и вычисляет стоимости для навигации
     */
    private static CachedChunk analyzeChunk(Chunk chunk) {
        CachedChunk cached = new CachedChunk(chunk.getX(), chunk.getZ());
        
        // Проходим по всем блокам в чанке на уровне воды
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = (chunk.getX() << 4) + x;
                int worldZ = (chunk.getZ() << 4) + z;
                
                Block block = chunk.getBlock(x, WATER_LEVEL, z);
                
                if (isWater(block.getType())) {
                    // Вода - вычисляем расстояние до берега
                    cached.waterCosts[x][z] = calculateShoreDistance(chunk.getWorld(), worldX, worldZ);
                } else {
                    // Суша или другое
                    cached.waterCosts[x][z] = 100; // Непроходимо
                }
            }
        }
        
        return cached;
    }
    
    /**
     * Вычисляет расстояние от воды до ближайшей суши
     * Возвращает стоимость: 1 = глубокая вода, 5 = у берега, 100 = суша
     */
    private static int calculateShoreDistance(World world, int x, int z) {
        // Проверяем соседние блоки
        int minDistance = 6; // Максимум 5 блоков от берега
        
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (dx == 0 && dz == 0) continue;
                
                Block neighbor = world.getBlockAt(x + dx, WATER_LEVEL, z + dz);
                
                if (!isWater(neighbor.getType())) {
                    // Нашли сушу - вычисляем расстояние
                    int distance = Math.max(Math.abs(dx), Math.abs(dz)); // Чебышевское расстояние
                    minDistance = Math.min(minDistance, distance);
                }
            }
        }
        
        // Преобразуем расстояние в стоимость
        if (minDistance >= 6) return 1;  // Глубокая вода
        if (minDistance == 5) return 1;  // 5 блоков от берега
        if (minDistance == 4) return 2;  // 4 блока
        if (minDistance == 3) return 3;  // 3 блока
        if (minDistance == 2) return 4;  // 2 блока
        return 5;                        // 1 блок от берега
    }
    
    private static boolean isWater(Material material) {
        return material == Material.WATER || material == Material.WATER;
    }
}
