package com.example.boatroutes.commands;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class ExportCommand implements CommandExecutor {

    private static final int WATER_LEVEL = 62;
    private final JavaPlugin plugin;

    public ExportCommand(JavaPlugin plugin) {
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
            player.sendMessage("§e║   Экспорт карты для          ║");
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
        player.sendMessage("§a║  Экспорт кеша мира           ║");
        player.sendMessage("§a╚══════════════════════════════╝");
        player.sendMessage("");
        player.sendMessage("§7Твоя позиция: §f" + centerX + ", " + centerZ);
        player.sendMessage("§7Радиус: §f" + radius + " чанков (§e" + (radius * 16) + " блоков§f)");
        player.sendMessage("§7Файл: §f" + exportFile.getName());
        player.sendMessage("");
        player.sendMessage("§eЗапускаю экспорт... §7Это займёт время.");
        player.sendMessage("§7Следи за прогрессом в консоли сервера.");
        player.sendMessage("");

        // Экспорт в отдельном потоке чтобы не лагать
        new Thread(() -> {
            try {
                exportCache(player.getWorld(), centerX, centerZ, radius, exportFile);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("");
                    player.sendMessage("§a✓ Экспорт завершён успешно!");
                    player.sendMessage("");
                    player.sendMessage("§fФайл сохранён:");
                    player.sendMessage("§e" + exportFile.getAbsolutePath());
                    player.sendMessage("");
                    player.sendMessage("§7Следующие шаги:");
                    player.sendMessage("§71. Скопируй файл cache_export.json");
                    player.sendMessage("§72. Запусти визуализатор на компьютере");
                    player.sendMessage("§73. Загрузи JSON и создавай порты!");
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
        }).start();

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
        WorldCache(String name, int x, int z) {
            worldName = name;
            centerX = x;
            centerZ = z;
            chunks = new ArrayList<>();
            timestamp = System.currentTimeMillis();
        }
    }

    private void exportCache(World world, int cx, int cz, int radius, File file) throws Exception {
        WorldCache cache = new WorldCache(world.getName(), cx, cz);

        int centerChunkX = cx >> 4;
        int centerChunkZ = cz >> 4;
        int totalChunks = (radius * 2 + 1) * (radius * 2 + 1);
        int processed = 0;

        Bukkit.getLogger().info("═══════════════════════════════");
        Bukkit.getLogger().info("[CacheExport] Начинаем экспорт");
        Bukkit.getLogger().info("[CacheExport] Центр: " + cx + ", " + cz);
        Bukkit.getLogger().info("[CacheExport] Радиус: " + radius + " чанков");
        Bukkit.getLogger().info("[CacheExport] Всего чанков: " + totalChunks);
        Bukkit.getLogger().info("═══════════════════════════════");

        long startTime = System.currentTimeMillis();

        for (int chunkX = centerChunkX - radius; chunkX <= centerChunkX + radius; chunkX++) {
            for (int chunkZ = centerChunkZ - radius; chunkZ <= centerChunkZ + radius; chunkZ++) {

                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    world.loadChunk(chunkX, chunkZ);
                }

                Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                CachedChunk cached = new CachedChunk(chunkX, chunkZ);

                // Анализируем каждый блок в чанке на уровне воды
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        Block block = chunk.getBlock(x, WATER_LEVEL, z);

                        if (isWater(block.getType())) {
                            int worldX = (chunkX << 4) + x;
                            int worldZ = (chunkZ << 4) + z;
                            cached.waterCosts[x][z] = calculateShoreDistance(world, worldX, worldZ);
                        } else {
                            cached.waterCosts[x][z] = 100; // Суша = непроходимо
                        }
                    }
                }

                cache.chunks.add(cached);
                processed++;

                // Прогресс каждые 100 чанков
                if (processed % 100 == 0) {
                    int percent = (processed * 100) / totalChunks;
                    Bukkit.getLogger().info(String.format(
                            "[CacheExport] Прогресс: %d/%d (%d%%)",
                            processed, totalChunks, percent
                    ));
                }
            }
        }

        long endTime = System.currentTimeMillis();
        double seconds = (endTime - startTime) / 1000.0;

        // Сохраняем в JSON
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        FileWriter writer = new FileWriter(file);
        gson.toJson(cache, writer);
        writer.close();

        Bukkit.getLogger().info("═══════════════════════════════");
        Bukkit.getLogger().info("[CacheExport] ✓ Экспорт завершён!");
        Bukkit.getLogger().info("[CacheExport] Файл: " + file.getAbsolutePath());
        Bukkit.getLogger().info("[CacheExport] Чанков обработано: " + processed);
        Bukkit.getLogger().info("[CacheExport] Время: " + String.format("%.1f", seconds) + " сек");
        Bukkit.getLogger().info("═══════════════════════════════");
    }

    /**
     * Вычисляет расстояние от воды до ближайшей суши
     * Возвращает стоимость для навигации
     */
    private int calculateShoreDistance(World world, int x, int z) {
        int minDistance = 6; // Максимум проверяем 5 блоков вокруг

        // Проверяем все блоки в радиусе 5
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (dx == 0 && dz == 0) continue;

                Block neighbor = world.getBlockAt(x + dx, WATER_LEVEL, z + dz);

                if (!isWater(neighbor.getType())) {
                    // Нашли сушу - вычисляем расстояние (Чебышевское)
                    int distance = Math.max(Math.abs(dx), Math.abs(dz));
                    minDistance = Math.min(minDistance, distance);
                }
            }
        }

        // Преобразуем расстояние в стоимость для A*
        if (minDistance >= 6) return 1;  // Глубокая вода (самая низкая стоимость)
        if (minDistance == 5) return 1;  // 5 блоков от берега
        if (minDistance == 4) return 2;  // 4 блока от берега
        if (minDistance == 3) return 3;  // 3 блока от берега
        if (minDistance == 2) return 4;  // 2 блока от берега
        return 5;                        // 1 блок от берега (высокая стоимость)
    }

    private boolean isWater(Material material) {
        return material == Material.WATER;
    }
}