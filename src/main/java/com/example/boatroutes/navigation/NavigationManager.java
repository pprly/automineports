package com.example.boatroutes.navigation;

import com.example.boatroutes.BoatRoutesPlugin;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * NavigationManager v2.0 - Управление автопилотами
 * 
 * Отвечает за:
 * - Хранение активных autopilots
 * - Остановка autopilots при выходе из лодки
 * - Остановка всех autopilots при выключении сервера
 * 
 * @author BoatRoutes Team
 * @version 2.0
 */
public class NavigationManager {
    
    private final BoatRoutesPlugin plugin;
    
    // Активные автопилоты: UUID игрока → BoatAutopilot
    private final Map<UUID, BoatAutopilot> activeAutopilots;
    
    public NavigationManager(BoatRoutesPlugin plugin) {
        this.plugin = plugin;
        this.activeAutopilots = new HashMap<>();
    }
    
    /**
     * Добавить активный autopilot
     */
    public void addAutopilot(UUID playerUUID, BoatAutopilot autopilot) {
        // Останавливаем старый автопилот если есть
        if (activeAutopilots.containsKey(playerUUID)) {
            BoatAutopilot old = activeAutopilots.get(playerUUID);
            old.stopJourney("New journey started");
        }
        
        activeAutopilots.put(playerUUID, autopilot);
        plugin.getLogger().info("Active autopilots: " + activeAutopilots.size());
    }
    
    /**
     * Убрать активный autopilot
     */
    public void removeAutopilot(UUID playerUUID) {
        BoatAutopilot autopilot = activeAutopilots.remove(playerUUID);
        
        if (autopilot != null) {
            autopilot.stopJourney("Autopilot removed");
            plugin.getLogger().info("Active autopilots: " + activeAutopilots.size());
        }
    }
    
    /**
     * Получить активный autopilot игрока
     */
    public BoatAutopilot getAutopilot(UUID playerUUID) {
        return activeAutopilots.get(playerUUID);
    }
    
    /**
     * Проверить есть ли активный autopilot
     */
    public boolean hasActiveAutopilot(UUID playerUUID) {
        return activeAutopilots.containsKey(playerUUID);
    }
    
    /**
     * Остановить автопилот игрока
     */
    public void stopAutopilot(UUID playerUUID, String reason) {
        BoatAutopilot autopilot = activeAutopilots.remove(playerUUID);
        
        if (autopilot != null) {
            autopilot.stopJourney(reason);
            
            // Уведомляем игрока
            Player player = plugin.getServer().getPlayer(playerUUID);
            if (player != null && reason != null) {
                player.sendMessage("§7Autopilot stopped: " + reason);
            }
        }
    }
    
    /**
     * Остановить ВСЕ автопилоты (при выключении сервера)
     */
    public void stopAllAutopilots() {
        plugin.getLogger().info("Stopping " + activeAutopilots.size() + " active autopilots...");
        
        for (BoatAutopilot autopilot : activeAutopilots.values()) {
            autopilot.stopJourney("Server shutting down");
        }
        
        activeAutopilots.clear();
        
        plugin.getLogger().info("All autopilots stopped");
    }
    
    /**
     * Получить количество активных автопилотов
     */
    public int getActiveCount() {
        return activeAutopilots.size();
    }
}
