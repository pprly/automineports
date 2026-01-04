package com.example.boatroutes.commands;
import com.example.boatroutes.BoatRoutesPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PortTabCompleter implements TabCompleter {
    private final BoatRoutesPlugin plugin;
    private static final List<String> SUBCOMMANDS = Arrays.asList(
        "create", "delete", "list", "info", "connect", "visualize"
    );
    
    public PortTabCompleter(BoatRoutesPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS;
        }
        return new ArrayList<>();
    }
}