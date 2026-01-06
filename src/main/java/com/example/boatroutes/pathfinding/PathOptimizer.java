package com.example.boatroutes.pathfinding;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * PathOptimizer v4.0 - Оптимизация пути
 * 
 * Уменьшает количество waypoints используя алгоритм line-of-sight.
 * Путь из 500 точек может быть сокращён до 20-30.
 * 
 * @author BoatRoutes Team
 * @version 4.0
 */
public class PathOptimizer {
    
    private final PathValidator validator;
    
    public PathOptimizer(PathValidator validator) {
        this.validator = validator;
    }
    
    /**
     * Оптимизирует путь, убирая промежуточные точки.
     * Использует алгоритм line-of-sight: если из точки A видно точку C,
     * то точка B между ними не нужна.
     * 
     * @param path Исходный путь
     * @return Оптимизированный путь
     */
    public List<Location> optimize(List<Location> path) {
        if (path == null || path.size() <= 2) {
            return path;
        }
        
        List<Location> optimized = new ArrayList<>();
        
        // Всегда добавляем первую точку
        optimized.add(path.get(0));
        
        int currentIndex = 0;
        
        while (currentIndex < path.size() - 1) {
            // Ищем самую дальнюю точку, до которой есть прямой путь
            int farthest = currentIndex + 1;
            
            for (int i = currentIndex + 2; i < path.size(); i++) {
                if (hasLineOfSight(path.get(currentIndex), path.get(i))) {
                    farthest = i;
                }
            }
            
            // Добавляем эту точку
            optimized.add(path.get(farthest));
            currentIndex = farthest;
        }
        
        return optimized;
    }
    
    /**
     * Проверяет прямую видимость между двумя точками.
     * "Видимость" означает, что вся линия проходит через воду.
     */
    private boolean hasLineOfSight(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        
        // Для очень близких точек - всегда true
        if (distance < 3) return true;
        
        // Проверяем точки вдоль линии каждые 2 блока
        int steps = (int) Math.ceil(distance / 2);
        
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            int x = (int) (from.getX() + dx * t);
            int z = (int) (from.getZ() + dz * t);
            
            if (!validator.isNavigableWater(x, validator.getSeaLevel(), z, from.getWorld())) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Сглаживает путь, добавляя промежуточные точки на поворотах.
     * Делает движение лодки более плавным.
     */
    public List<Location> smooth(List<Location> path) {
        if (path == null || path.size() <= 2) {
            return path;
        }
        
        List<Location> smoothed = new ArrayList<>();
        smoothed.add(path.get(0));
        
        for (int i = 1; i < path.size() - 1; i++) {
            Location prev = path.get(i - 1);
            Location curr = path.get(i);
            Location next = path.get(i + 1);
            
            // Вычисляем угол поворота
            double angle = calculateTurnAngle(prev, curr, next);
            
            // Если угол большой (> 30°) - добавляем промежуточные точки
            if (Math.abs(angle) > 30) {
                // Добавляем точку перед поворотом
                double midX1 = (prev.getX() + curr.getX()) / 2;
                double midZ1 = (prev.getZ() + curr.getZ()) / 2;
                smoothed.add(new Location(curr.getWorld(), midX1, curr.getY(), midZ1));
                
                // Добавляем саму точку поворота
                smoothed.add(curr);
                
                // Добавляем точку после поворота
                double midX2 = (curr.getX() + next.getX()) / 2;
                double midZ2 = (curr.getZ() + next.getZ()) / 2;
                smoothed.add(new Location(curr.getWorld(), midX2, curr.getY(), midZ2));
            } else {
                smoothed.add(curr);
            }
        }
        
        smoothed.add(path.get(path.size() - 1));
        
        return smoothed;
    }
    
    /**
     * Вычисляет угол поворота в градусах
     */
    private double calculateTurnAngle(Location prev, Location curr, Location next) {
        double dx1 = curr.getX() - prev.getX();
        double dz1 = curr.getZ() - prev.getZ();
        double dx2 = next.getX() - curr.getX();
        double dz2 = next.getZ() - curr.getZ();
        
        double angle1 = Math.atan2(dz1, dx1);
        double angle2 = Math.atan2(dz2, dx2);
        
        double diff = Math.toDegrees(angle2 - angle1);
        
        // Нормализуем угол в диапазон [-180, 180]
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        
        return diff;
    }
    
    /**
     * Упрощённая оптимизация для очень длинных путей.
     * Просто берёт каждую N-ю точку.
     */
    public List<Location> simplify(List<Location> path, int keepEveryN) {
        if (path == null || path.size() <= 2) {
            return path;
        }
        
        List<Location> simplified = new ArrayList<>();
        
        // Всегда добавляем первую точку
        simplified.add(path.get(0));
        
        // Добавляем каждую N-ю точку
        for (int i = keepEveryN; i < path.size() - 1; i += keepEveryN) {
            simplified.add(path.get(i));
        }
        
        // Всегда добавляем последнюю точку
        simplified.add(path.get(path.size() - 1));
        
        return simplified;
    }
}
