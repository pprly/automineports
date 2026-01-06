package com.example.boatroutes.gui;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.port.Port;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Main GUI for port interaction
 * Opens when player clicks on port NPC
 */
public class PortGUI {
    
    private final BoatRoutesPlugin plugin;
    private final Port port;
    private final Player player;
    private Inventory inventory;
    
    public PortGUI(BoatRoutesPlugin plugin, Port port, Player player) {
        this.plugin = plugin;
        this.port = port;
        this.player = player;
        createInventory();
    }
    
    /**
     * Create the GUI inventory
     */
    private void createInventory() {
        inventory = Bukkit.createInventory(null, 27, "§6⚓ " + port.getName() + " Port");
        
        // Fill background with gray glass
        ItemStack background = GUIUtils.createItem(
            Material.GRAY_STAINED_GLASS_PANE,
            " "
        );
        
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, background);
        }
        
        // Port info item (slot 4 - top center)
        int available = getAvailableDocks();
        ItemStack portInfo = GUIUtils.createItem(
            Material.COMPASS,
            "§6" + port.getName() + " Port",
            "§7Total Docks: §f" + port.getDockCount(),
            "§7Available: §a" + available + " docks",
            "§7Occupied: §c" + (port.getDockCount() - available) + " docks",
            "",
            "§7Click below to create a boat"
        );
        inventory.setItem(4, portInfo);
        
        // Create boat button (slot 13 - center)
        if (available > 0) {
            ItemStack createBoat = GUIUtils.createItem(
                Material.OAK_BOAT,
                "§b🚤 Create New Boat",
                "§7Click to create a boat at this port",
                "",
                "§aAvailable docks: " + available,
                "",
                "§eClick to create!"
            );
            inventory.setItem(13, createBoat);
        } else {
            ItemStack noSpace = GUIUtils.createItem(
                Material.BARRIER,
                "§c✗ No Space Available",
                "§7All docks are occupied",
                "",
                "§7Wait for boats to leave or",
                "§7go to another port"
            );
            inventory.setItem(13, noSpace);
        }
        
        // Navigate button (slot 15 - right)
        ItemStack navigate = GUIUtils.createItem(
            Material.FILLED_MAP,
            "§e🧭 Navigate",
            "§7Select destination port",
            "",
            "§7(Coming in next stage)"
        );
        inventory.setItem(15, navigate);
        
        // Close button (slot 22 - bottom center)
        ItemStack close = GUIUtils.createItem(
            Material.RED_STAINED_GLASS_PANE,
            "§cClose",
            "§7Click to close menu"
        );
        inventory.setItem(22, close);
    }
    
    /**
     * Open the GUI for the player
     */
    public void open() {
        player.openInventory(inventory);
    }
    
    /**
     * Get number of available docks
     */
    private int getAvailableDocks() {
        int available = 0;
        for (var dock : port.getDocks()) {
            if (dock.isAvailable()) {
                available++;
            }
        }
        return available;
    }
    
    /**
     * Get the port this GUI is for
     */
    public Port getPort() {
        return port;
    }
    
    /**
     * Get the inventory (for event handling)
     */
    public Inventory getInventory() {
        return inventory;
    }
}
