# Better List for Litematica

A **Litematica add-on** for Fabric that turns the material list into a full build-tracking tool.

Track what you've placed, what's stored in your chests, and what each teammate is working on, all from one upgraded material list. Works in singleplayer and on servers, solo or in a party.

> Requires [Litematica](https://modrinth.com/mod/litematica) and [MaLiLib](https://modrinth.com/mod/malilib) (plus Fabric API). Client-side.

## Features

- **Material checklist.** Tick off items per schematic as you gather them; state is saved **per server**.
- **Tracked chests → live "stored" counts.** Mark any chest as storage and its contents are counted into a *Stored* column, so you instantly see what you still need vs. what's already in bulk storage.
  - 🔍 **Preview** a tracked chest's remembered contents without walking to it.
  - 💡 **Highlight** tracked chests in the world, visible **through walls**.
  - Tracked chests are forgotten automatically once the chest is gone, so a broken chest stops padding your *Stored* count.
  - Each tracked container shows its real block icon (chest, barrel, colored shulker, …).
  - **Shulker boxes follow their shulker.** Carried, dropped or placed elsewhere, it stays tracked. A lost one (burned, void, despawned, blown up) stays listed in red with the reason. Needs the server side (Fabric server mod or the Integration plugin 1.3.0+).
- **Find an item in your chests.** Left-click any row to search for that item; every tracked chest holding it lights up in the world with its own pulsing outline, distinct from the manual 💡 highlight. The tooltip tells you how many chests hold it and how many you have.
- **Targeted-items HUD.** Right-click items to *target* them, then toggle a compact top-right HUD showing `have / need`. Items drop off the HUD automatically once you've gathered enough. Works **solo**.
- **Party sync.** Invite players, share schematic placements, and sync checklist + stored progress between everyone. See which teammates are focusing which items (colored borders + player heads on the list).
- **Filtering & sorting.** Search, hide fully-placed / fully-stored / checked items, sort by any column, filter the list to a specific player's targets.
- **Shulker box counts** (optional). Large amounts are split into full shulkers first, shown with a shulker icon: `12000 = 6 [shulker] + 25 x 64 + 32`. Turn on *Show shulker boxes in counts* in the settings.
- **Localization.** English (default) and Polish; switch in *Options → Language*.

## Default keybinds

| Key | Action |
|-----|--------|
| `.` | Open material list |
| `R` | Reload list (inside the GUI) |
| `K` | Open tracked-chests screen |
| `H` | Toggle chest highlighting |
| `J` | Toggle targeted-items HUD |
| `O` | Open party screen |
| none | Open config (unbound by default) |

The config is also reachable from the mod list of any MaLiLib config screen and from Mod Menu (optional). All keybinds are MaLiLib hotkeys: rebindable, with key combinations and conflict detection.

## Party sync: server setup

All single-player features work without any server setup. **Party sync** additionally needs the server to relay BML packets. Two options:

- **Fabric server:** drop this same mod into the server's `mods/` folder (with Fabric API). No extra download.
- **Bukkit-family server** (Paper, Purpur, Spigot, …): install the companion **[BetterList Integration plugin](https://github.com/Dowimixworsafe/Better-List-Integration)**.

Either way, party members just need the **same mod version** on their clients. If someone is running a different one you'll get a one-time in-game warning, since sync guarantees can differ between versions.

Release notes live in [docs/CHANGELOG.md](docs/CHANGELOG.md).

## Minecraft versions

`main` targets the latest supported Minecraft release and also builds the previous one from the same code. Older ones live on maintenance branches.

| Minecraft | Branch |
|-----------|--------|
| 26.3 | `main` |
| 26.2 | `main` (build with the overrides below) |
| 26.1.2 | [`mc/26.1.2`](https://github.com/Dowimixworsafe/Better-List/tree/mc/26.1.2) |

## Building

Requires the Java 25 toolchain.

```bash
./gradlew build      # → build/libs/betterlist-<version>.jar
./gradlew runClient  # launch a dev client
```

`gradle.properties` targets the newest Minecraft version. To build for 26.2, override the versions:

```bash
./gradlew build -Pminecraft_version=26.2 -Pmod_version=1.5.1+26.2 -Ploader_version=0.19.3 \
  -Pfabric_api_version=0.154.2+26.2 -Pmalilib_version=0.29.3 -Plitematica_version=0.28.3 -Pmodmenu_version=20.0.3
```

## License

All Rights Reserved. Litematica and MaLiLib are separate projects (LGPL-3.0) and are required as runtime dependencies; they are not bundled with this mod.
