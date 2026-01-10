package com.example.boatroutes.listeners;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.port.Port;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles navigation point placement with compass item
 * ТОЧНО ТАК ЖЕ как установка доков в NPCListener!
 */
public class NavigationPointListener implements Listener {
    
    private final BoatRoutesPlugin plugin;
    
    public NavigationPointListener(BoatRoutesPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null || !item.hasItemMeta()) return;
        
        // Check if navigation point marker
        if (!plugin.getDockManager().getDockPlacer().isNavigationPointMarker(item)) {
            return;
        }
        
        // Only right-click (same as dock markers!)
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && 
            event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        
        event.setCancelled(true);
        
        // Get port name from item
        String portName = plugin.getDockManager().getDockPlacer().getPortNameFromNavMarker(item);
        if (portName == null) {
            player.sendMessage("§c✗ Invalid navigation point marker!");
            return;
        }
        
        // Get port
        Port port = plugin.getPortManager().getPort(portName);
        if (port == null) {
            player.sendMessage("§c✗ Port not found: " + portName);
            return;
        }
        
        // ИСПОЛЬЗУЕМ ТОЧНО ТОТ ЖЕ МЕТОД что и для доков!
        Block waterBlock = findWaterBlock(player, event);
        
        if (waterBlock == null) {
            player.sendMessage("§cNo water found!");
            player.sendMessage("§7Try: Aim at water, stand IN water, or click water from side");
            return;
        }
        
        Location location = waterBlock.getLocation();
        
        // Set navigation point
        boolean success = plugin.getDockManager().getDockPlacer()
            .setNavigationPoint(port, location, player);
        
        if (success) {
            // Remove item from inventory
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().remove(item);
            }
        }
    }
    
    /**
     * ТОЧНО ТОТ ЖЕ КОД что в NPCListener.findWaterBlock()!
     */
    private Block findWaterBlock(Player player, PlayerInteractEvent event) {
        // Method 1: Direct click on water block
        if (event.getClickedBlock() != null && isWater(event.getClickedBlock().getType())) {
            return event.getClickedBlock();
        }
        
        // Method 2: Ray trace with ALWAYS fluid collision (КЛЮЧЕВОЕ!)
        Block targetBlock = player.getTargetBlockExact(5, FluidCollisionMode.ALWAYS);
        if (targetBlock != null && isWater(targetBlock.getType())) {
            return targetBlock;
        }
        
        // Method 3: Block at player's feet
        Block feetBlock = player.getLocation().getBlock();
        if (isWater(feetBlock.getType())) {
            return feetBlock;
        }
        
        // Method 4: Block below player
        Block belowBlock = player.getLocation().subtract(0, 1, 0).getBlock();
        if (isWater(belowBlock.getType())) {
            return belowBlock;
        }
        
        // Method 5: Search in 2-block radius around player
        Location playerLoc = player.getLocation();
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -2; z <= 2; z++) {
                    Block checkBlock = playerLoc.clone().add(x, y, z).getBlock();
                    if (isWater(checkBlock.getType())) {
                        return checkBlock;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Check if material is water
     */
    private boolean isWater(Material material) {
        return material == Material.WATER || material.toString().contains("WATER");
    }
}
