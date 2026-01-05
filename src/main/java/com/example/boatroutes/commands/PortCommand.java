package com.example.boatroutes.commands;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.dock.Dock;
import com.example.boatroutes.port.Port;
import com.example.boatroutes.pathfinding.PathValidator;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Set;

/**
 * Handles all /port commands
 */
public class PortCommand implements CommandExecutor {
    
    private final BoatRoutesPlugin plugin;
    
    public PortCommand(BoatRoutesPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }
        
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "create" -> handleCreate(player, args);
            case "delete", "remove" -> handleDelete(player, args);
            case "list" -> handleList(player);
            case "info" -> handleInfo(player, args);
            case "fix" -> handleFix(player, args);
            case "connect" -> handleConnect(player, args);
            case "visualize", "show" -> handleVisualize(player, args);
            case "show-points" -> handleShowPoints(player, args);
            case "show-path" -> handleShowPath(player, args);
            case "debug-path" -> handleDebugPath(player, args);
            default -> sendHelp(player);
        }
        
        return true;
    }
    
    private void sendHelp(Player player) {
        player.sendMessage("§6§l=== BoatRoutes Commands ===");
        player.sendMessage("§e/port create <n> §7- Create a new port");
        player.sendMessage("§e/port delete <n> §7- Delete a port");
        player.sendMessage("§e/port list §7- List all ports");
        player.sendMessage("§e/port info <n> §7- Port information");
        player.sendMessage("§e/port fix <n> §7- Fix port points (find water)");
        player.sendMessage("§e/port connect <p1> <p2> §7- Find path between ports");
        player.sendMessage("§e/port visualize <n> §7- Show port structure (30 sec)");
        player.sendMessage("§e/port show-points <p1> <p2> §7- Show convergence/split (30 sec)");
        player.sendMessage("§e/port show-path <p1> <p2> §7- Show path route (30 sec)");
        player.sendMessage("§e/port debug-path <p1> <p2> §7- Debug pathfinding");
    }
    
    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: §e/port create <n>");
            player.sendMessage("§7Name must be English letters/numbers only");
            return;
        }
        
        String name = args[1];
        plugin.getPortManager().createPort(name, player);
    }
    
    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: §e/port delete <n>");
            return;
        }
        
        String name = args[1];
        
        if (!plugin.getPortManager().portExists(name)) {
            player.sendMessage("§cPort '" + name + "' does not exist!");
            return;
        }
        
        boolean success = plugin.getPortManager().deletePort(name);
        
        if (success) {
            player.sendMessage("§a✓ Port '" + name + "' deleted!");
        } else {
            player.sendMessage("§cFailed to delete port!");
        }
    }
    
    private void handleList(Player player) {
        Set<String> ports = plugin.getPortManager().getPortNames();
        
        if (ports.isEmpty()) {
            player.sendMessage("§7No ports created yet");
            player.sendMessage("§7Use §e/port create <n> §7to create one");
            return;
        }
        
        player.sendMessage("§6§l=== Ports (" + ports.size() + ") ===");
        
        for (String portName : ports) {
            Port port = plugin.getPortManager().getPort(portName);
            String status = port.isFullySetup() ? "§a✓" : "§e⚠";
            String dockInfo = port.getDockCount() + "/" + plugin.getConfig().getInt("port.docks-per-port", 3);
            
            player.sendMessage(status + " §e" + portName + " §7- Docks: " + dockInfo);
        }
    }
    
    
    private void handleFix(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: §e/port fix <name>");
            return;
        }
        
        String name = args[1];
        Port port = plugin.getPortManager().getPort(name);
        
        if (port == null) {
            player.sendMessage("§cPort '" + name + "' does not exist!");
            return;
        }
        
        if (!port.isFullySetup()) {
            player.sendMessage("§cPort '" + name + "' is not fully set up yet!");
            player.sendMessage("§7Complete port setup first (place all docks)");
            return;
        }
        
        player.sendMessage("§6=== Fixing Port: " + name + " ===");
        
        boolean fixed = false;
        PathValidator validator = plugin.getPathfindingManager().getPathfinder().getValidator();
        
        // Check and fix convergence point
        Location convergence = port.getConvergencePoint();
        if (convergence != null) {
            player.sendMessage("§7Checking convergence point: " + formatLocation(convergence));
            
            if (!validator.isValidWaterLocation(convergence)) {
                player.sendMessage("§c✗ Convergence point is NOT in water!");
                player.sendMessage("§7Searching for nearest water...");
                
                Location nearestWater = validator.findNearestWater(convergence, 20);
                
                if (nearestWater != null) {
                    port.setConvergencePoint(nearestWater);
                    player.sendMessage("§a✓ Fixed convergence point: " + formatLocation(nearestWater));
                    fixed = true;
                } else {
                    player.sendMessage("§c✗ Could not find water within 20 blocks!");
                }
            } else {
                player.sendMessage("§a✓ Convergence point is valid");
            }
        }
        
        // Check and fix split point
        Location split = port.getSplitPoint();
        if (split != null) {
            player.sendMessage("§7Checking split point: " + formatLocation(split));
            
            if (!validator.isValidWaterLocation(split)) {
                player.sendMessage("§c✗ Split point is NOT in water!");
                player.sendMessage("§7Searching for nearest water...");
                
                Location nearestWater = validator.findNearestWater(split, 20);
                
                if (nearestWater != null) {
                    port.setSplitPoint(nearestWater);
                    player.sendMessage("§a✓ Fixed split point: " + formatLocation(nearestWater));
                    fixed = true;
                } else {
                    player.sendMessage("§c✗ Could not find water within 20 blocks!");
                }
            } else {
                player.sendMessage("§a✓ Split point is valid");
            }
        }
        
        // Check and fix dock exit points
        for (Dock dock : port.getDocks()) {
            Location exitPoint = dock.getExitPoint();
            if (exitPoint != null && !validator.isValidWaterLocation(exitPoint)) {
                player.sendMessage("§c✗ Dock #" + dock.getNumber() + " exit point is NOT in water!");
                
                Location nearestWater = validator.findNearestWater(exitPoint, 15);
                if (nearestWater != null) {
                    dock.setExitPoint(nearestWater);
                    dock.setEntryPoint(nearestWater); // Entry = Exit for now
                    player.sendMessage("§a✓ Fixed dock #" + dock.getNumber() + " exit point");
                    fixed = true;
                }
            }
        }
        
        if (fixed) {
            // Save port
            plugin.getPortManager().savePort(port);
            
            player.sendMessage("");
            player.sendMessage("§a✓ Port '" + name + "' fixed!");
            player.sendMessage("§7Try connecting now: §e/port connect " + name + " <other_port>");
        } else {
            player.sendMessage("");
            player.sendMessage("§a✓ Port '" + name + "' is already valid!");
            player.sendMessage("§7All points are in water.");
        }
    }
    
    private void handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: §e/port info <n>");
            return;
        }
        
        String name = args[1];
        Port port = plugin.getPortManager().getPort(name);
        
        if (port == null) {
            player.sendMessage("§cPort '" + name + "' does not exist!");
            return;
        }
        
        player.sendMessage("§6§l=== Port: " + name + " ===");
        player.sendMessage("§7Status: " + (port.isFullySetup() ? "§aReady" : "§eIncomplete"));
        player.sendMessage("§7Docks: §f" + port.getDockCount() + "/" + plugin.getConfig().getInt("port.docks-per-port", 3));
        
        if (port.getNPCLocation() != null) {
            Location npc = port.getNPCLocation();
            player.sendMessage("§7NPC: §f" + formatLocation(npc));
        } else {
            player.sendMessage("§7NPC: §cNot placed");
        }
        
        if (port.getConvergencePoint() != null) {
            player.sendMessage("§7Convergence Point: §f" + formatLocation(port.getConvergencePoint()));
        }
        
        if (!port.getDocks().isEmpty()) {
            player.sendMessage("§7Dock Locations:");
            for (Dock dock : port.getDocks()) {
                player.sendMessage("  §e#" + dock.getNumber() + " §f" + formatLocation(dock.getLocation()));
            }
        }
    }
    
    private void handleConnect(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: §e/port connect <port1> <port2>");
            return;
        }
        
        String port1Name = args[1];
        String port2Name = args[2];
        
        Port port1 = plugin.getPortManager().getPort(port1Name);
        Port port2 = plugin.getPortManager().getPort(port2Name);
        
        if (port1 == null) {
            player.sendMessage("§cPort '" + port1Name + "' does not exist!");
            return;
        }
        
        if (port2 == null) {
            player.sendMessage("§cPort '" + port2Name + "' does not exist!");
            return;
        }
        
        if (!port1.isFullySetup()) {
            player.sendMessage("§cPort '" + port1Name + "' is not fully set up!");
            return;
        }
        
        if (!port2.isFullySetup()) {
            player.sendMessage("§cPort '" + port2Name + "' is not fully set up!");
            return;
        }
        
        // Calculate distance
        double distance = port1.getConvergencePoint().distance(port2.getConvergencePoint());
        
        player.sendMessage("");
        player.sendMessage("§6⚓ Searching for path from §e" + port1Name + " §6to §e" + port2Name + "§6...");
        player.sendMessage("§7Distance: §f" + (int)distance + " blocks");
        
        // Check distance limit
        int maxDistance = plugin.getConfig().getInt("pathfinding.max-distance", 500);
        if (distance > maxDistance) {
            player.sendMessage("§c⚠ Distance exceeds limit (" + maxDistance + " blocks)!");
            player.sendMessage("§7Increase 'max-distance' in config.yml to allow longer paths");
            return;
        }
        
        // === REAL PATHFINDING WITH DEBUG ===
        player.sendMessage("§7Finding path with detailed logging...");
        
        // Enable debug mode for this search
        List<Location> path = plugin.getPathfindingManager().findPath(
            port1.getConvergencePoint(), 
            port2.getSplitPoint(),
            true  // Enable debug
        );
        
        if (path == null || path.isEmpty()) {
            player.sendMessage("");
            player.sendMessage("§c✗ No path found!");
            player.sendMessage("§7Possible reasons:");
            player.sendMessage("§7- Land/obstacles between ports");
            player.sendMessage("§7- Not enough water depth");
            player.sendMessage("§7- Ports too far apart");
            player.sendMessage("");
            player.sendMessage("§7Try: §e/port debug-path " + port1Name + " " + port2Name);
            return;
        }
        
        // Success!
        player.sendMessage("");
        player.sendMessage("§a✓ Path found!");
        player.sendMessage("§7Type: §fStraight line");
        player.sendMessage("§7Waypoints: §f" + path.size() + " points");
        player.sendMessage("§7Distance: §f" + (int)distance + " blocks");
        
        // Estimate travel time (0.35 blocks/tick * 20 ticks/sec = 7 blocks/sec)
        int estimatedTime = (int) (distance / 7);
        player.sendMessage("§7Estimated time: §f" + formatTime(estimatedTime));
        
        player.sendMessage("");
        player.sendMessage("§7(Route saved - autopilot coming in Stage 5)");
    }
    
    private void handleVisualize(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: §e/port visualize <n>");
            return;
        }
        
        String name = args[1];
        Port port = plugin.getPortManager().getPort(name);
        
        if (port == null) {
            player.sendMessage("§cPort '" + name + "' does not exist!");
            return;
        }
        
        player.sendMessage("§aVisualizing port: " + name + " §7(30 seconds)");
        player.sendMessage("§7Blue = Docks, Green = Convergence, Red = Split");
        
        // Teleport player to convergence point if it exists
        if (port.getConvergencePoint() != null) {
            Location tpLoc = port.getConvergencePoint().clone().add(0, 5, 0);
            player.teleport(tpLoc);
            player.sendMessage("§7Teleported to convergence point");
        }
        
        // Show particles for 30 seconds (15 times, every 2 seconds)
        new BukkitRunnable() {
            int count = 0;
            
            @Override
            public void run() {
                if (count >= 15) {
                    cancel();
                    player.sendMessage("§7Visualization ended");
                    return;
                }
                
                // Docks - HUGE BLUE EXPLOSIONS
                for (Dock dock : port.getDocks()) {
                    Location loc = dock.getLocation();
                    
                    // Multiple particle types for visibility
                    loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc.clone().add(0.5, 1, 0.5), 3);
                    loc.getWorld().spawnParticle(Particle.BUBBLE_POP, loc.clone().add(0.5, 1, 0.5), 100, 2, 2, 2, 0.1);
                    loc.getWorld().spawnParticle(Particle.SPLASH, loc.clone().add(0.5, 1, 0.5), 50, 1, 1, 1, 0.1);
                    
                    // Sound effect
                    loc.getWorld().playSound(loc, "entity.firework_rocket.blast", 0.5f, 1.0f);
                    
                    if (dock.getExitPoint() != null) {
                        loc.getWorld().spawnParticle(Particle.END_ROD, dock.getExitPoint().clone().add(0.5, 1, 0.5), 30, 1, 1, 1, 0.05);
                    }
                }
                
                // Convergence point - MASSIVE GREEN EXPLOSION
                if (port.getConvergencePoint() != null) {
                    Location loc = port.getConvergencePoint();
                    loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc.clone().add(0.5, 1, 0.5), 5);
                    loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0.5, 1, 0.5), 200, 3, 3, 3, 0.1);
                    loc.getWorld().playSound(loc, "entity.firework_rocket.large_blast", 1.0f, 1.0f);
                }
                
                // Split point - HUGE RED FIREBALL
                if (port.getSplitPoint() != null) {
                    Location loc = port.getSplitPoint();
                    loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc.clone().add(0.5, 1, 0.5), 5);
                    loc.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0.5, 1, 0.5), 100, 2, 2, 2, 0.1);
                    loc.getWorld().playSound(loc, "entity.firework_rocket.large_blast", 1.0f, 0.8f);
                }
                
                count++;
                
                if (count % 5 == 0) {
                    player.sendMessage("§7Particles showing... (" + (count * 2) + "/30 seconds)");
                }
            }
        }.runTaskTimer(plugin, 0L, 40L); // Every 2 seconds (40 ticks)
    }
    
    
    
    private void handleShowPoints(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: §e/port show-points <port1> <port2>");
            return;
        }
        
        String port1Name = args[1];
        String port2Name = args[2];
        
        Port port1 = plugin.getPortManager().getPort(port1Name);
        Port port2 = plugin.getPortManager().getPort(port2Name);
        
        if (port1 == null || port2 == null) {
            player.sendMessage("§cOne or both ports do not exist!");
            return;
        }
        
        if (!port1.isFullySetup() || !port2.isFullySetup()) {
            player.sendMessage("§cOne or both ports are not fully set up!");
            return;
        }
        
        player.sendMessage("§6=== Showing Points ===");
        player.sendMessage("§e" + port1Name + " §7(Blue/Cyan):");
        player.sendMessage("  Convergence: " + formatLocation(port1.getConvergencePoint()));
        player.sendMessage("  Split: " + formatLocation(port1.getSplitPoint()));
        player.sendMessage("§e" + port2Name + " §7(Red/Orange):");
        player.sendMessage("  Convergence: " + formatLocation(port2.getConvergencePoint()));
        player.sendMessage("  Split: " + formatLocation(port2.getSplitPoint()));
        
        // Check if points are valid water
        PathValidator validator = plugin.getPathfindingManager().getPathfinder().getValidator();
        
        boolean conv1Valid = validator.isValidWaterLocation(port1.getConvergencePoint());
        boolean split1Valid = validator.isValidWaterLocation(port1.getSplitPoint());
        boolean conv2Valid = validator.isValidWaterLocation(port2.getConvergencePoint());
        boolean split2Valid = validator.isValidWaterLocation(port2.getSplitPoint());
        
        player.sendMessage("");
        player.sendMessage("§7Water Validation:");
        player.sendMessage("  " + port1Name + " convergence: " + (conv1Valid ? "§a✓" : "§c✗"));
        player.sendMessage("  " + port1Name + " split: " + (split1Valid ? "§a✓" : "§c✗"));
        player.sendMessage("  " + port2Name + " convergence: " + (conv2Valid ? "§a✓" : "§c✗"));
        player.sendMessage("  " + port2Name + " split: " + (split2Valid ? "§a✓" : "§c✗"));
        
        if (!conv1Valid || !split1Valid) {
            player.sendMessage("§c⚠ " + port1Name + " has invalid points! Use: §e/port fix " + port1Name);
        }
        if (!conv2Valid || !split2Valid) {
            player.sendMessage("§c⚠ " + port2Name + " has invalid points! Use: §e/port fix " + port2Name);
        }
        
        player.sendMessage("");
        player.sendMessage("§7Teleporting to " + port1Name + " convergence point...");
        
        // Teleport player above first convergence point
        Location tpLoc = port1.getConvergencePoint().clone().add(0, 5, 0);
        player.teleport(tpLoc);
        
        player.sendMessage("§7Showing MASSIVE particles for 30 seconds...");
        player.sendMessage("§7Listen for explosion sounds!");
        
        // Show particles for 30 seconds
        new BukkitRunnable() {
            int count = 0;
            
            @Override
            public void run() {
                if (count >= 15) {
                    cancel();
                    player.sendMessage("§7Points visualization ended");
                    return;
                }
                
                // Port 1 convergence - HUGE BLUE EXPLOSION
                Location conv1 = port1.getConvergencePoint();
                conv1.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, conv1.clone().add(0.5, 1, 0.5), 5);
                conv1.getWorld().spawnParticle(conv1Valid ? Particle.BUBBLE_POP : Particle.LAVA, 
                    conv1.clone().add(0.5, 1, 0.5), 200, 3, 3, 3, 0.1);
                conv1.getWorld().playSound(conv1, "entity.firework_rocket.large_blast", 1.0f, 1.0f);
                
                // Port 1 split - CYAN END RODS
                Location split1 = port1.getSplitPoint();
                split1.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, split1.clone().add(0.5, 1, 0.5), 3);
                split1.getWorld().spawnParticle(split1Valid ? Particle.END_ROD : Particle.LAVA,
                    split1.clone().add(0.5, 1, 0.5), 100, 2, 2, 2, 0.1);
                split1.getWorld().playSound(split1, "entity.firework_rocket.blast", 0.8f, 1.2f);
                
                // Port 2 convergence - HUGE RED FIREBALL
                Location conv2 = port2.getConvergencePoint();
                conv2.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, conv2.clone().add(0.5, 1, 0.5), 5);
                conv2.getWorld().spawnParticle(conv2Valid ? Particle.FLAME : Particle.LAVA,
                    conv2.clone().add(0.5, 1, 0.5), 200, 3, 3, 3, 0.1);
                conv2.getWorld().playSound(conv2, "entity.firework_rocket.large_blast", 1.0f, 0.8f);
                
                // Port 2 split - ORANGE LAVA
                Location split2 = port2.getSplitPoint();
                split2.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, split2.clone().add(0.5, 1, 0.5), 3);
                split2.getWorld().spawnParticle(split2Valid ? Particle.FALLING_LAVA : Particle.LAVA,
                    split2.clone().add(0.5, 1, 0.5), 100, 2, 2, 2, 0.1);
                split2.getWorld().playSound(split2, "entity.firework_rocket.blast", 0.8f, 0.8f);
                
                count++;
                
                if (count % 5 == 0) {
                    player.sendMessage("§7Explosions showing... (" + (count * 2) + "/30 seconds)");
                }
            }
        }.runTaskTimer(plugin, 0L, 40L);
    }
    
    private void handleShowPath(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: §e/port show-path <port1> <port2>");
            return;
        }
        
        String port1Name = args[1];
        String port2Name = args[2];
        
        Port port1 = plugin.getPortManager().getPort(port1Name);
        Port port2 = plugin.getPortManager().getPort(port2Name);
        
        if (port1 == null || port2 == null) {
            player.sendMessage("§cOne or both ports do not exist!");
            return;
        }
        
        if (!port1.isFullySetup() || !port2.isFullySetup()) {
            player.sendMessage("§cOne or both ports are not fully set up!");
            return;
        }
        
        player.sendMessage("§6⚓ Finding and visualizing path from §e" + port1Name + " §6to §e" + port2Name + "§6...");
        
        // Find path
        List<Location> path = plugin.getPathfindingManager().findPathBetweenPorts(port1, port2);
        
        if (path == null || path.isEmpty()) {
            player.sendMessage("§c✗ No path found!");
            return;
        }
        
        player.sendMessage("§a✓ Path found with " + path.size() + " waypoints!");
        player.sendMessage("§7Teleporting to start of path...");
        
        // Teleport player above first waypoint
        Location tpLoc = path.get(0).clone().add(0, 10, 0);
        player.teleport(tpLoc);
        
        player.sendMessage("§7Showing MASSIVE path particles for 30 seconds...");
        player.sendMessage("§7Listen for firework sounds!");
        player.sendMessage("§7Green=Start, Yellow=Path, Red=End");
        
        // Show path waypoints for 30 seconds (15 times, every 2 seconds)
        new BukkitRunnable() {
            int count = 0;
            
            @Override
            public void run() {
                if (count >= 15) {
                    cancel();
                    player.sendMessage("§7Path visualization ended");
                    return;
                }
                
                // Show each waypoint with MASSIVE particles
                for (int i = 0; i < path.size(); i++) {
                    Location waypoint = path.get(i);
                    
                    if (i == 0) {
                        // Start - HUGE GREEN EXPLOSION
                        waypoint.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, waypoint.clone().add(0.5, 1, 0.5), 5);
                        waypoint.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, waypoint.clone().add(0.5, 1, 0.5), 200, 3, 3, 3, 0.1);
                        waypoint.getWorld().playSound(waypoint, "entity.firework_rocket.large_blast", 1.0f, 1.0f);
                    } else if (i == path.size() - 1) {
                        // End - HUGE RED EXPLOSION
                        waypoint.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, waypoint.clone().add(0.5, 1, 0.5), 5);
                        waypoint.getWorld().spawnParticle(Particle.FLAME, waypoint.clone().add(0.5, 1, 0.5), 200, 3, 3, 3, 0.1);
                        waypoint.getWorld().playSound(waypoint, "entity.firework_rocket.large_blast", 1.0f, 0.8f);
                    } else {
                        // Middle waypoints - BRIGHT YELLOW FIREWORKS
                        waypoint.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, waypoint.clone().add(0.5, 1, 0.5), 2);
                        waypoint.getWorld().spawnParticle(Particle.END_ROD, waypoint.clone().add(0.5, 1, 0.5), 50, 2, 2, 2, 0.1);
                        waypoint.getWorld().spawnParticle(Particle.FIREWORK, waypoint.clone().add(0.5, 1, 0.5), 30, 1, 1, 1, 0.1);
                        
                        // Sound every 3rd waypoint
                        if (i % 3 == 0) {
                            waypoint.getWorld().playSound(waypoint, "entity.firework_rocket.blast", 0.5f, 1.0f);
                        }
                    }
                }
                
                count++;
                
                if (count % 5 == 0) {
                    player.sendMessage("§7Path explosions showing... (" + (count * 2) + "/30 seconds)");
                }
            }
        }.runTaskTimer(plugin, 0L, 40L); // Every 2 seconds (40 ticks)
    }
    
    private void handleDebugPath(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: §e/port debug-path <port1> <port2>");
            return;
        }
        
        String port1Name = args[1];
        String port2Name = args[2];
        
        Port port1 = plugin.getPortManager().getPort(port1Name);
        Port port2 = plugin.getPortManager().getPort(port2Name);
        
        if (port1 == null || port2 == null) {
            player.sendMessage("§cOne or both ports do not exist!");
            return;
        }
        
        if (!port1.isFullySetup() || !port2.isFullySetup()) {
            player.sendMessage("§cOne or both ports are not fully set up!");
            return;
        }
        
        player.sendMessage("§6=== Debug Pathfinding ===");
        player.sendMessage("§7From: §e" + port1Name + " §7to §e" + port2Name);
        
        Location start = port1.getConvergencePoint();
        Location end = port2.getSplitPoint();
        
        player.sendMessage("§7Start: §f" + formatLocation(start));
        player.sendMessage("§7End: §f" + formatLocation(end));
        player.sendMessage("");
        
        // Check start point
        player.sendMessage("§7Checking start point...");
        boolean startValid = plugin.getPathfindingManager().getPathfinder().getValidator().isValidWaterLocation(start);
        player.sendMessage(startValid ? "§a✓ Start is valid water" : "§c✗ Start is NOT valid water!");
        
        // Check end point
        player.sendMessage("§7Checking end point...");
        boolean endValid = plugin.getPathfindingManager().getPathfinder().getValidator().isValidWaterLocation(end);
        player.sendMessage(endValid ? "§a✓ End is valid water" : "§c✗ End is NOT valid water!");
        
        player.sendMessage("");
        player.sendMessage("§7Visualizing points (30 seconds)...");
        player.sendMessage("§7Green = Valid water, Red = Invalid");
        
        // Visualize start and end points for 30 seconds
        new BukkitRunnable() {
            int count = 0;
            
            @Override
            public void run() {
                if (count >= 15) {
                    cancel();
                    player.sendMessage("§7Debug visualization ended");
                    return;
                }
                
                // Start point
                Particle startParticle = startValid ? Particle.HAPPY_VILLAGER : Particle.LAVA;
                spawnParticles(start, startParticle, 50);
                
                // End point
                Particle endParticle = endValid ? Particle.HAPPY_VILLAGER : Particle.LAVA;
                spawnParticles(end, endParticle, 50);
                
                // Check midpoint
                Location mid = start.clone().add(
                    (end.getX() - start.getX()) / 2,
                    (end.getY() - start.getY()) / 2,
                    (end.getZ() - start.getZ()) / 2
                );
                
                boolean midValid = plugin.getPathfindingManager().getPathfinder().getValidator().isValidWaterLocation(mid);
                Particle midParticle = midValid ? Particle.HAPPY_VILLAGER : Particle.LAVA;
                spawnParticles(mid, midParticle, 30);
                
                count++;
            }
        }.runTaskTimer(plugin, 0L, 40L);
        
        // Try to find path with debug logging
        player.sendMessage("");
        player.sendMessage("§7Attempting pathfinding (check server logs)...");
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Location> path = plugin.getPathfindingManager().findPath(start, end, true);
            
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (path != null && !path.isEmpty()) {
                    player.sendMessage("§a✓ Path found with " + path.size() + " waypoints!");
                } else {
                    player.sendMessage("§c✗ No path found - check server logs for details");
                }
            });
        });
    }
    
    private void spawnParticles(Location loc, Particle particle, int count) {
        loc.getWorld().spawnParticle(particle, loc.clone().add(0.5, 1, 0.5), count, 0.5, 0.5, 0.5, 0.05);
    }
    
    private String formatLocation(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }
    
    private String formatTime(int seconds) {
        if (seconds < 60) {
            return seconds + " seconds";
        }
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return minutes + " min " + secs + " sec";
    }
}
