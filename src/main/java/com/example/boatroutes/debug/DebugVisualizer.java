package com.example.boatroutes.debug;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.dock.Dock;
import com.example.boatroutes.port.Port;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/**
 * Debug visualization system
 * Shows ports, docks, paths, and pathfinding process
 */
public class DebugVisualizer {
    
    private final BoatRoutesPlugin plugin;
    private static final int DURATION_TICKS = 600; // 30 seconds
    
    public DebugVisualizer(BoatRoutesPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Visualize a single port with all its components
     */
    public void visualizePort(Port port, Player player) {
        player.sendMessage("§6=== Visualizing Port: " + port.getName() + " ===");
        player.sendMessage("§7Duration: 30 seconds");
        
        World world = player.getWorld();
        
        new BukkitRunnable() {
            int ticks = 0;
            
            @Override
            public void run() {
                if (ticks >= DURATION_TICKS) {
                    player.sendMessage("§7Visualization ended");
                    cancel();
                    return;
                }
                
                // NPC location (RED)
                if (port.getNPCLocation() != null) {
                    Location npc = port.getNPCLocation();
                    world.spawnParticle(Particle.FLAME, npc.clone().add(0, 2, 0), 5, 0.3, 0.3, 0.3, 0);
                }
                
                // Convergence point (GREEN)
                if (port.getConvergencePoint() != null) {
                    Location conv = port.getConvergencePoint();
                    world.spawnParticle(Particle.HAPPY_VILLAGER, conv, 10, 0.5, 0.5, 0.5, 0);
                }
                
                // Split point (BLUE) - FIXED: SPLASH instead of WATER_SPLASH
                if (port.getSplitPoint() != null) {
                    Location split = port.getSplitPoint();
                    world.spawnParticle(Particle.SPLASH, split, 10, 0.5, 0.5, 0.5, 0);
                }
                
                // Docks (YELLOW)
                for (Dock dock : port.getDocks()) {
                    Location dockLoc = dock.getLocation();
                    world.spawnParticle(Particle.END_ROD, dockLoc.clone().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0);
                    
                    // Exit point (ORANGE)
                    if (dock.getExitPoint() != null) {
                        world.spawnParticle(Particle.LAVA, dock.getExitPoint(), 3, 0.2, 0.2, 0.2, 0);
                    }
                    
                    // Entry point (LIGHT BLUE)
                    if (dock.getEntryPoint() != null) {
                        world.spawnParticle(Particle.FALLING_WATER, dock.getEntryPoint(), 3, 0.2, 0.2, 0.2, 0);
                    }
                }
                
                // Sound effect every 2 seconds
                if (ticks % 40 == 0) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 2.0f);
                }
                
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        
        // Teleport player above port
        if (port.getNPCLocation() != null) {
            Location tpLoc = port.getNPCLocation().clone().add(0, 20, 0);
            player.teleport(tpLoc);
            player.sendMessage("§7Teleported above port for better view");
        }
        
        // Print legend
        player.sendMessage("");
        player.sendMessage("§6Legend:");
        player.sendMessage("§c🔥 RED = NPC Location");
        player.sendMessage("§a✓ GREEN = Convergence Point");
        player.sendMessage("§b💧 BLUE = Split Point");
        player.sendMessage("§e⭐ YELLOW = Docks");
        player.sendMessage("§6🌋 ORANGE = Exit Points");
        player.sendMessage("§b💦 LIGHT BLUE = Entry Points");
    }
    
    /**
     * Visualize convergence and split points between two ports
     */
    public void visualizePoints(Port port1, Port port2, Player player) {
        player.sendMessage("§6=== Visualizing: " + port1.getName() + " → " + port2.getName() + " ===");
        
        World world = player.getWorld();
        Location conv = port1.getConvergencePoint();
        Location split = port2.getSplitPoint();
        
        if (conv == null || split == null) {
            player.sendMessage("§cPorts not fully set up!");
            return;
        }
        
        int distance = (int) conv.distance(split);
        player.sendMessage("§7Distance: §f" + distance + " blocks");
        player.sendMessage("§7Duration: 30 seconds");
        
        new BukkitRunnable() {
            int ticks = 0;
            
            @Override
            public void run() {
                if (ticks >= DURATION_TICKS) {
                    player.sendMessage("§7Visualization ended");
                    cancel();
                    return;
                }
                
                // Convergence (GREEN)
                world.spawnParticle(Particle.HAPPY_VILLAGER, conv, 20, 1, 1, 1, 0);
                
                // Split (BLUE) - FIXED: SPLASH instead of WATER_SPLASH
                world.spawnParticle(Particle.SPLASH, split, 20, 1, 1, 1, 0);
                
                // Line between them (WHITE)
                drawLine(conv, split, world, Particle.END_ROD);
                
                if (ticks % 20 == 0) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 0.5f, 1.5f);
                }
                
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
        
        // Teleport between points
        Location midpoint = new Location(world,
            (conv.getX() + split.getX()) / 2,
            Math.max(conv.getY(), split.getY()) + 30,
            (conv.getZ() + split.getZ()) / 2
        );
        player.teleport(midpoint);
        player.sendMessage("§7Teleported to midpoint above");
    }
    
    /**
     * Visualize a calculated path
     */
    public void visualizePath(List<Location> path, Player player, String routeName) {
        if (path == null || path.isEmpty()) {
            player.sendMessage("§cNo path to visualize!");
            return;
        }
        
        player.sendMessage("§6=== Visualizing Path: " + routeName + " ===");
        player.sendMessage("§7Waypoints: §f" + path.size());
        player.sendMessage("§7Duration: 30 seconds");
        
        World world = player.getWorld();
        
        new BukkitRunnable() {
            int ticks = 0;
            int waypointIndex = 0;
            
            @Override
            public void run() {
                if (ticks >= DURATION_TICKS) {
                    player.sendMessage("§7Visualization ended");
                    cancel();
                    return;
                }
                
                // Draw all waypoints (PURPLE)
                for (int i = 0; i < path.size(); i++) {
                    Location wp = path.get(i);
                    
                    // Highlight current waypoint (GOLD)
                    if (i == waypointIndex) {
                        world.spawnParticle(Particle.FLASH, wp.clone().add(0, 2, 0), 1);
                        world.spawnParticle(Particle.END_ROD, wp, 30, 1, 1, 1, 0.1);
                    } else {
                        // Regular waypoint (PURPLE)
                        world.spawnParticle(Particle.WITCH, wp, 2, 0.2, 0.2, 0.2, 0);
                    }
                    
                    // Draw line to next waypoint
                    if (i < path.size() - 1) {
                        drawLine(wp, path.get(i + 1), world, Particle.DOLPHIN);
                    }
                }
                
                // Start and end markers
                world.spawnParticle(Particle.HAPPY_VILLAGER, path.get(0), 10, 0.5, 0.5, 0.5, 0);
                world.spawnParticle(Particle.HEART, path.get(path.size() - 1), 10, 0.5, 0.5, 0.5, 0);
                
                // Move highlight along path
                if (ticks % 10 == 0) {
                    waypointIndex = (waypointIndex + 1) % path.size();
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.3f, 2.0f);
                }
                
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        
        // Teleport to start of path
        Location startLoc = path.get(0).clone().add(0, 20, 0);
        player.teleport(startLoc);
        player.sendMessage("§7Teleported to path start");
    }
    
    /**
     * Show pathfinding process in real-time
     */
    public void visualizePathfinding(Location start, Location end, Player player) {
        player.sendMessage("§6=== Pathfinding Visualization ===");
        player.sendMessage("§7From: §f" + formatLoc(start));
        player.sendMessage("§7To: §f" + formatLoc(end));
        player.sendMessage("§7Watch the search expand!");
        
        World world = player.getWorld();
        
        new BukkitRunnable() {
            int ticks = 0;
            
            @Override
            public void run() {
                if (ticks >= 200) {
                    cancel();
                    return;
                }
                
                // Start (GREEN)
                world.spawnParticle(Particle.HAPPY_VILLAGER, start, 10, 1, 1, 1, 0);
                
                // End (RED)
                world.spawnParticle(Particle.FLAME, end, 10, 1, 1, 1, 0);
                
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }
    
    /**
     * Draw a line between two points with particles
     */
    private void drawLine(Location start, Location end, World world, Particle particle) {
        double distance = start.distance(end);
        int points = (int) (distance * 2); // 2 particles per block
        
        for (int i = 0; i <= points; i++) {
            double t = (double) i / points;
            
            double x = start.getX() + (end.getX() - start.getX()) * t;
            double y = start.getY() + (end.getY() - start.getY()) * t;
            double z = start.getZ() + (end.getZ() - start.getZ()) * t;
            
            Location point = new Location(world, x, y, z);
            world.spawnParticle(particle, point, 1, 0, 0, 0, 0);
        }
    }
    
    private String formatLoc(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }
}
