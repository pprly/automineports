package com.example.boatroutes.dock;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.port.Port;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

/**
 * Handles dock placement with markers
 * RESPONSIBILITY: ONLY dock placement logic
 */
public class DockPlacer {
    
    private final BoatRoutesPlugin plugin;
    private final int DOCKS_PER_PORT;
    
    public DockPlacer(BoatRoutesPlugin plugin) {
        this.plugin = plugin;
        this.DOCKS_PER_PORT = plugin.getConfig().getInt("port.docks-per-port", 3);
    }
    
    /**
     * Start dock placement process - give player markers
     */
    public void startDockPlacement(Port port, Player player) {
        ItemStack markers = createDockMarker(port.getName());
        markers.setAmount(DOCKS_PER_PORT);
        
        player.getInventory().addItem(markers);
        
        player.sendMessage("§a✓ Установите " + DOCKS_PER_PORT + " причала!");
        player.sendMessage("§7ПКМ по воде чтобы установить причал");
    }
    
    /**
     * Handle placing a single dock
     */
    public boolean placeDock(Port port, Location location, Player player) {
        // Validation: must be water
        if (!isWater(location.getBlock().getType())) {
            player.sendMessage("§cЭто не вода! Установите причал в воду.");
            return false;
        }
        
        // Check if all docks already placed
        if (port.getDockCount() >= DOCKS_PER_PORT) {
            player.sendMessage("§cВсе причалы уже установлены! (" + DOCKS_PER_PORT + "/" + DOCKS_PER_PORT + ")");
            return false;
        }
        
        // Create dock
        int dockNumber = port.getDockCount() + 1;
        Dock dock = new Dock(port, dockNumber, location.clone());
        
        // Add to port
        port.addDock(dock);
        
        // Visual feedback
        visualizeDock(location);
        
        player.sendMessage("§a✓ Причал #" + dockNumber + " установлен! " +
            "§7(" + port.getDockCount() + "/" + DOCKS_PER_PORT + ")");
        
        // All docks placed?
        if (port.getDockCount() == DOCKS_PER_PORT) {
            onAllDocksPlaced(port, player);
        }
        
        return true;
    }
    
    /**
     * Called when all docks are placed - trigger point calculation
     */
    private void onAllDocksPlaced(Port port, Player player) {
        player.sendMessage("");
        player.sendMessage("§a§l✓ Все причалы установлены!");
        player.sendMessage("§7Рассчитываем точки выхода и слияния...");
        
        // Calculate exit/entry points
        plugin.getDockManager().getDockPointCalculator().calculatePortPoints(port);
        
        // Save port
        plugin.getPortManager().savePort(port);
        
        player.sendMessage("§a✓ Порт §e" + port.getName() + " §aготов к работе!");
        player.sendMessage("§7Используйте: §e/port connect " + port.getName() + " <другой_порт>");
    }
    
    /**
     * Create dock marker item
     */
    private ItemStack createDockMarker(String portName) {
        ItemStack marker = new ItemStack(Material.PRISMARINE_SHARD);
        ItemMeta meta = marker.getItemMeta();
        
        meta.setDisplayName("§b🚤 Dock Marker");
        meta.setLore(Arrays.asList(
            "§7Порт: §f" + portName,
            "",
            "§7ПКМ по воде чтобы установить причал",
            "§7" + DOCKS_PER_PORT + " причалов на порт"
        ));
        
        // Store port name in PDC
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, "port_name"),
            PersistentDataType.STRING,
            portName
        );
        
        marker.setItemMeta(meta);
        return marker;
    }
    
    /**
     * Spawn particles at dock location
     */
    private void visualizeDock(Location location) {
        location.getWorld().spawnParticle(
            Particle.SPLASH,
            location.clone().add(0.5, 1, 0.5),
            50,
            1, 1, 1,
            0.1
        );
        
        location.getWorld().spawnParticle(
            Particle.BUBBLE_POP,
            location.clone().add(0.5, 0.5, 0.5),
            20,
            0.5, 0.5, 0.5,
            0.05
        );
    }
    
    /**
     * Check if material is water
     */
    private boolean isWater(Material material) {
        return material == Material.WATER || material.toString().contains("WATER");
    }
    
    /**
     * Check if item is a dock marker
     */
    public boolean isDockMarker(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(
            new NamespacedKey(plugin, "port_name"),
            PersistentDataType.STRING
        );
    }
    
    /**
     * Get port name from dock marker
     */
    public String getPortNameFromMarker(ItemStack item) {
        if (!isDockMarker(item)) return null;
        
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().get(
            new NamespacedKey(plugin, "port_name"),
            PersistentDataType.STRING
        );
    }
}