package com.example.boatroutes.gui;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

/**
 * Utility methods for creating GUI items
 */
public class GUIUtils {
    
    /**
     * Create an item with name and lore
     */
    public static ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            if (name != null) {
                meta.setDisplayName(name);
            }
            
            if (lore != null && lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Check if two ItemStacks have the same display name
     */
    public static boolean isSameItem(ItemStack item1, ItemStack item2) {
        if (item1 == null || item2 == null) return false;
        if (!item1.hasItemMeta() || !item2.hasItemMeta()) return false;
        
        ItemMeta meta1 = item1.getItemMeta();
        ItemMeta meta2 = item2.getItemMeta();
        
        if (!meta1.hasDisplayName() || !meta2.hasDisplayName()) return false;
        
        return meta1.getDisplayName().equals(meta2.getDisplayName());
    }
}
