# BoatRoutes - Stage 1 Development Framework

## 📦 What's Included:

### ✅ Fully Implemented:
- Project structure (Gradle, plugin.yml, config.yml)
- Main plugin class with all managers initialized
- **Data classes** (Port, Dock, PlayerBoat, Route) - COMPLETE
- **PortManager** with PortCreator and PortStorage - WORKING
- **Basic commands** (/port create, /port list) - WORKING

### 🔨 Stub Classes (Need Implementation):
All other classes are created as **stubs** - they compile but need logic added.
See `IMPLEMENTATION_PLAN.md` for detailed order and instructions.

## 🚀 Quick Start:

### 1. Open in IntelliJ IDEA
```
File → Open → Select BoatRoutes folder
```

### 2. Gradle Sync
IntelliJ will automatically detect Gradle project.
Wait for dependencies to download.

### 3. Build
```bash
# In IntelliJ terminal or external terminal:
./gradlew build

# JAR will be in: build/libs/BoatRoutes-1.0.0-ALPHA.jar
```

### 4. Test on Server
```bash
# Copy to your Paper 1.21.10 server:
cp build/libs/BoatRoutes-1.0.0-ALPHA.jar /path/to/server/plugins/

# Start server and test basic commands:
/port create spawn
/port list
```

## 📋 Current Functionality:

### What Works NOW:
- ✅ `/port create <name>` - Creates port, gives NPC egg
- ✅ `/port list` - Shows all ports
- ✅ Plugin loads/saves ports to `ports.yml`
- ✅ Port name validation (English only)

### What Needs Implementation:
- ❌ NPC spawning when using egg
- ❌ Dock placement system
- ❌ GUI menus
- ❌ Boat spawning
- ❌ Pathfinding
- ❌ Navigation/Autopilot

## 🛠️ Development Order:

Follow `IMPLEMENTATION_PLAN.md` for step-by-step guide.

**Recommended order:**
1. **DockPlacer** (Week 1) - Needed for port setup
2. **NPCManager** (Week 1) - Spawn and interact with NPCs  
3. **PortGUI** (Week 1-2) - Basic menu to spawn boats
4. **BoatSpawner** (Week 2) - Create boats at docks
5. **WaterPathfinder** (Week 2-3) - **CORE FEATURE** - A* algorithm
6. **BoatAutopilot** (Week 3-4) - Make boats move
7. **NavigationGUI** (Week 4) - Select destinations

## 📁 Project Structure:

```
BoatRoutes/
├── IMPLEMENTATION_PLAN.md         ← Read this first!
├── README.md                       ← This file
├── build.gradle.kts
├── src/main/
│   ├── java/com/example/boatroutes/
│   │   ├── BoatRoutesPlugin.java  ✅ Complete
│   │   │
│   │   ├── port/
│   │   │   ├── Port.java           ✅ Complete
│   │   │   ├── PortManager.java    ✅ Complete  
│   │   │   ├── PortCreator.java    ✅ Complete
│   │   │   └── PortStorage.java    ✅ Complete
│   │   │
│   │   ├── dock/
│   │   │   ├── Dock.java           ✅ Complete
│   │   │   ├── DockManager.java    ⚠️ Stub
│   │   │   ├── DockPlacer.java     ⚠️ Stub - IMPLEMENT FIRST
│   │   │   └── DockPointCalculator.java ⚠️ Stub
│   │   │
│   │   ├── boat/
│   │   │   ├── PlayerBoat.java     ✅ Complete
│   │   │   └── BoatManager.java    ⚠️ Stub
│   │   │
│   │   ├── route/
│   │   │   ├── Route.java          ✅ Complete
│   │   │   └── RouteManager.java   ⚠️ Stub
│   │   │
│   │   ├── pathfinding/
│   │   │   └── PathfindingManager.java ⚠️ Stub - CRITICAL
│   │   │
│   │   ├── navigation/
│   │   │   └── NavigationManager.java ⚠️ Stub
│   │   │
│   │   ├── npc/
│   │   │   └── NPCManager.java     ⚠️ Stub
│   │   │
│   │   ├── gui/
│   │   │   └── GUIManager.java     ⚠️ Stub
│   │   │
│   │   ├── commands/
│   │   │   ├── PortCommand.java    ✅ Basic working
│   │   │   └── PortTabCompleter.java ✅ Working
│   │   │
│   │   └── listeners/
│   │       ├── VehicleListener.java ⚠️ Stub
│   │       ├── PlayerListener.java  ⚠️ Stub
│   │       └── NPCListener.java     ⚠️ Stub
│   │
│   └── resources/
│       ├── plugin.yml              ✅ Complete
│       └── config.yml              ✅ Complete
```

## 🎯 Stage 1 Goals:

By end of Stage 1, you should be able to:
1. Create ports with `/port create`
2. Place NPC managers
3. Set up 3 docks per port
4. Connect ports with `/port connect spawn north`
5. Spawn a free boat
6. Select destination from GUI
7. **Boat automatically navigates to destination!**

## 📝 Configuration:

Edit `config.yml` to adjust settings:
- `pathfinding.max-distance`: 500 (Stage 1 limit)
- `port.docks-per-port`: 3
- `boat.default-speed`: 0.35

## 🐛 Debugging Tips:

1. **Check logs:** `logs/latest.log`
2. **Enable debug mode:** Set `debug: true` in config.yml
3. **Use logger:** `plugin.getLogger().info("Debug message")`
4. **Test incrementally:** Don't implement everything at once

## 🆘 Common Issues:

### "Class not found" errors:
- Run `./gradlew clean build`
- Refresh Gradle in IntelliJ

### NullPointerException:
- Check if managers are initialized in correct order
- Verify null checks before accessing objects

### Boat doesn't spawn:
- Check dock locations are water
- Verify port is fully set up (3 docks + points calculated)

## 📚 Next Steps:

1. Read `IMPLEMENTATION_PLAN.md`
2. Start with **DockPlacer.java**
3. Test each module before moving to next
4. Ask questions if stuck!

## 🎉 Good Luck!

You have a solid foundation. Follow the implementation plan and you'll have a working transport system in 4-5 weeks!

**Remember:** Test after each module. Don't rush to implement everything at once.
