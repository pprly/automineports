package com.example.boatroutes.port;
import com.example.boatroutes.BoatRoutesPlugin;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class PortStorage {
    private final BoatRoutesPlugin plugin;
    private final File file;
    private FileConfiguration config;
    
    public PortStorage(BoatRoutesPlugin plugin, File file) {
        this.plugin = plugin;
        this.file = file;
        this.config = YamlConfiguration.loadConfiguration(file);
    }
    
    public Map<String, Port> loadAll() {
        Map<String, Port> ports = new HashMap<>();
        if (config.getConfigurationSection("ports") == null) return ports;
        
        for (String name : config.getConfigurationSection("ports").getKeys(false)) {
            Port port = loadPort(name);
            if (port != null) ports.put(name.toLowerCase(), port);
        }
        return ports;
    }
    
    private Port loadPort(String name) {
        String path = "ports." + name;
        Port port = new Port(name);
        
        if (config.contains(path + ".npc-location")) {
            port.setNPCLocation((Location) config.get(path + ".npc-location"));
        }
        if (config.contains(path + ".convergence")) {
            port.setConvergencePoint((Location) config.get(path + ".convergence"));
        }
        if (config.contains(path + ".split")) {
            port.setSplitPoint((Location) config.get(path + ".split"));
        }
        
        return port;
    }
    
    public void saveAll(Map<String, Port> ports) {
        config.set("ports", null);
        for (Port port : ports.values()) {
            save(port);
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save ports: " + e.getMessage());
        }
    }
    
    public void save(Port port) {
        String path = "ports." + port.getName();
        if (port.getNPCLocation() != null) {
            config.set(path + ".npc-location", port.getNPCLocation());
        }
        if (port.getConvergencePoint() != null) {
            config.set(path + ".convergence", port.getConvergencePoint());
        }
        if (port.getSplitPoint() != null) {
            config.set(path + ".split", port.getSplitPoint());
        }
    }
}