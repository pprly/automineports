package com.example.boatroutes.gui;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.navigation.NavigationBook;
import com.example.boatroutes.navigation.NavigationGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class GUIListener implements Listener {
    
    private final BoatRoutesPlugin plugin;
    private final NavigationBook navigationBook;
    
    public GUIListener(BoatRoutesPlugin plugin) {
        this.plugin = plugin;
        this.navigationBook = new NavigationBook(plugin);
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        
        if (!title.contains("⚓")) return;
        
        event.setCancelled(true);
        
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        
        // Handle navigation GUI
        if (title.contains("Select Destination")) {
            String portName = clicked.getItemMeta().getDisplayName().replace("§e⚓ ", "");
            
            player.closeInventory();
            player.sendMessage("§aNavigating to: §e" + portName);
            player.sendMessage("§7(Autopilot will be in Stage 5)");
            
            // TODO: Start autopilot in Stage 5
        }
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null) return;
        
        // Check if navigation book
        if (navigationBook.isNavigationBook(item)) {
            event.setCancelled(true);
            
            // Get current port
            String currentPort = plugin.getBoatManager().getCreationPort(player.getUniqueId());
            
            if (currentPort == null) {
                player.sendMessage("§cCannot determine current port!");
                return;
            }
            
            // Open navigation GUI
            NavigationGUI gui = new NavigationGUI(plugin, player, currentPort);
            gui.open();
        }
    }
}
