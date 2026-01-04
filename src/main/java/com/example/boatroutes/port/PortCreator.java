package com.example.boatroutes.port;
import com.example.boatroutes.BoatRoutesPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.Arrays;

public class PortCreator {
    private final BoatRoutesPlugin plugin;
    
    public PortCreator(BoatRoutesPlugin plugin) {
        this.plugin = plugin;
    }
    
    public Port createPort(String name, Player creator) {
        if (!isValidPortName(name)) {
            creator.sendMessage("§cPort name must be English letters/numbers only!");
            return null;
        }
        
        Port port = new Port(name);
        port.setCreator(creator.getUniqueId());
        
        ItemStack npcEgg = createNPCSpawnEgg(name);
        creator.getInventory().addItem(npcEgg);
        
        creator.sendMessage("§aPort '" + name + "' created!");
        creator.sendMessage("§7Use the egg to place NPC manager");
        
        return port;
    }
    
    private boolean isValidPortName(String name) {
        return name.matches("^[a-zA-Z0-9_]+$");
    }
    
    private ItemStack createNPCSpawnEgg(String portName) {
        ItemStack egg = new ItemStack(Material.VILLAGER_SPAWN_EGG);
        ItemMeta meta = egg.getItemMeta();
        meta.setDisplayName("§6⚓ Port Manager: " + portName);
        meta.setLore(Arrays.asList("§7Right-click to place", "§7NPC manager for this port"));
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, "port_name"),
            PersistentDataType.STRING, portName);
        egg.setItemMeta(meta);
        return egg;
    }
}