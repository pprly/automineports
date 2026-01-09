package com.example.boatroutes;

import com.example.boatroutes.boat.BoatManager;
import com.example.boatroutes.commands.PortCommand;
import com.example.boatroutes.commands.PortTabCompleter;
import com.example.boatroutes.dock.DockManager;
import com.example.boatroutes.gui.GUIListener;
import com.example.boatroutes.gui.GUIManager;
import com.example.boatroutes.listeners.NPCListener;
import com.example.boatroutes.listeners.PlayerListener;
import com.example.boatroutes.listeners.VehicleListener;
import com.example.boatroutes.navigation.NavigationManager;
import com.example.boatroutes.npc.NPCManager;
import com.example.boatroutes.pathfinding.PathfindingManager;
import com.example.boatroutes.port.PortManager;
import com.example.boatroutes.route.RouteManager;
import org.bukkit.plugin.java.JavaPlugin;
import com.example.boatroutes.commands.ExportCommand;
import com.example.boatroutes.listeners.ChunkLoadListener;

public class BoatRoutesPlugin extends JavaPlugin {
    
    private PortManager portManager;
    private DockManager dockManager;
    private BoatManager boatManager;
    private RouteManager routeManager;
    private PathfindingManager pathfindingManager;
    private NavigationManager navigationManager;
    private NPCManager npcManager;
    private GUIManager guiManager;
    
    @Override
    public void onEnable() {
        saveDefaultConfig();

        getLogger().info("=================================");
        getLogger().info("BoatRoutes v6.0 - Cost System Edition");
        getLogger().info("=================================");

        getLogger().info("Initializing managers...");

        portManager = new PortManager(this);
        dockManager = new DockManager(this);
        npcManager = new NPCManager(this);
        routeManager = new RouteManager(this);
        pathfindingManager = new PathfindingManager(this);
        boatManager = new BoatManager(this);
        navigationManager = new NavigationManager(this);
        guiManager = new GUIManager(this);

        getLogger().info("Registering commands...");
        getCommand("port").setExecutor(new PortCommand(this));
        getCommand("port").setTabCompleter(new PortTabCompleter(this));
        getCommand("export-cache").setExecutor(new ExportCommand(this));

        getLogger().info("Registering listeners...");
        getServer().getPluginManager().registerEvents(new VehicleListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new NPCListener(this), this);
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(
               new ChunkLoadListener(this, pathfindingManager.getCache()), this);

        getLogger().info("Loading data...");
        portManager.loadAllPorts();
        boatManager.loadAllBoats();
        routeManager.loadAllRoutes();
        pathfindingManager.loadAllPaths();

        getLogger().info("=================================");
        getLogger().info("BoatRoutes v6.0 enabled successfully!");
        getLogger().info("Features: Cost-based A*, Passive Caching");
        getLogger().info("=================================");
    }
    
    @Override
    public void onDisable() {
        getLogger().info("Saving data...");
        
        if (portManager != null) portManager.saveAllPorts();
        if (boatManager != null) boatManager.saveAllBoats();
        if (routeManager != null) routeManager.saveAllRoutes();
        
        if (navigationManager != null) navigationManager.stopAllAutopilots();
        
        getLogger().info("BoatRoutes disabled!");
    }
    
    public PortManager getPortManager() {
        return portManager;
    }
    
    public DockManager getDockManager() {
        return dockManager;
    }
    
    public BoatManager getBoatManager() {
        return boatManager;
    }
    
    public RouteManager getRouteManager() {
        return routeManager;
    }
    
    public PathfindingManager getPathfindingManager() {
        return pathfindingManager;
    }
    
    public NavigationManager getNavigationManager() {
        return navigationManager;
    }
    
    public NPCManager getNPCManager() {
        return npcManager;
    }
    
    public GUIManager getGUIManager() {
        return guiManager;
    }
}
