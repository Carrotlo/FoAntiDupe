![FoAntiDupe cover](docs/assets/cover.png)

# FoAntiDupe

[![Modrinth](https://img.shields.io/badge/Download-Modrinth-1bd96a?logo=modrinth&logoColor=white)](https://modrinth.com/plugin/foantidupe) [![Versions](https://img.shields.io/badge/Supported_versions-Modrinth-1bd96a)](https://modrinth.com/plugin/foantidupe/versions) [![Discord](https://img.shields.io/badge/Support-Discord-5865f2?logo=discord&logoColor=white)](https://discord.gg/kEF77QCfu7)

Configurable anti-dupe detection and staff review tools for Minecraft servers.

*Developed by Carrotio.*

## Features

- Flags risky item transfers using configurable rules.
- Shows staff alerts with live or read-only inventory review.
- Provides an in-game rule editor for materials, metadata, and thresholds.
- Supports bypass and separate staff-view permissions.
- Keeps messages, sounds, and rule definitions configurable.

## Download and dependencies

Download the latest compatible version from [Modrinth](https://modrinth.com/plugin/foantidupe). Check the [versions page](https://modrinth.com/plugin/foantidupe/versions) for supported Minecraft versions and server platforms.

- **Required:** None
- **Optional integrations:** None

## Screenshots

![Manage anti-dupe flag rules.](docs/assets/screenshots/foantidupe-flag-rules.png)

*Manage anti-dupe flag rules.*

![Edit a detection rule in game.](docs/assets/screenshots/foantidupe-rule-editor.png)

*Edit a detection rule in game.*

## Support

For help or issue reports, join the [Carrotio Discord server](https://discord.gg/kEF77QCfu7).

<details>
<summary><strong>Commands and permissions</strong></summary>

### Commands

- `/foantidupe <version|reload|editor>` — FoAntiDupe admin command. Aliases: `/fad`.

### Permissions

- `foantidupe.admin` — Allows use of FoAntiDupe admin commands and editor. Default: `op`.
- `foantidupe.show` — Shows FoAntiDupe player flag alerts and opens editable live inventories. Default: `op`.
- `foantidupe.showonly` — Shows FoAntiDupe player flag alerts and opens read-only live inventories. Default: `false`.
- `foantidupe.bypass` — Bypasses FoAntiDupe checks. Default: `false`.

</details>

<details>
<summary><strong>PlaceholderAPI</strong></summary>

This plugin does not register public PlaceholderAPI placeholders.

</details>

<details>
<summary><strong>Configuration</strong></summary>

- `config.yml` — Main behavior and feature settings.
- `flag-rules.yml` — Anti-dupe detection rules.
- `messages.yml` — Player-facing messages and interface text.
- `sounds.yml` — Configurable interaction sounds.

Main config areas: `settings`, `checks`.

</details>

<details>
<summary><strong>Developer API</strong></summary>

### Maven dependency example

Install the matching `FoAntiDupe-1.7.jar` locally if it is not in your Maven repository. These coordinates do not claim a published artifact. Use `provided` scope so the server supplies FoAntiDupe:

```xml
<dependency>
  <groupId>me.foesio</groupId>
  <artifactId>FoAntiDupe</artifactId>
  <version>1.7</version>
  <scope>provided</scope>
</dependency>
```

Compile against the matching FoAntiDupe jar as a provided/compile-only dependency. Do not shade it into your plugin. For example, with Gradle and a local copy of the matching jar:

```gradle
dependencies {
    compileOnly(files("libs/FoAntiDupe-1.7.jar"))
}
```

For `plugin.yml`, declare `depend: [FoAntiDupe]` when your plugin requires these classes, or `softdepend: [FoAntiDupe]` when the integration is optional. For optional integrations, load the class that references FoAntiDupe types only after confirming that FoAntiDupe is enabled. Obtain the service after both plugins have enabled; reacquire it after a provider disable/re-enable rather than caching it across that lifecycle. A cached service becomes inert while FoAntiDupe is disabled: queries are empty and actions return false.

```java
RegisteredServiceProvider<FoAntiDupeApi> registration =
        getServer().getServicesManager().getRegistration(FoAntiDupeApi.class);
if (registration != null) {
    FoAntiDupeApi antiDupe = registration.getProvider();
    antiDupe.rule("diamond_block_stack").ifPresent(rule ->
            getLogger().info("Inventory threshold: " + rule.definition().inventoryThreshold()));

    RuleDefinition custom = RuleDefinition.builder("myplugin_token", Material.PAPER)
            .displayName("MyPlugin token")
            .inventoryThreshold(32)
            .dropThreshold(16)
            .build();
    if (!antiDupe.registerRule(this, custom)) {
        getLogger().warning("FoAntiDupe rule ID already registered or owner disabled");
    }
}
```

Imports for this example: `me.foesio.foAntiDupe.api.FoAntiDupeApi`, `me.foesio.foAntiDupe.api.RuleDefinition`, `org.bukkit.Material`, and `org.bukkit.plugin.RegisteredServiceProvider`.

After your plugin changes a player's inventory directly, call `requestInventoryScan(player)` on that player's owning thread. `true` means the normal delayed scan was queued. `false` means FoAntiDupe is disabled, the player is offline or bypassed, or a scan is already queued. The scan still checks online/bypass state when it runs, and normal rule thresholds and alert cooldown apply. This method does not scan immediately or promise that an alert will follow.

`rules()` and `rule(id)` describe **effective rules now**: configured `flag-rules.yml` rules and registered runtime rules. A `RuleSnapshot` has an immutable `RuleDefinition`; its `ownerPlugin()` is null for configured rules and the owning plugin's name for runtime rules. Rule items are copied on input and output. These methods do not report a history of past flags.

Runtime registration accepts lowercase IDs containing letters, digits, `_`, or `-` (1–64 characters). IDs are global. `registerRule` returns false for a collision, invalid ID/material, or disabled owner; it never changes `flag-rules.yml`. `unregisterRule(owner, id)` returns false if the rule is absent or belongs to another owner. Rules survive `/foantidupe reload` and are removed automatically when the owner disables. If a configured rule gains the same ID on reload, that configured rule takes precedence; the registered rule becomes effective again if the configured one is removed. Runtime rules are visible through this API but are not editable in FoAntiDupe's configured-rule editor. Register/unregister on the server's plugin lifecycle or global thread, not from an asynchronous task. Query results are snapshots; do not assume they remain current after a reload.

### Events

```java
@EventHandler
public void beforeAlert(FoAntiDupeBeforeAlertEvent event) {
    if (event.getRule().id().equals("myplugin_token") && shouldIgnore(event.getPlayer())) {
        event.setCancelled(true);
    }
}

@EventHandler
public void onFlag(FoAntiDupeFlagEvent event) {
    getLogger().info(event.getPlayer().getName() + " matched " + event.getRule().id());
}
```

`FoAntiDupeBeforeAlertEvent` fires after a rule match passes the alert cooldown, before the cooldown is consumed or staff/console/log output occurs. Cancelling it suppresses that alert and leaves the cooldown unconsumed. It does **not** cancel the underlying item action. `FoAntiDupeFlagEvent` fires after an accepted alert has been sent and logged. Neither event fires for a cooldown-suppressed match; the final event does not fire for a cancelled alert. Both events expose the check type (`INVENTORY` or `DROP`), amount, reason, effective rule snapshot, player, and a defensive item copy. For inventory totals the item is a representative rule icon; for drops it is the dropped stack.

Events run in the player's current execution context: directly in the drop event or in the scheduled player inventory scan. On regionized servers this can be a player region thread; check `event.isAsynchronous()` and schedule unrelated world/player work on its appropriate owner. The events do not guarantee the server's primary thread. This API uses Bukkit/Paper public types only and contains no relocated FoPluginCore types.

### Complete public API method index

Signatures use short type names; import Fo types from this plugin's `api` package and Bukkit types from Bukkit.

#### `FoAntiDupeApi`

```java
boolean requestInventoryScan(Player);
List<RuleSnapshot> rules();
Optional<RuleSnapshot> rule(String);
boolean registerRule(Plugin, RuleDefinition);
boolean unregisterRule(Plugin, String);
```

**Other public contract types:** `FlagType`, `RuleDefinition`, `RuleSnapshot`.

### Complete Bukkit event index

- `FoAntiDupeBeforeAlertEvent`.
- `FoAntiDupeFlagEvent`.

</details>
