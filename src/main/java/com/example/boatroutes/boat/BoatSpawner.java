package com.example.boatroutes.boat;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.dock.Dock;
import com.example.boatroutes.port.Port;
import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/**
 * Handles spawning boats at docks
 */
public class BoatSpawner {

    private final BoatRoutesPlugin plugin;

    public BoatSpawner(BoatRoutesPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Spawn a boat for a player at a port
     */
    public Boat spawnBoat(Player player, Port port, String boatName) {
        // Find first available dock
        Dock availableDock = findAvailableDock(port);

        if (availableDock == null) {
            player.sendMessage("§cNo docks available at this port!");
            return null;
        }

        // Spawn boat 1 block ABOVE water so it floats properly
        Location spawnLoc = availableDock.getLocation().clone().add(0.5, 1.0, 0.5);
        Boat boat = (Boat) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.OAK_BOAT);

        // Configure boat - ПРОСТО И ПОНЯТНО
        boat.setCustomName("§b🚤 " + boatName);
        boat.setCustomNameVisible(true);
        boat.setInvulnerable(true);
        boat.setPersistent(true);

        // Mark dock as occupied
        availableDock.setCurrentBoat(boat.getUniqueId());

        // Save port state
        plugin.getPortManager().savePort(port);

        return boat;
    }

    /**
     * Despawn a boat
     */
    public void despawnBoat(Boat boat) {
        if (boat == null || !boat.isValid()) return;

        // Find and free the dock
        for (Port port : plugin.getPortManager().getAllPorts()) {
            for (Dock dock : port.getDocks()) {
                if (dock.getCurrentBoatUUID() != null &&
                        dock.getCurrentBoatUUID().equals(boat.getUniqueId())) {
                    dock.setCurrentBoat(null);
                    plugin.getPortManager().savePort(port);
                    break;
                }
            }
        }

        // Remove boat entity
        boat.remove();
    }

    /**
     * Find first available dock at port
     */
    private Dock findAvailableDock(Port port) {
        for (Dock dock : port.getDocks()) {
            if (dock.isAvailable()) {
                return dock;
            }
        }
        return null;
    }

    /**
     * Get dock at location
     */
    public Dock getDockAtLocation(Location location) {
        for (Port port : plugin.getPortManager().getAllPorts()) {
            for (Dock dock : port.getDocks()) {
                if (dock.getLocation().distance(location) < 5) {
                    return dock;
                }
            }
        }
        return null;
    }

    /**
     * Check if boat is part of port system
     */
    public boolean isPortBoat(Boat boat) {
        for (Port port : plugin.getPortManager().getAllPorts()) {
            for (Dock dock : port.getDocks()) {
                if (dock.getCurrentBoatUUID() != null &&
                        dock.getCurrentBoatUUID().equals(boat.getUniqueId())) {
                    return true;
                }
            }
        }
        return false;
    }
}