package fr.skynex.worldx.command.sub;

import org.bukkit.command.CommandSender;

public interface WorldXSubCommand {
    String getName();
    String getDescription();
    String getSyntax();
    boolean execute(CommandSender sender, String[] args);
}
