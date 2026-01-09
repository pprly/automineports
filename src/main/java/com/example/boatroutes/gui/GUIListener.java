package com.example.boatroutes.gui;

import com.example.boatroutes.BoatRoutesPlugin;
import com.example.boatroutes.navigation.BoatAutopilot;
import com.example.boatroutes.navigation.NavigationBook;
import com.example.boatroutes.navigation.NavigationGUI;
import com.example.boatroutes.port.Port;
import org.bukkit.Material;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * GUIListener v3.0 - COMPLETE!
 *
 * Обрабатывает:
 * 1. Port GUI - создание лодок
 * 2. Navigation GUI - выбор порта назначения
 * 3. Navigation Book - открытие GUI
 *
 * @author BoatRoutes Team
 * @version 3.0-COMPLETE
 */
public class GUIListener implements Listener {

    private final BoatRoutesPlugin plugin;
    private final NavigationBook navigationBook;

    public GUIListener(BoatRoutesPlugin plugin) {
        this.plugin = plugin;
        this.navigationBook = new NavigationBook(plugin);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        if (!title.contains("⚓")) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        // === HANDLE PORT GUI ===
        if (title.contains("Port")) {
            handlePortGUI(player, clicked, title);
        }

        // === HANDLE NAVIGATION GUI ===
        else if (title.contains("Select Destination")) {
            handleNavigationGUI(player, clicked);
        }
    }

    /**
     * Обработка Port GUI (создание лодок)
     */
    private void handlePortGUI(Player player, ItemStack clicked, String title) {
        Material type = clicked.getType();

        // Кнопка "Create New Boat"
        if (type == Material.OAK_BOAT) {
            plugin.getLogger().info("Player " + player.getName() + " clicked: Create New Boat");

            // Извлекаем имя порта из заголовка
            String portName = title.replace("§6⚓ ", "").replace(" Port", "");

            Port port = plugin.getPortManager().getPort(portName);
            if (port == null) {
                player.sendMessage("§c✗ Port not found!");
                return;
            }

            player.closeInventory();

            // Запрашиваем имя лодки
            player.sendMessage("");
            player.sendMessage("§a✓ Creating boat at §e" + portName);
            player.sendMessage("§7Type a name for your boat in chat:");
            player.sendMessage("§7(e.g. 'SeaBreeze', 'WaveRider', 'MyBoat')");
            player.sendMessage("");

            // Помечаем что игрок ждёт ввода имени
            plugin.getBoatManager().setAwaitingBoatName(player.getUniqueId(), true);
            plugin.getBoatManager().setCreationPort(player.getUniqueId(), portName);

            plugin.getLogger().info("Player " + player.getName() + " is now awaiting boat name");
        }

        // Кнопка "Close"
        else if (type == Material.RED_STAINED_GLASS_PANE) {
            player.closeInventory();
        }

        // Кнопка "Navigate" (пока не реализована)
        else if (type == Material.FILLED_MAP) {
            player.sendMessage("§7Navigation will be available after Stage 5!");
        }
    }

    /**
     * Обработка Navigation GUI (выбор порта)
     */
    private void handleNavigationGUI(Player player, ItemStack clicked) {
        String portName = clicked.getItemMeta().getDisplayName()
                .replace("§e⚓ ", "")
                .replace("§c✗ ", "");

        // Игнорируем клик на "No destinations available"
        if (portName.contains("No destinations")) {
            return;
        }

        player.closeInventory();

        // Запускаем autopilot
        startAutopilot(player, portName);
    }

    /**
     * Запуск автопилота
     */
    private void startAutopilot(Player player, String destinationPort) {
        // Проверяем что игрок в лодке
        if (!(player.getVehicle() instanceof Boat boat)) {
            player.sendMessage("§c✗ You must be in a boat to navigate!");
            return;
        }

        // Получаем текущий порт
        String currentPort = plugin.getBoatManager().getCreationPort(player.getUniqueId());

        if (currentPort == null) {
            player.sendMessage("§c✗ Cannot determine current port!");
            plugin.getLogger().warning("Player " + player.getName() +
                    " tried to navigate but current port is null!");
            return;
        }

        // Проверяем что не пытаемся плыть в тот же порт
        if (currentPort.equalsIgnoreCase(destinationPort)) {
            player.sendMessage("§c✗ You are already at " + destinationPort + "!");
            return;
        }

        plugin.getLogger().info("Starting autopilot: " + player.getName() +
                " from " + currentPort + " to " + destinationPort);

        // Создаём и запускаем autopilot
        BoatAutopilot autopilot = new BoatAutopilot(
                plugin,
                boat,
                player,
                currentPort,
                destinationPort
        );

        boolean started = autopilot.startJourney();

        if (started) {
            // Сохраняем autopilot в NavigationManager
            plugin.getNavigationManager().addAutopilot(player.getUniqueId(), autopilot);

            plugin.getLogger().info("✓ Autopilot started successfully!");
        } else {
            plugin.getLogger().warning("✗ Failed to start autopilot!");
        }
    }

    /**
     * Обработка Navigation Book (ПКМ)
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null) return;

        // Check if navigation book
        if (navigationBook.isNavigationBook(item)) {
            event.setCancelled(true);

            // Проверяем что игрок в лодке
            if (!(player.getVehicle() instanceof Boat)) {
                player.sendMessage("§c✗ You must be in a boat to use navigation!");
                return;
            }

            // Get current port
            String currentPort = plugin.getBoatManager().getCreationPort(player.getUniqueId());

            if (currentPort == null) {
                player.sendMessage("§cCannot determine current port!");
                return;
            }

            // Open navigation GUI
            NavigationGUI gui = new NavigationGUI(plugin, player, currentPort);
            gui.open();
        }
    }
}