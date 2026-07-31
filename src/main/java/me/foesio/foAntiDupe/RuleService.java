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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RuleService {

    private final ConfigStore configStore;
    private final Map<String, DupeRule> rules = new LinkedHashMap<>();

    public RuleService(ConfigStore configStore) {
        this.configStore = configStore;
    }

    public void reload() {
        rules.clear();
        ConfigurationSection section = configStore.flagRules().getConfigurationSection("rules");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection ruleSection = section.getConfigurationSection(id);
            if (ruleSection != null) {
                rules.put(id, DupeRule.load(id, ruleSection));
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
        List<FlagResult> results = new ArrayList<>();
        for (DupeRule rule : rules.values()) {
            if (!rule.enabled() || !rule.checkInventory()) {
                continue;
            }
            int amount = rule.count(contents);
            if (amount >= rule.flagAtAmount()) {
                String reason = "had " + amount + "x " + rule.materialLabel() + " (flags at " + rule.flagAtAmount() + ")";
                results.add(new FlagResult(rule, reason, rule.iconItem(), amount));
            }
        }
        return results;
    }

    public List<FlagResult> scanDrop(ItemStack dropped) {
        List<FlagResult> results = new ArrayList<>();
        for (DupeRule rule : rules.values()) {
            if (!rule.enabled() || !rule.checkDrops()) {
                continue;
            }
            int amount = rule.count(dropped);
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
