package me.foesio.foAntiDupe;

import me.foesio.core.dialog.DialogButton;
import me.foesio.core.dialog.TextDialogRequest;
import me.foesio.core.editor.ConfigEditorButton;
import me.foesio.core.editor.ConfigEditorMenu;
import me.foesio.core.editor.ConfigEditorValueType;
import me.foesio.core.editor.EditorDialogInputs;
import me.foesio.core.editor.EditorItemFactory;
import me.foesio.core.editor.EditorMenuHolder;
import me.foesio.core.editor.EditorSaveResult;
import me.foesio.core.gui.GuiButtonConfig;
import me.foesio.core.gui.GuiSlots;
import me.foesio.core.gui.EntryBrowserClick;
import me.foesio.core.gui.EntryBrowserHolder;
import me.foesio.core.gui.EntryBrowserMenus;
import me.foesio.core.gui.EntryBrowserRequest;
import me.foesio.core.material.MaterialTypes;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.message.FoStyle;
import me.foesio.core.number.LargeNumberParser;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.core.sound.FoEditorSounds;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

public final class EditorManager implements Listener {

    private static final String SETTINGS_MENU_ID = "foantidupe-settings";
    private static final int RULE_EDITOR_ROWS = 5;
    private static final int RULE_EDITOR_BACK_SLOT = GuiSlots.bottomMiddleSlot(RULE_EDITOR_ROWS);
    private static final int SETTINGS_ROWS = 4;
    private static final int SETTINGS_BACK_SLOT = GuiSlots.bottomMiddleSlot(SETTINGS_ROWS);
    private static final Material EDITOR_FILLER_MATERIAL = Material.GRAY_STAINED_GLASS_PANE;
    private static final String MAIN_TITLE = "&8ꜰᴏᴀɴᴛɪᴅᴜᴘᴇ";
    private static final String RULE_EDITOR_TITLE = "&8ʀᴜʟᴇ ᴇᴅɪᴛᴏʀ";
    private static final String SETTINGS_TITLE = "&8ꜱᴇᴛᴛɪɴɢꜱ";

    private final FoAntiDupe plugin;
    private final ConfigStore configStore;
    private final FoMessageService messages;
    private final RuleService rules;
    private final FoEditorSounds sounds;
    private final GuiButtonConfig buttons = GuiButtonConfig.defaults();

    public EditorManager(FoAntiDupe plugin, ConfigStore configStore, FoMessageService messages, RuleService rules, FoEditorSounds sounds) {
        this.plugin = plugin;
        this.configStore = configStore;
        this.messages = messages;
        this.rules = rules;
        this.sounds = sounds;
    }

    public void openMain(Player player) {
        EditorHolder holder = new EditorHolder(EditorView.MAIN, null, 0);
        Inventory inventory = createInventory(holder, 27, title(MAIN_TITLE));
        inventory.setItem(11, EditorItemFactory.button(player, Material.CHEST, FoStyle.THEME, "Flag Rules", List.of(
                "Browse, add, and edit item flags."
        ), "browse flag rules"));
        inventory.setItem(13, EditorItemFactory.button(player, Material.REPEATER, FoStyle.THEME, "Settings", List.of(
                "Edit checks, cooldowns, and alerts."
        ), "open settings"));
        inventory.setItem(15, EditorItemFactory.button(player, Material.LIME_DYE, FoStyle.GOOD, "Reload", List.of(
                "Reload config and messages."
        ), "reload the plugin"));
        player.openInventory(inventory);
    }

    private void openRuleBrowser(Player player, int page) {
        openRuleBrowser(player, page, "");
    }

