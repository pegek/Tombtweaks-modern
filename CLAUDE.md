# CLAUDE.md

Project knowledge for Claude Code. It is loaded in every session opened in this folder — it is this project's "memory", not the session memory of `modDev`.

## Project

**TombTweaks (modern)** — an addon for [Corail Tombstone](https://www.curseforge.com/minecraft/mc-mods/corail-tombstone) for Minecraft **1.16.5, 1.20.1 and 1.21.1**. modid `tombtweaks`, package `com.spege.tombtweaks`. Repo: `github.com/pegek/Tombtweaks-modern` (local folder `E:\Isuth\tombtweaks-modern`; it was called `E:\Isuth\tombtweaks-1201` back when it only held the 1.20.1 port — older notes and plans may still use that name).

`tombtweaks` for **1.12.2** is a separate line in the `E:\Isuth\modDev` repo (module `tombtweaks/`, FG3, no refmap, different features). Nothing from there applies here automatically — in particular the `-proc:none` / "no refmap" rule from that `CLAUDE.md` is the **opposite** here.

Features (state as of 0.2.0 for 1.20.1; 1.21.1 and 1.16.5 in progress — matrix in `docs/superpowers/specs/2026-10-16-multiversion-design.md`):
- `[restore]` — a recovered grave returns items to the same slots;
- `[decay]` — an unvisited grave loses its contents (disabled by default);
- `[cooldown]` — cooldowns on Tombstone's books;
- `[graveKeys] survivesDeath = false` — grave keys become immune to Soulbound: carried keys go into the new grave instead of surviving every death (Tombstone hard-wires them as soulbound);
- always: enchantments disabled in Tombstone's config disappear from loot and trading (only where Tombstone has such a switch — not on 1.21.1).

## Layout and building

```
core/        SHARED decision code — Java 8, zero MC/Forge/NeoForge/Tombstone types, JUnit 5
mc1.16.5/    Forge 36,  ForgeGradle 5, Gradle 7, Java 8
mc1.20.1/    Forge 47,  ForgeGradle 6, Gradle 8.7, Java 17
mc1.21.1/    NeoForge 21.1.262, ModDevGradle 2.0.148, Gradle 8.10.2, Java 21
docs/        superpowers/specs/, superpowers/plans/, checklists/in-game-<mc>.md
```

Each `mcX/` is an **independent build with its own wrapper**; build it from its own directory:

```bash
cd mc1.20.1 && ./gradlew check build     # core + platform tests, guards, jar in build/libs/
```

All builds include `../core/src/{main,test}/java` via `sourceSets.*.java.srcDir` — **do not copy `core` into the builds**. The `core` tests run in every build, i.e. on JVM 8, 17 and 21.

JDK: the default `JAVA_HOME` on this machine is **JDK 8**. The Forge builds run Gradle on it and compile through the toolchain (foojay downloads 17 itself). **The NeoForge plugin refuses to run on a Gradle daemon below Java 17**, so `mc1.21.1/gradle/gradle-daemon-jvm.properties` (`toolchainVersion=21`) makes Gradle start its daemon on a locally installed JDK 21 — Gradle 8.10 cannot download a daemon JDK, so a JDK 21 must be installed on any machine building 1.21.1. MDG also does not put Minecraft on the test classpath by itself — `mc1.21.1` adds it with `addModdingDependenciesTo sourceSets.test`.

Item stacks on 1.21.1 have no NBT: `ItemKey`'s hash is the hash of the **serialized** `DataComponentPatch` (`DataComponentPatch.hashCode()` is not stable across restarts — holders hash by identity), and enchantment ids come from `EnchantmentHelper.getEnchantmentsForCrafting` (covers enchanted books' `STORED_ENCHANTMENTS`; `getTagEnchantments()` does not). A manually registered `JavaCompile` task does **not inherit** the toolchain — you have to supply `javaCompiler = javaToolchains.compilerFor {…}`, otherwise `--release` is an unknown flag (that is how `compileCoreJava8` is set up).

## Hard rules

