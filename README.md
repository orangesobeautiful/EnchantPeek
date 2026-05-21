# EnchantPeek

[繁體中文](docs/readme/README.zh-TW.md)

EnchantPeek is a Fabric client mod that shows the full enchantments offered by the enchantment table before you
spend lapis and levels.

The mod works on regular client-side prediction in singleplayer and most multiplayer servers. Servers can also
install the optional companion plugin to send exact server-side previews to players using the mod.

## Requirements

- Minecraft 1.21.11
- Fabric Loader 0.18.4 or newer
- Fabric API
- Java 21

## Installation

Install Fabric Loader and Fabric API for Minecraft 1.21.11, then place the EnchantPeek jar in your client's
`mods` folder.

## Optional Server Plugin

The Paper companion plugin is optional. It lets a server send authoritative enchantment previews to Fabric clients
with EnchantPeek installed, and can optionally refresh a player's enchantment seed when a new item is inserted into
the enchantment table.
