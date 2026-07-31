package me.foesio.foAntiDupe;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DetectionListener implements Listener {

    private final FoAntiDupe plugin;
    private final ConfigStore configStore;
    private final RuleService rules;
    private final AlertService alerts;
    private final Set<UUID> queuedScans = ConcurrentHashMap.newKeySet();

    public DetectionListener(FoAntiDupe plugin, ConfigStore configStore, RuleService rules, AlertService alerts) {
        this.plugin = plugin;
        this.configStore = configStore;
        this.rules = rules;
        this.alerts = alerts;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (configStore.bool("checks.inventory-click", true) && event.getWhoClicked() instanceof Player player) {
            queueInventoryScan(player);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (configStore.bool("checks.inventory-drag", true) && event.getWhoClicked() instanceof Player player) {
            queueInventoryScan(player);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPickup(EntityPickupItemEvent event) {
        if (configStore.bool("checks.item-pickup", true) && event.getEntity() instanceof Player player) {
            queueInventoryScan(player);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (isBypassed(player)) {
            return;
        }
        if (configStore.bool("checks.item-drop", true)) {
            for (FlagResult result : rules.scanDrop(event.getItemDrop().getItemStack())) {
                alerts.flag(player, result);
            }
            queueInventoryScan(player);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onCraft(CraftItemEvent event) {
        if (configStore.bool("checks.craft", true) && event.getWhoClicked() instanceof Player player) {
            queueInventoryScan(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (configStore.bool("checks.player-join", true)) {
            queueInventoryScan(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        queuedScans.remove(event.getPlayer().getUniqueId());
    }

    private void queueInventoryScan(Player player) {
        if (isBypassed(player) || !queuedScans.add(player.getUniqueId())) {
            return;
        }
        long delay = Math.max(1L, configStore.integer("settings.scan-delay-ticks", 1));
        plugin.core().scheduler().runLaterForPlayer(player, () -> {
            queuedScans.remove(player.getUniqueId());
            if (!player.isOnline() || isBypassed(player)) {
                return;
            }
            for (FlagResult result : rules.scanInventory(player.getInventory().getContents())) {
                alerts.flag(player, result);
            }
        }, delay);
    }

    private boolean isBypassed(Player player) {
        return player.hasPermission("foantidupe.bypass");
    }
}
