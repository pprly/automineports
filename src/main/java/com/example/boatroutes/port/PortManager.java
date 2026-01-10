package com.example.boatroutes.port;

import com.example.boatroutes.BoatRoutesPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Manager for all ports
 * Handles port creation, deletion, and storage
 */
public class PortManager {
    
    private final BoatRoutesPlugin plugin;
    private final File portsFile;
    private FileConfiguration portsConfig;
    
    // All ports: name -> Port object
    private final Map<String, Port> ports;
    
    // Helper classes
    private final PortCreator portCreator;
    private final PortStorage portStorage;
    
    public PortManager(BoatRoutesPlugin plugin) {
        this.plugin = plugin;
        this.portsFile = new File(plugin.getDataFolder(), "ports.yml");
        this.ports = new HashMap<>();
        
        // Initialize helpers
        this.portCreator = new PortCreator(plugin);
        this.portStorage = new PortStorage(plugin, portsFile);
        
        // Create ports file if doesn't exist
        if (!portsFile.exists()) {
            portsFile.getParentFile().mkdirs();
            portsConfig = new YamlConfiguration();
        } else {
            portsConfig = YamlConfiguration.loadConfiguration(portsFile);
        }
    }
    
    // === PORT MANAGEMENT ===
    
    /**
     * Create a new port
     */
    public Port createPort(String name, Player creator) {
        Port port = portCreator.createPort(name, creator);
        
        if (port != null) {
            ports.put(name.toLowerCase(), port);
            plugin.getLogger().info("Port created: " + name);
        }
        
        return port;
    }
    
    /**
     * Delete a port
     */
    public boolean deletePort(String name) {
        Port port = ports.remove(name.toLowerCase());
        
        if (port != null) {
            // TODO: Remove NPC, cleanup docks
            plugin.getLogger().info("Port deleted: " + name);
            return true;
        }
        
        return false;
    }
    
    /**
     * Get port by name
     */
    public Port getPort(String name) {
        return ports.get(name.toLowerCase());
    }
    
    /**
     * Check if port exists
     */
    public boolean portExists(String name) {
        return ports.containsKey(name.toLowerCase());
    }
    
    /**
     * Get all port names
     */
    public Set<String> getPortNames() {
        return new HashSet<>(ports.keySet());
    }
    
    /**
     * Get all ports
     */
    public Collection<Port> getAllPorts() {
        return new ArrayList<>(ports.values());
    }
    
    // === STORAGE ===
    
    /**
     * Load all ports from file
     */
    public void loadAllPorts() {
        Map<String, Port> loadedPorts = portStorage.loadAll();
        ports.clear();
        ports.putAll(loadedPorts);
        
        plugin.getLogger().info("Loaded " + ports.size() + " ports");
        
        // Respawn NPCs after loading
        respawnAllNPCs();
    }
    
    /**
     * Save all ports to file
     */
    public void saveAllPorts() {
        portStorage.saveAll(ports);
        plugin.getLogger().info("Saved " + ports.size() + " ports");
    }
    
    /**
     * Save single port
     */
    public void savePort(Port port) {
        portStorage.save(port);
    }
    
    /**
     * Respawn NPCs for all loaded ports
     */
    private void respawnAllNPCs() {
        int respawnedCount = 0;
        
        for (Port port : ports.values()) {
            if (port.getNPCLocation() != null) {
                boolean success = plugin.getNPCManager().respawnNPC(port);
                if (success) {
                    respawnedCount++;
                }
            }
        }
        
        plugin.getLogger().info("Respawned " + respawnedCount + " NPCs");
    }
}
