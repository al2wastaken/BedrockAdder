# BedrockAdder

> [!WARNING]
> **BedrockAdder is in beta.** It works on the setups it was tested on, but expect bugs and breaking changes
> between versions. Back up your server before trying it, and please report problems.

Makes [ItemsAdder](https://itemsadder.devs.beer/) content visible to Bedrock players who join through
[Geyser](https://geysermc.org/).

ItemsAdder only draws its content with a Java resource pack, so Bedrock players normally see plain paper, note
blocks and missing furniture. BedrockAdder reads the pack ItemsAdder generates and converts it **on your server**:
there is no external conversion service and no manual pack work.

## How it works

1. You run `/iazip` (or ItemsAdder rebuilds its pack). BedrockAdder receives the pack and a snapshot of the
   ItemsAdder registry, and converts them off the main thread.
2. The result is a *bundle*: a Bedrock resource pack (`.mcpack`) plus custom item and block definitions.
3. The BedrockAdder **Geyser extension** registers the items, blocks and pack when Geyser starts. On a
   Geyser-Spigot server the extension installs itself.
4. While the server runs, BedrockAdder shows ItemsAdder furniture to Bedrock players.

Geyser only loads custom content when it starts, so **restart after each conversion**.

## Features

### Done

- [x] 2D items (`item/generated`, `item/handheld`)
- [x] 3D items: Bedrock geometry and attachables (first and third person, swing animation), rendered inventory icons
- [x] Items using `item_model` (1.21.4+) and legacy CustomModelData
- [x] Custom blocks on note block, mushroom, chorus and tripwire states: full cubes and custom geometry, light, hardness
- [x] Armor from 1.21.4+ `equipment` assets
- [x] Food, max stack size, durability and equippable item properties
- [x] Furniture (`item_display`): position, rotation and scale follow the Java entity
- [x] Font images (emojis, chat icons), sized by their Java `height`/`ascent`
- [x] Positive font offsets (shift characters keep their width)
- [x] GUI backgrounds (textured inventories such as `/ia`) on the classic chest screen
- [x] ItemsAdder's pack protection: overlays, `ia:N` sprite aliases, protected PNGs and zips
- [x] Same-server Geyser-Spigot, and proxy/standalone Geyser through a shared folder or HTTP
- [x] Skips unchanged conversions; warnings are written to `plugins/BedrockAdder/warnings.txt`

### Planned

- [ ] Custom sounds
- [ ] ItemsAdder custom entities (mobs)
- [ ] GUI backgrounds on the pocket (touch) UI layout
- [ ] Furniture types other than `item_display` (armor stands, item frames)
- [ ] Dyeable items and furniture colors
- [ ] Legacy shader armor (leather armor with shaders)
- [ ] Plugin-message sync for proxies (no shared folder or open HTTP port needed)
- [ ] In-game command to tune 3D held-item positions

### Not possible on Bedrock

- Negative font offsets, so HUDs that are aligned with them do not look like on Java. Bedrock text cannot move backwards.
- Glyphs taller than a text line inside chat or titles. Bedrock draws every glyph at text size; GUI backgrounds are handled separately.

## Comparison

How BedrockAdder compares with other ways of getting custom content onto Geyser. The details come from each
project's public documentation or source as of October 2026, so they may be out of date. `?` means the
documentation does not say; `—` means the row does not apply.

| | **BedrockAdder** | **Nexo Scaffolding** | **GeyserMC Rainbow** | **Chest-GUI-Generator** | **ItemsAdder alone** |
|---|---|---|---|---|---|
| Works with | ItemsAdder | Nexo | Any server (client mod) | Any Java chest GUI pack | — |
| How you run it | Server plugin + Geyser extension | Server plugin + optional Geyser extension | Fabric client mod | Browser tool | — |
| Where conversion runs | Locally, on your server | Hosted converter (self-hosted converter jar optional) | On your client | In your browser | — |
| Automatic on pack rebuild | ✅ | ✅ | ❌ | ❌ | — |
| Items (2D / 3D) | ✅ / ✅ | ✅ / ✅ | ✅ / ? | ❌ | ❌ |
| Custom blocks | ✅ | ✅ | ❌ | ❌ | ❌ |
| Armor | ✅ | ✅ | ? | ❌ | ❌ |
| Furniture | ✅ (`item_display`) | ✅ (with extension) | ❌ | ❌ | ❌ |
| Font images / emojis | ✅ | ✅ | ❌ | ❌ | ❌ |
| GUI backgrounds | ✅ (classic UI) | ✅ | ❌ | ✅ | ❌ |
| Sounds | ❌ (planned) | ✅ | ❌ | ❌ | ❌ |
| Custom mobs / ModelEngine | ❌ (planned) | ✅ (with extension) | ❌ | ❌ | ❌ |
| Proxy / standalone Geyser | ✅ (folder, HTTP) | ✅ (plugin message, folder) | — | — | — |
| Cost | Free | Sold on [nexomc.com](https://nexomc.com/products/scaffolding) | Free | Free | — |
| Status | Beta | Early access | — | — | — |

## Requirements

| | Version |
|---|---|
| Server | Paper 1.21.4+ or a fork (Purpur, Leaf, ...) |
| Java | 21+ |
| ItemsAdder | 4.0.x (tested with 4.0.18) |
| Geyser | 2.11.0+ (tested with 2.12.0) |
| Floodgate | Recommended; required for furniture when Geyser runs on a proxy |

Tested on Leaf 1.21.11 with Geyser-Spigot 2.12, Floodgate and ViaVersion, and on Paper 26.2.

## Installation

### Geyser-Spigot on the same server (default)

1. Put `BedrockAdder.jar` in `plugins/` next to ItemsAdder and Geyser-Spigot.
2. Make sure `enable-custom-content: true` is set in `plugins/Geyser-Spigot/config.yml`.
3. Start the server. BedrockAdder installs its extension into `plugins/Geyser-Spigot/extensions/`.
4. Run `/iazip`, or wait for ItemsAdder to build its pack, then **restart**.

### Geyser on a proxy or standalone

1. Install BedrockAdder on the backend that runs ItemsAdder. Copy `BedrockAdderExtension.jar` into the
   `extensions/` folder of the proxy or standalone Geyser.
2. Pick a sync type in `plugins/BedrockAdder/config.yml`:
   - `FOLDER`: a folder both machines can reach. On the Geyser side, set `source=FOLDER` and the same `folder`
     in `extensions/bedrockadder/config.properties`.
   - `HTTP`: the plugin serves the bundle on `sync.http.port`. On the Geyser side, set `source=HTTP`,
     `url=http://<backend>:<port>/bedrockadder/bundle` and `token=<sync.http.token>`.
3. Restart Geyser after each conversion.

> [!TIP]
> If your server runs with a Turkish system language, start Java with `-Duser.language=en -Duser.country=US`.
> Otherwise ItemsAdder turns `item_display` into `İTEM_DİSPLAY` and fails to build its pack.

## Configuration

`plugins/BedrockAdder/config.yml`:

| Key | Default | Meaning |
|---|---|---|
| `auto-convert` | `true` | Convert whenever ItemsAdder builds its pack |
| `pack-name` | `BedrockAdder` | Name of the Bedrock pack; changing it makes players download it again |
| `icon-size` | `64` | Size of rendered icons for 3D items |
| `fonts.enabled` | `true` | Convert font images and GUI backgrounds |
| `fonts.cell-size` | `32` | Java font pixels per Bedrock glyph cell; raise it if emojis look too big |
| `fonts.gui-offset-x` | `-16` | Horizontal position of GUI backgrounds (ItemsAdder's `x_position_pixels`) |
| `furniture.enabled` | `true` | Show furniture to Bedrock players |
| `furniture.yaw-offset` / `y-offset` | `0` | Fine-tuning if furniture looks turned or shifted |
| `sync.type` | `AUTO` | `AUTO`, `LOCAL`, `FOLDER`, `HTTP` or `OFF` |

## Commands

| Command | Description |
|---|---|
| `/bedrockadder reload [-f]` (`/ba`) | Convert from `ItemsAdder/output/generated.zip`; `-f` converts even when nothing changed |
| `/bedrockadder status` | Last conversion, deploy target, and whether Geyser needs a restart |

Permission: `bedrockadder.admin` (op by default).

## Building from source

ItemsAdder is a paid plugin and is not on any public repository. Put your licensed jar at
`libs/ItemsAdder-<version>.jar`; the folder is git-ignored and must never be committed. Then run:

```
./gradlew build
```

This builds two jars:

- `paper/build/libs/BedrockAdder-<version>.jar`: the server plugin, with the Geyser extension inside.
- `geyser-extension/build/libs/BedrockAdderExtension-<version>.jar`: needed only for proxy or standalone Geyser.

| Module | Contents |
|---|---|
| `common` | The converter: Java pack reader, model resolver, Bedrock pack writer, specs. No Bukkit or Geyser dependency. |
| `paper` | Bukkit plugin: ItemsAdder hook, conversion service, deployers, furniture mirrors |
| `geyser-extension` | Geyser extension: registers items, blocks and the pack |

## License

BedrockAdder is licensed under the [GNU Affero General Public License v3.0](LICENSE) (AGPL-3.0-only).

Copyright (C) 2026 Altuğ

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero
General Public License as published by the Free Software Foundation, version 3. It is distributed in the hope
that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE. See the [LICENSE](LICENSE) file for details.

If you run a modified version of BedrockAdder on a server that players connect to, the AGPL requires you to
offer those players the source code of your modified version.

ItemsAdder is a separate, paid product of its own authors. It is not included in this repository and is not
covered by this license.

## Credits

- The title-glyph test that shows GUI backgrounds on Bedrock follows
  [Chest-GUI-Generator](https://github.com/wlsgunzz/Chest-GUI-Generator).
- [GeyserMC](https://geysermc.org/) for the custom item, block and resource pack APIs.
