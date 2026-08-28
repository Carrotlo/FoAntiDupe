package me.foesio.foAntiDupe;

import me.foesio.core.command.FoAdminCommand;
import me.foesio.core.command.FoAdminCommandContext;
import me.foesio.core.command.FoAdminMessages;
import me.foesio.core.command.FoAdminSubcommand;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.reload.FoReloadResult;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FoAntiDupeCommand implements CommandExecutor, TabCompleter {

    private final FoAntiDupe plugin;
    private final FoMessageService messages;
    private final AlertService alerts;
    private final EditorManager editor;
    private final FoAdminCommand adminCommand;

    public FoAntiDupeCommand(FoAntiDupe plugin, FoMessageService messages, AlertService alerts, EditorManager editor) {
        this.plugin = plugin;
        this.messages = messages;
        this.alerts = alerts;
        this.editor = editor;
        this.adminCommand = createAdminCommand();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        UUID inventoryViewTarget = args.length == 0 ? null : parseUuid(args[0]);
        if (inventoryViewTarget != null) {
            handleInventoryView(sender, inventoryViewTarget);
            return true;
        }

        return adminCommand.onCommand(sender, command, label, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return adminCommand.onTabComplete(sender, command, alias, args);
    }

    private void handleInventoryView(CommandSender sender, UUID targetId) {
        if (!alerts.canViewFlags(sender)) {
            messages.send(sender, "commands.no-permission", "{prefix}{bad}No permission{muted}.");
            plugin.adminSounds().updateError(sender);
            return;
        }
        alerts.openInventoryView(sender, targetId);
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private FoAdminCommand createAdminCommand() {
        return FoAdminCommand.builder(plugin, messages)
                .commandName("foantidupe")
                .permission("foantidupe.admin")
                .versionCommand(false)
                .adminSounds(plugin.adminSounds())
                .adminMessages(FoAdminMessages.builder()
                        .generalNoPermission("commands.no-permission", "{prefix}{bad}No permission{muted}.")
                        .generalPlayerOnly("commands.player-only", "{prefix}{bad}Only players can use this{muted}.")
                        .usage("commands.usage", "{prefix}Use {theme}/foantidupe <version|reload|editor>{muted}.")
                        .commandFailed("commands.command-failed", "{prefix}{bad}Command failed: {white}{error}{muted}.")
                        .reloadSuccess("commands.reloaded", "{prefix}{good}Reloaded {theme}config.yml{muted}, {theme}flag-rules.yml{muted}, and {theme}messages.yml{muted}.")
                        .reloadFailed("commands.reload-failed", "{prefix}{bad}Reload failed at {theme}{step}{muted}: {white}{error}{muted}.")
                        .build())
                .addSubcommand(FoAdminSubcommand.builder("version", this::handleVersion).usage("version").build())
                .addSubcommand(FoAdminSubcommand.builder("reload", this::handleReload).usage("reload").build())
                .addSubcommand(FoAdminSubcommand.builder("editor", this::handleEditor).usage("editor").playerOnly().build())
                .build();
    }

    private boolean handleVersion(FoAdminCommandContext context) {
        messages.send(context.sender(), "commands.version",
                "{prefix}Author: {theme}{author}{muted} Version: {theme}{version}{muted}.",
                Map.of(
                        "author", "Carrotio",
                        "version", plugin.getDescription().getVersion()
                ));
        return true;
    }

    private boolean handleReload(FoAdminCommandContext context) {
        sendReloadResult(context.sender(), plugin.reloadFoAntiDupe());
        return true;
    }

    private boolean handleEditor(FoAdminCommandContext context) {
        Player player = context.playerOrNull();
        plugin.pluginLog().info("Editor opened by " + player.getName() + " (" + player.getUniqueId() + ")");
        plugin.editorSounds().open(player);
        editor.openMain(player);
        return true;
    }

    private void sendReloadResult(CommandSender sender, FoReloadResult result) {
        if (result.successful()) {
            if (sender instanceof Player player) {
                plugin.adminSounds().reload(player);
            }
            messages.send(sender, "commands.reloaded", "{prefix}{good}Reloaded {theme}config.yml{muted}, {theme}flag-rules.yml{muted}, and {theme}messages.yml{muted}.");
            return;
        }
        if (sender instanceof Player player) {
            plugin.adminSounds().reloadError(player);
        }
        messages.send(sender, "commands.reload-failed",
                "{prefix}{bad}Reload failed at {theme}{step}{muted}: {white}{error}{muted}.",
                Map.of(
                        "step", result.failedStep(),
                        "error", result.errorMessage()
                ));
    }
}
