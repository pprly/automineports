package com.example.boatroutes.npc;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.port.Port;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;

/**
 * Manager for all NPCs
 * Handles NPC spawning and respawning
 */
public class NPCManager {
    
    private final BoatRoutesPlugin plugin;
    
    public NPCManager(BoatRoutesPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Respawn NPC for a port (used on server load)
     */
    public boolean respawnNPC(Port port) {
        Location npcLoc = port.getNPCLocation();
        
        if (npcLoc == null) {
            return false;
        }
        
        // Check if world is loaded
        if (npcLoc.getWorld() == null) {
            plugin.getLogger().warning("World not loaded for port: " + port.getName());
            return false;
        }
        
        // Force load chunk
        Chunk chunk = npcLoc.getChunk();
        if (!chunk.isLoaded()) {
            chunk.load();
        }
        
        // Remove old NPC if exists
        if (port.getNpcUUID() != null) {
            Entity oldNPC = Bukkit.getEntity(port.getNpcUUID());
            if (oldNPC != null) {
                oldNPC.remove();
            }
        }
        
        // Spawn new NPC
        Villager npc = (Villager) npcLoc.getWorld().spawnEntity(npcLoc, EntityType.VILLAGER);
        
        npc.setAI(false);
        npc.setInvulnerable(true);
        npc.setCustomName("§6⚓ " + port.getName() + " Port");
        npc.setCustomNameVisible(true);
        npc.setSilent(true);
        npc.setProfession(Villager.Profession.FISHERMAN);
        
        // Update port with new UUID
        port.setNpcUUID(npc.getUniqueId());
        
        plugin.getLogger().info("  Respawned NPC for port: " + port.getName());
        
        return true;
    }
}
