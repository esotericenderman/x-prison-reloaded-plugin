package dev.drawethree.ultraprisoncore.tokens.commands;

import com.google.common.collect.ImmutableList;
import dev.drawethree.ultraprisoncore.interfaces.Permissionable;
import dev.drawethree.ultraprisoncore.tokens.managers.CommandManager;
import lombok.Getter;
import org.bukkit.command.CommandSender;

public abstract class TokensCommand implements Permissionable {

	protected static final String PERMISSION_ROOT = "ultraprison.tokens.";

	@Getter
	private final String name;
	protected final CommandManager commandManager;
	@Getter
	private final String[] aliases;

	TokensCommand(CommandManager commandManager, String name, String... aliases) {
		this.commandManager = commandManager;
		this.name = name;
		this.aliases = aliases;
	}

	public abstract boolean execute(CommandSender sender, ImmutableList<String> args);

	public abstract boolean canExecute(CommandSender sender);

	public abstract String getUsage();

	@Override
	public String getRequiredPermission() {
		return PERMISSION_ROOT + this.name;
	}
}
