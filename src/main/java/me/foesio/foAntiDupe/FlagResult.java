package me.foesio.foAntiDupe;

import org.bukkit.inventory.ItemStack;

public record FlagResult(DupeRule rule, String reason, ItemStack item, int amount) {
}
