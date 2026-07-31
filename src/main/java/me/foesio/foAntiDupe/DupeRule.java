package me.foesio.foAntiDupe;

import me.foesio.core.item.FoItemStacks;
import me.foesio.core.material.MaterialTypes;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class DupeRule {

    private static final int MAX_CONTAINER_DEPTH = 6;

    private final String id;
    private String displayName;
    private boolean enabled;
    private Material material;
    private int flagAtAmount;
    private int flagAtDropAmount;
    private boolean checkInventory;
    private boolean checkDrops;
    private boolean exactItem;
    private boolean strictMeta;
    private boolean matchName;
    private boolean matchLore;
    private boolean matchEnchants;
    private boolean matchCustomModelData;
    private ItemStack item;

    public DupeRule(String id) {
        this.id = id;
        this.displayName = humanize(id);
        this.enabled = true;
        this.material = Material.STONE;
        this.flagAtAmount = 64;
        this.flagAtDropAmount = 64;
        this.checkInventory = true;
        this.checkDrops = true;
    }

    public static DupeRule load(String id, ConfigurationSection section) {
        DupeRule rule = new DupeRule(id);
        rule.displayName = section.getString("display-name", humanize(id));
        rule.enabled = section.getBoolean("enabled", true);
        Material material = MaterialTypes.match(section.getString("material", Material.STONE.name()));
        rule.material = material == null ? Material.STONE : material;
        rule.flagAtAmount = Math.max(1, section.getInt("flag-at-amount", 64));
        rule.flagAtDropAmount = Math.max(1, section.getInt("flag-at-drop-amount", rule.flagAtAmount));
        rule.checkInventory = section.getBoolean("check-inventory", true);
        rule.checkDrops = section.getBoolean("check-drops", true);
        rule.exactItem = section.getBoolean("exact-item", false);
        rule.strictMeta = section.getBoolean("strict-meta", false);
        rule.matchName = section.getBoolean("match.name", false);
        rule.matchLore = section.getBoolean("match.lore", false);
        rule.matchEnchants = section.getBoolean("match.enchants", false);
        rule.matchCustomModelData = section.getBoolean("match.custom-model-data", false);
        rule.item = section.getItemStack("item");
        if (rule.item != null) {
            rule.material = rule.item.getType();
        }
        return rule;
    }

    public void write(FileConfiguration config, String basePath) {
        config.set(basePath + ".display-name", displayName);
        config.set(basePath + ".enabled", enabled);
        config.set(basePath + ".material", material.name());
        config.set(basePath + ".flag-at-amount", flagAtAmount);
        config.set(basePath + ".flag-at-drop-amount", flagAtDropAmount);
        config.set(basePath + ".check-inventory", checkInventory);
        config.set(basePath + ".check-drops", checkDrops);
        config.set(basePath + ".exact-item", exactItem);
        config.set(basePath + ".strict-meta", strictMeta);
        config.set(basePath + ".match.name", matchName);
        config.set(basePath + ".match.lore", matchLore);
        config.set(basePath + ".match.enchants", matchEnchants);
        config.set(basePath + ".match.custom-model-data", matchCustomModelData);
        config.set(basePath + ".item", item);
    }

    public boolean matches(ItemStack candidate) {
        if (candidate == null || candidate.getType().isAir()) {
            return false;
        }
        if (!exactItem || item == null) {
            return candidate.getType() == material;
        }
        if (candidate.getType() != item.getType()) {
            return false;
        }
        if (strictMeta) {
            return candidate.isSimilar(item);
        }
        return matchesSelectedMeta(candidate, item);
    }

    public int count(ItemStack[] contents) {
        long amount = 0L;
        for (ItemStack content : contents) {
            amount += count(content);
        }
        return capAmount(amount);
    }

    public int count(ItemStack itemStack) {
        return capAmount(countNested(itemStack, 0));
    }

    private long countNested(ItemStack itemStack, int depth) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return 0L;
        }

        long amount = matches(itemStack) ? itemStack.getAmount() : 0L;
        if (depth >= MAX_CONTAINER_DEPTH) {
            return amount;
        }

        int containerMultiplier = Math.max(1, itemStack.getAmount());
        for (ItemStack nestedItem : nestedItems(itemStack)) {
            amount += containerMultiplier * countNested(nestedItem, depth + 1);
            if (amount >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }
        return amount;
    }

    private List<ItemStack> nestedItems(ItemStack itemStack) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta instanceof BundleMeta bundleMeta && bundleMeta.hasItems()) {
            return bundleMeta.getItems();
        }
        if (meta instanceof BlockStateMeta blockStateMeta && blockStateMeta.hasBlockState()) {
            BlockState blockState = blockStateMeta.getBlockState();
            if (blockState instanceof ShulkerBox shulkerBox) {
                return new ArrayList<>(Arrays.asList(shulkerBox.getSnapshotInventory().getContents()));
            }
        }
        return List.of();
    }

    private int capAmount(long amount) {
        return amount >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
    }

    public ItemStack iconItem() {
        ItemStack icon = item == null ? new ItemStack(material) : FoItemStacks.cloneItem(item);
        icon.setAmount(1);
        return icon;
    }

    public String materialLabel() {
        return MaterialTypes.displayName(material);
    }

    private boolean matchesSelectedMeta(ItemStack candidate, ItemStack expected) {
        ItemMeta candidateMeta = candidate.getItemMeta();
        ItemMeta expectedMeta = expected.getItemMeta();
        if (matchName && !Objects.equals(displayName(candidateMeta), displayName(expectedMeta))) {
            return false;
        }
        if (matchLore && !Objects.equals(candidateMeta == null ? null : candidateMeta.getLore(), expectedMeta == null ? null : expectedMeta.getLore())) {
            return false;
        }
        if (matchEnchants && (!Objects.equals(candidate.getEnchantments(), expected.getEnchantments())
                || !Objects.equals(storedEnchantments(candidateMeta), storedEnchantments(expectedMeta)))) {
            return false;
        }
        if (matchCustomModelData && !Objects.equals(customModelData(candidateMeta), customModelData(expectedMeta))) {
            return false;
        }
        return true;
    }

    private static String displayName(ItemMeta meta) {
        return meta != null && meta.hasDisplayName() ? meta.getDisplayName() : null;
    }

    private static Map<Enchantment, Integer> storedEnchantments(ItemMeta meta) {
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            return storageMeta.getStoredEnchants();
        }
        return Map.of();
    }

    @SuppressWarnings("deprecation")
    private static Integer customModelData(ItemMeta meta) {
        return meta != null && meta.hasCustomModelData() ? meta.getCustomModelData() : null;
    }

    public static String humanize(String value) {
        String[] parts = value.toLowerCase(Locale.ROOT).split("[_\\- ]+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.isEmpty() ? value : builder.toString();
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public void displayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Material material() {
        return material;
    }

    public void material(Material material) {
        this.material = material;
    }

    public int flagAtAmount() {
        return flagAtAmount;
    }

    public void flagAtAmount(int flagAtAmount) {
        this.flagAtAmount = Math.max(1, flagAtAmount);
    }

    public int flagAtDropAmount() {
        return flagAtDropAmount;
    }

    public void flagAtDropAmount(int flagAtDropAmount) {
        this.flagAtDropAmount = Math.max(1, flagAtDropAmount);
    }

    public boolean checkInventory() {
        return checkInventory;
    }

    public void checkInventory(boolean checkInventory) {
        this.checkInventory = checkInventory;
    }

    public boolean checkDrops() {
        return checkDrops;
    }

    public void checkDrops(boolean checkDrops) {
        this.checkDrops = checkDrops;
    }

    public boolean exactItem() {
        return exactItem;
    }

    public void exactItem(boolean exactItem) {
        this.exactItem = exactItem;
    }

    public boolean strictMeta() {
        return strictMeta;
    }

    public void strictMeta(boolean strictMeta) {
        this.strictMeta = strictMeta;
    }

    public boolean matchName() {
        return matchName;
    }

    public void matchName(boolean matchName) {
        this.matchName = matchName;
    }

    public boolean matchLore() {
        return matchLore;
    }

    public void matchLore(boolean matchLore) {
        this.matchLore = matchLore;
    }

    public boolean matchEnchants() {
        return matchEnchants;
    }

    public void matchEnchants(boolean matchEnchants) {
        this.matchEnchants = matchEnchants;
    }

    public boolean matchCustomModelData() {
        return matchCustomModelData;
    }

    public void matchCustomModelData(boolean matchCustomModelData) {
        this.matchCustomModelData = matchCustomModelData;
    }

    public ItemStack item() {
        return item == null ? null : FoItemStacks.cloneItem(item);
    }

    public void item(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            this.item = null;
            return;
        }
        ItemStack clone = FoItemStacks.cloneItem(item);
        clone.setAmount(1);
        this.item = clone;
        this.material = clone.getType();
    }
}
