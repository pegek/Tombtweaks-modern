# TombTweaks on 1.20.1 — porting the core

**Date:** 2026-08-14
**Status:** implemented as `tombtweaks` 0.1.0 (repo `E:\Isuth\tombtweaks-1201`, since 2026-10-16 `E:\Isuth\tombtweaks-modern`, `github.com/pegek/Tombtweaks-modern` — see `2026-10-16-multiversion-design.md`). The spec was updated on 2026-10-02 to the state of the actual implementation — the places where the implementation departed from the original design are described explicitly, together with the reason.
**Scope:** the first working version of `tombtweaks` on Minecraft 1.20.1 / Forge 47 / Corail Tombstone 9.1.4

---

## 1. Goal and boundaries

`tombtweaks` exists today only on 1.12.2, as one of seven mods in the `modDev` repo. This spec describes the **first stage** of moving it to newer Minecraft versions: a public, general-purpose mod on 1.20.1, made up of three features that have no native counterpart in 9.1.4.

Further stages, **outside the scope of this spec**:

- **Stage 2** — integrations: Enigmatic Legacy (softening the Ring of the Seven Curses, the Relief for the Damned perk), Electroblob's Wizardry Redux (wand soulbind), Curios.
- **Stage 3** — tuning the native perks.
- **Stage 4** — the port to 1.16.5 (Tombstone 7.0.0).

The features deliberately left out of v1, with the reason why they are not urgent, are listed in §8.

### This is not a code port

What carries over from 1.12.2 to 1.20.1 is the **semantics, the config schema and the design decisions**, not the lines of code. The layer that touches Minecraft is written from scratch. Where 1.20.1 allows something to be done more simply than the original, the spec picks the simpler solution and says so explicitly — three such places are described in §4, §5 and §6.

---

## 2. Findings about Tombstone 9.1.4 (verified in bytecode)

Everything below was confirmed with `javap` on `tombstone-1.20.1-9.1.4.jar` (JDK 21, because the classes are class-file version 65).

| Finding | Meaning |
|---|---|
| `ovh.corail.tombstone.api.event.RestoreInventoryEvent` exists and has the same shape as on 1.12.2 | exact-slot restore needs not a single mixin |
| `BlockEntityPlayerGrave.giveInventory` distributes through `ItemHandlerHelper.giveItemToPlayer` | **slots are still lost** — the feature is still needed |
| `EventFactory.onRestoreInventory` fires after the `chanceLossOnDeath`/`percentLossOnDeath` cut, and before auto-equip and tool priority | the same useful moment as on 1.12.2 |
| After the event there is a pass of `PlayerPreference.isReverseInventorySorting()`, rewriting `Inventory.items` in reverse order | **a new risk**, see §4 |
| `BlockEntityPlayerGrave.serverTick(...)` — public static | a decay hook without scanning TEs |
| `BlockWritableGrave.countTicks` — `public long`, **saved** in `writeShared` and loaded in `load`; `serverTick` calls `commonTick` as its first instruction | the decay schedule can be stateless and survives a restart |
| `getOwnerDeathTime()` returns `deathDate`, set from `TimeHelper.systemTime()` = `System.currentTimeMillis()` | binding a snapshot to a grave goes by wall-clock time in ms, the same scale as `capturedAt` |
| `ItemBook implements ISoulConsumer` and declares `canEnchant(...)`, but **not** `setEnchant(...)` — only the concrete books have that | the cooldown needs two attachment points, not one, see §6 |
| `ISoulConsumer.canEnchant` and `.setEnchant` are called from **exactly one** place: `BlockDecorativeGrave.use`, 18 instructions apart | one point for setting the cooldown, covering all books |
| `ConsumeResult` is a record with `result()` → `Result.SUCCESS` / `Result.FAIL` | success is returned directly, no guessing from a change in the stack |
| `CooldownHandler.CooldownType` is an enum with 4 values (`NEXT_PRAY`, `RESET_PERKS`, `TELEPORT_DEATH`, `REQUEST_TELEPORT`) | the native cooldown system is not extensible without a mixin, we do not use it |
| `ConfigTombstone.general.unhandledBeneficialEffects` / `unhandledHarmfulEffects` | native blacklists of random effects — our whitelists are partly redundant, hence they dropped out of v1 |
| `ConfigTombstone.compatibility.curioAutoEquip` | Curios has native support; our feature is stage 2, not a gap |
| `Perk` moved from the API to `ovh.corail.tombstone.perk.Perk`, `PerkBranch` was added (4 branches) + `branchTier` + `getParent()`, the perks Archeologist / Channeler / Glyphographer / Guardian were added | tuning the perks is a rewrite from scratch, not a port — hence a separate stage |

