package com.example.boatroutes.commands;

import com.example.boatroutes.export.CacheExporter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;

/**
 * Команда для экспорта кеша: /port export-cache <radius>
 * 
 * Добавь в plugin.yml:
 * 
 * commands:
 *   port:
 *     description: Port management commands
 *     usage: /port <command>
 *     permission: automineports.admin
 */
public class ExportCacheCommand implements CommandExecutor {
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cЭту команду может использовать только игрок!");
            return true;
        }
        
        Player player = (Player) sender;
        
        // Проверяем аргументы
        if (args.length < 2 || !args[0].equalsIgnoreCase("export-cache")) {
            player.sendMessage("§eИспользование: /port export-cache <radius>");
            player.sendMessage("§7Пример: /port export-cache 50");
            player.sendMessage("§7radius - радиус в чанках (1 чанк = 16 блоков)");
            return true;
        }
        
        int radius;
        try {
            radius = Integer.parseInt(args[1]);
            if (radius < 1 || radius > 200) {
                player.sendMessage("§cРадиус должен быть от 1 до 200 чанков!");
                return true;
            }
        } catch (NumberFormatException e) {
            player.sendMessage("§cНеверный радиус! Используй число.");
            return true;
        }
        
        // Получаем координаты игрока
        int centerX = player.getLocation().getBlockX();
        int centerZ = player.getLocation().getBlockZ();
        
        // Файл для экспорта
        File pluginFolder = player.getServer().getPluginManager().getPlugin("AutoMinePorts").getDataFolder();
        File exportFile = new File(pluginFolder, "cache_export.json");
        
        player.sendMessage("§a┌─────────────────────────────┐");
        player.sendMessage("§a│  Экспорт кеша мира          │");
        player.sendMessage("§a└─────────────────────────────┘");
        player.sendMessage("");
        player.sendMessage("§7Центр: §f" + centerX + ", " + centerZ);
        player.sendMessage("§7Радиус: §f" + radius + " чанков (" + (radius * 16) + " блоков)");
        player.sendMessage("§7Файл: §f" + exportFile.getName());
        player.sendMessage("");
        player.sendMessage("§eЗапускаем экспорт... Это может занять время.");
        player.sendMessage("§7Смотри консоль для прогресса.");
        
        // Запускаем экспорт в отдельном потоке чтобы не лагать сервер
        new Thread(() -> {
            try {
                CacheExporter.exportCache(
                    player.getWorld(),
                    centerX,
                    centerZ,
                    radius,
                    exportFile
                );
                
                // Уведомляем игрока
                player.getServer().getScheduler().runTask(
                    player.getServer().getPluginManager().getPlugin("AutoMinePorts"),
                    () -> {
                        player.sendMessage("");
                        player.sendMessage("§a✓ Экспорт завершён!");
                        player.sendMessage("§7Файл: §f" + exportFile.getAbsolutePath());
                        player.sendMessage("");
                        player.sendMessage("§eТеперь скопируй файл §fcache_export.json");
                        player.sendMessage("§eв папку визуализатора и запусти программу.");
                    }
                );
                
            } catch (Exception e) {
                player.getServer().getScheduler().runTask(
                    player.getServer().getPluginManager().getPlugin("AutoMinePorts"),
                    () -> {
                        player.sendMessage("§c✗ Ошибка экспорта!");
                        player.sendMessage("§7" + e.getMessage());
                    }
                );
                e.printStackTrace();
            }
        }).start();
        
        return true;
    }
}
