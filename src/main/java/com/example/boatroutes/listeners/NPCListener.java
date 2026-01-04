package com.example.boatroutes.listeners;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.port.Port;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles NPC-related events:
 * - Spawning NPC with spawn egg
 * - Clicking NPC to open GUI
 * - Placing docks with markers
 */
public class NPCListener implements Listener {
    
    private final BoatRoutesPlugin plugin;
    
    public NPCListener(BoatRoutesPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Handle NPC spawn egg usage and dock marker placement
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null || !item.hasItemMeta()) return;
        
        ItemMeta meta = item.getItemMeta();
        NamespacedKey portKey = new NamespacedKey(plugin, "port_name");
        
        // Check if it's an NPC spawn egg (RIGHT_CLICK_BLOCK only)
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK &&
            item.getType() == Material.VILLAGER_SPAWN_EGG &&
            meta.getPersistentDataContainer().has(portKey, PersistentDataType.STRING)) {
            
            handleNPCSpawnEgg(event, player, item, meta);
            return;
        }
        
        // Check if it's a dock marker (ANY RIGHT_CLICK)
        if ((event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) &&
            plugin.getDockManager().getDockPlacer().isDockMarker(item)) {
            
            handleDockMarker(event, player, item);
            return;
        }
    }
    
    /**
     * Handle NPC spawn egg usage
     */
    private void handleNPCSpawnEgg(PlayerInteractEvent event, Player player, ItemStack item, ItemMeta meta) {
        event.setCancelled(true);
        
        String portName = meta.getPersistentDataContainer().get(
            new NamespacedKey(plugin, "port_name"),
            PersistentDataType.STRING
        );
        
        Port port = plugin.getPortManager().getPort(portName);
        if (port == null) {
            player.sendMessage("§cПорт не найден!");
            return;
        }
        
        // Check if NPC already placed
        if (port.getNPCLocation() != null) {
            player.sendMessage("§cNPC для этого порта уже установлен!");
            return;
        }
        
        // Spawn NPC at clicked location
        Location spawnLoc = event.getClickedBlock().getLocation().add(0.5, 1, 0.5);
        Villager npc = (Villager) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.VILLAGER);
        
        // Configure NPC
        npc.setAI(false);
        npc.setInvulnerable(true);
        npc.setCustomName("§6⚓ " + portName + " Port");
        npc.setCustomNameVisible(true);
        npc.setSilent(true);
        npc.setProfession(Villager.Profession.FISHERMAN);
        
        // Save NPC to port
        port.setNPCLocation(spawnLoc);
        port.setNpcUUID(npc.getUniqueId());
        plugin.getPortManager().savePort(port);
        
        // Remove egg from inventory
        item.setAmount(item.getAmount() - 1);
        
        player.sendMessage("§a✓ NPC установлен для порта: §e" + portName);
        player.sendMessage("§7Теперь установите " + plugin.getConfig().getInt("port.docks-per-port", 3) + " причала");
        
        // Give dock markers
        plugin.getDockManager().getDockPlacer().startDockPlacement(port, player);
    }
    
    /**
     * Handle dock marker placement
     * FIXED: Multiple methods to find water block
     */
    private void handleDockMarker(PlayerInteractEvent event, Player player, ItemStack item) {
        event.setCancelled(true);
        
        String portName = plugin.getDockManager().getDockPlacer().getPortNameFromMarker(item);
        if (portName == null) {
            player.sendMessage("§cНеверный маркер причала!");
            return;
        }
        
        Port port = plugin.getPortManager().getPort(portName);
        if (port == null) {
            player.sendMessage("§cПорт не найден!");
            return;
        }
        
        // Try to find water block in multiple ways
        Block waterBlock = findWaterBlock(player, event);
        
        if (waterBlock == null) {
            player.sendMessage("§cНе найдена вода!");
            player.sendMessage("§7Попробуй:");
            player.sendMessage("§7- Навестись прицелом на воду");
            player.sendMessage("§7- Встать В воде и кликнуть");
            player.sendMessage("§7- Кликнуть по воде сбоку");
            return;
        }
        
        // Debug info
        player.sendMessage("§7Найден блок: " + waterBlock.getType() + " на " + 
            waterBlock.getX() + ", " + waterBlock.getY() + ", " + waterBlock.getZ());
        
        // Place dock at water location
        Location dockLoc = waterBlock.getLocation();
        
        boolean success = plugin.getDockManager().getDockPlacer().placeDock(port, dockLoc, player);
        
        if (success) {
            // Remove marker from inventory
            item.setAmount(item.getAmount() - 1);
            
            // Save port
            plugin.getPortManager().savePort(port);
        }
    }
    
    /**
     * Find water block using multiple methods
     */
    private Block findWaterBlock(Player player, PlayerInteractEvent event) {
        // Method 1: If clicked on a block directly
        if (event.getClickedBlock() != null && isWater(event.getClickedBlock().getType())) {
            return event.getClickedBlock();
        }
        
        // Method 2: Player's exact target block (with fluid collision)
        Block targetBlock = player.getTargetBlockExact(5, FluidCollisionMode.ALWAYS);
        if (targetBlock != null && isWater(targetBlock.getType())) {
            return targetBlock;
        }
        
        // Method 3: Block player is standing in
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
                        // Found water nearby!
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
    
    /**
     * Handle clicking on NPC to open GUI
     */
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager npc)) return;
        
        Player player = event.getPlayer();
        
        // Find port with this NPC
        Port port = findPortByNPC(npc);
        if (port == null) return;
        
        event.setCancelled(true);
        
        // Check if port is fully set up
        if (!port.isFullySetup()) {
            player.sendMessage("§cПорт ещё не настроен полностью!");
            player.sendMessage("§7Нужно установить " + plugin.getConfig().getInt("port.docks-per-port", 3) + " причалов");
            return;
        }
        
        // Open port GUI
        // TODO: Implement GUI opening
        player.sendMessage("§eОткрываем GUI порта: " + port.getName());
        player.sendMessage("§7(GUI будет реализовано в следующем этапе)");
    }
    
    /**
     * Find port by NPC UUID
     */
    private Port findPortByNPC(Villager npc) {
        for (Port port : plugin.getPortManager().getAllPorts()) {
            if (port.getNpcUUID() != null && port.getNpcUUID().equals(npc.getUniqueId())) {
                return port;
            }
        }
        return null;
    }
}
