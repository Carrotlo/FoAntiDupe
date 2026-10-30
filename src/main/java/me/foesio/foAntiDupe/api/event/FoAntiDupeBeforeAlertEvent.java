package me.foesio.foAntiDupe.api.event;

import me.foesio.foAntiDupe.api.FlagType;
import me.foesio.foAntiDupe.api.RuleSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/** Fired after a rule match passes cooldown, before cooldown is consumed or any alert is sent.
 * Cancellation suppresses this alert only; it does not cancel the underlying item action.
 * The item is representative for inventory totals and the actual dropped stack for drops.
 * Runs on the flagged player's current execution context, including a region thread. */
public final class FoAntiDupeBeforeAlertEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final RuleSnapshot rule;
    private final FlagType type;
    private final String reason;
    private final ItemStack item;
    private final int amount;
    private boolean cancelled;

    public FoAntiDupeBeforeAlertEvent(Player player, RuleSnapshot rule, FlagType type, String reason, ItemStack item, int amount) {
        super(!Bukkit.isPrimaryThread());
        this.player = Objects.requireNonNull(player, "player");
        this.rule = Objects.requireNonNull(rule, "rule");
        this.type = Objects.requireNonNull(type, "type");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.item = item == null ? null : item.clone();
        this.amount = amount;
    }

    public Player getPlayer() { return player; }
    public RuleSnapshot getRule() { return rule; }
    public FlagType getType() { return type; }
    public String getReason() { return reason; }
    public ItemStack getItem() { return item == null ? null : item.clone(); }
    public int getAmount() { return amount; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
