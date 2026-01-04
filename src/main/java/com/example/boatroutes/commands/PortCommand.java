package com.example.boatroutes.commands;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.dock.Dock;
import com.example.boatroutes.port.Port;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
            case "connect" -> handleConnect(player, args);
            case "visualize", "show" -> handleVisualize(player, args);
            default -> sendHelp(player);
        }
        
        return true;
    }
    
    private void sendHelp(Player player) {
        player.sendMessage("§6§l=== BoatRoutes Commands ===");
        player.sendMessage("§e/port create <name> §7- Create a new port");
        player.sendMessage("§e/port delete <name> §7- Delete a port");
        player.sendMessage("§e/port list §7- List all ports");
        player.sendMessage("§e/port info <name> §7- Port information");
        player.sendMessage("§e/port connect <port1> <port2> §7- Find path between ports");
        player.sendMessage("§e/port visualize <name> §7- Show port structure");
    }
    
    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: §e/port create <name>");
            player.sendMessage("§7Name must be English letters/numbers only");
            return;
        }
        
        String name = args[1];
        plugin.getPortManager().createPort(name, player);
    }
    
    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: §e/port delete <name>");
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
            player.sendMessage("§7Use §e/port create <name> §7to create one");
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
    
    private void handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: §e/port info <name>");
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
        
        player.sendMessage("§eSearching for path from §f" + port1Name + " §eto §f" + port2Name + "§e...");
        player.sendMessage("§7(Pathfinding will be implemented in next stage)");
        
        double distance = port1.getConvergencePoint().distance(port2.getConvergencePoint());
        player.sendMessage("§7Distance: §f" + (int)distance + " blocks");
        
        if (distance > plugin.getConfig().getInt("pathfinding.max-distance", 500)) {
            player.sendMessage("§c⚠ Distance exceeds Stage 1 limit (500 blocks)!");
        }
    }
    
    private void handleVisualize(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: §e/port visualize <name>");
            return;
        }
        
        String name = args[1];
        Port port = plugin.getPortManager().getPort(name);
        
        if (port == null) {
            player.sendMessage("§cPort '" + name + "' does not exist!");
            return;
        }
        
        player.sendMessage("§aVisualizing port: " + name);
        
        // Show docks
        for (Dock dock : port.getDocks()) {
            Location loc = dock.getLocation();
            spawnParticles(loc, Particle.SPLASH, 50);
            
            if (dock.getExitPoint() != null) {
                spawnParticles(dock.getExitPoint(), Particle.BUBBLE_POP, 30);
            }
        }
        
        // Show convergence point
        if (port.getConvergencePoint() != null) {
            spawnParticles(port.getConvergencePoint(), Particle.HAPPY_VILLAGER, 100);
        }
        
        // Show split point
        if (port.getSplitPoint() != null) {
            spawnParticles(port.getSplitPoint(), Particle.FLAME, 50);
        }
        
        player.sendMessage("§7Blue particles = Docks");
        player.sendMessage("§7Green particles = Convergence point");
        player.sendMessage("§7Red particles = Split point");
    }
    
    private void spawnParticles(Location loc, Particle particle, int count) {
        loc.getWorld().spawnParticle(particle, loc.clone().add(0.5, 1, 0.5), count, 0.5, 0.5, 0.5, 0.05);
    }
    
    private String formatLocation(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }
}
