package com.example.boatroutes.gui;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.port.Port;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GUIManager {
    
    private final BoatRoutesPlugin plugin;
    private final Map<UUID, PortGUI> openGUIs;
    
    public GUIManager(BoatRoutesPlugin plugin) {
        this.plugin = plugin;
        this.openGUIs = new HashMap<>();
    }
    
    public void openPortGUI(Player player, Port port) {
        PortGUI gui = new PortGUI(plugin, port, player);
        gui.open();
        openGUIs.put(player.getUniqueId(), gui);
    }
    
    public void closeGUI(Player player) {
        openGUIs.remove(player.getUniqueId());
    }
    
    public PortGUI getOpenGUI(UUID playerUUID) {
        return openGUIs.get(playerUUID);
    }
    
    public boolean hasGUIOpen(UUID playerUUID) {
        return openGUIs.containsKey(playerUUID);
    }
}
