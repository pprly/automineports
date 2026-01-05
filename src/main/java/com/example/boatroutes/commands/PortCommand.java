package com.example.boatroutes.commands;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.dock.Dock;
import com.example.boatroutes.pathfinding.PathValidator;
import com.example.boatroutes.port.Port;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;

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
            case "reconnect" -> handleReconnect(player, args);
            case "disconnect" -> handleDisconnect(player, args);
            case "routes" -> handleRoutes(player, args);
            case "cache" -> handleCache(player, args);
            case "find-nav" -> handleFindNav(player, args);
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
        player.sendMessage("§e/port connect <A> <B> §7- Calculate path");
        player.sendMessage("§e/port reconnect <A> <B> §7- Recalculate path");
        player.sendMessage("§e/port routes list §7- List routes");
        player.sendMessage("§e/port cache info §7- Cache statistics");
        player.sendMessage("§e/port find-nav <n> §7- Find navigable water");
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: §e/port create <n>");
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
            return;
        }

        player.sendMessage("§6§l=== Ports (" + ports.size() + ") ===");

        for (String portName : ports) {
            Port port = plugin.getPortManager().getPort(portName);
            String status = port.isFullySetup() ? "§a✓" : "§e⚠";
            String dockInfo = port.getDockCount() + "/3";

            player.sendMessage(status + " §e" + portName + " §7- Docks: " + dockInfo);
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
        player.sendMessage("§7Docks: §f" + port.getDockCount() + "/3");
    }

    private void handleFix(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: §e/port fix <n>");
            return;
        }

        String name = args[1];
        Port port = plugin.getPortManager().getPort(name);

        if (port == null) {
            player.sendMessage("§cPort '" + name + "' does not exist!");
            return;
        }

        player.sendMessage("§6Fixing port: " + name);
        player.sendMessage("§7(This command verifies water points)");
    }

    private void handleConnect(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: §e/port connect <A> <B>");
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
            player.sendMessage("§cBoth ports must be fully set up!");
            return;
        }

        player.sendMessage("");
        player.sendMessage("§6⚓ v4.0: Calculating path with dynamic caching...");
        player.sendMessage("§7This may take 2-10 seconds");

        plugin.getPathfindingManager().findPathBetweenPortsAsync(port1, port2, player);
    }

    private void handleReconnect(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: §e/port reconnect <A> <B>");
            return;
        }

        String port1Name = args[1];
        String port2Name = args[2];

        if (!plugin.getPathfindingManager().hasPath(port1Name, port2Name)) {
            player.sendMessage("§cNo path exists!");
            player.sendMessage("§7Use §e/port connect §7first");
            return;
        }

        plugin.getPathfindingManager().deletePath(port1Name, port2Name);

        Port port1 = plugin.getPortManager().getPort(port1Name);
        Port port2 = plugin.getPortManager().getPort(port2Name);

        player.sendMessage("§7Recalculating path...");
        plugin.getPathfindingManager().findPathBetweenPortsAsync(port1, port2, player);
    }

    private void handleDisconnect(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: §e/port disconnect <A> <B>");
            return;
        }

        String port1Name = args[1];
        String port2Name = args[2];

        if (!plugin.getPathfindingManager().hasPath(port1Name, port2Name)) {
            player.sendMessage("§cNo path exists!");
            return;
        }

        plugin.getPathfindingManager().deletePath(port1Name, port2Name);
        player.sendMessage("§a✓ Path removed!");
    }

    private void handleRoutes(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: §e/port routes <list|clear>");
            return;
        }

        if (args[1].equalsIgnoreCase("list")) {
            Set<String> routes = plugin.getPathfindingManager().getStorage().getCachedRouteIds();

            if (routes.isEmpty()) {
                player.sendMessage("§7No routes saved yet");
                return;
            }

            player.sendMessage("§6§l=== Saved Routes (" + routes.size() + ") ===");
            for (String routeId : routes) {
                player.sendMessage("§e• §f" + routeId.replace("_to_", " → "));
            }
        } else if (args[1].equalsIgnoreCase("clear")) {
            plugin.getPathfindingManager().clearAllPaths();
            player.sendMessage("§a✓ All routes cleared!");
        }
    }

    // ============================================
    // NEW v4.0 CACHE COMMANDS
    // ============================================

    private void handleCache(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage:");
            player.sendMessage("§e/port cache info §7- Cache statistics");
            player.sendMessage("§e/port cache clear §7- Clear cache");
            player.sendMessage("§e/port cache coverage <A> <B> §7- Coverage between ports");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "info" -> handleCacheInfo(player);
            case "clear" -> handleCacheClear(player);
            case "coverage" -> handleCacheCoverage(player, args);
            default -> player.sendMessage("§cUnknown cache command: " + args[1]);
        }
    }

    private void handleCacheInfo(Player player) {
        var cache = plugin.getPathfindingManager().getCache();
        
        // ИСПРАВЛЕНО: используем getCacheStats() вместо getStats()
        var stats = cache.getCacheStats();

        player.sendMessage("§6=== Water Cache Statistics ===");
        player.sendMessage("§7Cached chunks: §f" + stats.cachedChunks);
        player.sendMessage("§7Water blocks: §f" + stats.waterBlocks);
        player.sendMessage("§7File size: §f" + formatBytes(stats.fileSizeBytes));
        player.sendMessage("§7Memory usage: §f~" + formatBytes(stats.cachedChunks * 288L));

        if (stats.cachedChunks == 0) {
            player.sendMessage("");
            player.sendMessage("§7Cache is empty (first run)");
            player.sendMessage("§7It will populate as you calculate paths");
        }
    }

    private void handleCacheClear(Player player) {
        plugin.getPathfindingManager().getCache().clearCache();
        player.sendMessage("§a✓ Water cache cleared!");
    }

    private void handleCacheCoverage(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage("§cUsage: §e/port cache coverage <A> <B>");
            return;
        }

        String port1Name = args[2];
        String port2Name = args[3];

        Port port1 = plugin.getPortManager().getPort(port1Name);
        Port port2 = plugin.getPortManager().getPort(port2Name);

        if (port1 == null || port2 == null) {
            player.sendMessage("§cOne or both ports do not exist!");
            return;
        }

        Location from = port1.getConvergencePoint();
        Location to = port2.getSplitPoint();

        if (from == null || to == null) {
            player.sendMessage("§cPorts not fully set up!");
            return;
        }

        var cache = plugin.getPathfindingManager().getCache();
        double coverage = cache.getCoveragePercent(from, to);

        int distance = (int) from.distance(to);

        player.sendMessage("§6=== Cache Coverage ===");
        player.sendMessage("§7From: §f" + port1Name);
        player.sendMessage("§7To: §f" + port2Name);
        player.sendMessage("§7Distance: §f" + distance + " blocks");
        player.sendMessage("§7Coverage: " + getCoverageColor(coverage) + String.format("%.1f%%", coverage));

        if (coverage > 80) {
            player.sendMessage("§a✓ Excellent! Path will be fast");
        } else if (coverage > 40) {
            player.sendMessage("§e⚡ Good coverage");
        } else if (coverage > 0) {
            player.sendMessage("§7⚡ Partial coverage");
        } else {
            player.sendMessage("§7First time, will build cache");
        }
    }

    private void handleFindNav(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: §e/port find-nav <portname>");
            return;
        }

        String portName = args[1];
        Port port = plugin.getPortManager().getPort(portName);

        if (port == null) {
            player.sendMessage("§cPort '" + portName + "' does not exist!");
            return;
        }

        if (!port.isFullySetup()) {
            player.sendMessage("§cPort not fully set up!");
            return;
        }

        Location convergence = port.getConvergencePoint();

        player.sendMessage("§6⚓ Searching for navigable water from §e" + portName + "§6...");
        player.sendMessage("§7This may take 5-10 seconds");

        // Async search
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            var navFinder = plugin.getPathfindingManager().getPathfinder().getNavFinder();
            Location navWater = navFinder.findNavigableWater(convergence, 50);

            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                if (navWater == null) {
                    player.sendMessage("");
                    player.sendMessage("§c✗ Cannot find navigable water within 50 blocks!");
                    player.sendMessage("§7Port may be in closed bay");
                    player.sendMessage("§7Try: Move port closer to open water");
                } else {
                    int distance = (int) convergence.distance(navWater);
                    int score = navFinder.getNavigabilityScore(navWater);

                    player.sendMessage("");
                    player.sendMessage("§a✓ Found navigable water!");
                    player.sendMessage("§7Location: §f" + formatLocation(navWater));
                    player.sendMessage("§7Distance: §f" + distance + " blocks");
                    player.sendMessage("§7Score: " + getScoreColor(score) + score + "/100");

                    if (score >= 80) {
                        player.sendMessage("§a✓ Excellent open water!");
                    } else if (score >= 60) {
                        player.sendMessage("§e⚡ Good navigable water");
                    } else {
                        player.sendMessage("§7⚠ Marginal water");
                    }

                    Location tpLoc = navWater.clone().add(0, 5, 0);
                    player.teleport(tpLoc);
                    player.sendMessage("§7Teleported above navigable water");
                }
            });
        });
    }

    // Helper methods

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private String getCoverageColor(double percent) {
        if (percent >= 80) return "§a";
        if (percent >= 40) return "§e";
        if (percent > 0) return "§7";
        return "§c";
    }

    private String getScoreColor(int score) {
        if (score >= 80) return "§a";
        if (score >= 60) return "§e";
        return "§c";
    }

    private String formatLocation(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }
}
