# TombTweaks (modern)

Tweaks and fixes for [Corail Tombstone](https://www.curseforge.com/minecraft/mc-mods/corail-tombstone) for Minecraft **1.16.5, 1.20.1 and 1.21.1**. The 1.12.2 version is a separate project.

| Feature | What it does | Default |
|---|---|---|
| `[restore]` | A recovered grave returns items to the **same slots** they died in — including armour and offhand | enabled |
| `[decay]` | An unvisited grave slowly drops its contents on the ground; four protection lists exempt chosen items from that | **disabled** |
| `[cooldown]` | Tombstone's magic books get a cooldown, configurable per book | enabled (2 books × 6 min) |
| `[graveKeys]` | `survivesDeath = false`: grave keys stop being soulbound — carried keys end up in the new grave, and Soulbound cannot be applied to a key (on 1.21.1 the anvil refuses that by itself) | `true` (Tombstone's behaviour) |
| *(always)* | Tombstone enchantments **disabled in its config** really disappear from chests and from librarians | — |

Config: `config/tombtweaks-common.toml`. All values are read live — no restart needed.

## Versions

| Minecraft | Loader | Tombstone | Status |
|---|---|---|---|
| 1.20.1 | Forge 47+ | 9.1.4 – 9.1.x | ✅ all features (0.2.0) |
| 1.21.1 | NeoForge 21.1 | 9.5.6 – 9.5.x | ✅ restore, decay, book cooldowns, grave keys (0.3.0, awaiting the in-game pass) — disabling enchantments **does not apply** (see below) |
| 1.16.5 | Forge 36 | 7.0.0 | ✅ all features (0.3.0, awaiting the in-game pass) |

The Tombstone version range is deliberately narrow: the mixins are verified against specific releases, and an incompatible version should refuse to start rather than crash the game on a class load.

**1.21.1 and disabled enchantments.** Since 1.21 Tombstone's enchantments are defined by data, Tombstone 9.5 has no switch that disables them, and it adds all of them to the vanilla loot and trading tags. To remove an enchantment from loot and trading, use a datapack that overrides the `minecraft:on_random_loot` / `minecraft:tradeable` tags (or `tombstone:enchantments`).

## How it works

**Restore.** At the moment of death the mod records a *seat plan* — for each slot only the item id and the hash of its NBT, not a copy of the item. When the grave is recovered it assigns items to seats in two passes over the whole grave (exact matches first, then by id alone) and inserts an item **only into an empty slot**. Whatever it does not insert, Tombstone distributes as it always does — the worst case is Tombstone's normal behaviour, never a lost or duplicated item.

**Decay.** The grave's `countTicks` counter (Tombstone saves it) sets the schedule: after `startTicks`, one stack drops on the ground every `intervalTicks`. Unprotected ones first; protected ones only when nothing else is left, and not at all with `protectedNeverDecay = true`. The counter only advances while the chunk is loaded.

**Cooldown.** The block sits on `ItemBook.canEnchant`; the cooldown starts on the only `setEnchant` call in Tombstone, and only on success.

**Keys and Soulbound.** Tombstone treats every grave key as Soulbound unconditionally (on 1.16.5 `EntityHelper.hasSoulbound` special-cases the key, on 1.20.1 the key carries a `forceSoulbound` flag, on 1.21.1 it carries a default `tb_soulbound` data component), regardless of the enchantment and of the Soulbound switch in its config. With `survivesDeath = false` the death-time decision (`DeathHandler.storeSoulboundsOnBody`) answers "no" for a key, so it lands in the new grave. The key to the grave that is just being created is issued by Tombstone separately — you get it as always.

**Disabled enchantments.** Tombstone ties the enchanting table, the anvil and the books to its config, but not `isDiscoverable()` (which vanilla `enchant_randomly` in chests filters on) nor `isTradeable()` (librarians). The mod adds both overrides: `isEnabled() && super`. For enabled enchantments nothing changes.

## Known limitations

- **Curios** are not handled by restore (the slot space 200+ is reserved). Tombstone has its own `curioAutoEquip`.
- **"Reverse inventory sorting"** — a per-player Tombstone preference, applied *after* our event; when enabled, it reverses the restored layout. In-game verification result: *to be filled in*.
- **Restore only works for the grave's owner.** A grave opened with a key by another player is distributed the standard way.
- **Merged graves.** A second death within 20 blocks of an uncollected grave adds items to it; only the items from the latest death are seated, older ones take the standard path. Merging also resets the decay counter.
- **The decay history of an offline player** goes only to the server log (DEBUG).
- **Two messages on a blocked book:** ours (with the number of seconds, on the action bar) and Tombstone's own "not allowed" in chat.
- **The Soulbound book** (`book_of_soulbound`) used on a key with `survivesDeath = false` is still consumed, although it does nothing — only the anvil route is blocked.
- **Disabled enchantments** in the loot of other mods that build their own enchantment pool bypassing the vanilla functions may still leak through — the vanilla paths are closed.

## Building

The repo contains three independent builds — build each from its own directory:

```bash
cd mc1.20.1 && ./gradlew check build   # tests + guards, jar in mc1.20.1/build/libs/
```

The same command works in `mc1.16.5/` and `mc1.21.1/`.

Gradle fetches the JDK (8 / 17 / 21) itself via the toolchain. Tombstone comes from CurseMaven — its jar deliberately does not live in the repo (licence "All rights reserved").

**After every mixin change in a Forge build**, check that `tombtweaks.refmap.json` in the built jar has entries for the targets that are Minecraft methods (1.20.1: `use` → `m_6227_`, `canEnchant` → `m_6081_`; 1.16.5: `use` → `func_225533_a_`, `canEnchant` → `func_92089_a`, `tick` → `func_73660_a`). NeoForge 1.21.1 runs on official names and has no refmap. Without the refmap the build is green and the game crashes only in a real instance — `runClient` will not show it.

## Architecture

| Directory / package | Contents | Tests |
|---|---|---|
| `core/` | all the decision logic, **shared by all versions**; zero Minecraft, loader and Tombstone types; Java 8 syntax | JUnit 5, in every build |
| `mcX/…/platform` | config, `ItemStack` → `core` adapters, the NBT codec | codec round-trip |
| `mcX/…/feature`, `mixin` | event handlers and mixins | in game — `docs/checklists/in-game-<mc>.md` |

Both `core` rules are enforced mechanically: `CoreHasNoForeignTypesTest` scans the compiled classes, `compileCoreJava8` compiles `core` with `--release 8`.

The rationale for the decisions is in `docs/superpowers/specs/` — the multi-version layout in `2026-10-16-multiversion-design.md`, the features in `2026-08-14-tombtweaks-1201-port-design.md`.
