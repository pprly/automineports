package com.example.boatroutes.listeners;

import com.example.boatroutes.BoatRoutesPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Handles player chat events
 * Used for boat name input
 */
public class PlayerListener implements Listener {
    
    private final BoatRoutesPlugin plugin;
    
    public PlayerListener(BoatRoutesPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Listen for boat name input in chat
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        
        // Check if player is awaiting boat name
        if (!plugin.getBoatManager().isAwaitingBoatName(player.getUniqueId())) {
            return;
        }
        
        // Cancel the chat event so message doesn't broadcast
        event.setCancelled(true);
        
        String input = event.getMessage().trim();
        
        // Handle cancel
        if (input.equalsIgnoreCase("cancel")) {
            plugin.getBoatManager().setAwaitingBoatName(player.getUniqueId(), false);
            plugin.getBoatManager().setCreationPort(player.getUniqueId(), null);
            player.sendMessage("§c✗ Boat creation cancelled");
            return;
        }
        
        // Validate boat name
        if (!isValidBoatName(input)) {
            player.sendMessage("§cInvalid boat name! Use English letters and numbers only.");
            player.sendMessage("§7Try again or type §ccancel");
            return;
        }
        
        // Get port where player is creating boat
        String portName = plugin.getBoatManager().getCreationPort(player.getUniqueId());
        if (portName == null) {
            player.sendMessage("§cError: Port not found! Please try again.");
            plugin.getBoatManager().setAwaitingBoatName(player.getUniqueId(), false);
            return;
        }
        
        // Create the boat (sync task required)
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            boolean success = plugin.getBoatManager().createBoat(
                player.getUniqueId(),
                input,
                portName
            );
            
            if (success) {
                player.sendMessage("");
                player.sendMessage("§a✓ Boat '§b" + input + "§a' created!");
                player.sendMessage("§7Your boat is waiting at §e" + portName + " §7port");
                player.sendMessage("");
            } else {
                player.sendMessage("§cFailed to create boat! Make sure the port has available docks.");
            }
            
            // Clear awaiting state
            plugin.getBoatManager().setAwaitingBoatName(player.getUniqueId(), false);
            plugin.getBoatManager().setCreationPort(player.getUniqueId(), null);
        });
    }
    
    /**
     * Validate boat name
     */
    private boolean isValidBoatName(String name) {
        // Must be 1-20 characters, English letters/numbers/spaces only
        if (name.length() < 1 || name.length() > 20) {
            return false;
        }
        
        return name.matches("^[a-zA-Z0-9 ]+$");
    }
}
