package com.example.boatroutes.commands;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.dock.Dock;
import com.example.boatroutes.port.Port;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * ПОЛНЫЙ экспорт системы портов
 * Команда: /port export-full
 * 
 * Экспортирует:
 * - Все порты с координатами
 * - Все доки
 * - Navigation points
 * - Все рассчитанные пути
 * - Waypoints каждого пути (raw path!)
 */
public class FullExportCommand implements CommandExecutor {

    private final BoatRoutesPlugin plugin;

    public FullExportCommand(BoatRoutesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cТолько для игроков!");
            return true;
        }

        Player player = (Player) sender;
        
        player.sendMessage("");
        player.sendMessage("§a╔══════════════════════════════╗");
        player.sendMessage("§a║  ПОЛНЫЙ ЭКСПОРТ СИСТЕМЫ      ║");
        player.sendMessage("§a╚══════════════════════════════╝");
        player.sendMessage("");
        player.sendMessage("§7Экспортирую:");
        player.sendMessage("§7  • Порты и доки");
        player.sendMessage("§7  • Navigation points");
        player.sendMessage("§7  • Рассчитанные пути");
        player.sendMessage("§7  • Все waypoints");
        player.sendMessage("");

        File exportFile = new File(plugin.getDataFolder(), "full_export.json");

        try {
            FullSystemExport export = createFullExport();
            
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            FileWriter writer = new FileWriter(exportFile);
            gson.toJson(export, writer);
            writer.close();

            player.sendMessage("§a✓ Экспорт завершён!");
            player.sendMessage("");
            player.sendMessage("§fСтатистика:");
            player.sendMessage("§7  Портов: §f" + export.ports.size());
            player.sendMessage("§7  Доков: §f" + countDocks(export));
            player.sendMessage("§7  Путей: §f" + export.routes.size());
            player.sendMessage("§7  Waypoints: §f" + countWaypoints(export));
            player.sendMessage("");
            player.sendMessage("§fФайл:");
            player.sendMessage("§e" + exportFile.getAbsolutePath());
            player.sendMessage("");
            player.sendMessage("§7Загрузи этот файл в визуализатор!");
            player.sendMessage("§7Кнопка: §e'Загрузить полную систему'");
            player.sendMessage("");

        } catch (Exception e) {
            player.sendMessage("§c✗ Ошибка экспорта!");
            player.sendMessage("§7" + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    private FullSystemExport createFullExport() {
        FullSystemExport export = new FullSystemExport();
        export.timestamp = System.currentTimeMillis();
        export.ports = new ArrayList<>();
        export.routes = new ArrayList<>();

        // ===== ЭКСПОРТ ПОРТОВ =====
        for (Port port : plugin.getPortManager().getAllPorts()) {
            ExportedPort ep = new ExportedPort();
            ep.name = port.getName();
            
            // NPC location
            if (port.getNPCLocation() != null) {
                ep.npcLocation = locationToArray(port.getNPCLocation());
            }
            
            // Navigation point (приоритет над convergence)
            if (port.getNavigationPoint() != null) {
                ep.navigationPoint = locationToArray(port.getNavigationPoint());
            } else if (port.getConvergencePoint() != null) {
                ep.navigationPoint = locationToArray(port.getConvergencePoint());
            }
            
            // Docks
            ep.docks = new ArrayList<>();
            for (Dock dock : port.getDocks()) {
                ExportedDock ed = new ExportedDock();
                ed.number = dock.getNumber();
                ed.location = locationToArray(dock.getLocation());
                ed.occupied = false; // TODO: track actual status
                ep.docks.add(ed);
            }
            
            export.ports.add(ep);
            plugin.getLogger().info("Exported port: " + port.getName() + 
                                    " (" + ep.docks.size() + " docks)");
        }

        // ===== ЭКСПОРТ ПУТЕЙ =====
        List<String> pathKeys = plugin.getPathfindingManager().getStorage().getAllPathKeys();
        
        for (String pathKey : pathKeys) {
            // Parse key: "portA_to_portB"
            String[] parts = pathKey.split("_to_");
            if (parts.length != 2) continue;
            
            String fromPort = parts[0];
            String toPort = parts[1];
            
            // Get RAW path (not optimized!)
            List<Location> rawPath = plugin.getPathfindingManager().getRawPath(fromPort, toPort);
            
            if (rawPath == null || rawPath.isEmpty()) {
                // Fallback to optimized path
                rawPath = plugin.getPathfindingManager().getStorage().loadPath(fromPort, toPort);
            }
            
            if (rawPath != null && !rawPath.isEmpty()) {
                ExportedRoute er = new ExportedRoute();
                er.fromPort = fromPort;
                er.toPort = toPort;
                er.waypointCount = rawPath.size();
                er.distance = calculateDistance(rawPath);
                er.estimatedTime = (int) (er.distance / (0.35 * 20)); // blocks / (speed * tps)
                
                // Convert waypoints to arrays
                er.waypoints = new ArrayList<>();
                for (Location loc : rawPath) {
                    er.waypoints.add(locationToArray(loc));
                }
                
                export.routes.add(er);
                plugin.getLogger().info("Exported route: " + fromPort + " → " + toPort + 
                                        " (" + rawPath.size() + " waypoints)");
            }
        }

        plugin.getLogger().info("Full export complete: " + 
                                export.ports.size() + " ports, " + 
                                export.routes.size() + " routes");

        return export;
    }

    private double[] locationToArray(Location loc) {
        return new double[]{loc.getX(), loc.getY(), loc.getZ()};
    }

    private double calculateDistance(List<Location> path) {
        if (path.size() < 2) return 0;
        
        double total = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            total += path.get(i).distance(path.get(i + 1));
        }
        return total;
    }

    private int countDocks(FullSystemExport export) {
        int count = 0;
        for (ExportedPort port : export.ports) {
            count += port.docks.size();
        }
        return count;
    }

    private int countWaypoints(FullSystemExport export) {
        int count = 0;
        for (ExportedRoute route : export.routes) {
            count += route.waypoints.size();
        }
        return count;
    }

    // ===== JSON CLASSES =====
    
    static class FullSystemExport {
        long timestamp;
        List<ExportedPort> ports;
        List<ExportedRoute> routes;
    }

    static class ExportedPort {
        String name;
        double[] npcLocation;      // [x, y, z]
        double[] navigationPoint;  // [x, y, z]
        List<ExportedDock> docks;
    }

    static class ExportedDock {
        int number;
        double[] location;  // [x, y, z]
        boolean occupied;
    }

    static class ExportedRoute {
        String fromPort;
        String toPort;
        int waypointCount;
        double distance;
        int estimatedTime;  // seconds
        List<double[]> waypoints;  // List of [x, y, z]
    }
}
