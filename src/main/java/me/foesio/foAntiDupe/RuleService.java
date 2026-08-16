package me.foesio.foAntiDupe;

import me.foesio.core.item.FoItemStacks;
import me.foesio.core.material.MaterialTypes;
import me.foesio.core.text.FoText;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RuleService {

    private final ConfigStore configStore;
    private final Map<String, DupeRule> rules = new LinkedHashMap<>();
    private final Map<Material, List<DupeRule>> inventoryRulesByMaterial = new EnumMap<>(Material.class);
    private final Map<Material, List<DupeRule>> dropRulesByMaterial = new EnumMap<>(Material.class);

    public RuleService(ConfigStore configStore) {
        this.configStore = configStore;
    }

    public void reload() {
        rules.clear();
        inventoryRulesByMaterial.clear();
        dropRulesByMaterial.clear();
        ConfigurationSection section = configStore.flagRules().getConfigurationSection("rules");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection ruleSection = section.getConfigurationSection(id);
            if (ruleSection != null) {
                DupeRule rule = DupeRule.load(id, ruleSection);
                rules.put(id, rule);
                indexRule(rule);
            }
        }
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
        Map<DupeRule, Integer> amounts = countMatchingItems(contents, inventoryRulesByMaterial);
        List<FlagResult> results = new ArrayList<>();
        for (DupeRule rule : rules.values()) {
            if (!rule.enabled() || !rule.checkInventory()) {
                continue;
            }
            int amount = amounts.getOrDefault(rule, 0);
            if (amount >= rule.flagAtAmount()) {
                String reason = "had " + amount + "x " + rule.materialLabel() + " (flags at " + rule.flagAtAmount() + ")";
                results.add(new FlagResult(rule, reason, rule.iconItem(), amount));
            }
        }
        return results;
    }

    public List<FlagResult> scanDrop(ItemStack dropped) {
        Map<DupeRule, Integer> amounts = countMatchingItems(new ItemStack[]{dropped}, dropRulesByMaterial);
        List<FlagResult> results = new ArrayList<>();
        for (DupeRule rule : rules.values()) {
            if (!rule.enabled() || !rule.checkDrops()) {
                continue;
            }
            int amount = amounts.getOrDefault(rule, 0);
            if (amount >= rule.flagAtDropAmount()) {
                String reason = "dropped " + amount + "x " + rule.materialLabel() + " (flags at " + rule.flagAtDropAmount() + ")";
                results.add(new FlagResult(rule, reason, FoItemStacks.cloneItem(dropped), amount));
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
        while (rules.containsKey(id)) {
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

    private void indexRule(DupeRule rule) {
        if (!rule.enabled()) {
            return;
        }
        if (rule.checkInventory()) {
            inventoryRulesByMaterial.computeIfAbsent(rule.material(), ignored -> new ArrayList<>()).add(rule);
        }
        if (rule.checkDrops()) {
            dropRulesByMaterial.computeIfAbsent(rule.material(), ignored -> new ArrayList<>()).add(rule);
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