1. **`core` = Java 8 syntax and API, zero foreign types.** This is enforced mechanically by `CoreHasNoForeignTypesTest` (a scan of the compiled classes for `net/minecraft`, `net/minecraftforge`, `net/neoforged`, `ovh/corail`) and `compileCoreJava8` (`--release 8`). No records, `var`, `List.of`, switch expressions. Tests may use newer Java.
2. **Forge builds (1.16.5, 1.20.1): the refmap is mandatory.** Never `-proc:none`. MixinGradle 0.7 **does not add the processor by itself** — an explicit `annotationProcessor 'org.spongepowered:mixin:0.8.5:processor'` is required. Without it the build is green and silent, and the jar has no refmap → a crash in a real instance that `runClient` will not show (dev uses official names). **The proof of correctness is an entry in `tombtweaks.refmap.json` inside the built jar**, not the silence of the processor.
3. **NeoForge 1.21.1 uses Mojang's official names at runtime** — no SRG and no reobfuscation, so a refmap is not needed to map MC names.
4. **`fg.deobf(files(...))` does not work on ForgeGradle 6** — it silently supplies the obfuscated jar, with a single warning in the configuration log. Dependencies come from CurseMaven (`curse.maven:<slug>-<projectId>:<fileId>`).
5. **We do not commit Tombstone's jars or any other mod's jars.** The licence is "All rights reserved". Source: CurseMaven (Tombstone = project 243707).
6. **Confirm mixin targets with `javap -p -c`, attributing the call to the method it sits in** — `grep -A` spills across method boundaries. A redirect requires exactly one call of the target in the method; count them.
7. **Changing the Tombstone version** = a `javap -p` comparison of all target classes (mixins, events, fields) between the old and the new version + the number of calls in the methods with redirects. Pattern: 9.1.4 → 9.1.5 (identical). Unpack jars into a fresh directory — files in the scratchpad get cleaned up from time to time, and an old comparison can silently compare nothing with something.
8. **A mixin that ADDS a method overriding an MC method** (e.g. `isDiscoverable` in `MixinTombstoneEnchantment`) must inherit from that MC class — only then does reobfuscation swap the name for the SRG one. Check the built jar with `javap`: on 1.20.1 the methods must be named `m_6592_` / `m_6594_`.
9. **In-game tests are done by the author, on the built jar in a real instance** — not in `runClient`. Lists: `docs/checklists/in-game-<mc>.md`. Agents do not run `runClient`/`runServer` (they block and open a window).
10. The Tombstone range in `mods.toml` is pinned to the verified versions (e.g. `[9.1.4,9.2)`), because the mixins are `required: true` + `defaultRequire: 1` — it is better for Forge to refuse to start with a readable message than for the game to crash on a class load.

## Feature invariants

- **Restore: the worst case = Tombstone's behaviour**, never a lost or duplicated item. This is guaranteed by one thing: an item is inserted only into an empty slot, re-checked right before it is taken out of the grave. `SlotPlan.NO_SEAT` guarantees nothing by itself.
- Restore works **only for the grave's owner** (`getPlayer()` is whoever *opens* the grave — a key holder too). Compare by name with `getOwnerName()`, not via `isOwner` (it returns `true` for a grave without an `ownerId`).
- Tombstone **merges graves**: a second death within 20 blocks of an uncollected grave adds items to it and resets `deathDate` and `countTicks`. Hence the two-pass `SlotPlan.assign` over the whole grave.
- Decay: the grave's inventory is a bare `ItemStackHandler` without `onContentsChanged` — **after taking an item out, `grave.setChanged()` is mandatory**, otherwise after a restart the item is both on the ground and in the grave. The history is written **before** `Containers.dropItemStack`, which consumes the stack.
- Cooldown is keyed by the book's **`Item`**, not by the stack (the book gets used up, and an empty stack reports `minecraft:air`).
- The protection list in decay: **an empty list protects nothing.**
- Config is read live; the flags do not gate mixins (there is no `IMixinConfigPlugin`) — the comments say "read live".

## Findings about Tombstone (bytecode)

