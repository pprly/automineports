package com.example.boatroutes.navigation;

import com.example.boatroutes.BoatRoutesPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

public class NavigationBook {
    
    private final BoatRoutesPlugin plugin;
    private final NamespacedKey bookKey;
    
    public NavigationBook(BoatRoutesPlugin plugin) {
        this.plugin = plugin;
        this.bookKey = new NamespacedKey(plugin, "navigation_book");
    }
    
    public void giveBook(Player player) {
        // Remove old book if exists
        removeBook(player);
        
        // Create new book
        ItemStack book = new ItemStack(Material.BOOK);
        ItemMeta meta = book.getItemMeta();
        
        meta.setDisplayName("§6§l⚓ Navigation Book");
        meta.setLore(Arrays.asList(
            "§7Right-click to open",
            "§7navigation menu",
            "",
            "§8Автоматическая навигация"
        ));
        
        meta.getPersistentDataContainer().set(bookKey, PersistentDataType.BYTE, (byte) 1);
        
        book.setItemMeta(meta);
        
        // Give to player
        player.getInventory().addItem(book);
        player.sendMessage("§a✓ Navigation book added to inventory");
    }
    
    public void removeBook(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isNavigationBook(item)) {
                player.getInventory().remove(item);
            }
        }
    }
    
    public boolean isNavigationBook(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(bookKey, PersistentDataType.BYTE);
    }
}
