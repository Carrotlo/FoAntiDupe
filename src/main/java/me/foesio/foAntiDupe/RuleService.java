package me.foesio.foAntiDupe;

import me.foesio.foAntiDupe.api.RuleDefinition;
import me.foesio.foAntiDupe.api.RuleSnapshot;
import me.foesio.core.item.FoItemStacks;
import me.foesio.core.material.MaterialTypes;
import me.foesio.core.text.FoText;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class RuleService implements Listener {

    private final ConfigStore configStore;
    private final Map<String, DupeRule> rules = new LinkedHashMap<>();
    private final Map<String, RuntimeRule> runtimeRules = new LinkedHashMap<>();
    private volatile RuleIndex index = RuleIndex.empty();

    public RuleService(ConfigStore configStore) {
        this.configStore = configStore;
    }

    public synchronized void reload() {
        Map<String, DupeRule> loaded = new LinkedHashMap<>();
        ConfigurationSection section = configStore.flagRules().getConfigurationSection("rules");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection ruleSection = section.getConfigurationSection(id);
                if (ruleSection != null) {
                    loaded.put(id, DupeRule.load(id, ruleSection));
                }
            }
        }
        rules.clear();
        rules.putAll(loaded);
        rebuildIndex();
    }

    public Collection<DupeRule> rules() {
        return List.copyOf(rules.values());
    }

    public List<DupeRule> sortedRules() {
        return rules.values().stream()
                .sorted(Comparator.comparing(DupeRule::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public DupeRule get(String id) {
        return rules.get(id);
    }

    public List<FlagResult> scanInventory(ItemStack[] contents) {
        RuleIndex current = index;
        Map<DupeRule, Integer> amounts = countMatchingItems(contents, current.inventoryRulesByMaterial());
        List<FlagResult> results = new ArrayList<>();
        for (DupeRule rule : current.effectiveRules()) {
            if (!rule.enabled() || !rule.checkInventory()) {
                continue;
            }
            int amount = amounts.getOrDefault(rule, 0);
            if (amount >= rule.flagAtAmount()) {
                String reason = "had " + amount + "x " + rule.materialLabel() + " (flags at " + rule.flagAtAmount() + ")";
                results.add(new FlagResult(rule, current.snapshot(rule.id()), reason, rule.iconItem(), amount));
            }
        }
        return results;
    }

    public List<FlagResult> scanDrop(ItemStack dropped) {
        RuleIndex current = index;
        Map<DupeRule, Integer> amounts = countMatchingItems(new ItemStack[]{dropped}, current.dropRulesByMaterial());
        List<FlagResult> results = new ArrayList<>();
        for (DupeRule rule : current.effectiveRules()) {
            if (!rule.enabled() || !rule.checkDrops()) {
                continue;
            }
            int amount = amounts.getOrDefault(rule, 0);
            if (amount >= rule.flagAtDropAmount()) {
                String reason = "dropped " + amount + "x " + rule.materialLabel() + " (flags at " + rule.flagAtDropAmount() + ")";
                results.add(new FlagResult(rule, current.snapshot(rule.id()), reason, FoItemStacks.cloneItem(dropped), amount));
            }
        }
        return results;
    }

    public void saveRule(DupeRule rule) {
        rule.write(configStore.flagRules(), "rules." + rule.id());
        configStore.saveFlagRules();
        reload();
    }

    public void deleteRule(String id) {
        configStore.flagRules().set("rules." + id, null);
        configStore.saveFlagRules();
        reload();
    }

    public String createRuleFromItem(ItemStack source) {
        ItemStack item = FoItemStacks.cloneItem(source);
        item.setAmount(1);
        String baseId = uniqueBaseId(item);
        String id = baseId;
        int index = 2;
        while (rules.containsKey(id) || runtimeRules.containsKey(id)) {
            id = baseId + "_" + index;
            index++;
        }

        DupeRule rule = new DupeRule(id);
        rule.material(item.getType());
        rule.item(item);
        rule.exactItem(true);
        rule.strictMeta(true);
        rule.flagAtAmount(Math.max(1, source.getAmount()));
        rule.flagAtDropAmount(Math.max(1, source.getAmount()));
        rule.displayName(displayNameFor(item));
        saveRule(rule);
        return id;
    }

    public List<RuleSnapshot> effectiveRuleSnapshots() {
        return index.snapshots();
    }

    public Optional<RuleSnapshot> effectiveRuleSnapshot(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(index.snapshotsById().get(id));
    }

    public synchronized boolean registerRule(Plugin owner, RuleDefinition definition) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(definition, "definition");
        if (!owner.isEnabled() || !definition.material().isItem() || definition.material().isAir()
                || !definition.id().matches("[a-z0-9][a-z0-9_-]{0,63}")
                || rules.containsKey(definition.id()) || runtimeRules.containsKey(definition.id())) {
            return false;
        }
        runtimeRules.put(definition.id(), new RuntimeRule(owner, definition));
        rebuildIndex();
        return true;
    }

    public synchronized boolean unregisterRule(Plugin owner, String id) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(id, "id");
        RuntimeRule registered = runtimeRules.get(id);
        if (registered == null || registered.owner() != owner) {
            return false;
        }
        runtimeRules.remove(id);
        rebuildIndex();
        return true;
    }

    @EventHandler
    public synchronized void onPluginDisable(PluginDisableEvent event) {
        if (runtimeRules.values().removeIf(rule -> rule.owner() == event.getPlugin())) {
            rebuildIndex();
        }
    }

    private void rebuildIndex() {
        Map<Material, List<DupeRule>> inventory = new EnumMap<>(Material.class);
        Map<Material, List<DupeRule>> drops = new EnumMap<>(Material.class);
        List<DupeRule> effective = new ArrayList<>();
        List<RuleSnapshot> snapshots = new ArrayList<>();
        for (DupeRule configured : rules.values()) {
            RuleDefinition definition = definitionOf(configured);
            DupeRule copy = internalRule(definition);
            effective.add(copy);
            snapshots.add(new RuleSnapshot(definition, null));
            indexRule(copy, inventory, drops);
        }
        for (RuntimeRule runtime : runtimeRules.values()) {
            if (rules.containsKey(runtime.definition().id())) {
                continue; // Server owner rules win if a collision appears on reload.
            }
            DupeRule copy = internalRule(runtime.definition());
            effective.add(copy);
            snapshots.add(new RuleSnapshot(runtime.definition(), runtime.owner().getName()));
            indexRule(copy, inventory, drops);
        }
        Map<String, RuleSnapshot> byId = new LinkedHashMap<>();
        snapshots.forEach(snapshot -> byId.put(snapshot.id(), snapshot));
        index = new RuleIndex(List.copyOf(effective), List.copyOf(snapshots), Map.copyOf(byId),
                immutableIndex(inventory), immutableIndex(drops));
    }

    private static Map<Material, List<DupeRule>> immutableIndex(Map<Material, List<DupeRule>> source) {
        Map<Material, List<DupeRule>> result = new EnumMap<>(Material.class);
        source.forEach((material, values) -> result.put(material, List.copyOf(values)));
        return Map.copyOf(result);
    }

    private static void indexRule(DupeRule rule, Map<Material, List<DupeRule>> inventory, Map<Material, List<DupeRule>> drops) {
        if (!rule.enabled()) {
            return;
        }
        if (rule.checkInventory()) {
            inventory.computeIfAbsent(rule.material(), ignored -> new ArrayList<>()).add(rule);
        }
        if (rule.checkDrops()) {
            drops.computeIfAbsent(rule.material(), ignored -> new ArrayList<>()).add(rule);
        }
    }

    private static RuleDefinition definitionOf(DupeRule rule) {
        return RuleDefinition.builder(rule.id(), rule.material())
                .displayName(rule.displayName() == null || rule.displayName().isBlank() ? rule.id() : rule.displayName())
                .enabled(rule.enabled())
                .inventoryThreshold(rule.flagAtAmount())
                .dropThreshold(rule.flagAtDropAmount())
                .checkInventory(rule.checkInventory())
                .checkDrops(rule.checkDrops())
                .exactItem(rule.exactItem() && rule.item() != null)
                .strictMeta(rule.strictMeta())
                .matchName(rule.matchName())
                .matchLore(rule.matchLore())
                .matchEnchants(rule.matchEnchants())
                .matchCustomModelData(rule.matchCustomModelData())
                .item(rule.item())
                .build();
    }

    private static DupeRule internalRule(RuleDefinition definition) {
        DupeRule rule = new DupeRule(definition.id());
        rule.displayName(definition.displayName());
        rule.enabled(definition.enabled());
        rule.material(definition.material());
        rule.flagAtAmount(definition.inventoryThreshold());
        rule.flagAtDropAmount(definition.dropThreshold());
        rule.checkInventory(definition.checkInventory());
        rule.checkDrops(definition.checkDrops());
        rule.exactItem(definition.exactItem());
        rule.strictMeta(definition.strictMeta());
        rule.matchName(definition.matchName());
        rule.matchLore(definition.matchLore());
        rule.matchEnchants(definition.matchEnchants());
        rule.matchCustomModelData(definition.matchCustomModelData());
        rule.item(definition.item());
        return rule;
    }

    private record RuntimeRule(Plugin owner, RuleDefinition definition) {}

    private record RuleIndex(List<DupeRule> effectiveRules, List<RuleSnapshot> snapshots,
                             Map<String, RuleSnapshot> snapshotsById,
                             Map<Material, List<DupeRule>> inventoryRulesByMaterial,
                             Map<Material, List<DupeRule>> dropRulesByMaterial) {
        private RuleSnapshot snapshot(String id) {
            return Objects.requireNonNull(snapshotsById.get(id));
        }
        private static RuleIndex empty() {
            return new RuleIndex(List.of(), List.of(), Map.of(), Map.of(), Map.of());
        }
    }

    private Map<DupeRule, Integer> countMatchingItems(ItemStack[] contents, Map<Material, List<DupeRule>> rulesByMaterial) {
        Map<DupeRule, Integer> amounts = new LinkedHashMap<>();
        DupeRule.visitInventoryItems(contents, (item, amount) -> {
            List<DupeRule> candidates = rulesByMaterial.get(item.getType());
            if (candidates == null) {
                return;
            }
            for (DupeRule rule : candidates) {
                if (rule.matches(item)) {
                    amounts.merge(rule, amount, RuleService::capAmount);
                }
            }
        });
        return amounts;
    }

    private static int capAmount(int current, int additional) {
        long total = (long) current + additional;
        return total >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    private String uniqueBaseId(ItemStack item) {
        String label = displayNameFor(item).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return label.isBlank() ? item.getType().name().toLowerCase(Locale.ROOT) : label;
    }

    private String displayNameFor(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String stripped = FoText.stripColor(meta.getDisplayName());
            if (stripped != null && !stripped.isBlank()) {
                return stripped;
            }
        }
        Material material = item.getType();
        return MaterialTypes.displayName(material);
    }
}
