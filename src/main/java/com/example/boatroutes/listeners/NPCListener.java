package com.example.boatroutes.listeners;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.gui.PortGUI;
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

public class NPCListener implements Listener {
    
    private final BoatRoutesPlugin plugin;
    
    public NPCListener(BoatRoutesPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null || !item.hasItemMeta()) return;
        
        ItemMeta meta = item.getItemMeta();
        NamespacedKey portKey = new NamespacedKey(plugin, "port_name");
        
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK &&
            item.getType() == Material.VILLAGER_SPAWN_EGG &&
            meta.getPersistentDataContainer().has(portKey, PersistentDataType.STRING)) {
            
            handleNPCSpawnEgg(event, player, item, meta);
            return;
        }
        
        if ((event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) &&
            plugin.getDockManager().getDockPlacer().isDockMarker(item)) {
            
            handleDockMarker(event, player, item);
            return;
        }
    }
    
    private void handleNPCSpawnEgg(PlayerInteractEvent event, Player player, ItemStack item, ItemMeta meta) {
        event.setCancelled(true);
        
        String portName = meta.getPersistentDataContainer().get(
            new NamespacedKey(plugin, "port_name"),
            PersistentDataType.STRING
        );
        
        Port port = plugin.getPortManager().getPort(portName);
        if (port == null) {
            player.sendMessage("§cPort not found!");
            return;
        }
        
        if (port.getNPCLocation() != null) {
            player.sendMessage("§cNPC for this port is already placed!");
            return;
        }
        
        Location spawnLoc = event.getClickedBlock().getLocation().add(0.5, 1, 0.5);
        Villager npc = (Villager) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.VILLAGER);
        
        npc.setAI(false);
        npc.setInvulnerable(true);
        npc.setCustomName("§6⚓ " + portName + " Port");
        npc.setCustomNameVisible(true);
        npc.setSilent(true);
        npc.setProfession(Villager.Profession.FISHERMAN);
        
        port.setNPCLocation(spawnLoc);
        port.setNpcUUID(npc.getUniqueId());
        plugin.getPortManager().savePort(port);
        
        item.setAmount(item.getAmount() - 1);
        
        player.sendMessage("§a✓ NPC placed for port: §e" + portName);
        player.sendMessage("§7Now place " + plugin.getConfig().getInt("port.docks-per-port", 3) + " docks");
        
        plugin.getDockManager().getDockPlacer().startDockPlacement(port, player);
    }
    
    private void handleDockMarker(PlayerInteractEvent event, Player player, ItemStack item) {
        event.setCancelled(true);
        
        String portName = plugin.getDockManager().getDockPlacer().getPortNameFromMarker(item);
        if (portName == null) {
            player.sendMessage("§cInvalid dock marker!");
            return;
        }
        
        Port port = plugin.getPortManager().getPort(portName);
        if (port == null) {
            player.sendMessage("§cPort not found!");
            return;
        }
        
        Block waterBlock = findWaterBlock(player, event);
        
        if (waterBlock == null) {
            player.sendMessage("§cNo water found!");
            player.sendMessage("§7Try: Aim at water, stand IN water, or click water from side");
            return;
        }
        
        Location dockLoc = waterBlock.getLocation();
        
        boolean success = plugin.getDockManager().getDockPlacer().placeDock(port, dockLoc, player);
        
        if (success) {
            item.setAmount(item.getAmount() - 1);
            plugin.getPortManager().savePort(port);
        }
    }
    
    private Block findWaterBlock(Player player, PlayerInteractEvent event) {
        if (event.getClickedBlock() != null && isWater(event.getClickedBlock().getType())) {
            return event.getClickedBlock();
        }
        
        Block targetBlock = player.getTargetBlockExact(5, FluidCollisionMode.ALWAYS);
        if (targetBlock != null && isWater(targetBlock.getType())) {
            return targetBlock;
        }
        
        Block feetBlock = player.getLocation().getBlock();
        if (isWater(feetBlock.getType())) {
            return feetBlock;
        }
        
        Block belowBlock = player.getLocation().subtract(0, 1, 0).getBlock();
        if (isWater(belowBlock.getType())) {
            return belowBlock;
        }
        
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
    
    private boolean isWater(Material material) {
        return material == Material.WATER || material.toString().contains("WATER");
    }
    
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager npc)) return;
        
        Player player = event.getPlayer();
        
        Port port = findPortByNPC(npc);
        if (port == null) return;
        
        event.setCancelled(true);
        
        if (!port.isFullySetup()) {
            player.sendMessage("§cPort is not fully set up yet!");
            player.sendMessage("§7Need to place " + plugin.getConfig().getInt("port.docks-per-port", 3) + " docks");
            return;
        }
        
        plugin.getBoatManager().setCreationPort(player.getUniqueId(), port.getName());
        
        PortGUI gui = new PortGUI(plugin, port, player);
        gui.open();
    }
    
    private Port findPortByNPC(Villager npc) {
        for (Port port : plugin.getPortManager().getAllPorts()) {
            if (port.getNpcUUID() != null && port.getNpcUUID().equals(npc.getUniqueId())) {
                return port;
            }
        }
        return null;
    }
}
