package com.example.boatroutes.commands;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.cache.WaterWorldCache; // ИСПРАВЛЕНО: правильный пакет!
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Экспорт кеша для визуализатора (использует готовый WaterWorldCache!)
 * Команда: /export-cache <radius>
 */
public class ExportCommand implements CommandExecutor {

    private final BoatRoutesPlugin plugin;

    public ExportCommand(BoatRoutesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cТолько для игроков!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage("§e╔══════════════════════════════╗");
            player.sendMessage("§e║   Экспорт кеша для           ║");
            player.sendMessage("§e║   визуализатора              ║");
            player.sendMessage("§e╚══════════════════════════════╝");
            player.sendMessage("");
            player.sendMessage("§fИспользование: §a/export-cache <radius>");
            player.sendMessage("");
            player.sendMessage("§7Пример: §f/export-cache 50");
            player.sendMessage("§7  (50 чанков = 800 блоков)");
            player.sendMessage("");
            player.sendMessage("§7Рекомендуемые значения:");
            player.sendMessage("§7  • 30-50 для тестов");
            player.sendMessage("§7  • 100-150 для больших карт");
            player.sendMessage("");
            player.sendMessage("§7Экспортирует готовый кеш (быстро!)");
            return true;
        }

        int radius;
        try {
            radius = Integer.parseInt(args[0]);
            if (radius < 1 || radius > 200) {
                player.sendMessage("§cРадиус должен быть от 1 до 200!");
                return true;
            }
        } catch (NumberFormatException e) {
            player.sendMessage("§cНеверное число! Используй целое число.");
            return true;
        }

        int centerX = player.getLocation().getBlockX();
        int centerZ = player.getLocation().getBlockZ();
        File exportFile = new File(plugin.getDataFolder(), "cache_export.json");

        player.sendMessage("");
        player.sendMessage("§a╔══════════════════════════════╗");
        player.sendMessage("§a║  Экспорт кеша (из памяти)    ║");
        player.sendMessage("§a╚══════════════════════════════╝");
        player.sendMessage("");
        player.sendMessage("§7Твоя позиция: §f" + centerX + ", " + centerZ);
        player.sendMessage("§7Радиус: §f" + radius + " чанков (§e" + (radius * 16) + " блоков§f)");
        player.sendMessage("§7Файл: §f" + exportFile.getName());
        player.sendMessage("");
        player.sendMessage("§eЗапускаю экспорт из кеша...");
        player.sendMessage("");

        // Экспорт в async потоке (безопасно - читаем только из памяти!)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long startTime = System.currentTimeMillis();

                WorldCache cache = exportFromCache(player.getWorld().getName(), centerX, centerZ, radius);

                // Сохраняем в JSON
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                FileWriter writer = new FileWriter(exportFile);
                gson.toJson(cache, writer);
                writer.close();

                long elapsed = System.currentTimeMillis() - startTime;

                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("");
                    player.sendMessage("§a✓ Экспорт завершён успешно!");
                    player.sendMessage("");
                    player.sendMessage("§fСтатистика:");
                    player.sendMessage("§7  • Чанков: §f" + cache.chunks.size());
                    player.sendMessage("§7  • Из кеша: §a" + cache.cachedChunks);
                    player.sendMessage("§7  • Пропущено: §7" + cache.skippedChunks);
                    player.sendMessage("§7  • Время: §f" + elapsed + " мс");
                    player.sendMessage("");
                    player.sendMessage("§fФайл: §e" + exportFile.getName());
                    player.sendMessage("");
                    player.sendMessage("§7Следующие шаги:");
                    player.sendMessage("§71. Открой PortSystemVisualizer.java");
                    player.sendMessage("§72. Нажми 'Импорт кеша (cache_export)'");
                    player.sendMessage("§73. Выбери файл cache_export.json");
                    player.sendMessage("§74. Увидишь карту воды/земли!");
                    player.sendMessage("");
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("");
                    player.sendMessage("§c✗ Ошибка экспорта!");
                    player.sendMessage("§7" + e.getMessage());
                    player.sendMessage("");
                });
                e.printStackTrace();
            }
        });

        return true;
    }

    // Классы для JSON
    static class CachedChunk {
        int chunkX, chunkZ;
        int[][] waterCosts;

        CachedChunk(int x, int z) {
            chunkX = x;
            chunkZ = z;
            waterCosts = new int[16][16];
        }
    }

    static class WorldCache {
        String worldName;
        int centerX, centerZ;
        List<CachedChunk> chunks;
        long timestamp;
        int cachedChunks;   // Статистика: из кеша
        int skippedChunks;  // Статистика: пропущено

        WorldCache(String name, int x, int z) {
            worldName = name;
            centerX = x;
            centerZ = z;
            chunks = new ArrayList<>();
            timestamp = System.currentTimeMillis();
            cachedChunks = 0;
            skippedChunks = 0;
        }
    }

    /**
     * НОВОЕ: Экспорт из готового кеша (быстро!)
     */
    private WorldCache exportFromCache(String worldName, int cx, int cz, int radius) {
        WorldCache worldCache = new WorldCache(worldName, cx, cz);

        // Получаем кеш из PathfindingManager
        WaterWorldCache waterCache = plugin.getPathfindingManager().getCache();

        int centerChunkX = cx >> 4;
        int centerChunkZ = cz >> 4;
        int totalChunks = (radius * 2 + 1) * (radius * 2 + 1);

        Bukkit.getLogger().info("═══════════════════════════════");
        Bukkit.getLogger().info("[CacheExport] Экспорт из кеша");
        Bukkit.getLogger().info("[CacheExport] Центр: " + cx + ", " + cz);
        Bukkit.getLogger().info("[CacheExport] Радиус: " + radius + " чанков");
        Bukkit.getLogger().info("[CacheExport] Всего чанков: " + totalChunks);
        Bukkit.getLogger().info("═══════════════════════════════");

        for (int chunkX = centerChunkX - radius; chunkX <= centerChunkX + radius; chunkX++) {
            for (int chunkZ = centerChunkZ - radius; chunkZ <= centerChunkZ + radius; chunkZ++) {

                CachedChunk exported = new CachedChunk(chunkX, chunkZ);
                boolean foundInCache = false;

                // Проверяем все блоки в чанке (16x16)
                for (int localX = 0; localX < 16; localX++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        int worldX = (chunkX << 4) + localX;
                        int worldZ = (chunkZ << 4) + localZ;

                        // Получаем из кеша cost
                        Integer cost = waterCache.getCost(worldX, worldZ);

                        if (cost != null) {
                            // Есть в кеше!
                            exported.waterCosts[localX][localZ] = cost;
                            foundInCache = true;
                        } else {
                            // Нет в кеше - дефолтное значение
                            exported.waterCosts[localX][localZ] = 1; // Вода по умолчанию
                        }
                    }
                }

                worldCache.chunks.add(exported);

                if (foundInCache) {
                    worldCache.cachedChunks++;
                } else {
                    worldCache.skippedChunks++;
                }
            }
        }

        Bukkit.getLogger().info("═══════════════════════════════");
        Bukkit.getLogger().info("[CacheExport] ✓ Экспорт завершён!");
        Bukkit.getLogger().info("[CacheExport] Из кеша: " + worldCache.cachedChunks);
        Bukkit.getLogger().info("[CacheExport] Пропущено: " + worldCache.skippedChunks);
        Bukkit.getLogger().info("[CacheExport] Всего: " + worldCache.chunks.size());
        Bukkit.getLogger().info("═══════════════════════════════");

        return worldCache;
    }
}