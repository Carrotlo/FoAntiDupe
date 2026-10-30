package me.foesio.foAntiDupe.api;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;

/** Obtain through Bukkit's ServicesManager after FoAntiDupe has enabled. */
public interface FoAntiDupeApi {

    /** Queue the normal delayed inventory scan on the player's owning thread.
     * Returns false when disabled, offline, bypassed, or already queued. */
    boolean requestInventoryScan(Player player);

    /** Current effective configured and runtime rules. Never exposes mutable internal rules. */
    List<RuleSnapshot> rules();

    /** Current effective rule, if present. */
    Optional<RuleSnapshot> rule(String id);

    /** Register one runtime rule for an enabled owner. Returns false on an ID collision
     * or an invalid runtime ID.
     * Runtime rules are not written to flag-rules.yml and survive FoAntiDupe reloads.
     * Call on the server's global/plugin lifecycle thread. */
    boolean registerRule(Plugin owner, RuleDefinition definition);

    /** Remove a runtime rule only when it belongs to the supplied owner. */
    boolean unregisterRule(Plugin owner, String id);
}
