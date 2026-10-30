package me.foesio.foAntiDupe;

import org.bukkit.inventory.ItemStack;
import me.foesio.foAntiDupe.api.RuleSnapshot;

public record FlagResult(DupeRule rule, RuleSnapshot snapshot, String reason, ItemStack item, int amount) {
}
