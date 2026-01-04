# BoatRoutes - Implementation Plan for Stage 1

## ✅ COMPLETED:
- [x] Project structure (build.gradle.kts, plugin.yml, config.yml)
- [x] Main plugin class (BoatRoutesPlugin.java)
- [x] Data classes (Port, Dock, PlayerBoat, Route)
- [x] PortManager with PortCreator and PortStorage

## 🔨 TO IMPLEMENT (in order):

### 1. Dock Module (Week 1)
- [ ] `DockPlacer.java` - Handle dock placement with markers
- [ ] `DockPointCalculator.java` - Calculate exit/entry points
- [ ] Test: Create port, place 3 docks, verify points calculated

### 2. NPC Module (Week 1)
- [ ] `NPCManager.java` - Manage all NPCs
- [ ] `PortNPC.java` - Data for port NPC
- [ ] `NPCInteractionHandler.java` - Handle clicks on NPC
- [ ] `NPCListener.java` - Event listener for NPC spawning
- [ ] Test: Spawn NPC, click to open GUI

### 3. GUI Module (Week 1-2)
- [ ] `GUIManager.java` - Manage all GUIs
- [ ] `PortGUI.java` - Port menu (spawn boat button)
- [ ] `NavigationGUI.java` - Navigation menu (select destination)
- [ ] `GUIUtils.java` - Helper methods for GUI creation
- [ ] Test: Open port GUI, spawn boat

### 4. Boat Module (Week 2)
- [ ] `BoatManager.java` - Manage all boats
- [ ] `BoatSpawner.java` - Spawn boats at docks
- [ ] `BoatOwnership.java` - Track 1 boat per player
- [ ] `BoatStorage.java` - Save/load boats
- [ ] Test: Spawn boat, verify ownership, save/load

### 5. Pathfinding Module (Week 2-3) **MOST COMPLEX**
- [ ] `PathfindingManager.java` - Coordinate pathfinding
- [ ] `WaterPathfinder.java` - A* algorithm implementation
- [ ] `PathValidator.java` - Validate water safety
- [ ] `PathOptimizer.java` - Reduce waypoints
- [ ] `PathStorage.java` - Save/load routes
- [ ] Test: Connect 2 ports, verify path found

### 6. Route Module (Week 3)
- [ ] `RouteManager.java` - Manage all routes
- [ ] `RouteBuilder.java` - Build full paths with dock segments
- [ ] Test: Get full path from dock to dock

### 7. Navigation Module (Week 3-4)
- [ ] `NavigationManager.java` - Manage autopilots
- [ ] `NavigationBook.java` - Give book on boat enter
- [ ] `BoatAutopilot.java` - Move boat along waypoints
- [ ] Test: Start journey, boat moves automatically

### 8. Event Listeners (Week 4)
- [ ] `VehicleListener.java` - Boat enter/exit events
- [ ] `PlayerListener.java` - Player events
- [ ] Already created: `NPCListener.java`
- [ ] Test: Events fire correctly

### 9. Commands (Week 4)
- [ ] `PortCommand.java` - Handle /port commands
- [ ] `PortTabCompleter.java` - Tab completion
- [ ] Test: All commands work

### 10. Utilities (Week 4)
- [ ] `LocationUtils.java` - Location helpers
- [ ] `ItemBuilder.java` - Item creation
- [ ] `MessageUtils.java` - Message formatting

## 🧪 FINAL TESTING (Week 5):
1. Create port "spawn"
2. Place NPC, place 3 docks
3. Create port "north" (500 blocks away)
4. Connect ports: `/port connect spawn north`
5. Spawn boat at spawn
6. Travel to north - verify autopilot works
7. Return journey

## 📝 NOTES:
- Each module is INDEPENDENT - implement and test separately
- Start with simplest (Dock) and work up to complex (Pathfinding)
- Test after each module before moving to next
- Use `plugin.getLogger().info()` extensively for debugging

## 🚀 QUICK START FOR DEVELOPMENT:
```bash
# 1. Open in IntelliJ IDEA
# 2. Gradle sync
# 3. Implement DockPlacer first
# 4. Build and test on server
./gradlew build
```

## ⚠️ CRITICAL FILES TO IMPLEMENT FIRST:
1. DockPlacer - needed for port creation
2. WaterPathfinder - core feature
3. BoatAutopilot - makes boats move

Everything else supports these 3 core features.
