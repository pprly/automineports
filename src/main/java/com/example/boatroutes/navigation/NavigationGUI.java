package com.example.boatroutes.navigation;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.port.Port;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * NavigationGUI v2.0 - С BIDIRECTIONAL PATH SUPPORT!
 * 
 * НОВОЕ:
 * - Проверяет путь в ОБЕ стороны (A→B и B→A)
 * - Один путь работает для обеих сторон
 * - Автореверс в autopilot
 * 
 * @author BoatRoutes Team
 * @version 2.0-BIDIRECTIONAL
 */
public class NavigationGUI {
    
    private final BoatRoutesPlugin plugin;
    private final Player player;
    private final String currentPort;
    
    public NavigationGUI(BoatRoutesPlugin plugin, Player player, String currentPort) {
        this.plugin = plugin;
        this.player = player;
        this.currentPort = currentPort;
    }
    
    public void open() {
        Inventory inv = Bukkit.createInventory(null, 27, "§6§l⚓ Select Destination");
        
        Collection<Port> allPorts = plugin.getPortManager().getAllPorts();
        int slot = 10;
        
        for (Port port : allPorts) {
            // Skip current port
            if (port.getName().equalsIgnoreCase(currentPort)) continue;
            
            // НОВОЕ: Проверяем путь В ОБЕ СТОРОНЫ!
            boolean hasPathForward = plugin.getPathfindingManager().hasPath(currentPort, port.getName());
            boolean hasPathReverse = plugin.getPathfindingManager().hasPath(port.getName(), currentPort);
            
            boolean hasPath = hasPathForward || hasPathReverse;
            
            if (hasPath) {
                ItemStack item = new ItemStack(Material.MAP);
                ItemMeta meta = item.getItemMeta();
                
                meta.setDisplayName("§e⚓ " + port.getName());
                
                List<String> lore = new ArrayList<>();
                lore.add("§7Click to navigate");
                lore.add("");
                
                // Показываем в какую сторону путь существует
                if (hasPathForward && hasPathReverse) {
                    lore.add("§aPath available (both ways) ✓✓");
                } else if (hasPathForward) {
                    lore.add("§aPath available (→) ✓");
                } else {
                    lore.add("§aPath available (←) ✓");
                }
                
                meta.setLore(lore);
                item.setItemMeta(meta);
                
                inv.setItem(slot, item);
                slot++;
                
                if (slot == 17) slot = 19; // Next row
                if (slot >= 26) break; // Inventory full
            }
        }
        
        // Если нет доступных портов
        if (slot == 10) {
            ItemStack noPortsItem = new ItemStack(Material.BARRIER);
            ItemMeta meta = noPortsItem.getItemMeta();
            meta.setDisplayName("§c✗ No destinations available");
            
            List<String> lore = new ArrayList<>();
            lore.add("§7No connected ports found");
            lore.add("§7Use §e/port connect §7to create routes");
            
            meta.setLore(lore);
            noPortsItem.setItemMeta(meta);
            
            inv.setItem(13, noPortsItem);
        }
        
        player.openInventory(inv);
    }
}
