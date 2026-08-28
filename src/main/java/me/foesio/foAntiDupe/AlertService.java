package me.foesio.foAntiDupe;

import me.foesio.core.message.FoMessageService;
import me.foesio.core.logging.FoFileLogger;
import me.foesio.core.sound.FoGuiSounds;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AlertService implements Listener {

    private final FoAntiDupe plugin;
    private final ConfigStore configStore;
    private final FoMessageService messages;
    private final FoFileLogger pluginLog;
    private final FoGuiSounds guiSounds;
    private final Map<String, Long> lastAlerts = new HashMap<>();
    private final Set<UUID> readOnlyViewers = new HashSet<>();

    public AlertService(FoAntiDupe plugin, ConfigStore configStore, FoMessageService messages, FoFileLogger pluginLog, FoGuiSounds guiSounds) {
        this.plugin = plugin;
        this.configStore = configStore;
        this.messages = messages;
        this.pluginLog = pluginLog;
        this.guiSounds = guiSounds;
    }

    public void flag(Player flagged, FlagResult result) {
        String key = flagged.getUniqueId() + ":" + result.rule().id();
        long now = System.currentTimeMillis();
        long last = lastAlerts.getOrDefault(key, 0L);
        if (now - last < configStore.secondsAsMillis("settings.alert-cooldown-seconds", 5)) {
            return;
        }
        lastAlerts.put(key, now);

        Map<String, String> placeholders = Map.of(
                "player", flagged.getName(),
                "rule", result.rule().displayName(),
                "reason", result.reason(),
                "amount", String.valueOf(result.amount())
        );
        String alert = messages.render("flags.alert",
                "{prefix}{bad}Flag {theme}{player}{muted} matched {theme}{rule}{muted}: {white}{reason}{muted}.",
                placeholders);
        String action = messages.render("flags.view-action", " {theme}[click here to view their inventory]", placeholders);
        BaseComponent[] components = appendClickableAction(alert, action, "/foantidupe " + flagged.getUniqueId());

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (canViewFlags(staff)) {
                staff.spigot().sendMessage(components);
                plugin.sounds().play(staff, "alert.flagged");
            }
        }
        if (configStore.bool("settings.notify-console", true)) {
            Bukkit.getConsoleSender().sendMessage(alert);
        }
        pluginLog.info("Flagged " + flagged.getName()
                + " (" + flagged.getUniqueId() + ")"
                + " rule=" + result.rule().id()
                + " amount=" + result.amount()
                + " reason=\"" + result.reason() + "\"");
    }

    public void openInventoryView(CommandSender sender, UUID targetId) {
        if (!canViewFlags(sender)) {
            messages.send(sender, "commands.no-permission", "{prefix}{bad}No permission{muted}.");
            pluginLog.info("Inventory view denied for " + sender.getName() + " target=" + targetId + " reason=permission");
            return;
        }
        if (!(sender instanceof Player viewer)) {
            messages.send(sender, "flags.view-player-only", "{prefix}{bad}Only players can open live inventories{muted}.");
            pluginLog.info("Inventory view denied for non-player sender " + sender.getName() + " target=" + targetId);
            return;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            messages.send(viewer, "flags.view-offline", "{prefix}{bad}Player is offline{muted}; live inventory is unavailable.");
            pluginLog.info("Inventory view failed viewer=" + viewer.getName() + " target=" + targetId + " reason=offline");
            return;
        }
        boolean readOnly = !canEditViewedInventory(viewer);
        if (!readOnly) {
            readOnlyViewers.remove(viewer.getUniqueId());
        }
        viewer.openInventory(target.getInventory());
        guiSounds.open(viewer);
        if (readOnly) {
            readOnlyViewers.add(viewer.getUniqueId());
        }
        messages.send(viewer,
                readOnly ? "flags.view-opened-read-only" : "flags.view-opened",
                readOnly
                        ? "{prefix}{good}Opened {theme}{player}{muted}'s live inventory {white}(read-only){muted}."
                        : "{prefix}{good}Opened {theme}{player}{muted}'s live inventory.",
                Map.of("player", target.getName()));
        pluginLog.info("Inventory view opened viewer=" + viewer.getName()
                + " target=" + target.getName()
                + " (" + target.getUniqueId() + ")"
                + " readOnly=" + readOnly);
    }

    public boolean canViewFlags(CommandSender sender) {
        return sender.hasPermission("foantidupe.admin")
                || sender.hasPermission("foantidupe.show")
                || sender.hasPermission("foantidupe.showonly");
    }

    private boolean canEditViewedInventory(CommandSender sender) {
        return sender.hasPermission("foantidupe.admin")
                || sender.hasPermission("foantidupe.show");
    }

    @EventHandler
    public void onReadOnlyInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !readOnlyViewers.contains(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onReadOnlyInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !readOnlyViewers.contains(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onReadOnlyInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        readOnlyViewers.remove(player.getUniqueId());
    }

    private BaseComponent[] appendClickableAction(String alert, String action, String command) {
        BaseComponent[] alertParts = TextComponent.fromLegacyText(alert);
        BaseComponent[] actionParts = TextComponent.fromLegacyText(action);
        ClickEvent clickEvent = new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
        for (BaseComponent component : actionParts) {
            component.setClickEvent(clickEvent);
        }

        BaseComponent[] combined = new BaseComponent[alertParts.length + actionParts.length];
        System.arraycopy(alertParts, 0, combined, 0, alertParts.length);
        System.arraycopy(actionParts, 0, combined, alertParts.length, actionParts.length);
        return combined;
    }
}
