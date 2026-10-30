package me.foesio.foAntiDupe;

import me.foesio.foAntiDupe.api.FlagType;
import me.foesio.foAntiDupe.api.event.FoAntiDupeBeforeAlertEvent;
import me.foesio.foAntiDupe.api.event.FoAntiDupeFlagEvent;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.logging.FoFileLogger;
import me.foesio.core.sound.FoGuiSounds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AlertService implements Listener {

    private final FoAntiDupe plugin;
    private final ConfigStore configStore;
    private final FoMessageService messages;
    private final FoFileLogger pluginLog;
    private final FoGuiSounds guiSounds;
    private final Map<String, Long> lastAlerts = new ConcurrentHashMap<>();
    private final Set<String> inFlightAlerts = ConcurrentHashMap.newKeySet();
    private final Set<UUID> readOnlyViewers = ConcurrentHashMap.newKeySet();

    public AlertService(FoAntiDupe plugin, ConfigStore configStore, FoMessageService messages, FoFileLogger pluginLog, FoGuiSounds guiSounds) {
        this.plugin = plugin;
        this.configStore = configStore;
        this.messages = messages;
        this.pluginLog = pluginLog;
        this.guiSounds = guiSounds;
    }

    public void flag(Player flagged, FlagResult result, FlagType type) {
        String key = flagged.getUniqueId() + ":" + result.rule().id();
        if (!inFlightAlerts.add(key)) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            long last = lastAlerts.getOrDefault(key, 0L);
            if (now - last < configStore.secondsAsMillis("settings.alert-cooldown-seconds", 5)) {
                return;
            }
            FoAntiDupeBeforeAlertEvent before = new FoAntiDupeBeforeAlertEvent(
                    flagged, result.snapshot(), type, result.reason(), result.item(), result.amount());
            Bukkit.getPluginManager().callEvent(before);
            if (before.isCancelled() || !plugin.isEnabled()) {
                return;
            }
            lastAlerts.put(key, System.currentTimeMillis());

            sendAlert(flagged, result, type);
        } finally {
            inFlightAlerts.remove(key);
        }
    }

    private void sendAlert(Player flagged, FlagResult result, FlagType type) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", flagged.getName());
        placeholders.put("rule", result.rule().displayName());
        placeholders.put("reason", result.reason());
        placeholders.put("amount", String.valueOf(result.amount()));
        Material matchedMaterial = result.item() == null || result.item().getType().isAir()
                ? result.rule().material()
                : result.item().getType();
        Map<String, String> materialPlaceholders = FoMessageService.materialPlaceholders(matchedMaterial);
        placeholders.put("item", materialPlaceholders.get("material"));
        placeholders.put("item_sprite", materialPlaceholders.get("material_sprite"));

        String alertFallback = "{prefix}{bad}Flag {theme}{player}{muted} matched "
                + "{white}{item_sprite}{item}{muted} {theme}{rule}{muted}: "
                + "{white}{reason}{muted}.";
        String actionFallback = " {theme}[click here to view their inventory]";
        String command = "/foantidupe " + flagged.getUniqueId();

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (canViewFlags(staff)) {
                Component alert = messages.renderComponent(staff, "flags.alert", alertFallback, placeholders);
                Component action = messages.renderComponent(staff, "flags.view-action", actionFallback, placeholders);
                staff.sendMessage(alert.append(withClickEvent(action, command)));
                plugin.sounds().play(staff, "alert.flagged");
            }
        }
        if (configStore.bool("settings.notify-console", true)) {
            Bukkit.getConsoleSender().sendMessage(messages.render("flags.alert", alertFallback, placeholders));
        }
        pluginLog.info("Flagged " + flagged.getName()
                + " (" + flagged.getUniqueId() + ")"
                + " rule=" + result.rule().id()
                + " amount=" + result.amount()
                + " reason=\"" + result.reason() + "\"");
        Bukkit.getPluginManager().callEvent(new FoAntiDupeFlagEvent(
                flagged, result.snapshot(), type, result.reason(), result.item(), result.amount()));
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

    private Component withClickEvent(Component component, String command) {
        ClickEvent clickEvent = ClickEvent.runCommand(command);
        return applyClickEvent(component, clickEvent);
    }

    private Component applyClickEvent(Component component, ClickEvent clickEvent) {
        return component
                .children(component.children().stream()
                        .map(child -> applyClickEvent(child, clickEvent))
                        .toList())
                .clickEvent(clickEvent);
    }
}
