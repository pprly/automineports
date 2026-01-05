package com.example.boatroutes.listeners;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.navigation.NavigationBook;
import com.example.boatroutes.port.Port;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.*;

public class VehicleListener implements Listener {
    
    private final BoatRoutesPlugin plugin;
    private final NavigationBook navigationBook;
    
    public VehicleListener(BoatRoutesPlugin plugin) {
        this.plugin = plugin;
        this.navigationBook = new NavigationBook(plugin);
    }
    
    @EventHandler
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getVehicle() instanceof Boat)) return;
        if (!(event.getEntered() instanceof Player player)) return;
        
        // Check if this is a port boat
        Boat boat = (Boat) event.getVehicle();
        if (!plugin.getBoatManager().getBoatSpawner().isPortBoat(boat)) return;
        
        // Find which port the boat is at
        Port currentPort = findPortForBoat(boat);
        if (currentPort == null) return;
        
        // Give navigation book
        navigationBook.giveBook(player);
        
        // Store current port for player
        plugin.getBoatManager().setCreationPort(player.getUniqueId(), currentPort.getName());
    }
    
    @EventHandler
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getVehicle() instanceof Boat)) return;
        if (!(event.getExited() instanceof Player player)) return;
        
        // Remove navigation book
        navigationBook.removeBook(player);
    }
    
    @EventHandler
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (!(event.getVehicle() instanceof Boat boat)) return;
        
        if (plugin.getBoatManager().getBoatSpawner().isPortBoat(boat)) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (!(event.getVehicle() instanceof Boat boat)) return;
        
        if (plugin.getBoatManager().getBoatSpawner().isPortBoat(boat)) {
            event.setCancelled(true);
        }
    }
    
    private Port findPortForBoat(Boat boat) {
        for (Port port : plugin.getPortManager().getAllPorts()) {
            if (port.getNPCLocation() != null && 
                port.getNPCLocation().getWorld() == boat.getWorld() &&
                port.getNPCLocation().distance(boat.getLocation()) < 100) {
                return port;
            }
        }
        return null;
    }
}
