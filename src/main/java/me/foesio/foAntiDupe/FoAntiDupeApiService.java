package me.foesio.foAntiDupe;

import me.foesio.foAntiDupe.api.FoAntiDupeApi;
import me.foesio.foAntiDupe.api.RuleDefinition;
import me.foesio.foAntiDupe.api.RuleSnapshot;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class FoAntiDupeApiService implements FoAntiDupeApi {
    private final FoAntiDupe plugin;
    private final DetectionListener detection;
    private final RuleService rules;

    FoAntiDupeApiService(FoAntiDupe plugin, DetectionListener detection, RuleService rules) {
        this.plugin = plugin;
        this.detection = detection;
        this.rules = rules;
    }

    @Override
    public boolean requestInventoryScan(Player player) {
        return detection.requestInventoryScan(Objects.requireNonNull(player, "player"));
    }

    @Override
    public List<RuleSnapshot> rules() {
        return plugin.isEnabled() ? rules.effectiveRuleSnapshots() : List.of();
    }

    @Override
    public Optional<RuleSnapshot> rule(String id) {
        Objects.requireNonNull(id, "id");
        return plugin.isEnabled() ? rules.effectiveRuleSnapshot(id) : Optional.empty();
    }

    @Override
    public boolean registerRule(Plugin owner, RuleDefinition definition) {
        return plugin.isEnabled() && rules.registerRule(owner, definition);
    }

    @Override
    public boolean unregisterRule(Plugin owner, String id) {
        return plugin.isEnabled() && rules.unregisterRule(owner, id);
    }
}