    private void openRuleBrowser(Player player, int page, String query) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        List<EntryBrowserRequest.Entry> entries = rules.sortedRules().stream()
                .filter(rule -> matchesRuleSearch(rule, normalizedQuery))
                .map(rule -> EntryBrowserRequest.Entry.of(rule.id(), ruleIcon(player, rule)))
                .toList();
        EntryBrowserMenus.open(player, EntryBrowserRequest.builder()
                .title("Flag Rules")
                .entries(entries)
                .page(page)
                .filter(normalizedQuery)
                .buttons(buttons)
                .showBack(true)
                .addButton(EditorItemFactory.button(player, Material.ANVIL, FoStyle.GOOD, "Add Held Item", List.of(
                        "Hold an item on your cursor.",
                        "Create a new flag rule."
                ), "add the held item"))
                .build());
    }

    private boolean matchesRuleSearch(DupeRule rule, String query) {
        if (query.isBlank()) {
            return true;
        }
        String normalized = query.toLowerCase(java.util.Locale.ROOT);
        return rule.id().toLowerCase(java.util.Locale.ROOT).contains(normalized)
                || rule.displayName().toLowerCase(java.util.Locale.ROOT).contains(normalized)
                || rule.materialLabel().toLowerCase(java.util.Locale.ROOT).contains(normalized);
    }

    private void openRuleEditor(Player player, String ruleId) {
        DupeRule rule = rules.get(ruleId);
        if (rule == null) {
            openRuleBrowser(player, 0);
            return;
        }

        EditorHolder holder = new EditorHolder(EditorView.RULE_EDITOR, ruleId, 0);
        Inventory inventory = createInventory(holder, RULE_EDITOR_ROWS * GuiSlots.ROW_SIZE, title(RULE_EDITOR_TITLE));
        inventory.setItem(10, EditorItemFactory.toggle(player, "Rule Enabled", rule.enabled()));
        inventory.setItem(11, EditorItemFactory.button(player, Material.OAK_SIGN, FoStyle.THEME, "Display Name", List.of(
                "Current: " + rule.displayName(),
                "Set the name in chat."
        ), "edit the display name"));
        inventory.setItem(12, itemSlot(player, rule));
        inventory.setItem(13, EditorItemFactory.toggle(player, "Inventory Checks", rule.checkInventory()));
        inventory.setItem(14, EditorItemFactory.button(player, Material.PAPER, FoStyle.THEME, "Inventory Amount", List.of(
                "Current: " + rule.flagAtAmount(),
                "Flag when inventory reaches this amount."
        ), "edit the inventory amount"));
        inventory.setItem(15, EditorItemFactory.toggle(player, "Drop Checks", rule.checkDrops()));
        inventory.setItem(16, EditorItemFactory.button(player, Material.DROPPER, FoStyle.THEME, "Drop Amount", List.of(
                "Current: " + rule.flagAtDropAmount(),
                "Flag when one drop reaches this amount."
        ), "edit the drop amount"));

        inventory.setItem(19, EditorItemFactory.toggle(player, "Exact Item Mode", rule.exactItem()));
        inventory.setItem(20, EditorItemFactory.toggle(player, "Strict Meta", rule.strictMeta()));
        inventory.setItem(21, EditorItemFactory.toggle(player, "Match Name", rule.matchName()));
        inventory.setItem(22, EditorItemFactory.toggle(player, "Match Lore", rule.matchLore()));
        inventory.setItem(23, EditorItemFactory.toggle(player, "Match Enchants", rule.matchEnchants()));
        inventory.setItem(24, EditorItemFactory.toggle(player, "Match Model Data", rule.matchCustomModelData()));

        inventory.setItem(31, EditorItemFactory.button(player, Material.RED_DYE, FoStyle.BAD, "Delete Rule", List.of(
                "Shift-right-click to delete.",
                "This saves immediately."
        ), "delete this rule"));
        inventory.setItem(RULE_EDITOR_BACK_SLOT, buttons.back(player));
        player.openInventory(inventory);
    }

    private void openSettings(Player player) {
        Inventory inventory = settingsEditor().build(player);
        inventory.setItem(SETTINGS_BACK_SLOT, buttons.back(player));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (topInventory.getHolder() instanceof EntryBrowserHolder entryBrowserHolder) {
            if (!prepareTopInventoryClick(event, topInventory)) {
                return;
            }
            handleEntryBrowserClick(player, event, entryBrowserHolder);
            return;
        }

        if (topInventory.getHolder() instanceof EditorHolder holder) {
            if (!prepareTopInventoryClick(event, topInventory)) {
                return;
            }
            switch (holder.view) {
                case MAIN -> handleMainClick(player, event.getSlot());
                case RULE_EDITOR -> handleRuleEditorClick(player, holder.ruleId, event.getSlot(), event.getClick(), event.getCursor(), event.isShiftClick());
            }
            return;
        }

        if (topInventory.getHolder() instanceof EditorMenuHolder holder && settingsEditor().matches(holder)) {
            if (!prepareTopInventoryClick(event, topInventory)) {
                return;
            }
            handleSettingsClick(player, holder, event.getSlot());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof EntryBrowserHolder)) {
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < topInventory.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private boolean prepareTopInventoryClick(InventoryClickEvent event, Inventory topInventory) {
        if (event.getClickedInventory() == null) {
            event.setCancelled(true);
            return false;
        }
        if (!event.getClickedInventory().equals(topInventory)) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return false;
        }
        event.setCancelled(true);
        return true;
    }

    private void handleMainClick(Player player, int slot) {
        switch (slot) {
            case 11 -> {
                sounds.open(player);
                openRuleBrowser(player, 0);
            }
            case 13 -> {
                sounds.open(player);
                openSettings(player);
            }
            case 15 -> {
                sendReloadResult(player, plugin.reloadFoAntiDupe());
                openMain(player);
            }
            default -> {
            }
        }
    }

    private void handleEntryBrowserClick(Player player, InventoryClickEvent event, EntryBrowserHolder holder) {
        EntryBrowserRequest request = holder.request();
        EntryBrowserClick click = EntryBrowserMenus.handleClick(event.getSlot(), holder);
        switch (click.action()) {
            case ENTRY -> {
                DupeRule rule = rules.get(click.entryId());
                if (rule == null) {
                    openRuleBrowser(player, request.page(), request.filter());
                    return;
                }
                if (event.getClick().isRightClick()) {
                    rule.enabled(!rule.enabled());
                    sounds.toggle(player, rule.enabled());
                    saveAndReopenBrowser(player, rule, request.page(), request.filter(), "enabled");
                    return;
                }
                sounds.open(player);
                openRuleEditor(player, rule.id());
            }
            case ADD -> {
                ItemStack cursor = event.getCursor();
                if (cursor == null || cursor.getType().isAir()) {
                    sounds.addItemError(player);
                    messages.send(player, "editor.hold-item", "{prefix}{bad}Hold an item on your cursor first{muted}.");
                    return;
                }
                String id = rules.createRuleFromItem(cursor);
                sounds.addItem(player);
                messages.send(player, "editor.saved", "{prefix}{good}Saved {theme}{setting}{muted}.", Map.of("setting", rules.get(id).displayName()));
                openRuleEditor(player, id);
            }
            case BACK -> {
                sounds.back(player);
                openMain(player);
            }
            case SEARCH -> {
                sounds.search(player);
                TextDialogRequest searchRequest = new TextDialogRequest(
                        "Search",
                        List.of("Search by rule id, display name, or material."),
                        "Search",
                        request.filter(),
                        "rule id, name, or material",
                        DialogButton.search(),
                        DialogButton.cancel("Back"),
                        320,
                        300,
                        64,
                        true,
                        true,
                        false
                );
                openTextInputFromInventory(player, searchRequest,
                        input -> openRuleBrowser(player, 0, input),
                        () -> openRuleBrowser(player, request.page(), request.filter()));
            }
            case CLEAR_SEARCH -> {
                sounds.clearSearch(player);
                openRuleBrowser(player, 0, "");
            }
            case PREVIOUS_PAGE -> {
                sounds.previousPage(player);
                openRuleBrowser(player, request.page() - 1, request.filter());
            }
            case NEXT_PAGE -> {
                sounds.nextPage(player);
                openRuleBrowser(player, request.page() + 1, request.filter());
            }
            case NONE -> {
            }
        }
    }

    private void handleRuleEditorClick(Player player, String ruleId, int slot, ClickType click, ItemStack cursor, boolean shiftClick) {
        DupeRule rule = rules.get(ruleId);
        if (rule == null) {
            openRuleBrowser(player, 0);
            return;
        }
        if (slot == RULE_EDITOR_BACK_SLOT) {
            sounds.back(player);
            openRuleBrowser(player, 0);
            return;
        }
        switch (slot) {
            case 10 -> toggleRule(player, rule, DupeRule::enabled, !rule.enabled(), "enabled");
            case 11 -> promptRuleText(player, rule.id(), "display name", DupeRule::displayName);
            case 12 -> handleItemSlot(player, rule, click, cursor);
            case 13 -> toggleRule(player, rule, DupeRule::checkInventory, !rule.checkInventory(), "inventory checks");
            case 14 -> promptRuleNumber(player, rule.id(), "inventory amount", rule.flagAtAmount(), DupeRule::flagAtAmount);
            case 15 -> toggleRule(player, rule, DupeRule::checkDrops, !rule.checkDrops(), "drop checks");
            case 16 -> promptRuleNumber(player, rule.id(), "drop amount", rule.flagAtDropAmount(), DupeRule::flagAtDropAmount);
            case 19 -> toggleRule(player, rule, DupeRule::exactItem, !rule.exactItem(), "exact item mode");
            case 20 -> toggleRule(player, rule, DupeRule::strictMeta, !rule.strictMeta(), "strict meta");
            case 21 -> toggleRule(player, rule, DupeRule::matchName, !rule.matchName(), "match name");
            case 22 -> toggleRule(player, rule, DupeRule::matchLore, !rule.matchLore(), "match lore");
            case 23 -> toggleRule(player, rule, DupeRule::matchEnchants, !rule.matchEnchants(), "match enchants");
            case 24 -> toggleRule(player, rule, DupeRule::matchCustomModelData, !rule.matchCustomModelData(), "match model data");
            case 31 -> {
                if (shiftClick && click.isRightClick()) {
                    String name = rule.displayName();
                    rules.deleteRule(rule.id());
                    sounds.delete(player);
                    messages.send(player, "editor.deleted-rule", "{prefix}{good}Deleted rule {theme}{rule}{muted}.", Map.of("rule", name));
                    openRuleBrowser(player, 0);
                }
            }
            default -> {
            }
        }
    }

    private void handleSettingsClick(Player player, EditorMenuHolder holder, int slot) {
        if (slot == SETTINGS_BACK_SLOT) {
            sounds.back(player);
            openMain(player);
            return;
        }
        if (holder.actionAt(slot) == null) {
            return;
        }

        ConfigEditorMenu editor = settingsEditor();
        Optional<ConfigEditorButton> clicked = editor.buttonAt(holder, slot);
        if (clicked.isEmpty()) {
            return;
        }

        ConfigEditorButton button = clicked.get();
        if (button.type() == ConfigEditorValueType.BOOLEAN) {
            handleSettingSave(player, button, editor.toggle(button));
            return;
        }
        if (button.type() == ConfigEditorValueType.INTEGER) {
            promptSettingNumber(player, editor, button);
        }
    }

    private void handleItemSlot(Player player, DupeRule rule, ClickType click, ItemStack cursor) {
        if (click.isRightClick() && (cursor == null || cursor.getType().isAir())) {
            rule.item(null);
            rule.exactItem(false);
            rules.saveRule(rule);
            messages.send(player, "editor.saved", "{prefix}{good}Saved {theme}{setting}{muted}.", Map.of("setting", "item"));
            sounds.delete(player);
            openRuleEditor(player, rule.id());
            return;
        }
        if (cursor == null || cursor.getType().isAir()) {
            sounds.addItemError(player);
            messages.send(player, "editor.hold-item", "{prefix}{bad}Hold an item on your cursor first{muted}.");
            return;
        }
        rule.item(cursor);
        rule.exactItem(true);
        rule.strictMeta(true);
        rules.saveRule(rule);
        messages.send(player, "editor.saved", "{prefix}{good}Saved {theme}{setting}{muted}.", Map.of("setting", "item"));
        sounds.addItem(player);
        openRuleEditor(player, rule.id());
    }

    private void promptRuleNumber(Player player, String ruleId, String setting, int currentValue, ObjIntConsumer<DupeRule> setter) {
        TextDialogRequest request = TextDialogRequest.number(
                List.of("Enter a whole number above zero for " + setting + "."),
                String.valueOf(currentValue),
                String.valueOf(currentValue)
        );
        openTextInputFromInventory(player, request, raw -> {
            OptionalInt parsed = parsePositiveInteger(raw);
            if (parsed.isEmpty()) {
                messages.send(player, "editor.invalid-number", "{prefix}{bad}Enter a whole number above zero{muted}, or type {theme}cancel{muted}.");
                sounds.error(player);
                openRuleEditor(player, ruleId);
                return;
            }
            DupeRule current = rules.get(ruleId);
            if (current == null) {
                return;
            }
            setter.accept(current, parsed.getAsInt());
            rules.saveRule(current);
            messages.send(player, "editor.saved", "{prefix}{good}Saved {theme}{setting}{muted}.", Map.of("setting", setting));
            sounds.save(player);
            openRuleEditor(player, ruleId);
        }, () -> cancelAndRun(player, () -> openRuleEditor(player, ruleId)));
    }

    private void promptRuleText(Player player, String ruleId, String setting, BiConsumer<DupeRule, String> setter) {
        DupeRule rule = rules.get(ruleId);
        String currentValue = rule == null ? "" : rule.displayName();
        TextDialogRequest request = TextDialogRequest.text(
                List.of("Enter text for " + setting + "."),
                currentValue,
                currentValue
        );
        openTextInputFromInventory(player, request, value -> {
            if (value == null || value.isBlank()) {
                messages.send(player, "editor.invalid-text", "{prefix}{bad}Enter text{muted}, or type {theme}cancel{muted}.");
                sounds.error(player);
                openRuleEditor(player, ruleId);
                return;
            }
            DupeRule current = rules.get(ruleId);
            if (current == null) {
                return;
            }
            setter.accept(current, value);
            rules.saveRule(current);
            messages.send(player, "editor.saved", "{prefix}{good}Saved {theme}{setting}{muted}.", Map.of("setting", setting));
            sounds.save(player);
            openRuleEditor(player, ruleId);
        }, () -> cancelAndRun(player, () -> openRuleEditor(player, ruleId)));
    }

    private void promptSettingNumber(Player player, ConfigEditorMenu editor, ConfigEditorButton button) {
        int currentValue = button.integerValue();
        TextDialogRequest request = TextDialogRequest.number(
                List.of("Enter a whole number above zero for " + button.label() + "."),
                String.valueOf(currentValue),
                String.valueOf(currentValue)
        );
        openTextInputFromInventory(player, request, raw -> {
            OptionalInt parsed = editor.parseInteger(raw, 1, Integer.MAX_VALUE);
            if (parsed.isEmpty()) {
                messages.send(player, "editor.invalid-number", "{prefix}{bad}Enter a whole number above zero{muted}, or type {theme}cancel{muted}.");
                openSettings(player);
                return;
            }
            handleSettingSave(player, button, editor.save(button, parsed.getAsInt()));
        }, () -> cancelAndRun(player, () -> openSettings(player)));
    }

    private void openTextInputFromInventory(Player player, TextDialogRequest request, Consumer<String> onSubmit, Runnable onCancel) {
        EditorDialogInputs.openTextFromInventory(
                plugin,
                plugin.core().inventoryCloseSuppressor(),
                plugin.core().dialogService(),
                player,
                request,
                onSubmit,
                onCancel
        );
    }

    private OptionalInt parsePositiveInteger(String input) {
        return LargeNumberParser.parsePositiveInt(input);
    }

    private void cancelAndRun(Player player, Runnable action) {
        messages.send(player, "editor.cancelled", "{prefix}{bad}Cancelled{muted}.");
        action.run();
    }

    private void handleSettingSave(Player player, ConfigEditorButton button, EditorSaveResult result) {
        if (!result.successful()) {
            sounds.error(player);
            plugin.getLogger().warning("Failed saving editor setting " + button.configPath() + ": " + result.errorMessage());
            messages.send(player, "editor.save-failed",
                    "{prefix}{bad}Could not save {theme}{setting}{muted}: {white}{error}{muted}.",
                    Map.of(
                            "setting", button.label(),
                            "error", result.errorMessage()
                    ));
            openSettings(player);
            return;
        }
        messages.send(player, "editor.saved", "{prefix}{good}Saved {theme}{setting}{muted}.", Map.of("setting", button.label()));
        sounds.save(player);
        openSettings(player);
    }

    private void toggleRule(Player player, DupeRule rule, BiConsumer<DupeRule, Boolean> setter, boolean value, String setting) {
        setter.accept(rule, value);
        rules.saveRule(rule);
        sounds.toggle(player, value);
        messages.send(player, "editor.saved", "{prefix}{good}Saved {theme}{setting}{muted}.", Map.of("setting", setting));
        openRuleEditor(player, rule.id());
    }

    private void saveAndReopenBrowser(Player player, DupeRule rule, int page, String query, String setting) {
        rules.saveRule(rule);
        messages.send(player, "editor.saved", "{prefix}{good}Saved {theme}{setting}{muted}.", Map.of("setting", setting));
        openRuleBrowser(player, page, query);
    }

    private Inventory createInventory(EditorHolder holder, int size, String title) {
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.inventory = inventory;
        fill(inventory);
        return inventory;
    }

    private void fill(Inventory inventory) {
        ItemStack filler = EditorItemFactory.filler();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler.clone());
        }
    }

    private ItemStack ruleIcon(Player player, DupeRule rule) {
        Map<String, String> material = FoMessageService.materialPlaceholders(rule.material());
        return EditorItemFactory.button(player, MaterialTypes.icon(rule.material()),
                rule.enabled() ? FoStyle.GOOD : FoStyle.BAD,
                rule.displayName(), List.of(
                        "State: " + (rule.enabled() ? FoStyle.GOOD + "ON" : FoStyle.BAD + "OFF"),
                        "Material: " + messages.renderTemplateForViewer(player, "%material_sprite%%material%", material),
                        "Inventory amount: " + rule.flagAtAmount(),
                        "Drop amount: " + rule.flagAtDropAmount(),
                        "Exact item: " + (rule.exactItem() ? FoStyle.GOOD + "ON" : FoStyle.BAD + "OFF")
                ), "left-click to edit or right-click to toggle");
    }

    private ItemStack itemSlot(Player player, DupeRule rule) {
        Map<String, String> material = FoMessageService.materialPlaceholders(rule.material());
        return EditorItemFactory.button(player, Material.HOPPER, FoStyle.THEME, "Configured Item", List.of(
                "Material: " + messages.renderTemplateForViewer(player, "%material_sprite%%material%", material),
                "Exact item: " + (rule.exactItem() ? FoStyle.GOOD + "ON" : FoStyle.BAD + "OFF"),
                "Hold an item and click to set.",
                "Right-click empty cursor to clear."
        ), "set or clear the configured item");
    }

    private ConfigEditorMenu settingsEditor() {
        return ConfigEditorMenu.builder(plugin, messages, settingsEditorDefaults())
                .id(SETTINGS_MENU_ID)
                .title("title", "settings")
                .size("size", SETTINGS_ROWS * GuiSlots.ROW_SIZE)
                .filler("filler", true, EDITOR_FILLER_MATERIAL)
                .button(ConfigEditorButton.integerSetting("alert-cooldown", "settings.alert-cooldown-seconds",
                                "alert cooldown", "buttons.alert-cooldown", 10,
                                () -> configStore.integer("settings.alert-cooldown-seconds", 5))
                        .fallbackMaterial(Material.CLOCK)
                        .build())
                .button(ConfigEditorButton.integerSetting("scan-delay", "settings.scan-delay-ticks",
                                "scan delay", "buttons.scan-delay", 11,
                                () -> configStore.integer("settings.scan-delay-ticks", 1))
                        .fallbackMaterial(Material.REPEATER)
                        .build())
                .button(booleanSetting("notify-console", "settings.notify-console", "console alerts", "buttons.notify-console", 12))
                .button(booleanSetting("inventory-click", "checks.inventory-click", "inventory clicks", "buttons.inventory-click", 19))
                .button(booleanSetting("inventory-drag", "checks.inventory-drag", "inventory drags", "buttons.inventory-drag", 23))
                .button(booleanSetting("item-pickup", "checks.item-pickup", "item pickups", "buttons.item-pickup", 24))
                .button(booleanSetting("item-drop", "checks.item-drop", "item drops", "buttons.item-drop", 20))
                .button(booleanSetting("craft", "checks.craft", "crafting", "buttons.craft", 21))
                .button(booleanSetting("player-join", "checks.player-join", "player join", "buttons.player-join", 22))
                .build();
    }

    private ConfigEditorButton booleanSetting(String id, String configPath, String label, String guiPath, int slot) {
        return ConfigEditorButton.booleanSetting(id, configPath, label, guiPath, slot, () -> configStore.bool(configPath, true))
                .toggleMaterials(Material.LIME_DYE, Material.RED_DYE)
                .build();
    }

    private YamlConfiguration settingsEditorDefaults() {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("title", SETTINGS_TITLE);
        defaults.set("size", SETTINGS_ROWS * GuiSlots.ROW_SIZE);
        defaults.set("filler.enabled", true);
        defaults.set("filler.material", EDITOR_FILLER_MATERIAL.name());
        settingButton(defaults, "buttons.alert-cooldown", 10, "Alert cooldown", List.of(
                "{white}Current: {theme}{value}s",
                "{white}Click to set in chat."
        ));
        defaults.set("buttons.alert-cooldown.material", Material.CLOCK.name());
        settingButton(defaults, "buttons.scan-delay", 11, "Scan delay", List.of(
                "{white}Current: {theme}{value} ticks",
                "{white}Click to set in chat."
        ));
        defaults.set("buttons.scan-delay.material", Material.REPEATER.name());
        toggleButton(defaults, "buttons.notify-console", 12, "Console alerts", "Send flag alerts to console.");
        toggleButton(defaults, "buttons.inventory-click", 19, "Inventory clicks", "Scan after inventory clicks.");
        toggleButton(defaults, "buttons.inventory-drag", 23, "Inventory drags", "Scan after inventory drags.");
        toggleButton(defaults, "buttons.item-pickup", 24, "Item pickups", "Scan after item pickups.");
        toggleButton(defaults, "buttons.item-drop", 20, "Item drops", "Scan item drops.");
        toggleButton(defaults, "buttons.craft", 21, "Crafting", "Scan after crafting.");
        toggleButton(defaults, "buttons.player-join", 22, "Player join", "Scan when players join.");
        return defaults;
    }

    private void toggleButton(YamlConfiguration defaults, String path, int slot, String name, String description) {
        settingButton(defaults, path, slot, name, List.of(
                "{white}" + description,
                "{white}Current: {value}",
                "{white}Click to toggle."
        ));
        defaults.set(path + ".enabled-material", Material.LIME_DYE.name());
        defaults.set(path + ".disabled-material", Material.RED_DYE.name());
    }

    private void settingButton(YamlConfiguration defaults, String path, int slot, String name, List<String> lore) {
        defaults.set(path + ".slot", slot);
        defaults.set(path + ".name", "{theme}" + name);
        defaults.set(path + ".lore", lore);
    }

    private String title(String title) {
        return messages.renderTemplate(title, Map.of());
    }

    private void sendReloadResult(Player player, FoReloadResult result) {
        if (result.successful()) {
            messages.send(player, "commands.reloaded", "{prefix}{good}Reloaded {theme}config.yml{muted}, {theme}flag-rules.yml{muted}, and {theme}messages.yml{muted}.");
            return;
        }
        messages.send(player, "commands.reload-failed",
                "{prefix}{bad}Reload failed at {theme}{step}{muted}: {white}{error}{muted}.",
                Map.of(
                        "step", result.failedStep(),
                        "error", result.errorMessage()
                ));
    }

    private enum EditorView {
        MAIN,
        RULE_EDITOR
    }

    private static final class EditorHolder implements InventoryHolder {
        private final EditorView view;
        private final String ruleId;
        private final int page;
        private Inventory inventory;

        private EditorHolder(EditorView view, String ruleId, int page) {
            this.view = view;
            this.ruleId = ruleId;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
