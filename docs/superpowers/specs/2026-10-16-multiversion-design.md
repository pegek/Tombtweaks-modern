# TombTweaks modern — one repo, three Minecraft versions

**Date:** 2026-10-16
**Status:** approved; stage 0 (housekeeping) done on the same day
**Predecessor:** `2026-08-14-tombtweaks-1201-port-design.md` (the 1.20.1 port, versions 0.1.0–0.2.0)

## Goal

A single addon project for Corail Tombstone covering **1.16.5, 1.20.1 and 1.21.1**, with full feature parity wherever the given Tombstone version allows it. Immediate reason: a player asked for slot restore on 1.21.1. Repo: `E:\Isuth\tombtweaks-modern` → `github.com/pegek/Tombtweaks-modern` (the folder was called `E:\Isuth\tombtweaks-1201` until stage 0).

`tombtweaks` for **1.12.2** stays in the `modDev` repo — it is a separate line with a different feature set, a different toolchain (FG3, no refmap) and its own history.

## Layout

```
tombtweaks-modern/
├─ CLAUDE.md        project knowledge — loaded in every session opened in this folder
├─ README.md        feature × version matrix, building
├─ core/            SHARED: Java 8, zero MC/Forge/NeoForge/Tombstone types, JUnit 5 tests
├─ mc1.16.5/        Forge 36, ForgeGradle 5, Gradle 7, Java 8,  Tombstone 7.0.0
├─ mc1.20.1/        Forge 47, ForgeGradle 6, Gradle 8, Java 17, Tombstone 9.1.5
├─ mc1.21.1/        NeoForge 21.1, ModDevGradle, Gradle 8, Java 21, Tombstone 9.5.6
└─ docs/            superpowers/specs/, superpowers/plans/, checklists/in-game-<mc>.md
```

**Why separate builds but one repo.** What stood in the way in August was a single Gradle *build* (one wrapper), not a single git repo. FG5 requires Gradle 7, FG6 requires Gradle 8, and FG6 and ModDevGradle in one build clash on the plugin classpath. So every `mcX/` has its own wrapper and is built separately.

**A shared `core` without copying.** Every build includes `../core/src/main/java` and `../core/src/test/java` via `sourceSets.*.java.srcDir`. The decision logic exists once, and the `core` tests run on three JVMs (8, 17, 21). Two mechanical guards make sure `core` is usable on all three: `CoreHasNoForeignTypesTest` (a scan of the compiled classes for `net/minecraft`, `net/minecraftforge`, `net/neoforged`, `ovh/corail`) and `compileCoreJava8` (`--release 8`, in the 17/21 builds).

**One mod version for all MC versions.** 0.3.0 = the first version on three MC versions. Jars: `tombtweaks-<mc>-<version>.jar`.

## Feature matrix — findings from the bytecode

| Feature | 1.16.5 / Tombstone 7.0.0 | 1.20.1 / 9.1.5 | 1.21.1 / 9.5.6 |
|---|---|---|---|
| Slot restore | ✅ the same `RestoreInventoryEvent` on `MinecraftForge.EVENT_BUS`; Tombstone's `LivingDeathEvent` at LOWEST | ✅ | ✅ identical event (`IItemHandler` from NeoForge) |
| Grave decay | ✅ different hook: `tick()` (`func_73660_a`) declared in **`TileEntityWritableGrave`** (the player grave does not override it) → mixin there + `instanceof TileEntityPlayerGrave`; `countTicks` is an **`int`**, saved/loaded; inventory is a bare `ItemStackHandler` → `markDirty()` | ✅ | ✅ `serverTick` static, `countTicks` |
| Book cooldown | ✅ `ItemBook.canEnchant`; exactly 1× `canEnchant` and 1× `setEnchant` in `BlockDecorativeGrave.use` (`func_225533_a_`); `ConsumeResult` is a class (Java 8), but with the same `result()` | ✅ | ✅ `ItemBook.canEnchant`; the `setEnchant` call is in `BlockDecorativeGrave.useItemOn` (1.21 split `use`) |
| Keys immune to Soulbound | ✅ `EntityHelper.hasSoulbound` with a `grave_key` branch, one call in `storeSoulboundsOnBody` | ✅ | ✅ `Helper.isSoulbound(ItemStack, Level)` — one call in `storeSoulboundsOnBody`; **the anvil block is unnecessary**: Tombstone's own `supportsEnchantment` hook refuses Soulbound on a stack marked `tb_soulbound`, which every key is |
| Disabled enchantments outside loot/trading | ✅ same as 1.20.1: `TombstoneEnchantment` does not override `func_230309_h_`/`func_230310_i_` (the counterparts of `isTradeable`/`isDiscoverable`) — to be confirmed on vanilla 1.16.5 which of them `EnchantRandomly` and trading read | ✅ | ❌ **not applicable**: the enchantments are JSON files (`data/tombstone/enchantment/`), Tombstone 9.5.6 has no "enable" switch, only `*EnchantingTable`, and it puts all of them into the vanilla `on_random_loot` / `tradeable` tags via `#tombstone:enchantments`. Disabling = a datapack overriding the tags; described in the README |

A difference in `platform` on 1.21.1: stacks have no NBT (data components since 1.20.5), so `ItemKey` hashes the components. `core` is unchanged — it only receives `(id, hash)`.

The anvil in 1.20.1 vs 1.21.1: in 1.20.1 the key goes through the Soulbound category (`maxStackSize == 1`), so the block is needed. In 1.21.1 it is not — see the matrix.

## Stages

0. **Housekeeping** (done 2026-10-16): folder renamed, `git mv` into `core/` + `mc1.20.1/` (history preserved), `srcDir` pointing at the shared core, the 1.20.1 dependency moved to Tombstone 9.1.5 after comparing the signatures of all target classes and the number of calls in the methods with redirects (identical), documents moved over from `modDev`, `CLAUDE.md`, new GitHub repo.
1. **1.21.1** — this is what the player asked for. First step of the plan: full verification of the targets in 9.5.6 (including whether the key carries the `SOULBOUND` component by default), then implementation.
2. **1.16.5** — as above, on 7.0.0.
3. Release 0.3.0 on all three versions.

Every stage: verify the targets in bytecode → implement → review → build → `javap` of the built jar (refmap / names after reobfuscation) → in-game tests by the author.
