package com.example.boatroutes.boat;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.port.Port;
import org.bukkit.Bukkit;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;

import java.util.*;

/**
 * Manages all player boats
 * NEW LOGIC: Players can create multiple boats (one per available dock)
 */
public class BoatManager {
    
    private final BoatRoutesPlugin plugin;
    private final BoatSpawner boatSpawner;
    
    // Track all player boats: playerUUID -> List of PlayerBoat
    private final Map<UUID, List<PlayerBoat>> playerBoats;
    
    // Track players waiting to name their boat
    private final Set<UUID> awaitingBoatName;
    
    // Track which port player is creating boat at
    private final Map<UUID, String> creationPorts;
    
    public BoatManager(BoatRoutesPlugin plugin) {
        this.plugin = plugin;
        this.boatSpawner = new BoatSpawner(plugin);
        this.playerBoats = new HashMap<>();
        this.awaitingBoatName = new HashSet<>();
        this.creationPorts = new HashMap<>();
    }
    
    // === BOAT CREATION ===
    
    /**
     * Create a new boat for a player
     */
    public boolean createBoat(UUID playerUUID, String boatName, String portName) {
        Port port = plugin.getPortManager().getPort(portName);
        if (port == null) {
            return false;
        }
        
        // Check if port has available docks
        boolean hasAvailable = false;
        for (var dock : port.getDocks()) {
            if (dock.isAvailable()) {
                hasAvailable = true;
                break;
            }
        }
        
        if (!hasAvailable) {
            Bukkit.getPlayer(playerUUID).sendMessage("§cNo available docks at this port!");
            return false;
        }
        
        // Create PlayerBoat data object
        PlayerBoat playerBoat = new PlayerBoat(playerUUID, boatName);
        
        // Spawn physical boat
        Boat boat = boatSpawner.spawnBoat(
            Bukkit.getPlayer(playerUUID),
            port,
            boatName
        );
        
        if (boat == null) {
            return false;
        }
        
        // Link boat entity to data
        playerBoat.setBoatEntityUUID(boat.getUniqueId());
        playerBoat.setCurrentPort(portName, findDockNumber(port, boat));
        
        // Store (allow multiple boats per player)
        playerBoats.computeIfAbsent(playerUUID, k -> new ArrayList<>()).add(playerBoat);
        
        return true;
    }
    
    /**
     * Despawn a specific boat by UUID
     */
    public boolean despawnBoat(UUID boatEntityUUID) {
        for (var entry : playerBoats.entrySet()) {
            List<PlayerBoat> boats = entry.getValue();
            
            for (int i = 0; i < boats.size(); i++) {
                PlayerBoat playerBoat = boats.get(i);
                
                if (playerBoat.getBoatEntityUUID() != null && 
                    playerBoat.getBoatEntityUUID().equals(boatEntityUUID)) {
                    
                    // Find and remove boat entity
                    Entity entity = Bukkit.getEntity(boatEntityUUID);
                    if (entity instanceof Boat boat) {
                        boatSpawner.despawnBoat(boat);
                    }
                    
                    // Remove from tracking
                    boats.remove(i);
                    
                    return true;
                }
            }
        }
        
        return false;
    }
    
    // === BOAT QUERIES ===
    
    /**
     * Check if player has any boats
     */
    public boolean hasBoat(UUID playerUUID) {
        List<PlayerBoat> boats = playerBoats.get(playerUUID);
        return boats != null && !boats.isEmpty();
    }
    
    /**
     * Get all player's boats
     */
    public List<PlayerBoat> getPlayerBoats(UUID playerUUID) {
        return playerBoats.getOrDefault(playerUUID, new ArrayList<>());
    }
    
    /**
     * Get player's boat data (first one for compatibility)
     */
    public PlayerBoat getPlayerBoat(UUID playerUUID) {
        List<PlayerBoat> boats = playerBoats.get(playerUUID);
        return (boats != null && !boats.isEmpty()) ? boats.get(0) : null;
    }
    
    /**
     * Get boat entity
     */
    public Boat getBoatEntity(UUID playerUUID) {
        PlayerBoat playerBoat = getPlayerBoat(playerUUID);
        if (playerBoat == null || playerBoat.getBoatEntityUUID() == null) {
            return null;
        }
        
        Entity entity = Bukkit.getEntity(playerBoat.getBoatEntityUUID());
        if (entity instanceof Boat boat) {
            return boat;
        }
        
        return null;
    }
    
    // === NAME INPUT TRACKING ===
    
    /**
     * Set player as awaiting boat name input
     */
    public void setAwaitingBoatName(UUID playerUUID, boolean awaiting) {
        if (awaiting) {
            awaitingBoatName.add(playerUUID);
        } else {
            awaitingBoatName.remove(playerUUID);
        }
    }
    
    /**
     * Check if player is awaiting boat name
     */
    public boolean isAwaitingBoatName(UUID playerUUID) {
        return awaitingBoatName.contains(playerUUID);
    }
    
    /**
     * Set port where player is creating boat
     */
    public void setCreationPort(UUID playerUUID, String portName) {
        if (portName == null) {
            creationPorts.remove(playerUUID);
        } else {
            creationPorts.put(playerUUID, portName);
        }
    }
    
    /**
     * Get port where player is creating boat
     */
    public String getCreationPort(UUID playerUUID) {
        return creationPorts.get(playerUUID);
    }
    
    // === STORAGE ===
    
    /**
     * Load all boats from file
     */
    public void loadAllBoats() {
        // TODO: Implement file loading in future
        plugin.getLogger().info("Boat loading not yet implemented");
    }
    
    /**
     * Save all boats to file
     */
    public void saveAllBoats() {
        // TODO: Implement file saving in future
        plugin.getLogger().info("Boat saving not yet implemented");
    }
    
    // === UTILITY ===
    
    /**
     * Find dock number where boat is spawned
     */
    private int findDockNumber(Port port, Boat boat) {
        var dock = boatSpawner.getDockAtLocation(boat.getLocation());
        return dock != null ? dock.getNumber() : 1;
    }
    
    /**
     * Get boat spawner
     */
    public BoatSpawner getBoatSpawner() {
        return boatSpawner;
    }
}
