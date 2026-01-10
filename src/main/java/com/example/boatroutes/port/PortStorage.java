package com.example.boatroutes.port;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.dock.Dock;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * COMPLETE Port Storage with ALL fields
 * Saves: NPC location, NPC UUID, docks, navigation point
 */
public class PortStorage {
    
    private final BoatRoutesPlugin plugin;
    private final File file;
    private FileConfiguration config;
    
    public PortStorage(BoatRoutesPlugin plugin, File file) {
        this.plugin = plugin;
        this.file = file;
        
        // Create file if doesn't exist
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create ports.yml: " + e.getMessage());
            }
        }
        
        this.config = YamlConfiguration.loadConfiguration(file);
    }
    
    /**
     * Load all ports from file
     */
    public Map<String, Port> loadAll() {
        Map<String, Port> ports = new HashMap<>();
        
        ConfigurationSection portsSection = config.getConfigurationSection("ports");
        if (portsSection == null) {
            plugin.getLogger().info("No ports found in ports.yml");
            return ports;
        }
        
        for (String name : portsSection.getKeys(false)) {
            try {
                Port port = loadPort(name);
                if (port != null) {
                    ports.put(name.toLowerCase(), port);
                    plugin.getLogger().info("  Loaded port: " + name);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load port '" + name + "': " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        return ports;
    }
    
    /**
     * Load single port from config
     */
    private Port loadPort(String name) {
        String path = "ports." + name;
        
        if (!config.contains(path)) {
            return null;
        }
        
        Port port = new Port(name);
        
        // 1. NPC Location
        if (config.contains(path + ".npc-location")) {
            port.setNPCLocation((Location) config.get(path + ".npc-location"));
        }
        
        // 2. NPC UUID
        if (config.contains(path + ".npc-uuid")) {
            String uuidString = config.getString(path + ".npc-uuid");
            try {
                port.setNpcUUID(UUID.fromString(uuidString));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid NPC UUID for port " + name);
            }
        }
        
        // 3. Navigation Point (NEW!)
        if (config.contains(path + ".navigation-point")) {
            port.setNavigationPoint((Location) config.get(path + ".navigation-point"));
        }
        
        // 4. Legacy: Convergence/Split points (backward compatibility)
        if (config.contains(path + ".convergence")) {
            port.setConvergencePoint((Location) config.get(path + ".convergence"));
        }
        if (config.contains(path + ".split")) {
            port.setSplitPoint((Location) config.get(path + ".split"));
        }
        
        // 5. Docks (NEW!)
        if (config.contains(path + ".docks")) {
            List<Map<?, ?>> docksList = config.getMapList(path + ".docks");
            
            for (int i = 0; i < docksList.size(); i++) {
                Map<?, ?> dockMap = docksList.get(i);
                
                if (dockMap.containsKey("location")) {
                    Location dockLoc = (Location) dockMap.get("location");
                    int dockNumber = i + 1;
                    
                    Dock dock = new Dock(port, dockNumber, dockLoc);
                    port.addDock(dock);
                }
            }
        }
        
        // 6. Metadata
        if (config.contains(path + ".creator")) {
            String creatorString = config.getString(path + ".creator");
            try {
                port.setCreator(UUID.fromString(creatorString));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid creator UUID for port " + name);
            }
        }
        
        if (config.contains(path + ".created-at")) {
            port.setCreatedAt(config.getLong(path + ".created-at"));
        }
        
        return port;
    }
    
    /**
     * Save all ports to file
     */
    public void saveAll(Map<String, Port> ports) {
        // Clear existing data
        config.set("ports", null);
        
        // Save each port
        for (Port port : ports.values()) {
            save(port);
        }
        
        // Write to disk
        try {
            config.save(file);
            plugin.getLogger().info("Saved " + ports.size() + " ports to ports.yml");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save ports.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Save single port (also writes to disk!)
     */
    public void save(Port port) {
        String path = "ports." + port.getName();
        
        // 1. NPC Location
        if (port.getNPCLocation() != null) {
            config.set(path + ".npc-location", port.getNPCLocation());
        }
        
        // 2. NPC UUID
        if (port.getNpcUUID() != null) {
            config.set(path + ".npc-uuid", port.getNpcUUID().toString());
        }
        
        // 3. Navigation Point (NEW!)
        if (port.getNavigationPoint() != null) {
            config.set(path + ".navigation-point", port.getNavigationPoint());
        }
        
        // 4. Legacy: Convergence/Split points (for backward compatibility)
        if (port.getConvergencePoint() != null) {
            config.set(path + ".convergence", port.getConvergencePoint());
        }
        if (port.getSplitPoint() != null) {
            config.set(path + ".split", port.getSplitPoint());
        }
        
        // 5. Docks (NEW!)
        if (!port.getDocks().isEmpty()) {
            List<Map<String, Object>> docksList = new ArrayList<>();
            
            for (Dock dock : port.getDocks()) {
                Map<String, Object> dockMap = new HashMap<>();
                dockMap.put("number", dock.getNumber());
                dockMap.put("location", dock.getLocation());
                docksList.add(dockMap);
            }
            
            config.set(path + ".docks", docksList);
        }
        
        // 6. Metadata
        if (port.getCreator() != null) {
            config.set(path + ".creator", port.getCreator().toString());
        }
        
        config.set(path + ".created-at", port.getCreatedAt());
        
        // Write to disk immediately
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save port " + port.getName() + ": " + e.getMessage());
        }
    }
}
