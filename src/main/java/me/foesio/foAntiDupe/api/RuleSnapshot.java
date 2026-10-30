package me.foesio.foAntiDupe.api;

import java.util.Objects;

/** Effective rule and its origin. A null owner means flag-rules.yml. */
public record RuleSnapshot(RuleDefinition definition, String ownerPlugin) {
    public RuleSnapshot {
        Objects.requireNonNull(definition, "definition");
    }

    public String id() {
        return definition.id();
    }
}
