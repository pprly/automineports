package com.example.boatroutes.listeners;

import com.example.boatroutes.BoatRoutesPlugin;
import org.bukkit.entity.Boat;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;

/**
 * Handles vehicle events - protects boats from damage
 */
public class VehicleListener implements Listener {
    
    private final BoatRoutesPlugin plugin;
    
    public VehicleListener(BoatRoutesPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Protect port boats from damage
     */
    @EventHandler
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (!(event.getVehicle() instanceof Boat boat)) return;
        
        // Check if this is a port boat
        if (plugin.getBoatManager().getBoatSpawner().isPortBoat(boat)) {
            event.setCancelled(true); // Cancel damage
        }
    }
    
    /**
     * Protect port boats from destruction
     */
    @EventHandler
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (!(event.getVehicle() instanceof Boat boat)) return;
        
        // Check if this is a port boat
        if (plugin.getBoatManager().getBoatSpawner().isPortBoat(boat)) {
            event.setCancelled(true); // Cancel destruction
        }
    }
}