| | 1.16.5 / 7.0.0 | 1.20.1 / 9.1.4–9.1.5 | 1.21.1 / 9.5.6 (NeoForge) |
|---|---|---|---|
| Restore | `RestoreInventoryEvent` on `MinecraftForge.EVENT_BUS` | `RestoreInventoryEvent` on `MinecraftForge.EVENT_BUS`, after loss-on-death, before auto-equip | the same event on `NeoForge.EVENT_BUS`, `IItemHandler` from NeoForge |
| Snapshot on death | Tombstone's `LivingDeathEvent` listener is LOWEST | Tombstone collects the items in `LivingDropsEvent`, its `LivingDeathEvent` is LOWEST → our HIGHEST sees an untouched inventory | same: Tombstone's `LivingDeathEvent` is LOWEST, the grave is built from `LivingDropsEvent` |
| Persisted player data | — | `Player.PERSISTED_NBT_TAG` | `Player.PERSISTED_NBT_TAG` still exists (`"PlayerPersisted"`); NeoForge 21.1.262 `ServerPlayer.restoreFrom` copies it unconditionally (also on death respawn) |
| Grave | `tick()` (`func_73660_a`) declared on **`TileEntityWritableGrave`** (the player grave does not override it); `countTicks` is an **`int`**, saved/loaded; bare `ItemStackHandler` | `BlockEntityPlayerGrave.serverTick` static; `countTicks` saved in NBT; `deathDate` = `System.currentTimeMillis()` | `serverTick`, `countTicks` (on `BlockEntityWritableGrave`); bare `ItemStackHandler` |
| Books | `ItemBook.canEnchant`; exactly one `canEnchant` + one `setEnchant` in `BlockDecorativeGrave.use` (`func_225533_a_`); `ConsumeResult` is a class with `result()` | `ItemBook.canEnchant`; `setEnchant` called once, in `BlockDecorativeGrave.use` (`m_6227_`); `ConsumeResult.result()` = `SUCCESS`/`FAIL` | `canEnchant`; exactly one `setEnchant` in `useItemOn`; `ISoulConsumer` moved to `ovh.corail.tombstone.api.item` |
| Soulbound key | `EntityHelper.hasSoulbound` returns `true` for `grave_key`; one call in `storeSoulboundsOnBody` | `forceSoulbound` flag → `getEnchantmentLevel(soulbound)` = 1; decision: one `Helper.isSoulbound` in `storeSoulboundsOnBody` | `Helper.isSoulbound(ItemStack, Level)` = `ModDataComponents.SOULBOUND` (`tb_soulbound`) or the enchantment; the `ItemGraveKey` constructor sets `tb_soulbound = true` as a default component; one call in `storeSoulboundsOnBody` (the only other caller is `ItemBookOfSoulbound.setEnchant`) |
| Enchantments | classes; `TombstoneEnchantment` does not override `func_230309_h_`/`func_230310_i_` (the 1.16 counterparts of `isTradeable`/`isDiscoverable`) | classes; the config gates `canEnchant`/`canApplyAtEnchantingTable`/`isAllowedOnBooks`, **not** `isDiscoverable`/`isTradeable` | **JSON** (`data/tombstone/enchantment/`), no "enable" switch, only `*EnchantingTable`; all of them in the `on_random_loot`/`tradeable` tags |

In all versions the grave key is hard-wired as soulbound, regardless of the Soulbound config.

## Conventions

- 🚨 **The repository is public and everything in it is in English** — code, comments, Javadoc, tests (including test method names), config comments, docs, README, CLAUDE.md, and commit messages. The author talks to Claude in Polish in chat, but nothing Polish goes into the repo (this replaces the earlier convention of Polish ASCII code comments and a Polish README). The layout of `docs/` follows the author's `modDev` workspace (`docs/superpowers/specs` and `docs/superpowers/plans`).
- One mod version for all MC versions (`version` in every `mcX/build.gradle`); tags `vX.Y.Z`. The version in the log is read from the mod container (the manifest), not from a constant — a constant drifts.
- Every behaviour change: an entry in `README.md` and in the relevant list under `docs/checklists/`.
- Decision logic goes into `core` (with a test); `platform`/`feature` hold only adapters and execution; mixins only delegate.

## Documents

- `docs/superpowers/specs/2026-10-16-multiversion-design.md` — repo layout, feature matrix, stages.
- `docs/superpowers/specs/2026-08-14-tombtweaks-1201-port-design.md` — the full rationale for the features (the 1.20.1 port, §11 = 0.2.0).
- `docs/superpowers/plans/2026-08-14-tombtweaks-1201-port.md` — the 1.20.1 port plan (historical; annotations on the tasks that were superseded).
- `docs/superpowers/plans/2026-10-16-mc1211-port.md` — the 1.21.1 port plan.
- `docs/checklists/in-game-<mc>.md` — per-version in-game verification lists (`in-game-1.20.1.md`, `in-game-1.21.1.md`).
