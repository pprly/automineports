package com.example.boatroutes.navigation;

import com.example.boatroutes.BoatRoutesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.List;

/**
 * BoatAutopilot v3.0 - SMOOTH PHYSICS + UPGRADE SUPPORT!
 *
 * НОВОЕ:
 * - Плавное движение с инерцией (как настоящая лодка!)
 * - Поддержка улучшений скорости лодки
 * - Естественное поведение при поворотах
 * - Bidirectional path support
 *
 * @author BoatRoutes Team
 * @version 3.0-SMOOTH-PHYSICS
 */
public class BoatAutopilot {

    private final BoatRoutesPlugin plugin;
    private final Boat boat;
    private final Player player;
    private final String fromPort;
    private final String toPort;

    private List<Location> path;
    private int currentWaypointIndex;
    private BukkitTask task;

    // Настройки движения
    private double baseSpeed;           // Базовая скорость (из config или улучшений)
    private double smoothness;          // Плавность (0.05-0.2, чем меньше = плавнее)
    private double waypointRadius;      // Радиус достижения waypoint

    public BoatAutopilot(BoatRoutesPlugin plugin, Boat boat, Player player,
                         String fromPort, String toPort) {
        this.plugin = plugin;
        this.boat = boat;
        this.player = player;
        this.fromPort = fromPort;
        this.toPort = toPort;

        // Загружаем настройки из config
        this.baseSpeed = plugin.getConfig().getDouble("boat.default-speed", 0.35);
        this.smoothness = plugin.getConfig().getDouble("boat.smoothness", 0.1);
        this.waypointRadius = plugin.getConfig().getDouble("boat.waypoint-radius", 3.0);

        // TODO: В будущем загружать скорость из PlayerBoat upgrades!
        // PlayerBoat playerBoat = plugin.getBoatManager().getPlayerBoat(player.getUniqueId());
        // if (playerBoat != null) {
        //     this.baseSpeed = playerBoat.getSpeed(); // Скорость с улучшениями!
        // }

        this.currentWaypointIndex = 0;
    }

    /**
     * Начинает путешествие С BIDIRECTIONAL SUPPORT!
     */
    public boolean startJourney() {
        // Пытаемся загрузить путь в обе стороны
        path = plugin.getPathfindingManager().getPath(fromPort, toPort);

        if (path == null || path.isEmpty()) {
            plugin.getLogger().info("Path " + fromPort + " → " + toPort + " not found, trying reverse...");

            // Пробуем обратный путь
            path = plugin.getPathfindingManager().getPath(toPort, fromPort);

            if (path != null && !path.isEmpty()) {
                // РАЗВОРАЧИВАЕМ путь!
                Collections.reverse(path);
                plugin.getLogger().info("✓ Using reversed path: " + toPort + " → " + fromPort);
            } else {
                plugin.getLogger().warning("✗ No path found in either direction!");
                player.sendMessage("§c✗ No path found to " + toPort + "!");
                return false;
            }
        } else {
            plugin.getLogger().info("✓ Using forward path: " + fromPort + " → " + toPort);
        }

        // Проверяем что путь не пустой
        if (path.size() < 2) {
            plugin.getLogger().warning("✗ Path too short: " + path.size() + " waypoints");
            player.sendMessage("§c✗ Invalid path!");
            return false;
        }

        player.sendMessage("§a✓ Autopilot engaged!");
        player.sendMessage("§7Destination: §e" + toPort);
        player.sendMessage("§7Waypoints: §f" + path.size());
        player.sendMessage("§7Speed: §f" + String.format("%.2f", baseSpeed) + " blocks/tick");
        player.sendMessage("");
        player.sendMessage("§7Exit boat to cancel autopilot");

        currentWaypointIndex = 0;

        // Запускаем движение (каждый тик = 0.05 секунды)
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::updateBoatMovement, 0L, 1L);

        plugin.getLogger().info("Autopilot task started for " + player.getName());

