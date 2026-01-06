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
            
            // Check if path exists
            boolean hasPath = plugin.getPathfindingManager().hasPath(currentPort, port.getName());
            
            if (hasPath) {
                ItemStack item = new ItemStack(Material.MAP);
                ItemMeta meta = item.getItemMeta();
                
                meta.setDisplayName("§e⚓ " + port.getName());
                
                List<String> lore = new ArrayList<>();
                lore.add("§7Click to navigate");
                lore.add("");
                lore.add("§aPath available ✓");
                
                meta.setLore(lore);
                item.setItemMeta(meta);
                
                inv.setItem(slot, item);
                slot++;
                
                if (slot == 17) slot = 19; // Next row
                if (slot >= 26) break; // Inventory full
            }
        }
        
        player.openInventory(inv);
    }
}
