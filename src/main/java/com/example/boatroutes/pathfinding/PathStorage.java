package com.example.boatroutes.pathfinding;

import com.example.boatroutes.BoatRoutesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * PathStorage v4.0 - Сохранение и загрузка путей
 * 
 * Сохраняет рассчитанные пути в routes.yml для мгновенной загрузки.
 * Путь рассчитывается один раз и кешируется навсегда.
 * 
 * @author BoatRoutes Team
 * @version 4.0
 */
public class PathStorage {
    
    private final BoatRoutesPlugin plugin;
    private final File routesFile;
    private FileConfiguration routesConfig;
    
    // Кеш путей в памяти
    private final Map<String, List<Location>> pathCache = new HashMap<>();
    
    public PathStorage(BoatRoutesPlugin plugin) {
        this.plugin = plugin;
        this.routesFile = new File(plugin.getDataFolder(), "routes.yml");
        
        loadRoutesFile();
    }
    
    /**
     * Загружает файл routes.yml
     */
    private void loadRoutesFile() {
        if (!routesFile.exists()) {
            try {
                routesFile.getParentFile().mkdirs();
                routesFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Could not create routes.yml: " + e.getMessage());
            }
        }
        
        routesConfig = YamlConfiguration.loadConfiguration(routesFile);
    }
    
    /**
     * Сохраняет routes.yml на диск
     */
    private void saveRoutesFile() {
        try {
            routesConfig.save(routesFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save routes.yml: " + e.getMessage());
        }
    }
    
    /**
     * Создаёт ключ для пути между портами
     */
    private String createPathKey(String from, String to) {
        // Сортируем имена для консистентности (A->B = B->A)
        if (from.compareTo(to) > 0) {
            String temp = from;
            from = to;
            to = temp;
        }
        return from + "_to_" + to;
    }
    
    /**
     * Сохраняет путь между портами
     */
    public void savePath(String fromPort, String toPort, List<Location> path) {
        String key = createPathKey(fromPort, toPort);
        
        // Сохраняем в кеш
        pathCache.put(key, new ArrayList<>(path));
        
        // Сохраняем в YAML
        ConfigurationSection pathSection = routesConfig.createSection("routes." + key);
        
        pathSection.set("from", fromPort);
        pathSection.set("to", toPort);
        pathSection.set("waypoints", path.size());
        pathSection.set("created", System.currentTimeMillis());
        
        // Сохраняем waypoints как список строк
        List<String> waypointStrings = new ArrayList<>();
        for (Location loc : path) {
            String waypointStr = String.format("%s;%.2f;%.2f;%.2f",
                loc.getWorld().getName(),
                loc.getX(),
                loc.getY(),
                loc.getZ());
            waypointStrings.add(waypointStr);
        }
        pathSection.set("path", waypointStrings);
        
        saveRoutesFile();
        
        plugin.getLogger().info("Saved path: " + key + " (" + path.size() + " waypoints)");
    }
    
    /**
     * Загружает путь между портами
     */
    public List<Location> loadPath(String fromPort, String toPort) {
        String key = createPathKey(fromPort, toPort);
        
        // Сначала проверяем кеш
        if (pathCache.containsKey(key)) {
            return new ArrayList<>(pathCache.get(key));
        }
        
        // Загружаем из YAML
        ConfigurationSection pathSection = routesConfig.getConfigurationSection("routes." + key);
        if (pathSection == null) {
            return null;
        }
        
        List<String> waypointStrings = pathSection.getStringList("path");
        if (waypointStrings.isEmpty()) {
            return null;
        }
        
        List<Location> path = new ArrayList<>();
        
        for (String waypointStr : waypointStrings) {
            Location loc = parseLocation(waypointStr);
            if (loc != null) {
                path.add(loc);
            }
        }
        
        // Сохраняем в кеш
        if (!path.isEmpty()) {
            pathCache.put(key, path);
        }
        
        return path;
    }
    
    /**
     * Парсит строку координат в Location
     */
    private Location parseLocation(String str) {
        try {
            String[] parts = str.split(";");
            if (parts.length < 4) return null;
            
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) {
                // Пробуем найти первый доступный мир
                world = Bukkit.getWorlds().get(0);
            }
            
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            
            return new Location(world, x, y, z);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Проверяет, существует ли путь
     */
    public boolean hasPath(String fromPort, String toPort) {
        String key = createPathKey(fromPort, toPort);
        
        if (pathCache.containsKey(key)) {
            return true;
        }
        
        return routesConfig.contains("routes." + key);
    }
    
    /**
     * Удаляет путь
     */
    public void deletePath(String fromPort, String toPort) {
        String key = createPathKey(fromPort, toPort);
        
        pathCache.remove(key);
        routesConfig.set("routes." + key, null);
        
        saveRoutesFile();
        
        plugin.getLogger().info("Deleted path: " + key);
    }
    
    /**
     * Удаляет все пути
     */
    public void clearAllPaths() {
        pathCache.clear();
        routesConfig.set("routes", null);
        
        saveRoutesFile();
        
        plugin.getLogger().info("Cleared all paths");
    }
    
    /**
     * Загружает все пути в кеш
     */
    public void loadAllPaths() {
        pathCache.clear();
        
        ConfigurationSection routesSection = routesConfig.getConfigurationSection("routes");
        if (routesSection == null) {
            plugin.getLogger().info("No saved routes found");
            return;
        }
        
        int count = 0;
        for (String key : routesSection.getKeys(false)) {
            ConfigurationSection pathSection = routesSection.getConfigurationSection(key);
            if (pathSection == null) continue;
            
            List<String> waypointStrings = pathSection.getStringList("path");
            if (waypointStrings.isEmpty()) continue;
            
            List<Location> path = new ArrayList<>();
            for (String waypointStr : waypointStrings) {
                Location loc = parseLocation(waypointStr);
                if (loc != null) {
                    path.add(loc);
                }
            }
            
            if (!path.isEmpty()) {
                pathCache.put(key, path);
                count++;
            }
        }
        
        plugin.getLogger().info("Loaded " + count + " routes from routes.yml");
    }
    
    /**
     * Возвращает список всех сохранённых путей
     */
    public List<String> getAllPathKeys() {
        Set<String> keys = new HashSet<>(pathCache.keySet());
        
        ConfigurationSection routesSection = routesConfig.getConfigurationSection("routes");
        if (routesSection != null) {
            keys.addAll(routesSection.getKeys(false));
        }
        
        return new ArrayList<>(keys);
    }
    
    /**
     * Возвращает Set ID закешированных маршрутов (для совместимости с PortCommand)
     */
    public Set<String> getCachedRouteIds() {
        Set<String> keys = new HashSet<>(pathCache.keySet());
        
        ConfigurationSection routesSection = routesConfig.getConfigurationSection("routes");
        if (routesSection != null) {
            keys.addAll(routesSection.getKeys(false));
        }
        
        return keys;
    }
    
    /**
     * Возвращает информацию о пути
     */
    public Map<String, Object> getPathInfo(String fromPort, String toPort) {
        String key = createPathKey(fromPort, toPort);
        
        ConfigurationSection pathSection = routesConfig.getConfigurationSection("routes." + key);
        if (pathSection == null) {
            return null;
        }
        
        Map<String, Object> info = new HashMap<>();
        info.put("from", pathSection.getString("from"));
        info.put("to", pathSection.getString("to"));
        info.put("waypoints", pathSection.getInt("waypoints"));
        info.put("created", pathSection.getLong("created"));
        
        return info;
    }
}
