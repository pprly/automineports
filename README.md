# 💾 BoatRoutes - Data Persistence Fix (ГОТОВЫЕ ФАЙЛЫ)

## ✅ ЧТО ВНУТРИ:

Все 4 файла **ПОЛНОСТЬЮ ГОТОВЫ** к использованию!

```
boatroutes-persistence-ready/
├── src/main/java/com/example/boatroutes/
│   ├── BoatRoutesPlugin.java      ← ГОТОВ!
│   ├── port/
│   │   ├── PortStorage.java       ← ГОТОВ!
│   │   └── PortManager.java       ← ГОТОВ!
│   └── npc/
│       └── NPCManager.java        ← ГОТОВ!
└── README.md                       ← ТЫ ЗДЕСЬ
```

---

## 🚀 УСТАНОВКА (2 СПОСОБА):

### Способ 1: Через IntelliJ (ПРОЩЕ!)

1. Распакуй `boatroutes-persistence-ready.zip`
2. Открой свой проект BoatRoutes в IntelliJ
3. В левом меню найди папки:
   - `src/main/java/com/example/boatroutes/`
   - `src/main/java/com/example/boatroutes/port/`
   - `src/main/java/com/example/boatroutes/npc/`
4. **Перетащи файлы** из архива в соответствующие папки:
   - `BoatRoutesPlugin.java` → в `boatroutes/`
   - `PortStorage.java` → в `boatroutes/port/`
   - `PortManager.java` → в `boatroutes/port/`
   - `NPCManager.java` → в `boatroutes/npc/`
5. IntelliJ спросит "Replace?" → **ДА для всех!**
6. Готово! ✅

---

### Способ 2: Через терминал

```bash
# Распакуй архив
unzip boatroutes-persistence-ready.zip

# Перейди в папку своего проекта
cd ~/BoatRoutes

# Скопируй все 4 файла
cp boatroutes-persistence-ready/src/main/java/com/example/boatroutes/BoatRoutesPlugin.java \
   src/main/java/com/example/boatroutes/

cp boatroutes-persistence-ready/src/main/java/com/example/boatroutes/port/PortStorage.java \
   src/main/java/com/example/boatroutes/port/

cp boatroutes-persistence-ready/src/main/java/com/example/boatroutes/port/PortManager.java \
   src/main/java/com/example/boatroutes/port/

cp boatroutes-persistence-ready/src/main/java/com/example/boatroutes/npc/NPCManager.java \
   src/main/java/com/example/boatroutes/npc/
```

---

## 🔨 КОМПИЛЯЦИЯ:

```bash
cd ~/BoatRoutes
./gradlew clean build
```

Если успешно:
```
BUILD SUCCESSFUL in 5s
```

Jar файл будет в: `build/libs/BoatRoutes-X.X.X.jar`

---

## 🚀 УСТАНОВКА НА СЕРВЕР:

```bash
# 1. Останови сервер
/stop

# 2. Замени плагин
cp build/libs/BoatRoutes-*.jar /path/to/server/plugins/BoatRoutes.jar

# 3. Запусти сервер
./start.sh

# 4. Проверь логи:
[BoatRoutes] Loading data...
[BoatRoutes] Loaded 2 ports
[BoatRoutes]   Loaded port: spawn
[BoatRoutes]   Loaded port: north
[BoatRoutes]   Respawned NPC for port: spawn
[BoatRoutes]   Respawned NPC for port: north
[BoatRoutes] Respawned 2 NPCs
[BoatRoutes] Saved 2 ports to ports.yml
[BoatRoutes] BoatRoutes v6.0 enabled successfully!
```

---

## 🧪 ТЕСТИРОВАНИЕ:

```bash
# 1. Создай порт
/port create testport

# 2. Установи NPC, доки, navigation point

# 3. Перезапусти сервер
/stop
./start.sh

# 4. Проверь что всё на месте:
- Кликни на NPC → GUI открылось? ✅
- /port info testport → Доки и navigation point есть? ✅
- Лодки работают? ✅
```

---

## ✅ ЧТО ИСПРАВЛЕНО:

После установки:
- ✅ Порты сохраняются после перезапуска
- ✅ NPCs автоматически возрождаются
- ✅ Доки восстанавливаются
- ✅ Navigation points работают
- ✅ Пути между портами сохраняются

---

## 📝 СТРУКТУРА ports.yml:

После установки файл `plugins/BoatRoutes/ports.yml` будет выглядеть так:

```yaml
ports:
  spawn:
    npc-location:
      world: world
      x: 100.5
      y: 64.0
      z: 200.5
    npc-uuid: "12345678-1234-1234-1234-123456789abc"
    navigation-point:
      world: world
      x: 95.5
      y: 64.0
      z: 195.5
    docks:
      - number: 1
        location:
          world: world
          x: 102.5
          y: 64.0
          z: 198.5
      - number: 2
        location:
          world: world
          x: 98.5
          y: 64.0
          z: 202.5
      - number: 3
        location:
          world: world
          x: 105.5
          y: 64.0
          z: 203.5
    creator: "player-uuid"
    created-at: 1736446800000
```

---

## 🔍 ЧТО ИЗМЕНИЛОСЬ В КАЖДОМ ФАЙЛЕ:

### PortStorage.java
- ✅ Добавлено сохранение/загрузка navigation-point
- ✅ Добавлено сохранение/загрузка docks
- ✅ Улучшена работа с NPC UUID
- ✅ Backward compatibility со старыми ports.yml

### PortManager.java
- ✅ Добавлен метод `respawnAllNPCs()`
- ✅ Вызов respawn после `loadAllPorts()`

### NPCManager.java
- ✅ Добавлен метод `respawnNPC(Port port)`
- ✅ Chunk loading
- ✅ Удаление старых NPC
- ✅ Обновление UUID

### BoatRoutesPlugin.java
- ✅ Добавлен вызов `portManager.saveAllPorts()` после загрузки
- ✅ Обновление UUID в файле после respawn

---

## 🐛 TROUBLESHOOTING:

**Ошибка компиляции:**
→ Проверь что все 4 файла на месте
→ Попробуй `./gradlew clean` потом `./gradlew build`

**NPCs не появляются:**
→ Проверь логи: должно быть "Respawned X NPCs"
→ Проверь что world загружен

**ports.yml не обновляется:**
→ Проверь что добавлена строка `portManager.saveAllPorts()` в onEnable()

**Старые порты не работают:**
→ Это нормально! Система обратно совместима
→ Navigation-point создастся из convergence-point автоматически

---

## 🎉 ГОТОВО!

Теперь все данные будут сохраняться после перезапуска сервера!

Порты, NPCs, доки, navigation points - всё останется на месте! 🚀
