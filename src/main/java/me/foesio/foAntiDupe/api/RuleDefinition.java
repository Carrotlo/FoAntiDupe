package me.foesio.foAntiDupe.api;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/** An immutable rule value. Item stacks are copied on input and output. */
public final class RuleDefinition {

    private final String id;
    private final String displayName;
    private final boolean enabled;
    private final Material material;
    private final int inventoryThreshold;
    private final int dropThreshold;
    private final boolean checkInventory;
    private final boolean checkDrops;
    private final boolean exactItem;
    private final boolean strictMeta;
    private final boolean matchName;
    private final boolean matchLore;
    private final boolean matchEnchants;
    private final boolean matchCustomModelData;
    private final ItemStack item;

    private RuleDefinition(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.enabled = builder.enabled;
        this.material = builder.material;
        this.inventoryThreshold = builder.inventoryThreshold;
        this.dropThreshold = builder.dropThreshold;
        this.checkInventory = builder.checkInventory;
        this.checkDrops = builder.checkDrops;
        this.exactItem = builder.exactItem;
        this.strictMeta = builder.strictMeta;
        this.matchName = builder.matchName;
        this.matchLore = builder.matchLore;
        this.matchEnchants = builder.matchEnchants;
        this.matchCustomModelData = builder.matchCustomModelData;
        this.item = builder.item == null ? null : builder.item.clone();
        if (this.item != null) {
            this.item.setAmount(1);
        }
    }

    public static Builder builder(String id, Material material) {
        return new Builder(id, material);
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public boolean enabled() { return enabled; }
    public Material material() { return material; }
    public int inventoryThreshold() { return inventoryThreshold; }
    public int dropThreshold() { return dropThreshold; }
    public boolean checkInventory() { return checkInventory; }
    public boolean checkDrops() { return checkDrops; }
    public boolean exactItem() { return exactItem; }
    public boolean strictMeta() { return strictMeta; }
    public boolean matchName() { return matchName; }
    public boolean matchLore() { return matchLore; }
    public boolean matchEnchants() { return matchEnchants; }
    public boolean matchCustomModelData() { return matchCustomModelData; }
    public ItemStack item() { return item == null ? null : item.clone(); }

    public static final class Builder {
        private final String id;
        private final Material material;
        private String displayName;
        private boolean enabled = true;
        private int inventoryThreshold = 64;
        private int dropThreshold = 64;
        private boolean checkInventory = true;
        private boolean checkDrops = true;
        private boolean exactItem;
        private boolean strictMeta;
        private boolean matchName;
        private boolean matchLore;
        private boolean matchEnchants;
        private boolean matchCustomModelData;
        private ItemStack item;

        private Builder(String id, Material material) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Rule ID must not be blank");
            }
            this.id = id;
            this.material = Objects.requireNonNull(material, "material");
            this.displayName = id;
        }

        public Builder displayName(String value) { this.displayName = Objects.requireNonNull(value, "displayName"); return this; }
        public Builder enabled(boolean value) { this.enabled = value; return this; }
        public Builder inventoryThreshold(int value) { this.inventoryThreshold = value; return this; }
        public Builder dropThreshold(int value) { this.dropThreshold = value; return this; }
        public Builder checkInventory(boolean value) { this.checkInventory = value; return this; }
        public Builder checkDrops(boolean value) { this.checkDrops = value; return this; }
        public Builder exactItem(boolean value) { this.exactItem = value; return this; }
        public Builder strictMeta(boolean value) { this.strictMeta = value; return this; }
        public Builder matchName(boolean value) { this.matchName = value; return this; }
        public Builder matchLore(boolean value) { this.matchLore = value; return this; }
        public Builder matchEnchants(boolean value) { this.matchEnchants = value; return this; }
        public Builder matchCustomModelData(boolean value) { this.matchCustomModelData = value; return this; }
        public Builder item(ItemStack value) { this.item = value == null ? null : value.clone(); return this; }

        public RuleDefinition build() {
            if (displayName.isBlank()) {
                throw new IllegalArgumentException("Rule display name must not be blank");
            }
            if (inventoryThreshold < 1 || dropThreshold < 1) {
                throw new IllegalArgumentException("Rule thresholds must be positive");
            }
            if (item != null && (!item.getType().isItem() || item.getType().isAir())) {
                throw new IllegalArgumentException("Rule item must be an item");
            }
            if (item != null && item.getType() != material) {
                throw new IllegalArgumentException("Rule item must match its material");
            }
            if (exactItem && item == null) {
                throw new IllegalArgumentException("Exact-item rules require an item");
            }
            return new RuleDefinition(this);
        }
    }
}
