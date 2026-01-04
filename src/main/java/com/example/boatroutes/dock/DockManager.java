package com.example.boatroutes.dock;
import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.port.Port;
import org.bukkit.entity.Player;

public class DockManager {
    private final BoatRoutesPlugin plugin;
    private final DockPlacer dockPlacer;
    private final DockPointCalculator dockPointCalculator;
    
    public DockManager(BoatRoutesPlugin plugin) {
        this.plugin = plugin;
        this.dockPlacer = new DockPlacer(plugin);
        this.dockPointCalculator = new DockPointCalculator();
    }
    
    public DockPlacer getDockPlacer() {
        return dockPlacer;
    }
    
    public DockPointCalculator getDockPointCalculator() {
        return dockPointCalculator;
    }
}