### Assumptions not yet confirmed

One remains, and bytecode cannot check it: **`PlayerPreference.isReverseInventorySorting()`** is a private per-player field, toggled from the client GUI — meaning some players may have it enabled regardless of the default value. To check in game (§9 item 2): what the default value is and what exactly it does to the recovered inventory once enabled.

---

## 3. Shape of the project

### Workspace

**A separate git repo: `E:\Isuth\tombtweaks-1201\`** (renamed `E:\Isuth\tombtweaks-modern\` on 2026-10-16 when it grew into the multi-version repo), outside `modDev`.

This is not a matter of taste but of a hard obstacle: `modDev` has a **Gradle 4.9 + ForgeGradle 3** wrapper, while ForgeGradle 6 (required for 1.20.1) needs **Gradle 8.1+**. One repo = one wrapper. The same obstacle applies to the future 1.16.5 port (FG5 + Gradle 7), so **every Minecraft version gets its own workspace**.

Toolchain: Gradle 8.7, ForgeGradle 6, Java 17 (toolchain + foojay — Gradle downloads the JDK itself, the machine does not need 17 installed), `official` mappings. There is no Parchment — it was not needed.

Tombstone does **not** live in the repo: it is "All rights reserved", so its jar cannot go into a public repository. CurseMaven fetches it (`curse.maven:corail-tombstone-243707:8606942`, sha256 verified — byte for byte the same file as on CurseForge). `E:\Isuth\modDev\TestPort\` also holds `EnigmaticLegacy-2.30.1.jar` and `electroblobs-wizardry-redux-0.8.6-forge.jar` for stage 2 and `tombstone-1.16.5-7.0.0.jar` for stage 4 — at those stages also check CurseMaven before anything goes into the repo.

### Identity

- modid `tombtweaks`, group `com.spege.tombtweaks`, log prefix `[TombTweaks]`
- The same modid as on 1.12.2 — different Minecraft versions will never meet, and the name is already taken by the same project.
- `pack.mcmeta` (`pack_format: 15`) is in the jar. On 1.12.2 its absence silently disabled the lang files; on 1.20.1 Forge can synthesize it, but since the mod has `assets/tombtweaks/lang/en_us.json`, the file costs nothing and closes that class of bug.

### Three layers

| Layer | Contents | Tests |
|---|---|---|
| `core` | **Zero Minecraft types and zero Tombstone types.** The config schema as plain objects, the parser and evaluator of the protection lists, the decay schedule arithmetic, the cooldown arithmetic, the slot snapshot model on `int`/`String` | JUnit 5, `./gradlew test` |
| `platform` | Thin adapters: `ItemStack` → matcher key, `CompoundTag` ↔ snapshot, `ForgeConfigSpec` → `core` objects | indirectly |
| `feature` | Three handlers + two mixins, as dumb as possible — `core` makes the decisions | manually in game |

The split is practical, not aesthetic. On the 1.16.5 port `core` goes over copy-paste together with its tests, and it holds all the logic that is easy to break silently. A pattern proven in the same repo by `commandsuggest` (72 tests on a bare JVM).

### Mixins

The standard Forge 1.20.1 mechanism: `mixins.tombtweaks.json` referenced from `META-INF/mods.toml`, no MixinBooter, no `ILateMixinLoader`.

**The refmap is generated normally and is meant to be.** This is the opposite of the rule in `modDev/CLAUDE.md`, where `-proc:none` disables the mixin annotation processor and matching goes by explicit SRG names. That rule **does not carry over** to the new workspace and must not be copied here.

🚨 **MixinGradle 0.7 does not add the annotation processor by itself** — you need an explicit `annotationProcessor 'org.spongepowered:mixin:0.8.5:processor'`. Without it the build is green and *silent*, and the jar comes out without `tombtweaks.refmap.json`; the redirect on `use` then crashes the game in a real instance, and `runClient` will not show it, because in dev the names are official. Discovered only after Tasks 12–13, by inspecting the contents of the built jar. **The silence of the processor is not proof that the targets are correct** — the proof is an entry in the refmap.

Tombstone's classes are not obfuscated, so we write the mixin targets with plain names; `remap = false` only for members belonging to Tombstone.

### Dependencies

- `fg.deobf('curse.maven:corail-tombstone-243707:8606942')` — deobf is necessary, because in Tombstone's bytecode the references to Minecraft are SRG ones (`m_58904_` etc.). 🚨 The FG3 syntax `fg.deobf(files(...))` **silently does not work on FG6** — it supplies the obfuscated jar, with a single warning in the configuration log.
- `annotationProcessor 'org.spongepowered:mixin:0.8.5:processor'` — see §3 Mixins.
- JUnit 5 for tests. Nothing else in v1.
- An additional task `compileCoreJava8` (in `check`) compiles `core` alone with `--release 8`, so syntax or API from outside Java 8 in `core` breaks the build. It needs an explicit `javaCompiler` from the 17 toolchain, because a manually registered `JavaCompile` took Gradle's own JVM (JDK 8 on this machine, which does not know `--release`).

---

## 4. Feature: exact-slot grave restore

### Problem

Tombstone hands out the grave's contents through `ItemHandlerHelper.giveItemToPlayer`, i.e. into the first free slot. After death the inventory comes back complete, but shuffled.

### Recording — `LivingDeathEvent`, priority `HIGHEST`

This is the last moment at which the inventory layout is still the truth: `Player.die()` empties the inventory right afterwards, and everything that reaches `LivingDropsEvent` is already a flat pile with no indices. Priority `HIGHEST`, so that a handler cancelling the death does not run before us — a snapshot for a death that did not happen costs one unused entry, which the store prunes anyway.

We record a **seat plan, not a copy of the items**: for each non-empty slot only as much as is needed to recognize the item on return. A fully serialized inventory in player data is tens of kilobytes rewritten on every autosave and — worse — a second instance of the truth about what the player owned. **The grave is the only instance of the truth; the snapshot is the guests' seating chart.**

Exit conditions: server side, `gamerule keepInventory` disabled, `[restore] enabled`.

Slot encoding (carried over from 1.12.2, because it worked):

```
0-35    main inventory (the player's own indices)
100+    armour
150     offhand
200+    reserved for Curios (stage 2)
```

### Reading — `RestoreInventoryEvent`

The event provides the grave's live `IItemHandler`, so an item taken out in the handler simply does not exist for the standard distribution path, and everything we do not touch is distributed exactly as before. **Zero mixins.**

**Only for the grave's owner.** `getPlayer()` is the player who *opens* the grave — Tombstone calls `giveInventory` also for the holder of a grave key and for tomb raiding (`BlockGrave.activatePlayerGrave`). The snapshot is per player, so without this condition two players from one blast (deaths within the 10 s tolerance) would get each other's layout. Compare by name with `getOwnerName()`, not through `BlockWritableGrave.isOwner` — that one returns `true` for a grave without an `ownerId`. Found in the final review before 0.1.0.

### Binding a snapshot to a grave

A snapshot is created at death, i.e. **before the grave exists** — so it is born "pending" and must be associated with a specific grave only on recovery.

The binding goes by wall-clock time: the snapshot's `capturedAt` (the moment of `LivingDeathEvent`) against the grave's `getOwnerDeathTime()`, which is the death date in milliseconds — the same scale. On recovery we pick the snapshot with the closest `capturedAt`, within a tolerance of a few seconds, and **consume it** (remove it from the list). Thanks to this, a player with several unvisited graves gets from each of them its own seating, and not the seating from the last death.

No match within the tolerance = no snapshot = the standard path, in line with the invariant below.

The list of pending snapshots is pruned from two sides: a per-player count limit and age (a snapshot older than the grave can be, i.e. simply very old, is thrown out). Without that, one death with no return to the grave would leave an entry forever.

The scale is confirmed: the grave's `deathDate` comes from `TimeHelper.systemTime()`, i.e. straight from `System.currentTimeMillis()`.

### The matching key — where the port differs

On 1.12.2 the key is `(registry name, metadata, NBT hash)`. On 1.20.1 **there is no metadata, and damage lives in NBT**, so the key is `(registry name, NBT hash)` — and for that reason it is more brittle: a stack gets into the grave by serialization to NBT and back, and the round trip can normalize the tag (add `Damage:0`, drop an empty tag), which changes the hash.

That is why the port matches **in two passes over the whole grave at once** (`SlotPlan.assign`), which the original does not do:

1. **exact** — name + NBT hash, for all the grave's items,
2. **fallback** — the name alone, only for those that did not match in the first,
3. with no match the item stays in the grave and goes the standard path.

The original design matched greedily, item by item — and the `core` review showed with a test that an item with a drifted hash then took a seat that a later item matched exactly. The real trigger: **Tombstone merges graves**. A second death within 20 blocks of an uncollected grave adds items to it and moves its death date to the newest (`findExistingGraveLocation` + `resetDeathTime()`), so the grave contains items from two deaths, but is bound only to the snapshot of the latest. Items from the older death go the standard path; the older snapshot waits until `prune` cuts it.

The player's occupied slots (e.g. a soulbound item that never went into the grave) **do not take part in matching** — otherwise an item would get a seat that cannot be used, and would not try any other.

Stack size is recorded, but **is never a matching criterion**: `percentLossOnDeath` shrinks the stacks inside the grave before anyone sees them, so the count recorded at death is not the count that survives to recovery.

### Invariant

**Every insertion is conditional on the target slot being empty — re-checked right before taking the item out of the grave.** It is this condition, and not the "no seat" returned by `SlotPlan`, that gives the guarantee below. If in the meantime the player looted something, crafted something, or died a second time, the seat is occupied and the item stays in the grave. From this follows the guarantee that has to be held throughout the feature's development:

> The worst case is Tombstone's behaviour today — never a lost or duplicated item.

### Known risk: `isReverseInventorySorting`

9.1.4 runs, **after** our event, a pass of `PlayerPreference.isReverseInventorySorting()` that rewrites the whole `Inventory.items` in reverse order. It is a per-player preference. If enabled — it reverses our seating.

Course of action: verify in game (§9 item 2), document it explicitly in a config comment. A workaround via a third mixin **does not enter v1** — first proof that it is a real problem, not a hypothetical one.

---

## 5. Feature: grave item decay

### Goal

A grave nobody comes back for slowly loses its contents, instead of standing forever as a storage. Four protection lists let you exempt chosen items from that.

### Hook — `@Inject` in `BlockEntityPlayerGrave.serverTick(...)`

Here the port comes out clearly better than the original, because the whole auxiliary apparatus disappears:

| 1.12.2 | 1.20.1 |
|---|---|
| Scan of `world.loadedTileEntityList` every 40 ticks + comparing the class name as a string | the grave's own ticker, one per grave |
| Reflection on `getInventory` / `countTicks` / `getOwnerName` | all three public, compiled directly |
| `WeakHashMap<TileEntity, Long>` for the time of the last decay — lost on restart and on chunk unload | **zero state** |

The schedule is a pure function, because `countTicks` is saved in the grave's NBT:

```
decay when    countTicks >= startTicks
              && (countTicks - startTicks) % intervalTicks == 0
```

`serverTick` fires once per tick per grave, and `countTicks` grows by one per tick, so the condition hits exactly once per interval. No map, no state lost on restart.

The semantics of `startTicks` are unchanged relative to 1.12.2: they are the ticks for which the grave **was loaded**, not wall-clock time — because it is the same counter as there.

### Choosing the victim — unchanged

Two candidate lists, not one:

1. Unprotected stacks are consumed first.
2. Protected ones are unreachable as long as anything else is left.
3. When only protected ones are left, `protectedNeverDecay` decides: `true` = protection is an exemption (the grave stops in place), `false` = protection is only an ordering.

The choice within a list is random. The removed stack lands as an `ItemEntity` above the grave.

🚨 **After taking an item out, `grave.setChanged()` is mandatory.** The grave's inventory is a bare `ItemStackHandler` with no overridden `onContentsChanged` (checked in the constructor of `BlockEntityPlayerGrave`), so taking an item out does not by itself mark the block entity as changed. The dropped item is an entity, saved separately — without `setChanged` the chunk could save the grave in its old state and after a restart the item would be **both on the ground and in the grave**. The plan did not foresee this; it came out in the review before Task 12.

Merging a grave (see §4) resets `countTicks`, so decay starts over after every further death nearby.

The four kinds of protection — full item names, name prefixes, enchantments, `key=value` NBT matchers — go **entirely into `core`** and get tests. This is exactly the logic that is easy to break silently: an empty list must mean "nothing protected", not "everything protected".

### A bug of the original, fixed

On 1.12.2 the decay history (`GraveDecayHistory`, serving `/tombtweaks restore`) is written through `getPlayerByUsername`, which returns `null` for an offline player — so **the grave of an offline player decays without a trace in the history**, exactly in the scenario the feature was made for.

In the port the history goes by `ownerId` (UUID). When the owner is online — into their `PlayerPersisted`, as just the id and count (no full NBT — the history has no consumer, and a shulker box in an entry could bloat the playerdata). The write happens **before** `Containers.dropItemStack`, because that consumes the stack passed to it — in the original order every entry was `minecraft:air x0`. **When offline — in v1 only to the server log, at DEBUG level** (`grave at … lost … (owner … offline)`). The original design assumed a `WorldSavedData` with a transfer on login; that is deferred, because in v1 the history has no consumer — the `/tombtweaks restore` command from 1.12.2 was not ported.

---

## 6. Feature: book cooldowns

### Hook — two mixins, each with one responsibility

`ItemBook` implements the public API `ISoulConsumer`, but declares **only `canEnchant`** from it; `setEnchant` is only on the concrete books. Hence two points, not one:

1. **`MixinItemBook`** — `@Inject` at the head of `canEnchant(Level, BlockPos, Player, ItemStack)`, `cancellable = true`, `remap = false` (the target is a Tombstone method on a Tombstone class, nothing to remap). It returns `false` plus a message with the remaining time while a cooldown is active. **The block.**
2. **`MixinBlockDecorativeGrave`** — `@Redirect` on the `ISoulConsumer.setEnchant` call inside `use(...)`. It lets the call through, reads the returned `ConsumeResult`, and on `Result.SUCCESS` sets the cooldown. **The cooldown start.**

The separation is deliberate: the block sits on the book type, so it works no matter where someone calls it from; the cooldown start sits on the only existing call site, so it covers all books with one mixin.

A note on remapping in mixin no. 2: the selector `method = "use"` targets a **Minecraft method** (`BlockBehaviour.use`, `m_6227_` at runtime), so it has to be remapped — and that is the reason refmaps are enabled in this repo (§3). The inner `@At(target = "L…/ISoulConsumer;setEnchant…")` targets a Tombstone member and gets its own `remap = false`.

In line with the rule that `modDev` has written down separately: **the receiver of a `@Redirect` must be exactly the owner of the call** — here the call is `invokeinterface ISoulConsumer.setEnchant`, so the handler's first parameter is `ISoulConsumer`, not a concrete book.

This replaces the whole machinery from 1.12.2 (`PlayerInteractEvent` + a deferred check "did the stack shrink after the tick", to guess whether the use succeeded). Here success is returned directly as a record value.

### Consequence: the config becomes a map

Since the hook sits on the base class, it covers **all** of Tombstone's books. So the config stops being two fields and becomes a list of entries `registry name;minutes`, filled by default with the two entries from 1.12.2. Zero additional code, wider reach.

### Storage

`PlayerPersisted` in the player's data, a key per book: `tombtweaks_cooldown_<registry name>`, the value = the time of last use in ms (not the expiry time — this way a change of the length in the config takes effect immediately).

🚨 **The key is the book's `Item`, not the stack.** The original design keyed by `idOf(stack)` read *after* a successful `setEnchant` — and the book is consumed on use, an empty `ItemStack` in 1.20.1 reports `minecraft:air`, so for a single book the cooldown would silently not start. In the implementation: `(Item) (Object) this` in `MixinItemBook`, the receiver `ISoulConsumer` (for books an `ItemBook` instance — the `SoulConsumerProvider(this)` capability) in `MixinBlockDecorativeGrave`.

The block works solely through `ItemBook.canEnchant`, so an entry in `books` only makes sense for `ItemBook` subclasses. When a book is blocked, Tombstone additionally sends its own "not allowed" message — ours (with the number of seconds) lands on the action bar.

**The keys start from zero.** The `InsaneTweaks_` prefix from the original exists there solely so as not to reset the cooldowns of existing players during the mod split in R4 — a 1.12.2 world will never meet a 1.20.1 world, so there is nothing to migrate here and no reason to carry that name forward.

### Why not the native `CooldownHandler`

Tombstone 9.1.4 has its own cooldown system, persisted and synchronized to the client. It is not suitable: `CooldownType` is an enum with four values, not extensible without a mixin, and its only advantage — synchronization to the client — is unnecessary for us, because we send the message server-side.

---

## 7. Config

The file `config/tombtweaks-common.toml`, built by hand through `ForgeConfigSpec` (the annotation-based `@Config` system does not exist on 1.20.1).

```toml
[restore]
  enabled          = true
  debugLogging     = false

[decay]
  enabled          = false
  startTicks       = 6000
  intervalTicks    = 1200
  maxHistory       = 10
  protectedItems         = []
  protectedItemPrefixes  = []
  protectedEnchantments  = []
  protectedNbtStrings    = []
  protectedNeverDecay     = true

[cooldown]
  enabled = true
  books   = ["tombstone:book_of_disenchantment;6",
             "tombstone:book_of_magic_impregnation;6"]
```

Two differences relative to 1.12.2:

- **There is no master switch** `enableTombstoneTweaks`. It made sense when one mod held a dozen modules; with three features that have their own switches it is just extra state to get stuck in.
- **The default protection lists are empty.** The ones from 1.12.2 (`insanetweaks_properties=ashen_legacy`, `insanetweaks:sentientcodex`, prefixes) concern a specific pack; a public mod has no business assuming them.

There is no equivalent of `@Config.RequiresMcRestart`. `restore` and `decay` read the config live. `cooldown` would require a restart only because the mixin already sits in the class — its comment says explicitly **"read live"**, instead of promising a mixin gating that does not exist. It is the same trap that `modDev/CLAUDE.md` describes for its mixins: a config flag does not gate a mixin without an `IMixinConfigPlugin`, and there will be none here.

---

## 8. Out of scope for v1

| Feature | Why not now |
|---|---|
| Curios in slot restore | A curio slot is a pair `identifier` + `index`, not a flat `int` — a separate design decision. Tombstone has its own `curioAutoEquip`, so this is not a gap but an improvement. Stage 2. |
| Tuning the native perks | A rewrite from scratch for `Perk`/`PerkBranch` and a different set of perks. The most expensive element of the whole. Stage 3. |
| Random-effect whitelists | 9.1.4 has the native blacklists `unhandledBeneficialEffects`/`unhandledHarmfulEffects`. Our version offers separate pools per source and a magic-scroll pool — a real but small advantage. |
| Nerfing the `grave_dust` drop, removing the Enchanted Grave Key recipe | Minor, each needs its own verification against 9.1.4; they block nothing. |
| Curse of Possession patch | First we have to establish whether the exploit still exists at all. |
| First-kill reward, raider alignment | Generic and portable, but they are add-ons, not the core. |
| Knowledge tab (Reskillable) | Reskillable does not exist on 1.20.1. **Dropped for good.** |
| Fix for missing particle sprites | Specific to 1.12.2. **Dropped for good.** |
| Custom perks (Assimilated Knowledge, Relief for the Damned) | The first depends on SRParasites (absent on 1.20.1), the second on Enigmatic Legacy. Stage 2. |
| Wand soulbind | Requires EBW Redux and a counterpart of `ancientspellcraft:soulbound_upgrade`. Stage 2. |
| Port to 1.16.5 | Its own workspace (FG5 + Gradle 7). `core` goes over copy-paste. Stage 4. |

---

## 9. Tests and verification

### Automated tests

`./gradlew check` — 62 JUnit 5 tests (60 in `core`, 2 on the `SnapshotCodec` round trip through NBT, which run on a bare test JVM without a game bootstrap) plus two mechanical guards: `CoreHasNoForeignTypesTest` (a scan of the compiled `core` classes for `net/minecraft`, `net/minecraftforge`, `ovh/corail`, with a self-test on `TombTweaks.class`) and `compileCoreJava8` (`--release 8`). The areas in `core`:

| Area | Edge cases |
|---|---|
| Protection lists | prefix vs full name; `key=value` NBT matcher with no tag; an enchantment of a nonexistent mod; **an empty list ≠ "everything protected"** |
| Decay schedule | exactly one firing per interval; no firing before `startTicks`; `intervalTicks = 0`; `countTicks` set back |
| Victim choice | only protected + `protectedNeverDecay=true` → nothing; `=false` → protected; empty grave → nothing |
| Slot matching | exact hit; fallback to matching by name; two identical stacks → two distinct entries; no match → give up; **hash drift does not steal a seat from an exact match; merged grave; occupied seat excluded; empty grave slot** |
| Cooldown | `0` minutes = disabled; clock turned back; a malformed `name;minutes` entry; `null` and non-Strings in the config list |
| Robustness | negative `prune` limit, distance overflow in `claimNearest`, a draw out of range in the grave tick, negative `startTicks`, a defensive copy of the list in `SlotSnapshot` |

The handlers and mixins (`feature`) and the `StackViews`/`PlayerData` adapters have no unit tests — they need a live `Player`, `ItemStack` and grave. Their risk has been moved, as far as possible, to the compilation stage: every assumption about Tombstone checked in bytecode, the mixin targets confirmed by the annotation processor and by an entry in the built jar's refmap.

### In-game verification

In order from the cheapest to the most expensive, each step cuts off a specific risk:

1. **Startup log** — whether both mixins applied. "The game started" is not proof: a mixin that did not hit can silently do nothing.
2. **`[restore]`** — a death with a distinctive inventory layout, a return to the grave. Separately: whether `isReverseInventorySorting` is disabled by default and what happens once it is enabled.
3. **`[decay]`** — `startTicks` set to a few seconds, observing one grave. Then the same with the owner logged out (a test of the history fix from §5).
4. **`[cooldown]`** — using a book, a second use during the cooldown, a server restart during the cooldown.

---

## 10. Decisions not to revisit without a reason

- **A separate workspace per Minecraft version.** Forced by the clash of Gradle wrappers, not by preference.
- **`core` without a single Minecraft type.** The only thing that will carry over to the 1.16.5 port without rewriting.
- **The grave is the only instance of the truth about the items.** The snapshot stores seats, not items.
- **The worst case of slot restore = standard behaviour.** Never a lost or duplicated item.
- **Refmaps on 1.20.1 are enabled, and the annotation processor is declared explicitly.** The `-proc:none` rule from `modDev/CLAUDE.md` does not apply in this repo. After every mixin change: check the entry in `tombtweaks.refmap.json` in the built jar.
- **Tombstone's jar does not go into the repo.** The licence is "All rights reserved"; the source is CurseMaven.
- **In-game verification on the reobfuscated jar in a real instance, not in `runClient`.** Dev uses official names and hides exactly the class of bugs (a missing refmap) that is the most dangerous for this mod.

---

## 11. Addendum 0.2.0 (2026-10-04) — grave keys and disabled enchantments

A player's request: "make the key immune to the Soulbound enchantment" and "disabling enchantments has to work — they are still in chests". Findings from bytecode, identical in mechanism in Tombstone 4.8.0 (1.12.2), 7.0.0 (1.16.5) and 9.1.4 (1.20.1):

- **The key is hard-wired as soulbound.** 1.12.2/1.16.5: `EntityHelper.hasSoulbound` returns `true` for `ModItems.grave_key`; 1.20.1: the key has `forceSoulbound`, which makes `ItemGeneric.getEnchantmentLevel(soulbound)` = 1. No path looks at `enableEnchantmentSoulbound`. The decision at death is made in a single call in `DeathHandler.storeSoulboundsOnBody`.
- **Tombstone respects its config** at the table (`canApplyAtEnchantingTable`), in the anvil (`canEnchant`), on books (`isAllowedOnBooks`) and in its loot tables (`PreciseEnchantFunction` checks `isEnabled()`). It does **not** override `isDiscoverable()` (which vanilla `EnchantRandomlyFunction` filters on — for a book it skips `canEnchant`) nor `isTradeable()` (`VillagerTrades.EnchantBookForEmeralds`). That is the leak.

Implementation in 0.2.0:
- `[graveKeys] survivesDeath` (default `true`, read live). On `false`: a `@Redirect` of `Helper.isSoulbound` in `storeSoulboundsOnBody` returns `false` for a key; an `@Inject` at the head of `TombstoneEnchantment.canEnchant` refuses Soulbound on a key. Logic in `feature/gravekey/GraveKeyPolicy`.
- `MixinTombstoneEnchantment` **adds** `isDiscoverable()` / `isTradeable()` = `isEnabled() && super`. The mixin class inherits from `Enchantment`, so reobf swaps the names to `m_6592_` / `m_6594_` — checked with `javap` on the built jar. None of the 12 subclasses overrides these methods or `canEnchant`. No option: this is a bug fix, and for enabled enchantments the behaviour does not change.

Deliberately out of scope: the Book of Soulbound (`ItemBookOfSoulbound`) used on a key under `false` is still consumed; loot of other mods that build their own enchantment pool.

On 1.12.2 the same for the key went into `modDev/tombtweaks` 1.10.0 (branch `feat/tombtweaks-grave-key-soulbound`). The leak into chests on 1.12.2 is closed by the existing `enchanteraser` — with no new code. On 1.16.5 tombtweaks does not exist yet; at the port (stage 4) both fixes carry over 1:1, because the mechanism in Tombstone 7.0.0 is the same as in 4.8.0.