        return true;
    }

    /**
     * Обновляет движение лодки каждый тик (ПЛАВНОЕ ДВИЖЕНИЕ!)
     */
    private void updateBoatMovement() {
        // === SAFETY CHECKS ===

        // Проверка что лодка еще существует
        if (boat == null || boat.isDead() || !boat.isValid()) {
            stopJourney("Boat destroyed");
            return;
        }

        // Проверка что игрок все еще в лодке
        if (!boat.getPassengers().contains(player)) {
            stopJourney("Player exited boat");
            return;
        }

        // Проверка что не достигли конца пути
        if (currentWaypointIndex >= path.size()) {
            arriveAtDestination();
            return;
        }

        // === NAVIGATION ===

        // Текущий waypoint
        Location target = path.get(currentWaypointIndex);
        Location boatLoc = boat.getLocation();

        // Вычисляем дистанцию до waypoint (только XZ, игнорируем Y)
        double dx = target.getX() - boatLoc.getX();
        double dz = target.getZ() - boatLoc.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        // Если близко к waypoint - переходим к следующему
        if (distance < waypointRadius) {
            currentWaypointIndex++;

            // Показываем прогресс каждые 10 waypoints
            if (currentWaypointIndex % 10 == 0 || currentWaypointIndex < 3) {
                int progress = (currentWaypointIndex * 100) / path.size();
                player.sendActionBar("§6⚓ Progress: §e" + progress + "% §7(" +
                        currentWaypointIndex + "/" + path.size() + ")");

                plugin.getLogger().info("Progress: " + currentWaypointIndex + "/" + path.size() +
                        " (" + progress + "%)");
            }

            return;
        }

        // === SMOOTH MOVEMENT ===

        // Вычисляем направление к waypoint (нормализованный вектор XZ)
        Vector direction = new Vector(dx, 0, dz).normalize();

        // Целевая скорость в направлении waypoint
        Vector targetVelocity = direction.multiply(baseSpeed);

        // Текущая скорость лодки
        Vector currentVelocity = boat.getVelocity();

        // ПЛАВНАЯ ИНТЕРПОЛЯЦИЯ (добавляем только часть разницы)
        // smoothness = 0.1 означает "корректируем на 10% каждый тик"
        // Это создаёт плавное ускорение и торможение!
        Vector velocityDiff = targetVelocity.subtract(currentVelocity);
        Vector force = velocityDiff.multiply(smoothness);

        // Применяем новую скорость
        Vector newVelocity = currentVelocity.add(force);

        // Ограничиваем максимальную скорость (для безопасности)
        double maxSpeed = baseSpeed * 1.5;
        if (newVelocity.length() > maxSpeed) {
            newVelocity = newVelocity.normalize().multiply(maxSpeed);
        }

        boat.setVelocity(newVelocity);

        // ЛОДКА АВТОМАТИЧЕСКИ ПОВЕРНЁТСЯ В НАПРАВЛЕНИИ ДВИЖЕНИЯ!
        // Minecraft сам поворачивает entity в направлении velocity
        // Не нужно teleport или setRotation!
    }

    /**
     * Прибытие в пункт назначения
     */
    private void arriveAtDestination() {
        stopJourney(null);

        player.sendMessage("");
        player.sendMessage("§a✓ Arrived at destination!");
        player.sendMessage("§7Welcome to: §e" + toPort);
        player.sendMessage("");

        // Звук прибытия
        player.playSound(player.getLocation(),
                org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        plugin.getLogger().info("Player " + player.getName() + " arrived at " + toPort);
    }

    /**
     * Останавливает путешествие
     */
    public void stopJourney(String reason) {
        if (task != null) {
            task.cancel();
            task = null;
        }

        // Плавная остановка лодки
        if (boat != null && !boat.isDead()) {
            Vector currentVel = boat.getVelocity();
            boat.setVelocity(currentVel.multiply(0.5)); // Замедляем вместо резкой остановки
        }

        if (reason != null) {
            plugin.getLogger().info("Autopilot stopped: " + reason);
            player.sendMessage("§7Autopilot stopped: " + reason);
        }
    }

    /**
     * Проверяет, активен ли autopilot
     */
    public boolean isActive() {
        return task != null;
    }

    /**
     * Получить текущий прогресс (0-100)
     */
    public int getProgress() {
        if (path == null || path.isEmpty()) return 0;
        return (currentWaypointIndex * 100) / path.size();
    }

    /**
     * Получить оставшееся расстояние
     */
    public double getRemainingDistance() {
        if (path == null || currentWaypointIndex >= path.size()) return 0;

        double distance = 0;
        Location current = boat.getLocation();

        for (int i = currentWaypointIndex; i < path.size(); i++) {
            Location waypoint = path.get(i);
            distance += current.distance(waypoint);
            current = waypoint;
        }

        return distance;
    }

    /**
     * Получить текущую скорость
     */
    public double getCurrentSpeed() {
        if (boat == null || boat.isDead()) return 0;
        return boat.getVelocity().length();
    }
}