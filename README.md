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

FoAntiDupe exposes flag events, manual inventory scans, read-only rule snapshots, and runtime rule registration for other plugins. See [public API source](src/main/java/me/foesio/foAntiDupe/api) for discovery, lifecycle, and event semantics.

</details>
