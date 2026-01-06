package com.example.boatroutes.cache;

import java.util.BitSet;

/**
 * Cached water data for a single chunk (16x16 blocks)
 * Memory efficient: uses BitSet for water presence
 * 
 * FIXED v3.1.1: Index calculation consistency
 */
public class CachedWaterChunk {
    
    // 16x16 = 256 blocks per chunk
    // BitSet uses ~32 bytes for 256 bits
    private final BitSet waterPresence;
    private final byte[] waterLevels; // Y coordinate for each water block
    
    public CachedWaterChunk() {
        this.waterPresence = new BitSet(256);
        this.waterLevels = new byte[256];
    }
    
    /**
     * Mark block as having water at Y level
     * @param localX 0-15
     * @param localZ 0-15
     * @param y Y coordinate
     */
    public void setWater(int localX, int localZ, int y) {
        // CRITICAL FIX: Apply mask to prevent index overflow
        int index = (localX & 15) * 16 + (localZ & 15);
        waterPresence.set(index);
        waterLevels[index] = (byte) y;
    }
    
    /**
     * Check if block has water
     * @param localX 0-15
     * @param localZ 0-15
     */
    public boolean hasWater(int localX, int localZ) {
        int index = (localX & 15) * 16 + (localZ & 15);
        return waterPresence.get(index);
    }
    
    /**
     * Get water Y level
     * @param localX 0-15
     * @param localZ 0-15
     */
    public int getWaterY(int localX, int localZ) {
        int index = (localX & 15) * 16 + (localZ & 15);
        return waterLevels[index];
    }
    
    /**
     * Get count of water blocks in this chunk
     */
    public int getWaterBlockCount() {
        return waterPresence.cardinality();
    }
    
    /**
     * Serialize to string for YAML storage
     * Format: "bitset:levels"
     */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        
        // Serialize BitSet as hex string
        long[] bits = waterPresence.toLongArray();
        for (long bit : bits) {
            sb.append(Long.toHexString(bit)).append(",");
        }
        
        sb.append(":");
        
        // Serialize Y levels as base64-like compact string
        for (int i = 0; i < 256; i++) {
            if (waterPresence.get(i)) {
                sb.append(waterLevels[i]).append(",");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Deserialize from string
     */
    public static CachedWaterChunk deserialize(String data) {
        CachedWaterChunk chunk = new CachedWaterChunk();
        
        String[] parts = data.split(":");
        if (parts.length != 2) return chunk;
        
        // Deserialize BitSet
        String[] bitStrings = parts[0].split(",");
        long[] bits = new long[bitStrings.length];
        for (int i = 0; i < bitStrings.length; i++) {
            if (!bitStrings[i].isEmpty()) {
                bits[i] = Long.parseLong(bitStrings[i], 16);
            }
        }
        
        BitSet reconstructed = BitSet.valueOf(bits);
        
        // Deserialize Y levels
        String[] levelStrings = parts[1].split(",");
        int levelIndex = 0;
        
        for (int i = 0; i < 256; i++) {
            if (reconstructed.get(i)) {
                if (levelIndex < levelStrings.length && !levelStrings[levelIndex].isEmpty()) {
                    chunk.setWater(i / 16, i % 16, Integer.parseInt(levelStrings[levelIndex]));
                    levelIndex++;
                }
            }
        }
        
        return chunk;
    }
    
    /**
     * Get memory footprint estimate
     */
    public int getMemorySize() {
        // BitSet: ~32 bytes for 256 bits
        // byte array: 256 bytes
        // Total: ~288 bytes per chunk
        return 288;
    }
}
